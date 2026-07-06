---
name: complementary-pipeline
description: "ComplementaryReimagined base pipeline IRLite ports into — FORWARD shading via lib/lighting/mainLighting.glsl DoLighting() (12 gbuffers programs), hook sites (mainLighting diffuse/specular, ADDED reduced-res deferred2 VL march + composite1 upsample/outline, properties/lang) with verified anchors + locals, and the KEY deltas vs the Photon port (#version 130, in-file #extension, gamma-space lighting, compile-time entity flag)."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: complementary-port-phase0
---

Complementary (by EminGT, BSL lineage; ships BOTH styles — SHADER_STYLE 1 Reimagined / 4 Unbound, lighting path shared) base pipeline the IRLite inject is being PORTED into, third target pack after IterationRP and Photon. Parent: [[MEMORY]]. Photon analog of this doc: [[photon-pipeline]]. Inject internals being ported: [[shader-irlite-glsl]] / [[shader-shadow-sampling]] / [[shader-volumetric]]. Pristine base = Shadres/Original/ComplementaryReimagined; working copy = Shadres/Modification/ComplementaryReimagined ([[sync-workflow]]). GPU contracts unchanged: SSBO binding 7 [[addon-light-buffer-ssbo]], samplers [[addon-iris-integration]].

PACK LAYOUT (verified 2026-06-11 against pristine):
- Real source in shaders/program/*.glsl — COMBINED files, vertex+fragment halves gated #ifdef VERTEX_SHADER / #ifdef FRAGMENT_SHADER.
- world0/world1/world-1 hold THIN WRAPPERS (.fsh/.vsh): #version 130 + #define FRAGMENT_SHADER + #define OVERWORLD|NETHER|END + #define GBUFFERS_X|COMPOSITE1|... + #include "/program/<file>.glsl". ALL wrappers are #version 130.
- clrwl_* wrappers (Iris Colorwheel/OIT path, colorwheel.properties oit = true) include the SAME program files with an extra GBUFFERS_COLORWHEEL define -> any program/*.glsl or lib hook covers them automatically.
- Settings #defines live in lib/common.glsl itself (no settings.glsl); common.glsl also includes lib/uniforms.glsl (ALL uniforms declared unconditionally — cameraPosition, gbufferModelView/Inverse, near/far, frameCounter, noisetex...) — BUT #ifndef VOXY_PATCH (Voxy programs have a different uniform setup; gate IRLite out of Voxy).
- FORWARD SHADING: lib/lighting/mainLighting.glsl DoLighting() is included into 12 programs: gbuffers_terrain/entities/block/hand/water/textured/basic/lightning + dh_terrain + dh_water + voxy_opaque + voxy_translucent. ONE hook there applies everywhere (incl. translucents, particles, OIT path — broader coverage than the deferred packs ever had).

SSBO FACTS (the biggest porting relief):
- iris.features.optional = CUSTOM_IMAGES SSBO BLOCK_EMISSION_ATTRIBUTE FADE_VARIABLE — SSBO flag ALREADY PRESENT, no properties edit needed.
- Pack's own SSBOs: lib/voxelization/SSBOs/blockDataBuffer.glsl (binding 0) + playerVerticesBuffer.glsl (binding 3), both only active under COLORED_LIGHTING/WSR toggles (bufferObject.0/.3 in shaders.properties). BINDING 7 IS FREE.
- IN-FILE #extension IDIOM IS PACK-PROVEN: blockDataBuffer.glsl puts #extension GL_ARB_shader_storage_buffer_object : enable at the top of the INCLUDED file (not the wrapper), and that compiles at #version 130. So irlite_lights.glsl carries its own #extension lines and NO wrapper edits are needed (vs Photon's 6 wrapper ops).
- samplerCubeArray at #version 130 needs #extension GL_ARB_texture_cube_map_array : enable in-file — NOT yet field-proven in this pack, verify in-game EARLY (Phase 2 risk; NVIDIA lenient).

SPACES + COLOR MODEL (the deltas to get right):
- playerPos = camera-relative, world-oriented (scene space). ABSOLUTE world = playerPos + cameraPosition. normalM = VIEW-space shading normal; worldGeoNormal = world-space geo normal; nViewPos = normalize(viewPos). View<->player via gbufferModelView/Inverse (in scope everywhere via uniforms.glsl).
- GAMMA-SPACE lighting: gbuffers/DoLighting operate in gamma-ish space (BSL "square-add-sqrt" mixing); composite1 converts to linear via color = pow(color, vec3(2.2)); and the pack adds ITS VL after that line in linear. NO rec2020 anywhere (drop Photon's rec709_to_rec2020). IRLite light colors are linear RGB from the SSBO — intensity/hue response will differ from Photon; calibrate in Phase 1 (option: pow() the light color; or just retune IRLITE_INTENSITY default).
- Specular: lib/lighting/ggx.glsl float GGX(vec3 normalM, vec3 nViewPos, vec3 lightVec, float NdotLmax0, float smoothnessG) — VIEW-space, f0 hardcoded 0.05, area-light NoH (WATER_REFLECT_QUALITY>=2), references global `normal` varying (exists in all mainLighting includers — pack compiles today). Reuse per IRLite light: L_view = normalize(mat3(gbufferModelView) * (lightPlayerPos - playerPos)) where lightPlayerPos = posRadius.xyz - cameraPosition. (Analog of reusing Photon's get_specular_highlight.)

THE THREE HOOK SITES:

1. DIFFUSE + SPECULAR -> lib/lighting/mainLighting.glsl, inside void DoLighting(inout vec4 color, inout vec3 shadowMult, vec3 playerPos, vec3 viewPos, float lViewPos, vec3 geoNormal, vec3 normalM, float dither, vec3 worldGeoNormal, vec2 lightmap, ... float smoothnessG, float highlightMult, float emission).
   - include anchor: #include "/lib/lighting/ggx.glsl" (file top, unique) — irlite include after it.
   - DIFFUSE anchor: finalDiffuse = sqrt(max(finalDiffuse, vec3(0.0))); — insert AFTER: finalDiffuse += IRLITE_INTENSITY * irliteDiffuse;. The following color.rgb *= finalDiffuse; multiplies albedo automatically = exact Photon semantics. Note pack AO/directionShade do NOT apply to our term (consistent with Photon port).
   - SPECULAR anchor: color.rgb += lightHighlight; — insert AFTER: additive highlight, not albedo-multiplied.
   - nonTerrain = COMPILE-TIME: #if defined GBUFFERS_ENTITIES || defined GBUFFERS_BLOCK || defined GBUFFERS_HAND (wrapper defines visible inside mainLighting and our include). NO bit-7 material-mask dance, no &127u — forward rendering knows the program. Check in-session: gbuffers_entities_glowing / *_translucent wrapper define sets.
   - GATE: #if !defined DH_TERRAIN && !defined DH_WATER && !defined VOXY_PATCH around all IRLite code (Voxy lacks uniforms.glsl; DH = distant LOD, lights are near-camera).
   - in scope at both anchors: playerPos, cameraPosition, normalM, worldGeoNormal, nViewPos, smoothnessG, highlightMult, lightmap, shadowMult, GGX().

2. VOLUMETRIC — march in the ADDED program/deferred2.glsl, upsample+add in composite1 (REWORKED 2026-06-12 to the BSL perf recipe; was full-res in composite1 — whole screen marched inside a light radius).
   - deferred2 program (+6 wrappers world{0,1,-1}/deferred2.{fsh,vsh}: #version 130 + FRAGMENT_SHADER|VERTEX_SHADER + OVERWORLD|END|NETHER + DEFERRED2, NO trailing newline — pack wrapper parity): includes common.glsl + spaceConversion.glsl, #define IRLITE_VL_PASS right before the lib include; playerPos from depthtex0 via ScreenToView/ViewToPlayer — texture2D NOT texelFetch (gl_FragCoord lives in the REDUCED viewport while depthtex0 stays full-res; depthtex0 at the deferred stage = opaque depth = the z1 the old composite1 hook used); dither = composite1's idiom (noisetex .b + goldenRatio*frameCounter temporal under TAA — all in scope via common.glsl); writes /* RENDERTARGETS: 10 */.
   - properties plumbing: pass toggled by program.world{0,-1,1}/deferred2.enabled=IRLITE_VOLUMETRIC ×3 at the end of the Link-Programs section (patch anchor = before the unique # Miscellaneous comment; the pack comment L103 blesses enabled=BOOLEAN_DEFINE); buffer sized by size.buffer.colortex10 = IRLITE_VL_RESOLUTION IRLITE_VL_RESOLUTION — FLOAT option 0.5 // [1.0 0.5 0.25] referenced DIRECTLY (the pack's own REFLECTION_RES mechanism, NO #if ladder; line sits inside the existing IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE guard after the colortex7 line); format const RGB16F INSIDE the /* */ block of lib/pipelineSettings.glsl after colortex8Format (loader parses the comment; a bare const would C1503). colortex10 was free (pack uses 0-8, 12, 18, 19).
   - composite1 hook (same anchor color = pow(color, vec3(2.2));, insert AFTER — linear space): vec3 irliteVL = texture2D(colortex10, texCoord).rgb; bilinear upsample + underwater parity (*= pow2(vec3(0.80,0.87,0.97)*0.55) at isEyeInWater==1, = 0 at ==2) + color += irliteVL.
   - do NOT add into the pack's volumetricEffect var: LIGHTSHAFTS_ACTIVE may be compiled out (POTATO..LOW profiles + LIGHTSHAFT_BEHAVIOUR), NETHER_STORM OVERWRITES volumetricEffect (L237), ATM_COLOR_MULTS would tint ours. Independent block = always-on VL.

3. OUTLINE -> program/composite1.glsl (Phase 4.5; RELOCATED from deferred1 on 2026-06-12 — BBS replay models render through the TRANSLUCENT entity programs, so pre-translucent passes don't have them in depthtex0/colortex4/6; deferred1 is back to pristine).
   - same include as the VL hook (the lib's outline half is #ifdef COMPOSITE1 + #ifdef IRLITE_OUTLINE, coexists with the VL half).
   - ink anchor: color = pow(color, vec3(2.2)); — outline block inserted BEFORE it (gamma-space ink, matches the surface-lighting colour curve), VL block stays AFTER it (linear) — one anchor line serves both ops.
   - the hook computes its own locals: irliteOPlayerPos = ViewToPlayer(viewPos.xyz) (viewPos is the z0-based one), flat normal = texelFetch(colortex4).rgb under PBR_REFLECTIONS (deferred1 re-writes solid normals there, the translucent entity pass writes its own after it — both player-space) with dFdx/dFdy(irliteOPlayerPos) fallback + camera-facing flip; gate z0 < 1.0 && z0 > 0.56 (sky + hand/glowing rescaled-depth skip).
   - entity per-pixel test (forward has compile-time flags, screen-space needs gbuffer data): colortex6.g mask byte in [100,199] ("Entity Reflection Handling", pack idiom abs(maskInt-149.5)<50.0) OR == 254 (the "No SSAO, No TAA" DEFAULT of gbuffers_entities/hand/textured/lightning — GENERIC entities incl. BBS models never get a specific entityIPBR material and keep 254; particles/lightning false-positives accepted). Custom-PBR modes reuse the byte -> detection degrades. gbuffers_block (BBS model-blocks) NOT flagged ever.
   - depth: silhouette Laplacian works on the BSL normalized linearization (~ (2/far)*eyeZ for near<<far) because the threshold is RELATIVE (zA+zB-2zC)/zC — scale cancels. Self-contained in the lib.
   - (An IRLITE_OUTLINE_DEBUG mask-visualizer toggle existed during bring-up; REMOVED 2026-06-12. If outline diagnosis is needed again, re-add a temporary color = mix(color, vec3(1,0,1), irlite_outlineMask(irliteEntity)); branch at the composite1 ink call site.)

4. SETTINGS -> shaders.properties + lang/en_US.lang (capital US).
   - NO iris.features edit (SSBO already there). NO wrapper edits (in-file #extension).
   - screen: append ` [IRLITE_SETTINGS]` to screen.LIGHTING_SETTINGS= (~L50) + add screen.IRLITE_SETTINGS subtree (pack idiom = UPPERCASE_SETTINGS names; sub-screens like the Photon set, minus outline).
   - sliders: append numeric IRLITE_* to the giant single-line sliders= (~L67). Tail anchor must be END_STAR_INTENSITY GENERATED_NORMAL_RES — bare GENERATED_NORMAL_RES is NOT unique (also in screen.IPBR_SETTINGS L18).
   - lang/en_US.lang: append the IRLite block at file end (after the option.XLIGHT_CURVE.comment line). Conventions: screen.X / option.X / option.X.comment / value.X.N, § color codes.
   - profiles: 7 profile.* lines exist; IRLite stays independent of them (keep patch minimal).

INJECT FILE: shaders/lib/irlite/irlite_lights.glsl (+file). Port SOURCE = Shadres/Modification/Photon/shaders/include/irlite/irlite_lights.glsl (898 lines — carries ALL post-port bugfixes: normal-offset acne fix, gather-PCF spot atlas, merged surface loop, texel floors, IRLITE_VL_SHADOWS) + irlite_outline.glsl (the mask detector, folded INTO the lib here, not a separate file). Self-contained IRLITE_* defines at top. Program split discriminators (current, after outline bugfix 3 + the VL rework): surface half #ifdef IRLITE_SURFACE_PASS (defined by mainLighting.glsl on the line before its include — keeps GGX/normalM deps out of the screen-space passes), outline half = nested bare #ifdef COMPOSITE1 + #ifdef IRLITE_OUTLINE, VL march half = nested bare #ifdef IRLITE_VL_PASS + #ifdef IRLITE_VOLUMETRIC (IRLITE_VL_PASS defined by program/deferred2.glsl before its include), plus an upsample-side block #ifdef COMPOSITE1 + #ifdef IRLITE_VOLUMETRIC -> uniform sampler2D colortex10; (deferred2 must NOT see that decl — it renders to the buffer). Shadow block gate: #if defined IRLITE_SHADOWS && (defined IRLITE_SURFACE_PASS || (defined COMPOSITE1 && defined IRLITE_OUTLINE) || (defined IRLITE_VL_PASS && defined IRLITE_VOLUMETRIC && defined IRLITE_VL_SHADOWS)).

KEY DIFFERENCES vs the Photon port:
- FORWARD not deferred: hook a shared lib include once -> 12 programs; entity flag is compile-time; bonus coverage = translucents/water, particles, hand, lightning, clrwl OIT.
- #version 130 + in-file #extension; zero wrapper edits. samplerCubeArray@130 = the one unproven compile risk (Phase 2, test early).
- Gamma-space lighting + no rec2020: drop color conversion, recalibrate intensity (Phase 1 visual test vs Photon side-by-side).
- Specular reuse target = GGX(... smoothnessG) — no Material struct, f0 fixed 0.05, no roughness>0.95 early-out concept (smoothnessG low instead).
- VL march runs in an ADDED reduced-res deferred2 pass (Photon-parity cost since the 2026-06-12 rework) and must NOT couple to the pack's lightshaft/nether-storm plumbing.
- OUTLINE lives in deferred1, not the lighting loop (forward gbuffers can't read current-frame depth neighbors) — hook 4 above. Entity detection is per-pixel gbuffer data (mask [100,199], IPBR-only) instead of Photon's bit-7; crease not ported.
- shaders.properties is rich in #if blocks — capture anchors away from them; lang is en_US (capital).

Порт — выполнено (лог в _archive):
- Phases 0–5 done 2026-06-11..12; patches/complementaryreimagined.irlpatch (1539 lines, 21 ops); in-game verified.
- VL rework: full-res march -> half-res deferred2 pass (~2.8× perf); pattern shared with BSL.
- Outline: OLD IRLEngine LocalLightOutline via COMPOSITE1; unified params PIXEL_SIZE 2 / STRENGTH 0.65 / FRESNEL_POWER 2.2, default OFF; done 2026-06-28. Cross-pack record = [[project-photon-outline-switch-to-old]].

Связь: shader-inject (авторинг GLSL-инжекта и .irlights в IRLite/Shadres; в redactor попадает пропатченным паком через copy-patches.ps1). Дополняет [[project-port-1211]] (там Complementary лишь как 21 ops патчер-валидации). Источник: память IRLite.
