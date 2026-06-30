---
name: ref-irlengine-photon-patch
description: "Указатель на СТАРЫЙ IRLEngine->Photon пропатченный пак — тот же flattened Photon-пайплайн + те же irl_ имена сэмплеров + полная PCSS spot/point математика + per-light volumetric. Photon-референс интеграции для Phase 2 (тени) и Phase 3 (volumetric). Reuse интеграцию; adapt uniform->SSBO и lightIdx->vlParams.w."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: photon-port-phase1-followup
---

Reference asset for the Photon port. Parent: [[MEMORY]]. Used by [[photon-pipeline]] Phases 2-3; integration target = [[photon-pipeline]]; shader code source of truth = [[shader-shadow-sampling]] / [[shader-volumetric]]. Lineage context: [[project-refactor-origin]].

PATH (перемещён в архив 2026-06): C:\Users\Qualet\Media\Archive\AI_Slop_Projects\IRLEngine\Shaders\Patched\photon_IRLights (старый путь ...\BBS\IRLEngine\... больше не существует). Это же — source of truth для восстановленного старого outline, см. [[project-photon-outline-switch-to-old]].

WHAT IT IS: the predecessor project IRLEngine (uniform-based; IRLite is its SSBO refactor — [[project-refactor-origin]]) ALREADY injected into Photon ~v1.2-1.3a. Same FLATTENED pipeline as our current target (program/d4_deferred_shading.fsh, program/c0_vl.fsh, program/gbuffers_all_solid.fsh, world0/ wrappers). Monolithic include shaders/include/lighting/ir_lights.glsl (~2026 lines) = the IRLEngine analog of our irlite_lights.glsl.

WHY IT'S GOLD FOR PHASE 2/3:
- Uses the EXACT sampler names IRLite chose: uniform sampler2D irl_spotShadowAtlas (4x4 tile grid) + uniform samplerCubeArray irl_pointShadowArray. (ir_lights.glsl ~L582-583.)
- SAME shadow math our memory specifies: spot view-proj reconstructed inline (lookAt + perspective, near 0.05, far=range), manual NDC depth compare, Vogel-disk PCSS; point uses DOMINANT-AXIS perspective cube depth (not euclidean) + tangent-plane Vogel PCSS; world-space depth bias scaled to depth at receiver distance. (IR_SpotShadowSample ~L907-1034; IR_PointShadowSample ~L1060-1140.)
  CORRECTION (2026-06-09): the depth bias is NOT the anti-acne mechanism. IRLEngine's samplers use a PLAIN CONSTANT bias (IRL_WORLD_BIAS); shadow acne is killed by a RECEIVER NORMAL-OFFSET applied at the CALL SITE inside IRLightContribution (~L1253: startE = worldPos + worldNormal*0.05 + L_e*0.05) BEFORE IR_EntityShadow -> IR_*ShadowSample. The first IRLite port DROPPED this offset (its shadow fns took no normal) -> that was the staircase/acne bug. Fixed by mirroring it: IRLITE_SHADOW_NORMAL_OFFSET (commit 2eef284, see [[shader-shadow-sampling]]). Do NOT slope-scale the depth bias instead (tried -> peter-panning).
- Per-light VOLUMETRIC already hooked: SpotlightVolumetric(worldDir, opaqueDist, depth, noise) — Beer-Lambert march, HG phase, ray-cone/ray-sphere clip, tip glow, optional per-step entity shadow — called from program/c0_vl.fsh (~L259). (ir_lights.glsl ~L1683-1870.)
- Photon integration already solved: world/scene pos reconstruction (position_scene + cameraPosition = absolute), linear HDR working space (no gamma inside light code), VL writes fog_scattering in c0_vl.

HOW TO USE — REUSE, NOT FROM SCRATCH. Reference it for PHOTON INTEGRATION (which pass, coordinate spaces, gbuffer/depth reads, the c0_vl hook). Take the shader CODE primarily from our own SSBO-based IterationRP inject ([[shader-shadow-sampling]] / [[shader-volumetric]]) — IRLEngine and IRLite are siblings, both descend from the same IRLEngine math, so the formulas already match.

ADAPT (do NOT copy blindly):
- DATA LAYER: IRLEngine is UNIFORM-based — irlightPosX0..41, getIRLightData(int i) if-cascade (ir_lights.glsl ~L72-451, 640-719). DO NOT copy. We already iterate the binding-7 std430 SSBO from Phase 1 ([[addon-light-buffer-ssbo]]). Project preference = SSBO (better than uniforms).
- SHADOW INDEX SOURCE: IRLEngine tile/layer = sequential lightIdx; IRLite uses the BAKED tile/layer in light.vlParams.w (-1 = no map, set per-light by ShadowBaker). Use vlParams.w, not the loop index.
- DEFAULTS/NAMES: keep IRLite #define names + defaults (IRLITE_SHADOW_SIZE 0.10, IRLITE_SHADOW_BIAS 0.05, near 0.05) — IRLEngine used different constants (soft size 0.04, bias 0.02, receiver normal-offset 0.05). Formulas identical EXCEPT IRLite originally omitted IRLEngine's receiver normal-offset — now restored as IRLITE_SHADOW_NORMAL_OFFSET (see the CORRECTION above); otherwise just our knobs.
- DIFFUSE HOOK: IRLEngine edits Photon's include/lighting/diffuse_lighting.glsl (calls IRLightContribution at ~L284); WE already hooked program/d4_deferred_shading.fsh directly after get_diffuse_lighting() in Phase 1 — KEEP our approach (fewer files, cleaner patch).

VERSION DRIFT: ~v1.2-1.3a vs current Photon v1.3a — pipeline structure is the same, but RE-VERIFY anchors / colortex allocations / normal-encoding against the live Shadres/Modification/Photon files (folders refactored 2026-06-11, see [[sync-workflow]]) before trusting any line number from this old pack.

BONUS (out of current scope): shaders/include/lighting/ir_lens_flare.glsl = screen-space per-light lens flare (independent uniform cascade, hooked in program/c1_blend_layers.fsh). Possible FUTURE IRLite feature — not Phase 2/3.

Связь: shader-inject (содержимое .irlights / инжектируемый GLSL, владелец IRLite, синк в redactor через copy-patches.ps1). Дополняет [[project-port-1211]] внутренностями шейдерной математики (сэмплеры, PCSS, volumetric) как Photon-референс. Источник: память IRLite.
