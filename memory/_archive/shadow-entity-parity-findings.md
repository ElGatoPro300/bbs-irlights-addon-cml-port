---
name: shadow-entity-parity-findings
description: "Verification of IRLite entity-shadow Java path vs old IRLEngine — core bake is correct; world-block shadows DONE (committed db7aec5), 2 open follow-ups (cache animation, occluder selection)"
metadata: 
  node_type: memory
  mod_scope: both-mods-lockstep
  ported_from: IRLite
  consolidated: 2026-06-18
  type: project
  originSessionId: 06b336ac-7364-467e-9350-0a5dd883d492
---

Audit done 2026-06-06: compared IRLite entity-shadow Java code against the old IRLEngine
(IRLEngine uses UBO, IRLite uses SSBO — buffer mechanism intentionally differs, ignore that).
Parent: [[addon-shadows]].

VERDICT: the core bake algorithm on the Java side is CORRECT. No bug in the
"entity -> silhouette into depth map" path.
- Matrices: spot = perspective(fov=outerDeg, near .05, far=range)+lookAt w/ pickStableUp;
  point = 6 faces 90deg w/ correct cubemap dir/up table. Same as IRLEngine.
- Depth pass: bind FBO -> viewport+scissor tile -> clear -> draw -> endPass restores
  FBO/viewport/scissor/proj/sorter/colorMask/modelview. No GL leak into Iris/MC.
- drawEntity() is byte-for-byte identical to proven IRLEngine (morph player -> morph living
  -> vanilla EntityRenderDispatcher fallback, FULL_LIGHT, lerped pos, ShadowBakeState gate).
- Per-light cull countInRange/renderInRange (sphere, reach=range+occluderRadius). (The old noEntityShadows flag was replaced 2026-06-09 by a "shadows" master toggle: OFF = no entity AND no block shadows for that light; entity-only skip removed.)
- setShadowTile -> SSBO vlParams.w; tiles still assigned on cache-hit so shader always reads a valid map.

The 3 deltas below are POLICY differences (cache granularity + occluder selection), NOT bake-algorithm defects.

OPEN FOLLOW-UPS (revisit in a later session; not yet acted on):

1. [BIGGEST] Cache ignores in-place animation/rotation. RESOLVED for entity/replay 2026-06-09 (commit ec120e5,
   the [[shadow-bake-refactor-audit]] #1 per-light dirty refactor). The global position-only sceneHash is gone;
   entity/replay occluders are now classified as DYNAMIC -> their light re-bakes every frame, so in-place
   animation no longer freezes. NOT chosen: the "yaw/anim-phase token" idea below — age can't be a token (it
   ticks unconditionally; see the audit). STILL OPEN (narrow): a MODEL BLOCK whose form plays an in-place morph
   animation with no transform change stays cached -> frozen (model blocks are static-by-transform to keep
   decorative lamps cacheable; same freeze as the old hash, so not a regression). Candidate later fix: a
   per-form "is animating" signal folded into the model-block static hash. File: client/light/shadow/ShadowBaker.java.

2. Occluder selection = first-32 in world.getEntities() order, NOT nearest-32.
   The entity loop runs first and can fill all 32 MAX_OCCLUDERS slots before model-blocks/replays
   -> they get zero slots (starvation). And with >32 occluders the picked set is arbitrary, not
   nearest-to-camera. IRLEngine EntityShadowTracker.insertSorted() merged ALL types by distance,
   nearest-32 won regardless of type. Edge-case (needs 32+ occluders nearby), lower priority than #1.
   Fix: restore insertSorted across collect/collectModelBlocks/collectFilmReplays. File: ShadowBaker.java.

