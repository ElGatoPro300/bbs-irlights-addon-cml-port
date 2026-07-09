---
name: shader-iterationrp-pipeline
description: "IterationRP base pipeline IRLite hooks into — Composite/Soild_FS (deferred opaque), Volumetric_FS (fog), Gbuffers/Entities_FS; the pack symbols/conventions the injection treats as a contract, plus the exact hook anchors."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: phase3-shader
---

IterationRP base pipeline that IRLite injects into — documented SEPARATELY from the inject itself. Parent: [[MEMORY]]. The inject is in [[shader-irlite-glsl]] / [[shader-shadow-sampling]] / [[shader-volumetric]] / [[shader-settings]]. Source of truth for hooks: patches/iterationrp.irlpatch (see [[patcher]]). Pristine pack lives at Shadres/Original/IterationRP/shaders (folders refactored 2026-06-11, see [[sync-workflow]]); the dev copy is hand-edited under Shadres/Modification/<pack>, then diffed into the .irlpatch.

IterationRP = a heavy path-traced/deferred Iris pack by Tahnass (option.iterationRP_VERSION="iterationRP by Tahnass"). Deferred: gbuffer programs write G-data into colortex; composite programs read it back and do all lighting. IRLite adds its point/spot lighting AFTER the pack's own sun/held/PT lighting, into the same `color` accumulator, in the composite passes.

THREE HOOKED PROGRAMS (only these are touched):

