---
name: perf-audit-shadow-light-vl
description: "FULL perf audit 2026-06-09 of shadows + surface light + volumetric (Java addon AND Photon GLSL inject) — verified hot spots with file:line, ranked refactor plan: two-layer static/dynamic shadow maps, sticky tile assignment, moving-light block-collect quantization, merged diffuse+specular loop (halves PCSS), VL per-step shadow fast path"
metadata:
  mod_scope: both-mods-lockstep
  ported_from: IRLite
  consolidated: 2026-06-18
  type: project
  originSessionId: perf-audit-full
---

Full performance audit (2026-06-09) of the shadow / surface-light / volumetric pipeline, BOTH sides:
Java addon (src/client/.../irlite) and the Photon inject (Shadres/Modification). Extends
[[shadow-bake-refactor-audit]] (whose #1-#4 are verified PRESENT in code) with the remaining Java gaps
and the first SHADER-side perf pass. Parent: [[MEMORY]]. User rule: ranked risky/structural first,
micro-tricks last. Proposals approved in principle; implement per-item on request.

=== VERIFIED HOT SPOTS — JAVA ===
H1 [WORST, film scene] Cutout block re-tessellation per pass (old B8). ShadowRenderer.renderBlocksDepthCutout
   (ShadowRenderer.java:490-618) immediate-mode brm.renderBlock per cutout block, re-run EVERY dirty pass:
   6x per point per frame when an actor is in range. Leaves/bars/doors in shadow_block_radius -> tens of
   thousands of quads re-tessellated per frame per point light.
H2 Static casters re-render on dynamic dirty (old B6 remainder). dirty=true (actor in range) redraws the
   FULL in-range content: model-block form trees (renderCaster -> FormRenderer.render), opaque block VBO
   into all 6 faces (B7), cutout (H1). Only the actor actually changed.
H3 Tile-shift rebake storm. Tiles/layers assigned by iteration order (ShadowBaker.java:159-257/260-337);
   any light dropping out (behind-camera cull :178/:277, range/skip, removed) shifts every later light's
   tile -> lastTile mismatch -> full rebake of ALL shifted lights (points = 6 faces). Camera panning across
   a light's behind-plane boundary re-bakes the whole scene repeatedly. Also blocks safe rollout of the
   full 6-plane frustum cull (audit #2 open half).
H4 Moving light + block shadows = full re-collect EVERY frame. BlockShadowCache.hash is raw float bits of
   pos (BlockShadowCache.java:137-145); an entity/replay-attached light (render path) or transform-animated
   model-block lamp changes hash every frame -> BlockShadowCollector.collectForLight walks the whole sphere
   (~58k getBlockState at r=24, ~3.7M at r=96; BlockShadowCollector.java:38-135) + buildBlockVbo re-tessellate
   + new VertexBuffer upload + old close (ShadowRenderer.java:372-381) per frame.
H5 Double block-entity walk per frame. LightCollector.collect (LightCollector.java:58-128, MAX_DIST 256)
   AND ShadowBaker.collectModelBlocks (ShadowBaker.java:713-796, COLLECT_DIST 72) each iterate ALL
   blockEntityTickers and call world.getBlockEntity per in-range ticker. ~2x map lookups per ticker per frame.
H6 immediate.draw() per caster (ShadowRenderer.renderCaster:203) — flush per caster instead of per pass;
   32 casters x faces = up to ~192 flushes/frame.
H7 VRAM (old C12): PointShadowArray allocates all 96 layers up front (PointShadowArray.java:65-69):
   MEDIUM 96 MiB, ULTRA 1.5 GiB; spot atlas ULTRA = 16384^2 = 1 GiB (SpotlightDepthAtlas: TILE_SIZE*4 grid).
H8 SSBO upload glBufferSubData into possibly-in-use buffer (LightBuffer.upload:120-123) — implicit sync risk;
   no orphaning. Tiny data (<=20.5 KB) so minor.

=== VERIFIED HOT SPOTS — SHADER (Shadres/Modification) ===
S1 Shadow visibility computed TWICE per light per pixel. irlite_lightDiffuse (irlite_lights.glsl:479-483)
   and irlite_lightSpecular (:543-547) each run full PCSS; IRLITE_SPECULAR_HARD is OFF by default (:49)
   -> default quality 2 = 10 blocker + 18 PCF = 28 taps, x2 = 56 atlas/cube taps + 2 view-proj
   reconstructions per shadowed in-range light per pixel. Both calls use identical args when
   SPECULAR_HARD off -> merging is visually lossless.
S2 Both surface loops duplicate per-light geometry math (toLight/dist/falloff/cone) — same fix as S1 (merge).
S3 dist computed with length() BEFORE the range check (:458, :521-522, VL :734) — sqrt paid for every
   out-of-range light; compare dist^2 vs radius^2 first.
S4 Transcendental waste in irlite_spotShadow (:243,268): outerDeg=degrees(acos(cone.x)*2) then
   fY=1/tan(radians(outerDeg)*0.5) == analytic fY = cone.x/sqrt(1-cone.x^2) (clamp for outerDeg>=1deg
   edge). acos+tan per call per pixel per light (x2 with S1).
S5 VL per-step shadow recomputes per-light constants (irlite_lights.glsl:769-776): each of IRLITE_VL_STEPS
   (14) steps calls full irlite_spotShadow/pointShadow -> normalize(dir), acos/degrees/tan basis build,
   no-map check, normal-offset normalizes — ALL constant per light per ray. Needs a specialized fast path:
   hoist basis/fY/far before the march, per step = project + 1 tap compare.
S6 absorption = exp(-extinction*stepLen) is constant per light but computed inside the step loop (:780).
S7 Defensive normalize(light.dirType.xyz) in 4 places (diffuse :472, spec :536, spotShadow :241, VL :696)
   — CPU already uploads unit dir (LightCollector normalizes; contract in [[addon-light-buffer-ssbo]]).
S8 All shader loops iterate ALL SSBO lights with no CPU-side cull: lights fully behind the camera plane
   (dot(pos-cam,fwd) < -range) cannot light any visible fragment NOR any forward VL ray point — safe to
   drop at LightRegistry.flush() so per-pixel loops shrink (same predicate as the bake cull).
VL pass context: c0_vl is VL_RENDER_SCALE 0.50 -> quarter area; steps default 14; per-step 1-tap hard
   shadow already (fast=true); per-light "shadows off" free via vlParams.w<0 early return.

=== RANKED PLAN (risky/structural first) ===
R1 Two-layer shadow maps (static base + dynamic overlay) — fixes H1+H2 at the root, the structural win for
   films. IMPLEMENTED 2026-06-09 (build green, runtime verification + commit pending). As landed:
   glCopyImageSubData (GL43, not blit — stateless, point copies all 6 layer-faces in ONE call) restores the
   static base each overlay frame; begin* grew (toStatic, clear) params; static tile baked only when
   staticStale (new lastStaticSig/lastStaticTile/lastStaticBlocks maps, same purge/reset/retain as live);
   point overlay skips faces no dynamic sphere touches (faceHasDynamic); !hasStatic overlay = clear+dynamics
   (no static texture allocated -> static layer VRAM is lazy, first overlay bake only); !cache = legacy full
   bake (CASTERS_ALL). wasDynamic trailing-rebake became a trailing copy (leave-frame). Caster filters
   CASTERS_ALL/STATIC/DYNAMIC. KNOWN minor regression: a continuously-MOVING light in overlay re-bakes its
   static tile every frame + copy (sig changes) — slightly worse than the old combined bake; R3 (quantized
   block cache) is the moving-light fix and could later add a "skip static tile when sig churns" heuristic
   if it bites. Detail: [[addon-shadows]].
R2 Sticky tile assignment — fixes H3, prerequisite for the full frustum cull. IMPLEMENTED 2026-06-09
   (build green, runtime verification + commit pending). Design as landed: owner/active arrays per kind
   in ShadowBaker (no maps); owner keeps tile across skipped frames; free tile first, else steal
   most-stale owner inactive >= STALE_FRAMES=2 (no same-frame robbery -> no ping-pong; victim dirty
   state purged), else -1. BONUS WINS vs plan: (a) returning unchanged owner re-uses its old map with
   zero rebake (state retained while owning; retainDirtyState keeps owners, bakedIds removed);
   (b) quality-preset switch now resets tiles+state (fixed latent bug: old code kept lastSig/lastTile
   across the texture realloc -> lights silently sampled blank/unallocated maps until next natural
   dirty); (c) model-block occluder hash now folds in form identityHashCode (form swapped in place was
   invisible to the sig — mattered once state survives skipped frames). Profiler added in same change:
   -Dirlite.profileShadows=true -> 1/s log of avg/max bake ms + spot/point rebake counts.
R3 Quantized block-cache key for moving lights — fixes H4. IMPLEMENTED 2026-06-09 (build green, runtime
   verification + commit pending). As landed in BlockShadowCache.getOrCompute: center snaps to
   Math.round per axis, radius = ceil(radius) + SNAP_PAD 0.87 (> cell half-diagonal sqrt(3)/2); the
   quantized sphere is what gets hashed, collected AND section-indexed -> a moving lamp re-collects only
   on a 1-block cell crossing (was: every frame), and the stable list instance also stops the per-frame
   block-VBO rebuild. Padding blocks past the light's range are clipped by the bake far plane (blocks
   between shadowBlockRadius and radius+pad now cast — tiny quality IMPROVEMENT, not a regression).
   NOT done: spreading a re-collect over frames (revisit if cell-crossing spikes show in the profiler).
R4 Merge diffuse+specular into one loop, visibility once — fixes S1/S2 (+S3 folded in). IMPLEMENTED
   2026-06-09 in Shadres/Modification (synced to run/shaderpacks/Photon_IRLite; runtime + commit pending).
   irlite_lightSurface(fragWorld, normal, viewDir, material, nonTerrain, out diffuse, out specular)
   replaces irlite_lightDiffuse + irlite_lightSpecular; ONE PCSS visibility shared by both terms;
   IRLITE_SPECULAR_HARD/IRLITE_SPEC_SHADOW_FAST removed (glsl + shaders.properties screen.irlite_shadows +
   en_US.lang); dist^2-before-sqrt reject; rec2020 conversion stays PER LIGHT (specular multiplies the
   converted colour component-wise — hoisting the matrix out of the sum is only valid for diffuse);
   attenuation<=0 continue after shadow (toon(0)=0 -> identical); roughness>0.95 now skips only the spec
   term (diffuse still accumulates). d4 call site = one call + two #ifdef-gated adds, placed at the old
   diffuse anchor. Math verified term-for-term vs the old loops.
R5 VL fast shadow path — fixes S5+S4+S6. IMPLEMENTED 2026-06-09 in Shadres/Modification (synced; runtime +
   commit pending). irlite_vlSpotStep/irlite_vlPointStep (under IRLITE_VL_SHADOWS in the VL block) = one
   hard tap with all per-light constants hoisted before the march: shHas/shTile/shTx/shTy/shLayer; spot
   basis shS/shU + ANALYTIC shFY = c*inversesqrt(max(1-c^2,1e-12)) capped at 114.58865 (=1/tan(0.5deg)),
   which reproduces the old degrees(acos)/tan(radians) chain incl. its outerDeg>=1 clamp exactly (fY
   monotonic in c -> min() == clamp). Receiver nudge passed as 2*IRLITE_SHADOW_NORMAL_OFFSET*L - toLight
   == full fn's normal-offset with normal=L (algebra verified; point path: refDist = dist-2*offset,
   dir = toLight*(-refDist/dist) — collinear shrink). absorption + oneMinusAbsorption hoisted out of the
   step loop. Full irlite_*Shadow fns still compile into c0_vl (now unused there) — dead code, harmless.
