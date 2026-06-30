---
name: iterationrp-port-bugfix
description: "ARCHIVE of the FAILED 2026-06-11 IterationRP re-port (no sun, AE binarized, TAA broken). FINAL VERDICT 2026-06-12 (user): the port attempt FAILED — NEITHER 'resolved' theory (nested include / outline-feature + bounded-outline fix) is confirmed; treat every conclusion here as unconfirmed post-mortem material. Kept for lessons: AE craters on unbounded HDR terms, relay/temporal NaN stickiness, the ruled-out list, the old-patch known-good control. Active redo = iterationrp-reintegration-plan"
metadata: 
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: project
  originSessionId: 660b5269-d9e5-4b90-b4d2-20a36680cc9a
---

### FINAL VERDICT 2026-06-12 — PORT ATTEMPT FAILED (user). This file is an ARCHIVE.
Neither resolution below was ever confirmed working (the bounded-outline rewrite included). Do NOT
treat anything in this file as resolved; use it only for lessons + the ruled-out list. The failed
Shadres\Modification\IterationRP working copy was DELETED; take 3 starts from a fresh pristine copy
(made 2026-06-12) and re-integrates phase-by-phase with in-game gates: [[iterationrp-reintegration-plan]].
Known-good control: patches/iterationrp.irlpatch @ HEAD (old June-06 generation) + the 06-11 addon,
verified in-game 2026-06-11.

