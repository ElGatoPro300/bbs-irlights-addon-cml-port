---
name: shader-shadow-sampling
description: "IRLite shadow read path in GLSL — irlite_spotShadow (4x4 2D depth atlas, reconstructed spot view-proj, manual depth compare) and irlite_pointShadow (cube-map-array, dominant-axis perspective depth), PCSS tiers, depth encode/bias helpers."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: phase3-shader
---

The shadow-reading half of shaders/Lib/irlite_lights.glsl. Parent: [[MEMORY]]. Library overview: [[shader-irlite-glsl]]. Samplers bound by the mod: [[addon-iris-integration]]. Bake side (atlas layout, cube array, NEAREST, perspective depth, presets): [[addon-shadows]]. Tile/layer index lives in light.vlParams.w (-1 = no baked map) per [[addon-light-buffer-ssbo]]. NO-MAP CHECK MUST BE `if (light.vlParams.w < 0.0) return 1.0;` placed BEFORE the int rounding — NOT int(vlParams.w+0.5) < 0. GLSL int() truncates toward zero, so int(-1.0+0.5)=int(-0.5)=0: the -1 sentinel slips through as tile 0 and a shadow-OFF light samples tile/layer 0's stale map. This was the "Shadows toggle bakes once & freezes" bug (fixed 2026-06-09 in spot+point; SHARED inject -> fixed in Modification, both run copies, and iterationrp.irlpatch). A light's per-light "shadows" master toggle ([[addon-forms]], default ON) sets w=-1 in Java when OFF -> this guard is what makes "no shadow" actually happen.

SAMPLERS (declared in irlite_lights.glsl, bound by ProgramSamplersBuilderMixin by exact name — [[addon-iris-integration]]):
- uniform sampler2D irl_spotShadowAtlas   — 4x4 atlas of perspective depth tiles, one per shadowed spot. Tile index = int(vlParams.w+0.5) in 0..15; tx = tile%4, ty = tile/4; atlasUV = (tx + lightUV.x)/4, (ty + lightUV.y)/4.
- uniform samplerCubeArray irl_pointShadowArray — one depth cubemap per shadowed point; array layer = int(vlParams.w+0.5). Sampled with vec4(dir, layer) where dir = fragWorld - lightPos (NOT normalized; cube face picks dominant axis).

SHARED HELPERS:
- irlite_distFromDepth01(d01, near, far): inverse of the perspective depth encode -> world distance from light (metres), for PCSS penumbra math. near=0.05 always; far = light range/radius.
- irlite_depthBias(worldBias, dist, near, far) = worldBias*far*near / (dist*dist*(far-near)) — perspective depth bias scaled to world space (matches original IRLights). worldBias = IRLITE_SHADOW_BIAS, a PLAIN CONSTANT — do NOT slope-scale it (tried 2026-06-09 and reverted: slope-scaling depth bias only trades acne for peter-panning, since depth bias is a single-axis push toward the light).
- NORMAL-OFFSET BIAS (added 2026-06-09, commit 2eef284 — the ACTUAL anti-acne fix): irlite_spotShadow/irlite_pointShadow now take vec3 normal and, before projecting the receiver into the shadow map, shift it: fragWorld += (normalize(normal) + L) * IRLITE_SHADOW_NORMAL_OFFSET (default 0.05; L = unit dir to the light). Perpendicular/tiny spatial shift, NOT a depth push -> removes the texel staircase WITHOUT peter-panning. Mirrors IRLEngine (ir_lights.glsl:1253: startE = worldPos + worldNormal*0.05 + L*0.05 at the IRLightContribution call site); the original IRLite port had dropped this offset, which was the staircase bug. [[ref-irlengine-photon-patch]]. Possible refinement (not done): scale the offset to the actual shadow texel size via textureSize() so the magic 0.05 becomes "~1.5 texels", resolution/distance-independent.
- irlite_rotationPhi(): per-pixel hash rotation from gl_FragCoord (spatial only, NO temporal term -> no shimmer).
- irlite_vogel(i,n,phi): golden-angle sunflower disk sample for even penumbra coverage.

