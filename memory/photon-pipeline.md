---
name: photon-pipeline
description: "Photon shaderpack base pipeline IRLite ports into — the program/ + worldN-wrapper layout, the four hook sites (d4_deferred_shading diffuse/specular, c0_vl volumetric, gbuffers_all_solid entity flag, shaders.properties/lang/settings) with verified anchors + Photon locals, reusable Photon helpers, and the KEY differences vs the IterationRP inject."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: photon-port-phase0
---

Photon (by SixthSurge) base pipeline that the IRLite inject is being PORTED into, second target pack after IterationRP. Parent: [[MEMORY]]. This is the Photon analog of [[shader-iterationrp-pipeline]]. The inject GLSL itself (SSBO struct, diffuse/specular/toon/outline, shadow sampling, volumetric) is unchanged in concept — see [[shader-irlite-glsl]] / [[shader-shadow-sampling]] / [[shader-volumetric]]; THIS doc only records WHERE/HOW it attaches to Photon and the porting deltas. Pristine base = Shadres/Photon/shaders; active edit copy = Shadres/Modification ([[sync-workflow]]). GPU contracts unchanged: SSBO binding 7 [[addon-light-buffer-ssbo]], samplers [[addon-iris-integration]].

PACK LAYOUT — flattened program naming + per-dimension wrappers:
- Real shader source lives in shaders/program/*.fsh (e.g. d4_deferred_shading.fsh, c0_vl.fsh, gbuffers_all_solid.fsh).
- shaders/world0 (overworld), world1 (end), world-1 (nether) hold THIN WRAPPERS: #version + #define WORLD_* + #define PROGRAM_* + #include "/program/<file>.fsh". E.g. world0/deferred4.fsh = #define PROGRAM_DEFERRED4 then includes /program/d4_deferred_shading.fsh.
- CONSEQUENCE: inject into program/*.fsh ONCE and it applies to all dimensions. Far fewer edits than per-world. The world0 wrapper #defines (PROGRAM_GBUFFERS_ENTITIES etc.) are visible inside the program file — used to distinguish entity vs terrain at gbuffer time.
- Settings #defines flow through shaders/settings.glsl -> include/global.glsl (line ~53 #include "/settings.glsl") -> every program. (But keep IRLITE #defines self-contained at top of irlite_lights.glsl like IterationRP — saves patch ops.)

THE FOUR HOOK SITES (verified against Shadres/Original/Photon/shaders — pristine baseline, folders refactored 2026-06-11 see [[sync-workflow]]; line numbers approximate — re-capture unique anchors at edit time):

1. DIFFUSE + SPECULAR -> program/d4_deferred_shading.fsh  (Iris deferred4, the main deferred opaque shading pass; IterationRP analog = Soild_FS).
   - gbuffer decode (~L337-347): mat4x2 data = mat4x2(unpack_unorm_2x8(...)); vec3 albedo = vec3(data[0], data[1].x); uint material_mask = uint(255.0 * data[1].y); vec3 flat_normal = decode_unit_vector(data[2]). Decode the IRLite entity bit right after the material_mask line.
   - position: position_view = screen_to_view_space(...); position_scene = view_to_scene_space(position_view) (camera-RELATIVE); position_world = position_scene + cameraPosition (ABSOLUTE) ~L226-232. direction_world = camera->fragment world dir; view dir toward camera = -direction_world. normal = world-space shading normal. Material struct at include/surface/material.glsl (albedo, emission, f0, f82, roughness, sss_amount, is_metal...).
   - radiance accumulator = fragment_color (vec3, out location 0 -> colortex0).
   - DIFFUSE anchor (~L552): fragment_color = get_diffuse_lighting( ... a multi-line call ending ); at ~L575. NOTE it ASSIGNS, and get_diffuse_lighting MULTIPLIES albedo internally (returns ... * material.albedo * rcp_pi ...). So add IRLite diffuse AFTER that call as fragment_color += IRLITE_INTENSITY * irlite_lightDiffuse(position_world, normal, nonTerrain) * material.albedo; (self-multiply albedo to act as light-on-surface; differs from IterationRP where diffuse was added pre-multiply).
   - SPECULAR anchor (~L579-583): block fragment_color += get_specular_highlight(material, NoL, NoV, NoH, LoV, LoH) * light_color * shadows * cloud_shadows * ao; inside #if defined WORLD_OVERWORLD || defined WORLD_END. Add IRLite specular AFTER this block (additive highlight). Reuse Photon's own get_specular_highlight per light by recomputing per-light NoL/NoH/LoV/LoH (the existing NoL/NoV/NoH are for sun/moon light_dir only).
   - include anchor: put #include "/include/irlite/irlite_lights.glsl" near the top includes of d4_deferred_shading.fsh (capture a stable existing #include line as anchor).

2. VOLUMETRIC -> program/c0_vl.fsh  (Iris composite0; world0/composite.fsh includes it; IterationRP analog = Volumetric_FS). QUARTER-RES pass (VL_RENDER_SCALE 0.50), outputs colortex6/7.
   - outputs: out vec3 fog_transmittance (loc0 -> colortex6), out vec3 fog_scattering (loc1 -> colortex7). RENDERTARGETS 6,7.
   - ray in ABSOLUTE world space (differs from IterationRP camera-relative): world_start_pos = gbufferModelViewInverse[3].xyz + cameraPosition (camera eye, absolute) ~L171; world_end_pos = world_pos (opaque hit, absolute) ~L172; world dir = normalize(world_end_pos - world_start_pos). dither = temporal blue-noise jitter already computed ~L168-169.
   - anchor: AFTER the LPV-VL block #if defined LPV_VL && defined COLORED_LIGHTS\n  fog_scattering += get_lpv_fog_scattering(world_start_pos, world_end_pos, dither);\n#endif (~L231-234). Add fog_scattering += irlite_volumetric(world_start_pos, world_end_pos, worldDir); — PORT irlite_volumetric to absolute-world inputs (compute camera-relative internally), and account for quarter-res cost when picking IRLITE_VL_STEPS.

3. ENTITY FLAG -> program/gbuffers_all_solid.fsh  (shared solid gbuffer; IterationRP analog = Entities_FS). Distinguishes entity vs terrain via wrapper #defines: PROGRAM_GBUFFERS_ENTITIES / _TERRAIN / _TERRAIN_SOLID / _BLOCK / _HAND.
   - write anchor (~L389-393): gbuffer_data_0.y = pack_unorm_2x8(base_color.b, clamp01(float(material_mask) * rcp(255.0))); — material id byte stored in upper 8 bits of colortex1.y. Set bit 7 (|128u) on material_mask when PROGRAM_GBUFFERS_ENTITIES (and optionally _BLOCK/_HAND) before this pack.
   - decode (in d4, see hook 1): bool irlite_nonTerrain = (material_mask & 128u) != 0u;
   - RISK: Photon compares material_mask to exact material IDs (all <=103, see include/misc/material_masks.glsl) elsewhere. Bit 7 set would break those exact-match compares. MUST mask material_mask &= 127u; in d4 right after decoding the entity bit, OR confirm Photon's own reads of material_mask in d4 happen before any use. This is why entity flag is its own phase, not Phase 1.
   - RESOLVED (Phase 4, RISK AUDIT): material_mask &= 127u; added in d4 right after the irlite_nonTerrain decode. Audited every solid-gbuffer material-byte reader: ONLY d4:350 (now masked) and c1_blend_layers:367 (DISTANT_HORIZONS distant-water; entities/blocks/hand never render to the DH gbuffer + id|128 != MATERIAL_WATER, so safe unmasked) decode data[1].y; all other colortex1 readers (c0_vl, d3_ao, edge_highlight, c4) read albedo/normal, not the material byte. unorm8 round-trip preserves the full 0-255 byte, so id|128 survives and &127 restores it. Flagged ENTITIES+BLOCK+HAND via a local var (BLOCK's material_mask is a read-only flat-in varying).

4. SETTINGS -> shaders.properties + lang/en_us.lang.
   - SSBO: no iris.features.required exists; Photon has iris.features.optional = CUSTOM_IMAGES ENTITY_TRANSLUCENT (~L119). Append ` SSBO` -> ... ENTITY_TRANSLUCENT SSBO. REQUIRED for Iris to honor the binding-7 std430 buffer.
   - SSBO #extension (VERIFIED Phase 1, easy to miss): Photon programs are #version 400 compatibility, so a layout(std430) buffer block does NOT parse natively (SSBOs are core in 430). Every WRAPPER that includes a program declaring the SSBO must add #extension GL_ARB_shader_storage_buffer_object : enable right after #version — the same pattern Photon itself uses for GL_ARB_shader_image_load_store in world*/shadow.vsh (the wrappers, not the program files). Phase 1 added it to world0+world1+world-1 deferred4.fsh (the 3 .fsh wrappers of d4; the .vsh wrappers DON'T need it — the SSBO is fragment-only). CONSEQUENCE: program/*.fsh hooks are ONCE, but the extension is per-wrapper (3 files) — the photon.irlpatch must include these 3 wrapper edits.
   - screen: top-level screen = INFO <profile> <empty> <empty> [world] [lighting] [sky] [fog] [materials] [water] [post] [misc] (~L8). Append ` [irlite]`. Add screen.irlite = ... subtree + sub-screens. Append numeric IRLITE_* to the giant sliders = ... line (~L115); toggles stay off-list (render as on/off).
   - lang/en_us.lang conventions: screen.X = ..., option.X = ..., option.X.comment = ..., suffix.X = ..., value.X.N = ...; Minecraft section-color codes used (§e §a §b §d §c §9). Color-code IRLite labels per feature group (match [[shader-settings]] scheme).

REUSABLE PHOTON HELPERS (so the port leans on the pack, not its own copies):
- include/lighting/specular_lighting.glsl: vec3 get_specular_highlight(Material material, float NoL, float NoV, float NoH, float LoV, float LoH) — reuse for IRLite specular (IterationRP used SpecularGGX similarly).
- include/lighting/bsdf.glsl: distribution_ggx, v1/v2_smith_ggx, fresnel_schlick, fresnel_dielectric, fresnel_lazanyi_2019, f0_to_ior.
- include/utility/phase_functions.glsl: float henyey_greenstein_phase(float nu, float g) (+ cornette_shanks, etc.) — can replace IRLite's own irlite_phaseHG.
- include/utility/space_conversion.glsl: screen_to_view_space, view_to_screen_space, view_to_scene_space, scene_to_view_space, float screen_to_view_space_depth(mat4 projInv, float depth) (linearize, for outline).
- include/utility/dithering.glsl + random.glsl: bayer*, interleaved_gradient_noise, r1 (the c0_vl dither is already temporal).
- include/utility/encoding.glsl: pack/unpack_unorm_2x8, encode/decode_unit_vector.

KEY DIFFERENCES vs the IterationRP port (the porting deltas to get right):
- Photon get_diffuse_lighting() multiplies albedo INSIDE; IRLite diffuse must self-multiply material.albedo (IterationRP relied on a later *albedo).
- Photon deferred uses ABSOLUTE world position (position_world); IterationRP Soild_FS used camera-relative worldPos. Pass position_world straight into irlite_lightDiffuse (which already wants absolute fragWorld).
- Photon VL pass is QUARTER-RES and ABSOLUTE-world; IterationRP VL was full-ish and camera-relative. Adapt irlite_volumetric origin/dir and tune steps.
- Photon gbuffer programs are SEPARATE per category (PROGRAM_GBUFFERS_*) vs IterationRP's one Entities_FS; the entity flag uses those #defines and must mask &127 to not break Photon's exact material_mask compares.
- Binding 7 is FREE (Photon has zero SSBOs; its COLORED_LIGHTS/LPV uses CUSTOM_IMAGES light_sampler_a/b + voxel_sampler, not SSBOs). Sampler names irl_spotShadowAtlas / irl_pointShadowArray are FREE. IRLite (SSBO point/spot + baked shadows) and Photon LPV (voxel GI) coexist independently.

Порт — выполнено (лог в _archive):
- 6-phase port done 2026-06-10 (commit 620fdce); Phase 5 = 1296 lines, 20 ops, byte-identical applier output.
- Donor strategy: BSL lib internals (PCSS, VL per-step shadows, gather-PCF), Photon get_specular_highlight reuse, absolute-world quarter-res volumetric.
- Outline: switched to OLD IRLEngine LocalLightOutline 2026-06-28 (see [[project-photon-outline-switch-to-old]]). COUPLING: IRL-inject lives in TWO patches (photon.irlights + photon-irl-dof.irlights) — edit BOTH.

Связь: shader-inject (содержимое .irlights / GLSL-инжект, авторинг в IRLite, синк в redactor через copy-patches.ps1). Дополняет [[project-port-1211]] (там photon_v1.3b лишь на уровне offline op-count=20) и [[reference-edit-routing-by-area]]. Спутник [[photon-bugfix]]. Источник: память IRLite.