3. World-block (terrain) shadows — DONE 2026-06-06 (Stage A + Stage B; build green; Stage A visually verified in-game by user).
   Was: IRLite occluders = entities/model-block/replay only; terrain cast no shadow. Now ported from IRLEngine.
   STAGE A (done, "correctness, no cache"):
     - New: client/light/shadow/BlockShadowEntry.java + BlockShadowCollector.java (verbatim IRLEngine port;
       shape priority cullingShape->collisionShape->outlineShape, skip BlockRenderType.INVISIBLE = the
       ModelBlock hosting the lamp, cutout detect via RenderLayers.getBlockLayer -> entry.shape=null).
     - ShadowRenderer.renderBlocksDepth(List) v1 = immediate-mode (Tessellator QUADS+POSITION via QuadBoxConsumer
       for shaped entries; renderBlocksDepthCutout via BlockRenderManager.renderBlock + immediate.draw for cutout).
       NO VBO cache yet. anyShape guard avoids begin/draw on an all-cutout list (empty buffer throws).
       Self-balances cull (disable->enable in finally).
     - ShadowBaker.bake: removed early-out on occCount==0; per-light skip is now (entInRange==0 && blocks.isEmpty());
       blocks collected EVERY frame (not gated on dirty) so tile assignment stays stable on cache-hit frames;
       point collects blocks ONCE before the 6-face loop; blocks NOT gated by noEntityShadows; collect radius
       clamped to MAX_BLOCK_COLLECT_RADIUS=24 (that constant later replaced by the shadow_block_radius BBS setting,
       default 24 range 4..96, commit a29794b). New: markBlocksDirty() (lastHash=NaN) + collectBlocks() helper.
     - mixin/client/WorldBlockChangeMixin (World.setBlockState HEAD, isClient) -> ShadowBaker.markBlocksDirty();
       registered in irlite.client.mixins.json. Needed because sceneHash is position-only (can't see a block edit).
     - Setting shadow_blocks (default ON): IrliteConfig.shadowBlocks + BBSSettingsMixin + L10nMixin.
   STAGE B (done, "performance"):
     - New: client/light/shadow/BlockShadowCache.java — identity-keyed (Long2ObjectOpenHashMap<id,CacheEntry{hash,
       list,sectionKeys}>). Keyed by LightRegistry.id (=System.identityHashCode(form), stable across frames),
       NOT registry slot (slots are reassigned each frame). getOrCompute(id,...) recollects only on hash mismatch
       (light moved) or invalidation; returns the SAME List instance on a hit (drives the VBO reference check).
     - Section index: sectionToLightIds = Long2ObjectOpenHashMap<sectionKey, LongOpenHashSet of ids> (IRLEngine's
       64-bit bitmask can't hold 256 arbitrary ids). invalidateAt(pos) -> section lookup -> set affected entries'
       hash=EMPTY, returns whether any hit.
     - New: LightRegistry.getId(int) accessor (added).
     - ShadowRenderer: renderBlocksDepth now takes (long id, List); per-id VBO cache (blockVboById +
       blockVboListById, reference-compare rebuild), buildBlockVbo, releaseBlockVbo(id), retainBlockVbos(liveIds).
       Cutout path stays immediate (can't VBO a textured pass).
     - ShadowBaker: collectBlocks(id,...) -> BlockShadowCache.getOrCompute; after the loops, build liveIds from
       LightRegistry and call retainOnly + retainBlockVbos (empty liveIds when feature off -> drains both, also
       drained on the count==0 early-return so VBOs don't linger after leaving all lamps).
   NON-OBVIOUS (vs the original plan that said "switch markBlocksDirty -> invalidateAt"): IRLite KEEPS BOTH.
     WorldBlockChangeMixin does `if (isClient && BlockShadowCache.invalidateAt(pos)) ShadowBaker.markBlocksDirty()`.
     invalidateAt only makes CPU re-collection precise; the GL depth-map REUSE is gated on the global position-only
     sceneHash, which can't see a block edit — so markBlocksDirty (lastHash=NaN) is still required to actually
     re-render. invalidateAt's return value gates it so a far-from-any-lamp edit costs nothing (Stage A rebaked on
     every edit). Threading: invalidateAt + bake both run on the render thread (1.20.1 dispatches client block
     updates to main thread), so the FastUtil maps are never touched concurrently.
   BOB-FLICKER BUG + FIX (2026-06-06, after Stage B): with view bobbing ON, block shadows ON, the lit area
     flickered/"disappeared" on ALL lamps incl. fixed model-block ones. Root cause = terrain SELF-SHADOW ACNE:
     block shadows put the lamp's own lit floor/walls into the depth map; the default IRLITE_SHADOW_BIAS (0.05)
     leaves the lit surface right at the depth-compare threshold, and view bobbing jitters the comparison enough
     to flip it -> flicker. Confirmed acne because raising the shader's IRLITE_SHADOW_BIAS to 0.2-0.4 removes it.
     (Our Java side is provably bob-invariant: depth maps + SSBO are computed from world coords + light pos,
     byte-identical with/without bob; the per-frame variation is entirely shader consumption of the now
     self-shadowing terrain.) Earlier red herring: the IRLightPositionResolver bob-wobble (render-path lamps) is
     real but NOT this — model-block lamps have fixed positions and still flickered.
     FIX (ShadowRenderer.renderBlocksDepth, Java, NO shader change): second-depth shadow mapping on the AABB path
     — cull FRONT faces (RenderSystem.enableCull + GL11.glCullFace(GL_FRONT), restore GL_BACK in finally) so the
     map stores each block's FAR side; the lit near surface is then ~a block in front of the stored depth and
     can't self-shadow. Distance-independent (works for the perpendicular floor-under-a-point-light case where
     slope-scaled polygon offset would be near-useless). Entity casters bake earlier in the same pass, unculled,
     keeping their tight bias. QuadBoxConsumer winding verified consistent CCW-outward, so GL_FRONT = light-facing.
     Tradeoff: cast block shadows peter-pan by ~occluder thickness (fine for chunky block shadows).
     NOT YET DONE: the cutout path (renderBlocksDepthCutout, leaves/bars) still records front faces (RenderLayer
     drives its cull, can't easily flip) — if cutout self-shadow flickers, add a constant projection depth-bias
     around that sub-pass (winding/RenderLayer-independent). Awaiting user test of floor + leaves.

   SUPERSEDED 2026-06-07 (during the [[photon-bugfix]] B1 work): the GL_FRONT second-depth cull above was REVERTED
   to RenderSystem.disableCull() (proven IRLEngine mode, user-verified) — GL_FRONT stored far faces and peter-panned
   block shadows. So block self-shadow acne is now UNGUARDED again. If the bob-flicker resurfaces, do NOT re-add
   FRONT-cull and do NOT use a slope-scaled glPolygonOffset (it blew up at the grazing angles on a point light's
   cube faces and pushed block depth past the far plane = no shadows at all). Use a small CONSTANT depth bias on the
   block pass instead. (B1's actual bug was unrelated: a vanilla-mob ModelBlock caster corrupted RenderSystem's
   modelview, so renderBlocksDepth now draws the block VBO with the light's own currentView/currentProj.)

File map (current IRLite): src/client/java/qualet/irlite/client/light/shadow/ShadowBaker.java,
ShadowRenderer.java. Old: IRLEngine/src/client/java/org/wemppy/irlights/client/shader/{EntityShadowTracker,
EntityOccluderResolver,ShadowRenderer}.java.

---

## Связь с IRL-redactor (объединение памяти 2026-06-18)

- **Применимость:** both-mods-lockstep — Применимо к ОБОИМ модам (оркестрация бейка теней в lockstep: ShadowBaker/ShadowRenderer/BlockShadowCache/Collector). Это IRLite-сторона история-аудит 2026-06-06; перф- и корректностная работа отсюда (per-light dirty, sphere-exact инвалидация=T2.5, occluder-cap, VBO-кэш блоков) уже РЕАЛИЗОВАНА и задокументирована на стороне redactor как канон — см. [[project-shadow-bake-perf-audit]], [[plan-shadow-bake-perf]] и порт на канон-оркестрацию [[project-shadow-seam-phase3-impl]]. Файл хранится как IRLite-история, не удалять.
- **Связанные в redactor:** [[project-shadow-bake-perf-audit]], [[plan-shadow-bake-perf]], [[project-shadow-seam-phase3-impl]] (superseded-by-redactor)
- Источник: портировано из памяти IRLite; оригинал оставлен как архив.