### CORRECTION 2026-06-11 (session 3) — the real cause is the OUTLINE FEATURE, not the include
USER-CONFIRMED bisect: physically STRIP the outline block from irlite_lights.glsl (functions +
call-site math, all under #ifdef IRLITE_OUTLINE) -> IterationRP_IRLite renders CORRECTLY. Outline
present (even with the NaN-hardening below) -> still globally broken. So: (1) the "nested include"
RESOLUTION below is WRONG; inlining did not fix it. (2) NaN is NOT the cause — output-sanitizing
diffuse/specular/outline for NaN/Inf did not fix it.
MECHANISM (evidenced, Explore audit): the NEW Blender-style outline is an UNBOUNDED additive HDR
term. In the loop `outlineOut += lightCol * (edgeMask*ink*STRENGTH)` then `outlineOut *= 5.0`, added
in Soild_FS as `color += IRLITE_INTENSITY*irliteOutline` AFTER `color = color*albedo+...` (post-
albedo, so unbounded by albedo). lightCol = colorIntensity.rgb*colorIntensity.a — film lights carry
huge intensity. Bright silhouette pixels (esp. against sky, next to the sun) land in central tiles of
Exposure_CS.glsl, whose luminance is an ARITHMETIC TILE MEAN (/512), center-weighted, with NO upper
clamp -> mean spikes -> `ae = pow(luminance,-aeCurve)` craters -> sun/scene go dark, AE binarizes.
Photon survives the same inject (different, robust exposure). The OLD IterationRP fresnel outline
WORKED because it was added PRE-albedo into the diffuse accumulator (irlite_lightDiffuse), so it got
*albedo (<=1) and *(1-ndl)*attenuation*fresnel — bounded & darkened. Soild_FS runs full-res
(texelCoord=ivec2(gl_FragCoord.xy), no fsrRenderScale) so tap-misalignment is NOT involved.
FIX (Modification, 2026-06-11 s3; synced to run/shaderpacks/IterationRP_IRLite_OUTLINE test pack;
pending in-game visual confirm): outline colour = PEAK-NORMALIZED light hue (lightCol/max(peak,1e-4))
not raw radiance; DROP the x5 for outline; clamp `outlineOut = min(outlineOut, IRLITE_OUTLINE_MAX)`
(new #define, default 2.0); Soild_FS adds `color += irliteOutline` WITHOUT IRLITE_INTENSITY (already
bounded). Detector (silhouette Laplacian) unchanged. The NaN guards (zCsafe, normalize(flatNormal)
fallback, isnan/isinf output sanitize) were kept as cheap insurance but are NOT what fixes it.
PACK INVENTORY (run/shaderpacks): IterationRP_IRLite = outline PHYSICALLY STRIPPED, renders correctly
= safe fallback; IterationRP_IRLite_OUTLINE = the bounded-outline REWRITE (test); IterationRP_IRLite_OC
= older broken strip (functions gone but call kept -> undefined irlite_outlineMask if enabled).
Source of truth Shadres/Modification = canon WITH the rewritten bounded outline.

--- superseded theory below (kept for history) ---
RESOLUTION (2026-06-11, bisect via pack variants): root cause = the nested include directive
`/Lib/irlite_outline.glsl` inside irlite_lights.glsl. With it present, Iris silently broke the
WHOLE pack (no sun/moon, AE -> binary black/white, TAA jerks) even with every IRLITE feature
compiled out (all-off test still broken; NOINC variant without the includes was clean; NOOUTL
variant — full inject minus the nested include — worked perfectly WITH IRL lights; DECLS also
clean). Exact Iris mechanism never pinned (no compile errors logged; if it ever recurs, dump
patched source via -Diris.debug=true). PHOTON ASYMMETRY: photon.irlpatch / Photon Modification use
the SAME nested-include pattern (shaders/include/irlite/irlite_outline.glsl) and work fine — do NOT
"fix" Photon, but never nest includes in the IterationRP inject.
FIX APPLIED: outline block inlined into irlite_lights.glsl (still guarded by IRLITE_OUTLINE),
irlite_outline.glsl deleted from Modification + run pack, synced (diff clean). Also: never start a
comment line with `#include` — Iris's include scanner may be line-based and comment-blind.
Eliminated along the way (all verified innocent): addon June-7..10 bake rewrite (OLD June-6 pack
works with today's addon), patcher (OLD pack == iterationrp.irlpatch output), properties/lang/
Entities_FS +128 (NOINC clean), SSBO feature flag, texture-unit budget, INFO_1/Title, encodings.
FOLLOW-UPS: (1) verify IRLITE_OUTLINE=ON in-game on IterationRP (outline path now live there for
the first time); (2) clean up bisect packs in run/shaderpacks (IterationRP_IRLite_OLD/NOINC/NOOUTL/
DECLS/PROPS/ENT/OFF + their .txt) once the fixed main pack is confirmed; (3) regen
iterationrp.irlpatch from Modification ONLY on explicit user command ([[patcher]],
[[commit-checkpoints]]); (4) when promoting, update [[shader-irlite-glsl]]: IterationRP inject has
the outline INLINED (Photon keeps the separate file).

Status 2026-06-11: Shadres/Modification now holds the IterationRP RE-PORT (the Photon-level inject
rebuilt for IterationRP: merged irlite_lightSurface, gather-PCF, normal-offset, VL per-step shadows,
new rim outline). Uncommitted (git status shows Photon files deleted / IterationRP files modified vs
HEAD). Tested in-game 2026-06-11 09:40-09:48 (run/shaderpacks/IterationRP_IRLite, byte-identical to
Modification) — pack-global breakage. [[photon-bugfix]] is the analogous tracker for Photon.

SYMPTOMS (user): 1) sun/moon/global light fully absent; 2) auto exposure splits image into pure
black/white (white = emissives/blocklight); 3) TAA jerks / image broken.

SESSION FINDINGS (all verified against code/logs):
- Diff surface vs pristine Shadres/IterationRP is only 7 files: Soild_FS (include + merged-call
  inject), Volumetric_FS (include + irlite_volumetric add), Entities_FS (+128 materialID bit-7 flag),
  +Lib/irlite_lights.glsl, +Lib/irlite_outline.glsl, shaders.properties (SSBO flag + screens +
  sliders), en_us.lang. All anchors correctly placed; statically clean.