M1 Drop behind-camera lights from the SSBO at flush (S8) — shrink all per-pixel loops. Low risk.
M2 Single block-entity walk per frame shared by LightCollector + ShadowBaker (H5): collect ModelBlockEntity
   list once at renderWorld HEAD, both consumers read it. Low risk.
M3 VRAM sizing (H7): allocate PointShadowArray layers by actual shadowed-point count (grow/shrink with
   hysteresis); consider 2x2 spot grid at ULTRA. Needed if R1 doubles maps. Low risk, VRAM-only win.
M4 Full 6-plane frustum light cull (audit #2 open half) — only AFTER R2; reuse MC Frustum or build from
   proj*view at HEAD. Remember the Photon caveat: culled light = unshadowed VL shafts while VL shadows on
   ([[shader-volumetric]]).
M5 Batch immediate.draw() once per pass instead of per caster (H6) — verify MorphRenderer state safety.
U1 dist^2-before-sqrt (S3) — DONE in the surface loop via R4; still open in the VL clip (length needed
   anyway there). U2 analytic fY (S4) — DONE for VL via R5; the full irlite_spotShadow (surface path) still
   uses the acos/tan chain (1x per light per pixel — could adopt the analytic form too, micro). U3
   absorption hoist (S6) — DONE via R5. U4 drop defensive normalizes (S7) — open. U5 SSBO orphaning via
   glBufferData(NULL)+Sub (H8) — open. U6 store RenderLayer in BlockShadowEntry (subsumed by R1) — open/
   minor (only static bakes pay now). U7 obsolete (R4 removed IRLITE_SPECULAR_HARD).
NOT recommended: hardware PCF compare samplers (blocker search needs raw depth -> dual sampler views,
   texture-object state conflict with Iris binding); geometry-shader single-pass cubemap (MC pipeline can't).

STATUS 2026-06-09: R-tier fully implemented. R2 committed c6328d4, R1 committed 4d48ad0 (both build green;
R1 runtime-unverified). R3 (Java, build green) + R4/R5 (Shadres/Modification, synced to
run/shaderpacks/Photon_IRLite) await user runtime verification + commit. After verification: regen
photon.irlpatch from Modification and port R4/R5 + the earlier normal-offset/VL-shadows changes into
iterationrp.irlpatch (the IterationRP gap keeps widening — its inject still has the OLD separate
diffuse/specular loops and unshadowed VL). M1-M5 tiers still open. Shader doc sync: [[shader-irlite-glsl]]
(merged surface fn) + [[shader-volumetric]] (VL fast taps) updated 2026-06-09.

---

## Связь с IRL-redactor (объединение памяти 2026-06-18)

- **Применимость:** both-mods-lockstep — IRLite-сторонняя история (2026-06-09): Java-часть перф-аудита (H1-H8, R1-R3, M1-M5) перекрыта канон-работой редактора — Tier-1/Tier-2 реализованы и задокументированы на main(1.20.4), см. [[project-shadow-bake-perf-audit]] + [[plan-shadow-bake-perf]] (6 опт пока только в redactor, IRLite их не имеет — бэкпорт Ф1). Shader-сторона (S1-S8, R4 merge diffuse+specular, R5 VL fast-shadow в Shadres/Modification/Photon-инжекте) = shader-inject контент, авторится в IRLite, канона в редакторе нет. Файл сохраняется как IRLite-история, НЕ удалять.
- **Связанные в redactor:** [[project-shadow-bake-perf-audit]], [[plan-shadow-bake-perf]] (superseded-by-redactor)
- Источник: портировано из памяти IRLite; оригинал оставлен как архив.
