---
name: shader-volumetric
description: "IRLite volumetric inscatter in GLSL — irlite_volumetric ray-march with per-light ray-cone (spot) / ray-sphere (point) clip, Beer-Lambert transmittance, Henyey-Greenstein phase, tip glow, optional per-step shadow (IRLITE_VL_SHADOWS, Photon); the Volumetric_FS hook and VL params from vlParams."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: phase3-shader
---

The volumetric half of shaders/Lib/irlite_lights.glsl, ported from the original IRLights single-scatter BL engine. Parent: [[MEMORY]]. Library overview: [[shader-irlite-glsl]]. Hooked into Volumetric_FS — anchors/locals in [[shader-iterationrp-pipeline]]. VL fields come from the SSBO ([[addon-light-buffer-ssbo]]): vlParams.x = anisotropy (HG g), .y = vlDensity (extinction), .z = beamStrength; cone.x/y = spot cosines; posRadius = pos/range.

CALL SITE (Volumetric_FS, after the pack's VolumetricFog(...) line):
  #ifdef IRLITE_VOLUMETRIC
    color += irlite_volumetric(cameraPosition, rayWorldPos, worldDir);
  #endif
- cameraPosition = absolute; rayWorldPos = camera-relative ray end (to opaque/water hit); worldDir = normalized camera-relative view dir. Origin of the march ray is the camera (vec3(0) in camera-relative space).

vec3 irlite_volumetric(camPos, rayWorldPos, worldDir):
- maxDist = min(length(rayWorldPos), IRLITE_VL_MAX_DIST). steps = IRLITE_VL_STEPS. noise = per-pixel hash (jitters first sample to hide banding). tipR2 = IRLITE_VL_TIP_RADIUS^2.
- Per light (camera-relative): lightScenePos = posRadius.xyz - camPos; range = posRadius.w; lightCol = colorIntensity.rgb * colorIntensity.a * max(vlParams.z,0) (beamStrength gates VL only — a light with beam 0 contributes no volumetrics); g = clamp(vlParams.x,-0.95,0.95); extinction = max(vlParams.y,1e-4); isSpot = dirType.w>0.5.

PER-LIGHT RAY CLIP (find [tNear,tFar] segment of the view ray inside the light volume):
- Spot: irlite_rayCone(origin=vec3(0), worldDir, apex=lightScenePos, axis=normalize(dirType.xyz), cosHalfAngle=cone.x, height=range). Miss (x<0) -> skip. tNear=max(t.x,0), tFar=min(t.y,maxDist).
- Point: ray-sphere. oc=-lightScenePos; b=dot(oc,worldDir); c=dot(oc,oc)-range^2; disc=b*b-c; disc<0 or t1<=0 -> skip. tNear=max(t0,0), tFar=min(t1,maxDist).

irlite_rayCone(rO,rD,apex,axis,cosHalfAngle,height) -> vec2 [tNear,tFar] or vec2(-1) on miss (ported from IR_RayConeIntersect):
- Infinite double-cone quadratic a*t^2+2b*t+c with a=DdotV^2-cos2 etc.; validates each root's axial coord in [0,height]; adds an end-cap plane test (capRadius2 = h^2*(1-cos2)/cos2). Handles camera-inside-cone (insideCone => segment [0, nearest exit]). Degenerate a~0 falls back to the linear root.

MARCH (Beer-Lambert single scatter):
- stepLen = (tFar-tNear)/steps; pos starts at worldDir*(tNear + stepLen*noise); advance by worldDir*stepLen.
- phaseSpot precomputed once = irlite_phaseHG(dot(worldDir,-axis), g) (constant along a spot beam); point recomputes phase per step with dot(worldDir,-L).
- Per step: toLight = lightScenePos - pos; dist=length; dist>range -> skip. falloff = max(1-dist/range,0). Spot: atten = falloff * clamp((dot(-L,axis)-cosOuter)/epsilon,0,1) (epsilon=cone.y-cone.x); skip if cone atten<=0. Point: atten = falloff*falloff.
- lightT = exp(-extinction*dist) (distance attenuation through medium to the light). tipGlow = 1 + IRLITE_VL_TIP_BOOST*exp(-dist^2/tipR2) (bright bloom near the bulb). inscatter = atten*phase*lightT*tipGlow.
- absorption = exp(-extinction*stepLen); acc += lightCol*inscatter*(1-absorption)*transmittance; transmittance *= absorption; early-out when transmittance<0.02.
- result += acc per light; return result * IRLITE_VL_INTENSITY.

VL SHADOWS (Photon, ADDED 2026-06-09 commit f20aa3f — restores the per-step shadow term the SSBO migration dropped; IRLEngine's SpotlightVolumetric had it under IRL_LIGHTS_VL_ENTITY_SHADOWS). The march multiplies a per-step visibility into inscatter (inscatter = atten * visibility * phase * lightT * tipGlow). Guarded by if (shHas && atten > 0.01). TOGGLE: #define IRLITE_VL_SHADOWS (default ON; bare #define, NOT yet wired into shaders.properties/lang — settings-UI follow-up).
FAST TAPS (2026-06-09, perf-audit R5 — replaced the original full irlite_*Shadow(fast=true) per-step calls): irlite_vlSpotStep(toR, shS, shU, axis, shFY, range, shTx, shTy) + irlite_vlPointStep(toLight, dist, radius, shLayer), defined under IRLITE_VL_SHADOWS inside the VL block. All per-light constants hoist OUT of the march: shHas = vlParams.w>=0 (no-map lights skip entirely), shTile/shTx/shTy/shLayer, spot basis shS/shU (same up-vector rule as the full fn) and ANALYTIC shFY = c*inversesqrt(max(1-c^2,1e-12)) capped at 114.58865 = 1/tan(0.5deg) — exactly reproduces the full fn's outerDeg=max(degrees(acos(c))*2,1.0) -> fY=1/tan(radians(outerDeg)*0.5) chain (fY monotonic in c, so min == the clamp). Per step only: project toR (spot) / shrink toLight (point) + ONE depth tap + compare. Receiver nudge = the full fn's normal-offset with normal=L, passed algebraically: spot toR = 2*IRLITE_SHADOW_NORMAL_OFFSET*L - toLight; point refDist = dist - 2*offset, dir = toLight*(-refDist/dist) (collinear shrink). ALSO hoisted: absorption = exp(-extinction*stepLen) + oneMinusAbsorption (were inside the step loop). The full irlite_*Shadow fns still compile into c0_vl but are unused there (dead code, stripped by the compiler). receiver frame note: Photon absolute world; an IterationRP port would be camera-relative (cameraPosition+pos).

STRUCTURAL CHANGE (so the VL pass can sample): the shadow-sampling block (irl_spotShadowAtlas/irl_pointShadowArray samplers + irlite_spotShadow/irlite_pointShadow + irlite_vogel/distFromDepth01/rotationPhi/depthBias + the PCSS tap #defines) was MOVED OUT of #ifdef PROGRAM_DEFERRED4 into a shared #ifdef IRLITE_COMPILE_SHADOWS gate (= PROGRAM_DEFERRED4 || (PROGRAM_COMPOSITE0 && IRLITE_VOLUMETRIC && IRLITE_VL_SHADOWS)) so it also compiles into c0_vl. Samplers auto-bind in the VL pass (Iris builds samplers for every program; addDynamicSampler no-ops where the uniform isn't declared — [[addon-iris-integration]]). Diffuse/specular/toon/outline stay PROGRAM_DEFERRED4-only. The block depends only on the SSBO + the two depth samplers (no Material/get_specular_highlight/cameraPosition), which is why it's c0_vl-safe.

SCOPE: Photon ONLY so far (Shadres/Modification, committed f20aa3f). FOLLOW-UPS still open (user-scoped to Photon shader for now): (1) port the same change into patches/iterationrp.irlpatch — there the march is camera-relative so receiver = cameraPosition+pos; IterationRP's VL is STILL unshadowed; (2) regenerate patches/photon.irlpatch from the final Modification; (3) wire IRLITE_VL_SHADOWS into the settings screen ([[shader-settings]]).

BEHIND-CAMERA CULL NOW LIVE: because VL now samples the map, the [[project-shadow-bake-perf-audit]] #2 de-risk ("irlite_volumetric does NOT sample the shadow map") is INVALIDATED for Photon — a light culled from baking (shadowTile=-1, e.g. fully behind the camera) now has UNSHADOWED VL shafts (still scatters, just not occluded by geometry). Visible lights are unaffected (their maps bake normally). Accepted/deferred per user scope; fix = relax the behind-camera bake cull when VL shadows are on.

irlite_phaseHG(cosTheta, g) = (1/4pi)*(1-g^2)/(denom*sqrt(denom)), denom=1+g^2-2g*cosTheta — Henyey-Greenstein anisotropy (g>0 forward scatter).

VL OPTIONS (see [[shader-settings]]): IRLITE_VL_INTENSITY (master mult), IRLITE_VL_STEPS (march quality), IRLITE_VL_TIP_BOOST + IRLITE_VL_TIP_RADIUS (bulb glow), IRLITE_VL_MAX_DIST (cull distance), IRLITE_VL_SHADOWS (per-step beam/haze occlusion, default ON — see VL SHADOWS above). Per-light shape from vlParams (anisotropy/density/beam) is set by the addon, not the screen.

Связь: shader-inject (инжектируемый GLSL + содержимое .irlights), авторинг в IRLite -> синк в redactor через copy-patches.ps1. Дополняет [[project-port-1211]] (там инжект упомянут лишь обзорно, без деталей волюметрики). Источник: память IRLite.