- Pack COMPILES CLEAN: latest.log has zero Iris compile errors for IterationRP_IRLite; only
  pre-existing menu warnings (PT_TEMP_COORD_OFFSET, END_PLANET_WEAK_DIFFUSE — pristine shows the
  same). GPU = NVIDIA RTX 3060.
- CASCADE THEORY (high confidence): symptoms 2+3 are consequences of 1. Exposure_CS:
  ae = pow(luminance, -AE_CURVE) -> luminance~0 (black scene) -> exposure=Inf -> any lit pixel white,
  rest black (exact symptom 2). TAA/temporal on a flickering-exposure black scene = symptom 3.
  IterationRP_IRLite.txt (AE_MODE=2, MANUAL_EXPOSURE=true, INFO_1=1) = user's in-session workaround,
  not a cause.
- IterationRP fragility vs Photon: per-frame scalars travel through colortex corner pixels —
  IRC_CS/SH_TRACING_CS/SkyImage_CS read sunLight/colorShadowlight from texelFetch(FBTEX_ALT_OUTPUT,
  ivec2(0)); exposure lives in pixelData2D (PIXELDATA_EXPOSURE) + 1x1 colortex17; IRC/PT denoiser are
  temporal accumulators -> ONE NaN/Inf pixel in colortex6 spreads via bloom pyramid + relays and
  STICKS in temporal buffers. Photon has no such relay machinery (why same inject survives there).
- Direct sun in composite25 (Soild_FS) = colorShadowlight (VERTEX-shader atmosphere, Overworld_VS —
  cannot be touched by our FS additions; inject sits AFTER the sunlight math) * cloudShadow *
  VariablePenumbraShadow(shadowmap). textureLighting = PT GI from IRC (relay-contaminable).
- RULED OUT: texture-unit overflow (composite25 ~19 active samplers + our 2 << 32); bit-7 packing
  wrong byte (Pack2xU8_to_U16 puts y=materialID in LOW byte, &128u tests it correctly, pack decodes
  &127); option-name collisions; missing pack files; broken properties continuations; old patch also
  shipped the SSBO feature flag (not a delta).
- NaN edge paths that DO exist in the new GLSL (only for exotic light configs): spot with outer
  angle ~180deg -> fY=1/tan(pi/2)~0/Inf -> texelWorld=Inf -> fragWorld NaN -> irlite_spotGatherTap
  returns NaN via mix(.., fract(NaN)) -> NaN INTO color; range==near(0.05) division. Typical film
  lights (20-90deg) are NaN-free.