PCSS TIERS (#define from IRLITE_SHADOW_QUALITY): 0 -> hard 1-tap (no PCSS). 1 -> 6 blocker/10 PCF. 2 -> 10/18 (default). 3 -> 14/28. 4 -> 20/40. hard is forced when quality==0 OR the fast arg is true (specular fast path, IRLITE_SPECULAR_HARD).

irlite_spotShadow(fragWorld, normal, light, fast) -> visibility 1=lit 0=shadowed (applies the NORMAL-OFFSET to fragWorld first — see SHARED HELPERS — then the below):
- Reconstructs the spot's view-proj from the SSBO to match the Java bake EXACTLY (lookAt + perspective(outerAngle, aspect 1, near 0.05, far range)):
  lp=pos, ld=normalize(dirType.xyz), range=posRadius.w, outerDeg=degrees(acos(cone.x)*2).
  Build basis: up = |ld.y|>0.99 ? +Z : +Y; s=normalize(cross(ld,up)); u=cross(s,ld).
  eyeX=dot(s,toR), eyeY=dot(u,toR), eyeZ=-dot(ld,toR), toR=fragWorld-lp. Behind near plane (eyeZ>-near) -> lit.
  fY = 1/tan(radians(outerDeg)/2); ndcX=fY*eyeX/-eyeZ; ndcY=fY*eyeY/-eyeZ; outside [-1,1] -> lit (1.0).
  refDepth = (((far+near) - 2*far*near/dist)/(far-near))*0.5+0.5, dist=-eyeZ. bias via irlite_depthBias.
- Hard path: lightUV=ndc*0.5+0.5; atlasUV into tile; stored=texture(atlas).r; return (refDepth-bias > stored)?0:1. Manual compare (atlas is NEAREST, not a hardware comparison sampler).
- PCSS path: blocker search over IRLITE_BLOCKER_TAPS vogel taps within searchNdc (light-size cone), average blocker world distance; penumbra by similar triangles (penumbraWorld clamped to lightSize), then IRLITE_PCF_TAPS vogel PCF taps; out-of-bounds taps count as lit. lightSize = min(cone.w>0? cone.w : IRLITE_SHADOW_SIZE, range*0.5) (cone.w = per-light bulbSize override). worldToNdc = fY/dist.

irlite_pointShadow(fragWorld, normal, light, fast) -> visibility (applies the NORMAL-OFFSET to fragWorld first, as in spot):
- no-map guard FIRST: if (vlParams.w < 0.0) return 1.0; (see line-10 gotcha — NOT int(...)<0). Then layer=int(vlParams.w+0.5). lp=pos, radius=posRadius.w. dir=fragWorld-lp; refDist=length(dir); refDist>radius -> lit.
- DOMINANT-AXIS perspective depth (NOT euclidean length — euclidean gives a 6-pointed star of self-shadow): zPersp = max(|dir.x|,|dir.y|,|dir.z|); refDepth = (((far+near)-2*far*near/zPersp)/(far-near))*0.5+0.5; near=0.05, far=radius. This matches how the cube faces store perspective depth in [[addon-shadows]].
- Hard path: stored=texture(irl_pointShadowArray, vec4(dir, layer)).r; return (refDepth-bias > stored)?0:1.
- PCSS path: build tangent basis (T,B) around dirN; angular blocker taps offset dir by T*off.x+B*off.y on the cube; penumbra similar-triangles; PCF taps the same way. lightSize as in spot (cone.w override else IRLITE_SHADOW_SIZE, capped radius*0.5).

NOTE: both functions return 1.0 (fully lit, no shadow) on any out-of-frustum / behind-near / no-baked-map case — IRLite never darkens a fragment it has no valid depth sample for.

PHOTON DELTA (2026-06-10, [[shadow-distance-quality-plan]]) — the PHOTON inject (Shadres\Modification\Photon\shaders\include\irlite\irlite_lights.glsl — folders refactored 2026-06-11, see [[sync-workflow]]) has distance-quality fixes the IterationRP inject above does NOT have yet: (1) texel floors, commit 973d112 — normal offset / PCSS penumbra floored at 1.5 shadow-map texels, blocker search at 1 texel, texel size from textureSize() (the 0.05 offset and physical penumbra fall below one texel far from the light otherwise: staircase acne + PCF collapsing to a hard binary tap); (2) spot gather PCF — irlite_spotGatherTap: textureGather + bilinear blend of four binary compares (filter-after-compare) for the hard path + all PCF taps, UV clamped half a texel inside the tile against tile bleed; atlas stays NEAREST (gather ignores filter). Point cube kept on LINEAR compare-after-filter. Port both to the IterationRP inject whenever it is next touched.

Связь: shader-inject (инжектируемый GLSL-контракт обоих модов, authored IRLite, синк в redactor одно-направл.). Это ЧИТАЮЩАЯ половина теней в шейдере — парная к Java-стороне (раскладка/бейк/оркестрация в [[project-shadow-bake-perf-audit]] / [[plan-irl-core-shadow-extraction]]); среди redactor-памятей GLSL-путь чтения не покрыт. Источник: память IRLite.
