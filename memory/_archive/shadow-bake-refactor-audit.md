---
name: shadow-bake-refactor-audit
description: "Shadow-bake performance audit + refactor plan — where the scene cache gives NO gain (global all-or-nothing, dead in animated films) and where the rebake burns work (uncached casters, 6x block draw, glGet stalls). Ranked fix plan."
metadata:
  node_type: memory
  mod_scope: both-mods-lockstep
  ported_from: IRLite
  consolidated: 2026-06-18
  type: project
  originSessionId: shadow-bake-refactor
---

Performance/refactor audit of the IRLite shadow bake (started 2026-06-09). Parent: [[addon-shadows]].
Extends [[shadow-entity-parity-findings]] (which covered correctness #1 anim-freeze + #2 occluder selection)
with the PERF angle. Verified against current code, not just memory.

PERF MODEL TODAY: the only speed optimization is the global `dirty` flag in ShadowBaker.bake()
(ShadowBaker.java:111-113) — one `sceneHash(n)` over ALL lights + ALL occluders; if unchanged, skip the
GL depth render (`if (dirty)` spot :157 / point :204). Everything else (collect, countInRange,
collectBlocks, the hash itself) runs every frame regardless.

=== WHERE THE CACHE GIVES NO GAIN ===
A1 [ROOT] Global all-or-nothing cache. sceneHash (ShadowBaker.java:266-280) is one sum over everything;
   ANY occluder moving a hair flips dirty -> EVERY light fully rebakes (point = 6 passes). 10 static lamps +
   1 walking player = all 10 rebake/frame.
A2 Dead in the mod's main use case. BBS films/replays animate actors continuously -> global hash changes
   every frame -> dirty always true -> cache never helps exactly where the mod is used. Only helps on a fully
   static scene with a moving camera (camera is correctly NOT in the hash).
A3 Anim-on-the-spot paradox (parity #1): sceneHash sums only positions ox/oy/oz (:275-278), no yaw/anim phase
   -> an actor swaying in place doesn't dirty -> shadow FREEZES. So the cache is both too coarse (A1) and too
   blind (A3) at once.
A4 Even a cache HIT isn't free: on !dirty it still runs collect() over all world entities (:316), countInRange
   per light (:139/:196), collectBlocks per light (:146/:198), and sceneHash.
A5 No camera-frustum light cull: bake() walks all registry lights; a light whose influence sphere is fully
   off-screen still bakes (point = all 6 faces).

=== WHERE THE REBAKE BURNS WORK ===
B6 [DOMINANT] Caster rendering is fully uncached. renderCaster (ShadowRenderer.java:156-199) re-runs the full
   BBS form tree / vanilla dispatcher every call — 1x per spot, 6x per point face (ShadowBaker.java:206-218).
   Only blocks got a VBO cache (Stage B); the heavier actor/morph path did not. In a film (always dirty) this
   is paid every frame. NOTE: a working per-light dirty cache (A1 fix) subsumes most of this waste — static
   casters stop re-rendering when nothing in their range moved.
B7 [DEFERRED to #6, 1378305] Block VBO drawn 6x per point with no per-face cull: the whole sphere VBO redrawn
   each of 6 faces; each face sees ~1/6. Left as-is — one static POSITION-only depth-draw is cheap; a per-face
   cull (6 VBOs) isn't worth the VRAM/rebuild cost now that B10 removed the expensive caster draws. See plan #4.
B8 Cutout blocks have NO cache: renderBlocksDepthCutout (ShadowRenderer.java:476-604) is immediate-mode
   brm.renderBlock per block, 6x per point face, re-tessellating the BakedModel each time.
B9 savePassState does ~5 glGet* per pass (ShadowRenderer.java:694-717) = CPU<->GPU stalls; 16 points x 6 faces
   = ~480 round-trips/frame. State is identical across passes -> snapshot ONCE per bake.
B10 [DONE 1378305] Point rendered ALL occluders into all 6 faces even if they cluster on one side. Now
   renderInRangeFace culls casters per 90° face frustum (sphereTouchesFace). Faces still all cleared.
B11 [DONE 1378305] Spot used a SPHERE test (scanInRange/renderInRange), not a cone — casters outside the cone
   were draw-called (clipped by projection, but CPU paid). Now insideCone skips them in scanInRange +
   renderInRangeCone; also removes them from the dirty signature so they stop forcing re-bakes.

=== MEMORY / MISC ===
C12 VRAM allocated for full 16+16 regardless of light count. PointShadowArray reserves LAYER_COUNT=96 layers up
    front (PointShadowArray.java:65-69): MEDIUM ~96 MiB, ULTRA ~1.5 GiB even for one point. Spot atlas = full 4x4
    (SpotlightDepthAtlas.java:76-80).
C13 first-32 not nearest-32 (parity #2, ShadowBaker.java:318 break): entities can starve model-blocks/replays;
    >32 occluders = arbitrary set.
C14 [MITIGATED 2026-06-09, commit a29794b] block collect radius was a hardcoded 24 but far-plane = full range ->
    with range>24 far blocks were lit but cast no shadow. Now a BBS setting shadow_block_radius (default 24, range
    4..96) so a big light can shadow farther; the underlying limitation stays (blocks past the radius are still
    unshadowed, and the far-plane is still the full range), the cap is just user-tunable now.

=== RANKED REFACTOR PLAN ===
1. Per-light dirty (replace global sceneHash). DONE 2026-06-09, committed ec120e5 (build green). Final design
   diverged from the literal "anim token" plan — two key findings:
   (a) age is NOT usable as an anim token. StubEntity.update() does age+=1 UNCONDITIONALLY every tick and
       ModelBlockEntity.tick() calls it every tick, so a STATIC model-block lamp's stub age ticks regardless of
       whether its form animates -> an age token would dirty every model-block lamp every frame (kills the
       headline static-lamp win; worse than today's static-scene-moving-camera cache). BBS morph-internal anim
       phase has no cheap clean read either.
   (b) So instead of guessing animation, CLASSIFY occluders (closer to proven IRLEngine): entity/replay in range
       = dynamic subject -> light re-bakes EVERY frame (no freeze, fixes A3 for the main use case, no smoothness
       loss); model blocks = static-by-transform (cached; their in-place morph anim still freezes = documented,
       NOT a regression — today's position hash freezes it too). World terrain blocks = handled by list-instance
       reference compare (BlockShadowCache returns same instance until a block in range changes).
   Per-id state (key=getId, evicted like BlockShadowCache.retainOnly): lastSig (Long2Long: light geom incl.
   +cosOuter & +model-block rotation, two latent miss-fixes vs the old hash, + order-independent SUM of in-range
   model-block hashes), lastTile (Long2Int, also the first-bake marker via containsKey), lastBlocks (Long2Object,
   ref compare), wasDynamic (LongSet). dirty = !cache || first-bake || dynamicInRange || wasDynamic ||
   sig!=lastSig || blocks!=lastBlocks || tile!=lastTile. SELF-CAUGHT BUG -> wasDynamic: when a dynamic subject
   LEAVES range the sig returns to its prior value so the cache would reuse the last map with the subject baked
   in forever (the old global hash caught it via the subject's position); wasDynamic forces ONE trailing rebake
   on the dynamic->static transition. Tile-shift guard (tile!=lastTile) re-bakes a cached light whose atlas slot
   moved because a neighbor dropped out. Dirty-state evicted by bakedIds (lights that actually got a tile this
   frame) so a skipped/just-returned light is a first-bake (no stale-tile sampling); block caches still retained
   for ALL registered lights (survive a momentary out-of-range frame). WorldBlockChangeMixin simplified:
   invalidateAt + ref-compare replace the global markBlocksDirty (removed). Files: ShadowBaker.java (rewritten),
   WorldBlockChangeMixin.java.
2. Camera-frustum light cull (A5). DONE 2026-06-09 (commit 181cf88, build green, runtime-verified) — but only
   the SAFE SUBSET: behind-camera half-space cull. ShadowBaker.bake takes a cameraForward (Vec3d.fromPolar from
   GameRendererLightMixin); a light with dot(pos-cam, forward) < -range (whole influence sphere behind the camera
   plane) is `continue`d BEFORE scanInRange/collectBlocks -> no bake (point=6 faces) + no CPU collect, tile NOT
   consumed (freed for visible lights, helps >16-light scenes), shadowTile stays -1 (never sampled off-screen),
   first-bake on return. Provably safe (exact for the behind half-space; never culls a side-of-frustum light, so
   no shadow can go missing). Chosen over a full 6-plane frustum because a reliable frustum at renderWorld HEAD is
   fiddly (this frame's MC Frustum isn't built yet) and a wrong plane = missing shadow. KEY de-risk: irlite_volumetric
   does NOT sample the shadow map (see [[shader-volumetric]]), so culling the bake can't break an off-screen light's
   on-screen volumetric shafts. ** UPDATE 2026-06-09 (commit f20aa3f): this de-risk is now PARTLY INVALID for Photon —
   IRLITE_VL_SHADOWS makes irlite_volumetric sample the map, so a behind-camera-culled light (shadowTile=-1) now has
   UNSHADOWED VL shafts (still scatters, just not occluded). Accepted/deferred per user scope; revisit = relax this
   cull when VL shadows are on. IterationRP still matches the original de-risk (its VL is unshadowed). [[shader-volumetric]] ** NOT chosen: the audit's "keep tile" — freeing the tile (Option B) is the bigger win
   and the tile-shift dirty guard from #1 handles the reshuffle. STILL OPEN: full 6-plane frustum (side culling).
3. Hoist glGet* out of the per-pass loop — snapshot once per bake (B9). DONE 2026-06-09 (commit 181cf88).
   ShadowRenderer.savePassState now snapshots only on the FIRST pass of a bake (guarded by passStateSaved);
   ShadowBaker.bake calls ShadowRenderer.beginBake() once to re-arm it. endPass restores from the single snapshot
   as before (state is invariant across passes since each endPass restores the original). NOTE: beginBake() MUST be
   wired or passStateSaved sticks true forever (frozen snapshot) — that's why #2+#3 are one commit (they intertwine
   in ShadowBaker).
4. Per-face block + occluder cull for points (B7/B10) + cone test for spots (B11). PARTIALLY DONE 2026-06-09,
   committed 1378305 (build green + runtime-verified by user). Did B11 + B10 (occluder culls, both in
   ShadowBaker.java; ShadowRenderer untouched); B7 (block VBO) DEFERRED to #6, see below.
   B11 cone cull (spot): insideCone(dir,coneTheta,V,r) — keep occluder unless phi-alpha > coneTheta, where
     phi=angle(V,axis), alpha=asin(r/dist), coneTheta=acos(cosOuter). PROVABLY SAFE: a sphere entirely past
     coneTheta sits at a larger axis angle than ANY lit fragment (shader cuts light hard at cosOuter), so it can
     only shadow unlit fragments. +0.05 rad edge slack. Spot axis normalized once per light (collector already
     gives unit; defensive). The SAME predicate runs in scanInRange (gated by a `cone` flag) AND the new
     renderInRangeCone, so counted set == rendered set. BONUS: cone-culled occluders are also excluded from
     dynamicInRangeScratch/staticOccSigScratch -> an actor/block OUTSIDE the cone no longer dirties the spot every
     frame (real re-bake saving, not just a draw skip). wasDynamic still clears a subject that walks out of the cone.
   B10 per-face occluder cull (point): renderInRangeFace(face) -> sphereTouchesFace = conservative sphere-vs-90°-
     face-frustum (4 side planes through the light, inward normals axis±tangent, margin k=r*sqrt2). Removes ~5/6 of
     the (expensive) caster draws per point. CRITICAL: all 6 faces still beginPointFace/clear/endPass — a vacated
     face must be cleared or it keeps a stale-shadow phantom; only the caster DRAW is per-face culled. Union of 6
     faces covers every sphere-in-range occluder, so none is globally dropped (a boundary-straddler draws into 2-3
     faces). scanInRange for points stays cone=false (counts the whole sphere = correct dirty/count gate).
   B7 DEFERRED (documented): the block VBO is ONE static POSITION-only depth-draw, cached (static lamps never
     rebuild geometry), cheap per vertex; the 6x redraw is GPU vertex/raster only. A per-face cull needs 6 per-face
     VBOs (~1.5x VRAM from boundary dup, 6x rebuild on a terrain edit near the lamp, 6 VBO maps) for a GPU-only win
     that B10 already made minor (the expensive casters are gone). Revisit in #6 only if a block-heavy scene profiles
     hot. New ShadowBaker helpers: scanInRange(+dir,coneTheta,cone), renderInRangeCone, renderInRangeFace,
     insideCone, sphereTouchesFace, consts CONE_ANGLE_MARGIN=0.05f / SQRT2.
5. nearest-32 occluder selection (C13).
6. Optional: cutout VBO cache (B8), static-caster mesh cache (B6 remainder), VRAM sized to actual lights (C12).

Status: #1 (ec120e5), #2 (behind-camera subset) + #3 (181cf88), #4 (B11 cone + B10 per-face occluder cull, 1378305)
DONE — all build green + runtime-verified by user 2026-06-09. PENDING: #2 full 6-plane frustum (side cull),
#5 nearest-32 occluder selection, #6 (B7 per-face block VBO cull / cutout VBO cache / static-caster mesh cache /
VRAM sizing).
Note B6 (uncached caster render) is now largely subsumed by #1 for STATIC casters (model-block lamps with
nothing dynamic in range stop re-rendering); the remaining B6 cost is real only for lamps near live subjects.
Open limitation from #1: a model block whose form plays an in-place morph animation (no transform change) has a
frozen shadow — candidate for a later per-form animation token. Adversarial review of #1-#3 not run.

---

## Связь с IRL-redactor (объединение памяти 2026-06-18)

- **Применимость:** both-mods-lockstep — Относится к оркестрации/бейкингу теней, общей для обоих модов в lockstep (зеркалит шов ShadowCasterSource). Это IRLite-сторонняя ИСТОРИЯ перф-работы (2026-06-09); канон-версия той же работы — redactor-овские [[plan-shadow-bake-perf]] + [[project-shadow-bake-perf-audit]] (пере-проверены ~93 агентами и реализованы заново на main 1.20.4). Файл сохранён как генезис-аудит, не удалять.
- **Связанные в redactor:** [[plan-shadow-bake-perf]], [[project-shadow-bake-perf-audit]] (superseded-by-redactor)
- Источник: портировано из памяти IRLite; оригинал оставлен как архив.