OPEN HYPOTHESES (ranked): A) NaN/Inf from the actual scene's light configs (wide spot / weird radius)
poisoning the relay+temporal machinery; B) addon<->IterationRP runtime interaction (SSBO bind, bake GL
state, cube-array sampler rebind) — pristine+addon was only screen-checked ~9s at 09:48, NOT a
controlled comparison; C) perf collapse breaking temporal passes (weak — doesn't explain AE binary).

OLD KNOWN-GOOD REFERENCE (2026-06-11 session 2): C:\Users\Qualet\Desktop\tmep\IterationRP_IRLite =
EXACT patcher output of patches/iterationrp.irlpatch applied 2026-06-06 12:10 (verified by diff vs
pristine: same 6 files, op-for-op; patcher whitespace quirks only — anchor re-emitted unindented /
line-ending normalization on touched lines, cosmetic). Generation: separate diffuse/specular loops,
plain texture() taps (no gather/textureSize/normal-offset), no VL shadows, fresnel outline,
cone.z = bool entitiesOnly (0/1); Entities_FS +128 flag ALREADY present; iris.features SSBO flag
ALREADY present (so the flag is exonerated — it shipped in the working version). Copied to
run/shaderpacks/IterationRP_IRLite_OLD as the control sample.

CRITICAL TIMELINE FACT: the addon's whole shadow-bake Java path was rewritten AFTER the old pack's
last known-good date — 06-07..06-10 commits: per-light dirty cache ec120e5, behind-camera cull +
GL-state snapshot ONCE PER BAKE 181cf88 (memory carries a stale-restore hazard warning), cone.z
tri-state 0/1/2 2028a06, sticky tiles c6328d4, two-layer bake R1 4d48ad0 (glCopyImageSubData
mid-frame), R3/R4/R5, VL per-step f20aa3f. All of it was only ever in-game-tested under PHOTON.
So the IterationRP breakage may be an ADDON regression (GL-state leakage into IterationRP's
compute/imageStore machinery), not the new GLSL at all.

DECISIVE TEST RESULT (2026-06-11, user-confirmed): IterationRP_IRLite_OLD + today's addon WORKS
CORRECTLY. Addon fully exonerated (the 06-07..06-10 bake rewrite is innocent); patcher exonerated.
The regression is INSIDE the new-GLSL delta old->new, i.e. the Photon-backport features:
  (1) merged irlite_lightSurface loop, (2) gather-PCF taps (irlite_spotGatherTap),
  (3) textureSize() texel floors (spot+point), (4) normal-offset receiver nudge,
  (5) VL per-step shadows IRLITE_VL_SHADOWS (NEW ACTIVE samplers in composite51 — old pack had
      irl_* samplers dead-stripped there), (6) irlite_outline.glsl include (off, near-inert),
  (7) new call-site shape in Soild_FS.
Static analysis note: for pixels with NO light in range the new code executes NOTHING new — yet sun
loss is global. So the mechanism is either (a) NaN/Inf escaping from LIT pixels and poisoning the
pack's relay/temporal machinery (gather-tap mix(.., fract(NaN)) path CAN return NaN if texelWorld
goes Inf), or (b) program-level: the newly-ACTIVE samplerCubeArray in composite51 / driver
miscompile of the new code shape (NV 596.21).

FEATURE LADDER (current step — pins the culprit feature, all via in-game IRLITE options screen on
the NEW IterationRP_IRLite, lights present; each toggle recreates the pipeline so temporal poisoning
clears):
D) FIRST: all four OFF (IRLITE_DIFFUSE, IRLITE_SPECULAR, IRLITE_VOLUMETRIC, IRLITE_SHADOWS) ->
   still broken? -> program-level (declarations/active samplers); confirm with
   run/shaderpacks/IterationRP_IRLite_NOINC (same pack minus the two includes).
   healed? -> re-enable cumulatively and note which step re-breaks:
   1) +DIFFUSE +SPECULAR (no shadows, no VL) — merged surface loop alone
   2) +SHADOWS — gather-PCF + textureSize floors + normal-offset in composite25
      (sub-split: IRLITE_SHADOW_NORMAL_OFFSET=0.0 kills the nudge; QUALITY=0 still uses gather)
   3) +VOLUMETRIC (VL_SHADOWS still off) — plain VL march (≈old code)
   4) +IRLITE_VL_SHADOWS — per-step taps + newly-active irl_* samplers in composite51
Whichever step re-breaks = the culprit feature; then fix that code path (NaN guards / binding).

---

## Связь с IRL-redactor (объединение памяти 2026-06-18)

- **Применимость:** shader-inject — Применимо к shader-inject (GLSL-инжект в шейдерпак IterationRP, авторинг IRLite-side через Shadres/Modification + .irlpatch, односторонний sync в redactor через copy-patches.ps1). В redactor-каноне нет аналога; настоящие соседи (shader-iterationrp-pipeline, iterationrp-reintegration-plan, photon-bugfix) портированы тем же заходом, но в данном списке слугов отсутствуют. Отношение к канон-списку — unique.
- **Связанные в redactor:** — (unique)
- Источник: портировано из памяти IRLite; оригинал оставлен как архив.