1. Lib/Programs/Composite/Soild_FS.glsl — the main opaque deferred shading pass.
   - RENDERTARGETS 6; out framebuffer_mainOutput. Reads gbuffer via GetGbufferDataSoild() into `GbufferData gbuffer`.
   - Key locals at the inject point: worldPos = camera-RELATIVE world-space frag pos (= mat3(gbufferModelViewInverse)*viewPos — ROTATION-ONLY: the modelview translation column is dropped, and it carries the VIEW-BOBBING offset! True player-relative pos = worldPos + gbufferModelViewInverse[3].xyz — the pack's own idiom at the voxelPos/ShadowTracing call sites; absolute = cameraPosition + that. Found 2026-06-12: bare worldPos makes the light pattern wobble with bobbing ON). gbuffer.worldNormal world-space normal. gbuffer.material.roughness/metalness. color = running linear HDR radiance.
   - Overworld branch builds sunlight + specular, ending with the two lines IRLite anchors on:
       color = color * (1.0 - metalnessMask * METALMASK_STRENGTH) + textureLighting;
       color = color * gbuffer.albedo + sunlightSpecular;   <-- IRLite diffuse goes BEFORE this, specular AFTER.
   - Diffuse is injected pre-albedo-multiply intentionally? NO — see [[shader-irlite-glsl]]: diffuse is added to color BEFORE color = color*albedo+..., so IRLite diffuse is itself multiplied by gbuffer.albedo (acts like incoming light on the surface). Specular is added AFTER (already-shaded additive highlight, like sunlightSpecular).

2. Lib/Programs/Composite/Volumetric_FS.glsl — the fog / volumetric pass (RENDERTARGETS 6).
   - Locals at inject point: cameraPosition (uniform, absolute), rayWorldPos = worldDir * clamped view distance (camera-relative end of the view ray to the opaque/water hit), worldDir = normalized camera-relative view direction. color = scene radiance so far.
   - Anchor is the pack's own fog call:
       if (fogTimeFactor > 0.01 && isEyeInWater == 0) VolumetricFog(color, vec3(0.0), rayWorldPos, worldDir, globalCloudShadow, fogTimeFactor, fogTransmittance);
     IRLite volumetric is added AFTER it (additive inscatter). See [[shader-volumetric]].

3. Lib/Programs/Gbuffers/Entities_FS.glsl — entity/model-block gbuffer (shared by Entities/Spidereyes/Block/Hand programs).
   - Writes framebuffer_gsoildData = vec4(... , Pack2xU8_to_U16(vec2(parallaxShadow, v_materialIDs/255.0)), ...). materialID byte stored in the .z channel of colortex1.
   - IRLite's only edit here: replace the materialID pack with (v_materialIDs + 128.0)/255.0 — sets bit 7 of the material byte to flag "this fragment is an entity/model, not terrain". The pack's own decode masks materialID with &127 elsewhere, so materialID stays intact; bit 7 is free. Read back in Soild_FS for per-light entitiesOnly. See [[shader-irlite-glsl]] (irlite_nonTerrain).

PACK SYMBOLS THE INJECT DEPENDS ON (must exist in IterationRP for the patch to compile — the contract surface):
- struct GbufferData { vec3 albedo; vec3 worldNormal; vec3 vertexNormal; vec2 lightmap; float materialID; float parallaxShadow; Material material; } (Lib/GbufferData.glsl).
- struct Material { float roughness; float metalness; float emissiveness; float scattering; float reflectionStrength; }.
- vec3 SpecularGGX(vec3 n, vec3 v, vec3 l, float roughness, vec3 f0)  (Lib/Utilities.glsl) — reused by irlite_lightSpecular.
- float LinearDepth_From_ScreenDepth(float depth)  (Lib/Uniform/GbufferTransforms.glsl) — used by irlite_outlineFactor on depthtex0.
- #define FBTEX_GSOLID_DATA colortex1  (Lib/Settings.glsl) — the gbuffer data target; irlite_nonTerrain reads .z (materialID byte) from it.
- uniform vec3 cameraPosition (absolute), depthtex0, gl_FragCoord — standard Iris/pack uniforms.
- Both Soild_FS and Volumetric_FS already #include "/Lib/BasicFunctions/Blocklight.glsl" — that include line is the stable anchor the inject puts #include "/Lib/irlite_lights.glsl" AFTER (so the SSBO + functions are visible in main()).

HOOK ANCHOR INVENTORY (exact, from the .irlpatch — keep in sync with [[patcher]]):
- Soild_FS:      after  #include "/Lib/BasicFunctions/Blocklight.glsl"  -> include irlite_lights
                 before color = color * gbuffer.albedo + sunlightSpecular;  -> read irlite_nonTerrain + add IRLITE_DIFFUSE
                 after  color = color * gbuffer.albedo + sunlightSpecular;  -> add IRLITE_SPECULAR
- Volumetric_FS: after  #include "/Lib/BasicFunctions/Blocklight.glsl"  -> include irlite_lights
                 after  the VolumetricFog(...) call line  -> add irlite_volumetric
- Entities_FS:   replace the Pack2xU8_to_U16(vec2(parallaxShadow, v_materialIDs / 255.0)) with the +128.0 variant.
- shaders.properties + lang: see [[shader-settings]].

Порт — выполнено (лог в _archive):
- TAKE-3 reintegration phases 0–5 done from 2026-06-12, gate-verified in-game; #version 430 -> SSBO + samplerCubeArray native.
- Lessons: view-bobbing worldPos fix (Phase 2); outline excised then re-added.
- AE-crater pitfall: unbounded additive HDR outline term after albedo-multiply -> Exposure_CS tile-mean AE craters -> scene darkens. Fix = OLD fresnel outline integrated PRE-albedo (pre-multiply diffuse). Outline done 2026-06-29. PERMISSION 2026-07-09: автор Tahnass согласовал ПУБЛИЧНЫЙ патч -> патч+генератор расижнорены (блок .gitignore убран), коммитабельны; больше НЕ local-only (см. [[project-github-repos]]). NOTE: IterationRP VL still UNSHADOWED (known-open). Cross-pack record = [[project-photon-outline-switch-to-old]].
- ВЕРСИЯ АКТУАЛИЗИРОВАНА 2026-07-09: пак обновлён до новой версии (промоутнута в Shadres/Original/IterationRP; старая в scratchpad-бэкапе). Из 12 ops уехал ровно 1 якорь — вызов VolumetricFog в Volumetric_FS: пак добавил хвостовой арг fogTransmittance (сигнатура ...globalCloudShadow, fogTimeFactor, fogTransmittance)). Остальной contract-surface байт-стабилен: GbufferData получил поле albedoAlpha (доступ по имени безвреден), Material/SpecularGGX/FBTEX_GSOLID_DATA=colortex1/LinearDepth_From_ScreenDepth без изменений, materialID &127u маска цела (бит-7 non-terrain флаг свободен), DH/VX-LOD +128 семантика та же что в старой. Генератор-ассерт fog обновлён (gen-iterationrp-patch.ps1:49); патч регенерён, round-trip PatchHarness diff-clean (12 ops).

Связь: shader-inject (инжектируемый GLSL + .irlights, авторинг в IRLite, синк в redactor через copy-patches.ps1; redactor только потребляет). Дополняет [[project-port-1211]] (iterationrp как непокрытый пак ~12 ops) и [[reference-edit-routing-by-area]]. Источник: память IRLite.
