---
name: shader-irlite-glsl
description: "Injected shaders/Lib/irlite_lights.glsl — SSBO struct mirroring binding 7, the #define option inventory, and the diffuse/specular/toon/outline shading functions plus their Soild_FS call-site integration."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: phase3-shader
---

The injected GLSL library shaders/Lib/irlite_lights.glsl (created by +file in patches/iterationrp.irlpatch). Parent: [[MEMORY]]. It is #include'd into Soild_FS (diffuse+specular) and Volumetric_FS (see [[shader-volumetric]]). SSBO contract owner: [[addon-light-buffer-ssbo]]. Shadow sampling split into [[shader-shadow-sampling]]. Base pack hooks: [[shader-iterationrp-pipeline]]. Options screen: [[shader-settings]].

SSBO STRUCT — must mirror LightBuffer.java std430 binding 7 EXACTLY ([[addon-light-buffer-ssbo]]):
  struct IrliteLight {        // 96 bytes = 6 vec4
    vec4 posRadius;      // xyz world pos, w radius (blocks)
    vec4 colorIntensity; // rgb linear color, a intensity
    vec4 dirType;        // xyz spot dir (normalized), w type (0 point, 1 spot)
    vec4 cone;           // x cos(outer/2), y cos(inner/2), z lightMask(0 all/1 entities/2 blocks), w bulbSize
    vec4 vlParams;       // x anisotropy(HG g), y vlDensity, z beamStrength, w shadowTile/layer (-1 none)
    vec4 cookie;         // x gobo layer in irl_cookieArray (-1 none), y rotation(rad), z scale, w flags(bit0 invert). SPOT-ONLY (point writes -1,0,1,0). See [[project-spotlight-gobo-cookie-plan]].
  };
  layout(std430, binding = 7) buffer IrliteLights {
    uint irlite_lightCount; uint irlite_pad0,irlite_pad1,irlite_pad2; IrliteLight irlite_lights[];
  };
STALE-COMMENT WARNING: the in-file comments for cone.w and vlParams.w still say "pad". The CODE uses them: cone.w = bulbSize (light.cone.w, treated as 0 => fall back to IRLITE_SHADOW_SIZE), vlParams.w = baked shadow tile/cube-layer (int(vlParams.w+0.5), -1 = none). Trust the addon SSBO doc + the code, not those two comments. If the comments are ever fixed in-pack, drop this warning.

PHOTON DIVERGENCE (2026-06-09, perf-audit R4 — Shadres/Modification only, IterationRP patch still has the OLD shape described below): irlite_lightDiffuse + irlite_lightSpecular were MERGED into void irlite_lightSurface(fragWorld, normal, viewDir, material, nonTerrain, out vec3 diffuseOut, out vec3 specularOut) (signature since the 2026-06-10 outline rework: + vec3 flatNormal after normal, + out vec3 outlineOut last; d4 call site passes flat_normal and adds the outline un-albedo'd) — one loop, per-light geometry computed once, ONE shared PCSS visibility for both terms (halves shadow taps; IRLITE_SPECULAR_HARD removed from glsl+properties+lang as obsolete), dist^2-before-sqrt reject, rec2020 conversion per light (spec multiplies the converted colour component-wise), attenuation<=0 continue after shadows, roughness>0.95 skips only the spec term. d4 call site = one call at the old diffuse anchor + two #ifdef-gated adds (diffuse*albedo, specular additive). See [[project-shadow-bake-perf-audit]] R4. The sections below describe the per-light MATH, which is unchanged.

OPTION #defines (all live at the top of irlite_lights.glsl; the // [..] lists are Iris slider/toggle values, surfaced by [[shader-settings]]):
- Feature toggles: IRLITE_DIFFUSE, IRLITE_SPECULAR, IRLITE_VOLUMETRIC, IRLITE_SHADOWS, IRLITE_SPECULAR_HARD(off; REMOVED in Photon Modification by R4), IRLITE_TOON(off), IRLITE_OUTLINE(off).
- Master/intensity: IRLITE_INTENSITY (master, default 1.0), IRLITE_SPECULAR_INTENSITY (1.0), IRLITE_VL_INTENSITY (1.0).
- Shadows: IRLITE_SHADOW_QUALITY [0..4] (0 = hard 1-tap, 1-4 = PCSS tap tiers), IRLITE_SHADOW_SIZE (0.10, light size for penumbra), IRLITE_SHADOW_BIAS (0.05). Detail in [[shader-shadow-sampling]].
- Volumetric: IRLITE_VL_STEPS, IRLITE_VL_TIP_BOOST, IRLITE_VL_TIP_RADIUS, IRLITE_VL_MAX_DIST. Detail in [[shader-volumetric]].
- Toon: IRLITE_TOON_BANDS [2..8], IRLITE_TOON_SMOOTH.
- Outline (IterationRP patch, OLD set): IRLITE_OUTLINE_PIXEL_SIZE [1..5], IRLITE_OUTLINE_FRESNEL_POWER, IRLITE_OUTLINE_STRENGTH. Photon Modification (2026-06-10 rework) REPLACED the set: PIXEL_SIZE [1..5], STRENGTH, TARGET [0 all/1 entities-only, default 1], WRAP [0..1, default 0.5], DEPTH_THRESHOLD [0.02..0.20, default 0.05], CREASE (toggle, default OFF), CREASE_ANGLE [30 45 60 90]; FRESNEL_POWER deleted.
- const float IRLITE_PI; samplers irl_spotShadowAtlas (sampler2D) + irl_pointShadowArray (samplerCubeArray) declared here ([[addon-iris-integration]]).

SHARED PER-LIGHT MATH (both diffuse and specular loop over irlite_lightCount):
- lightMask gate (cone.z, updated 2026-06-09 from the old 0/1 entitiesOnly bool): int m=int(light.cone.z+0.5); if(m==1&&!nonTerrain)continue; if(m==2&&nonTerrain)continue; — 1=entities only (skip terrain), 2=blocks only (skip entities). nonTerrain comes from the bit-7 materialID flag (see [[shader-iterationrp-pipeline]]). Present at BOTH diffuse + specular loop tops in each pack (IterationRP patch + Photon Modification); volumetric loop does NOT gate on cone.z.
- Range cull: toLight = light.posRadius.xyz - fragWorld(absolute); dist>=radius || dist<1e-4 -> skip.
- Frostbite radial falloff: dr=dist/radius; falloff=max(1-dr^4,0); attenuation = falloff*falloff. (Matches original IRLights addon.)
- Spot cone: if dirType.w>0.5, theta=dot(-L, spotDir); spotCone=clamp((theta-cone.x)/(cone.y-cone.x),0,1); multiply attenuation, skip if <=0.
- Shadows (#ifdef IRLITE_SHADOWS): attenuation *= spot? irlite_spotShadow : irlite_pointShadow. Diffuse passes fast=false; specular passes fast=IRLITE_SPEC_SHADOW_FAST (1-tap hard when IRLITE_SPECULAR_HARD on). See [[shader-shadow-sampling]].

vec3 irlite_lightDiffuse(worldPos /*camera-relative*/, normal, bool nonTerrain):
- fragWorld = cameraPosition + worldPos (absolute).
- Per light: diffuse = irlite_toon(attenuation * max(dot(n,L),0)). Optional per-light outline contour added (see outline below). sum += colorIntensity.rgb * colorIntensity.a * (diffuse + contour).
- Returns sum * 5.0 (the original surfaceMult=5.0 baseline). Master IRLITE_INTENSITY applied at the CALL SITE, not inside.

vec3 irlite_lightSpecular(fragWorld /*absolute*/, normal, viewDir, roughness, f0, bool nonTerrain):
- Early out if roughness>0.95. No explicit NdotL term (GGX visibility handles it); requires dot(n,L)>0.
- sum += SpecularGGX(n,v,L,roughness,f0) * colorIntensity.rgb * colorIntensity.a * attenuation. Returns sum * 5.0 (energyMult baseline). Master*specular intensity applied at call site.

irlite_toon(float x) (#ifdef IRLITE_TOON else identity): quantize [0,1] into IRLITE_TOON_BANDS with a soft top-edge smoothstep(1-w,1,frac) blend (w=IRLITE_TOON_SMOOTH). Monotonic so shadow direction preserved. Ported from original IR_ToonBand.

OUTLINE — two generations:
- IterationRP patch (OLD, still shipped there): irlite_outlineFactor() — 4 diagonal depth taps, maxBehind+edgeness smoothsteps, ported LocalLightOutlineFactor; in diffuse, x Fresnel x (1-ndl)*attenuation (backlight rim, albedo-tinted). Port of the new design below is PENDING.
- Photon Modification (2026-06-10 full rewrite, commits ec408ad shader + f9dfc76 patch; zero LocalLightOutlineFactor lineage): new file shaders/include/irlite/irlite_outline.glsl (self-guards on IRLITE_OUTLINE && PROGRAM_DEFERRED4; included from irlite_lights.glsl). Blender Line Art-style GEOMETRY MASK irlite_outlineMask(centerFlatNormal, centerEntity): 8 taps (4 axis at +-ps, 4 diag at +-round(ps/sqrt2)) clamped to ivec2(view_res*taau_render_scale)-1; SILHOUETTE = second-difference (Laplacian) on linear depth over 4 opposite pairs, rel=(zA+zB-2zC)/zC, smoothstep(T,2T) — slope-immune (plane cancels), one-sided (line on near pixels only), thin features can't cancel; sky depth clamped to 65536; CREASE (toggle, default off) = dot(flatN_C,flatN_S)<cos(CREASE_ANGLE) within depth-continuous same-entity-class neighbors; BORDER = entity bit-7 mismatch, inked on entity side only; score -> smoothstep(0.35,1.0) = AA fringe. Mask computed LAZILY in the loop (first light that reaches the pixel; TARGET=1 pre-zeros it on terrain).
  MECHANISM (rim ink, user-chosen over front-lit inking): per light ink = edgeMask * smoothstep(0.02,0.10, attenuation*backKill) * frontKill * STRENGTH; frontKill = 1-smoothstep(wrapC-0.25,wrapC+0.25, dot(L,v)) with wrapC=2*WRAP-1 — FRONTAL LIGHT DRAWS NO LINE (WRAP 0=strict backlight, 0.5=side+back, 1=nearly any); backKill = smoothstep(-0.35,-0.05, dot(flatN,L)) kills the contour side facing away from the light; binarized reach = uniform stroke, fades only at shadow penumbra/cone/range edges (attenuation already includes shadows). Output = third out param outlineOut of irlite_lightSurface, added at the d4 call site as fragment_color += IRLITE_INTENSITY * irlite_outline — NOT albedo-multiplied (solid stroke in the light's color), x5.0 baseline like the other terms. Watch-item: at strict backlight the rim depends on PCSS bias leak at the kerb; if the ring breaks up, soften the shadow term for ink specifically.

CALL-SITE INTEGRATION in Soild_FS (the inject — see [[shader-iterationrp-pipeline]] for anchors):
- irlite_nonTerrain = (uint(texelFetch(FBTEX_GSOLID_DATA, texelCoord,0).z * 65535.0) & 128u) != 0u  — decode the bit-7 entity flag.
- Diffuse (before `color = color*albedo+...`): color += IRLITE_INTENSITY * irlite_lightDiffuse(worldPos, gbuffer.worldNormal, irlite_nonTerrain).  -> gets multiplied by albedo afterwards (light-on-surface).
- Specular (after that line): irliteF0 = mix(vec3(0.04), albedo, metalness); color += (IRLITE_INTENSITY*IRLITE_SPECULAR_INTENSITY) * irlite_lightSpecular(cameraPosition+worldPos, worldNormal, normalize(-worldPos), clamp(roughness,0.0015,0.9), irliteF0, irlite_nonTerrain).  -> additive highlight, not albedo-multiplied.
- So the 5.0 baselines * master intensity = original 5.0*IRL_LIGHTS_INTENSITY behavior, faithfully ported from the IRLights addon.

Связь: shader-inject (содержимое .irlights, авторинг в IRLite -> односторонний синк в redactor через copy-patches.ps1). GLSL-сторона SSBO-контракта; Java-сторона (LightBuffer std430 binding 7) в irl-core, см. [[addon-light-buffer-ssbo]], [[project-irl-sync-strategy]], [[reference-edit-routing-by-area]]. Источник: память IRLite.
