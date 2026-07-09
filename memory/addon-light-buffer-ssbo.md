---
name: addon-light-buffer-ssbo
description: "IRLite GPU light SSBO contract (binding 7, std430) — exact byte layout the shader must mirror; now 6×vec4 / 96 bytes (cookie vec4 added). LightBuffer packing. Живёт в irl-core, общий для обоих модов."
metadata:
  node_type: memory
  mod_scope: irl-core-shared
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 7caee3ab-d073-4ed1-bfcf-8daae6a18007
---

IRLite light SSBO — THE GPU contract between addon/redactor and shader. Lives in irl-core (org.qualet.irl.light.LightBuffer), shared by BOTH mods. Parent: [[addon-architecture]]. Filled by [[addon-light-collection]] (addon) / LightDriver (redactor). The patcher's injected GLSL ([[shader-irlite-glsl]]) MUST mirror this exactly.

LightBuffer (org.qualet.irl.light.LightBuffer), single static GL SSBO. Verified against irl-core code 2026-06-29.
- BINDING = 7 (glBindBufferBase target GL_SHADER_STORAGE_BUFFER, index 7).
- MAX_LIGHTS = 2048 (was 256; raised 2026-06-18 to back the redactor "Max sources" slider 0..2000 — see [[project-auto-block-lights]]). HEADER_BYTES = 16. LIGHT_BYTES = 96 (6 × vec4) — was 80 (5×vec4) until the gobo/cookie bump added a 6th vec4 (see [[project-spotlight-gobo-cookie-plan]]). CAPACITY = 16 + MAX_LIGHTS*96 (~192 KB). Injected GLSL array is unbounded irlite_lights[] looping irlite_lightCount, so the ceiling needs no pack regen — but the per-fragment loop makes high counts a perf cliff ([[project-gui-lag-gpu-bound-diagnosis]]).
- Usage GL_DYNAMIC_DRAW. scratch is an off-heap ByteBuffer (MemoryUtil), little-endian floats.

std430 LAYOUT (must match in GLSL — 6 × vec4 = 96 bytes):
  struct Light {            // 96 bytes
    vec4 posRadius;       // xyz = position RELATIVE TO CAMERA (eye), w = radius/range (blocks). До 2026-07-09 был absolute world — см. [[project-camera-relative-light-migration]]: LightRegistry.flush(originXYZ) вычитает камеру в double; getX/Y/Z реестра остаются АБСОЛЮТНЫМИ (их читает ShadowBaker).
    vec4 colorIntensity;  // rgb = linear color, a = intensity
    vec4 dirType;         // xyz = spot direction (normalized), w = type (0 point, 1 spot). point: dir=0.
    vec4 cone;            // x = cos(outerAngle/2), y = cos(innerAngle/2), z = lightMask, w = bulbSize (0 = use shader global)
    //   cone.z lightMask (added 2026-06-09): 0 = all, 1 = entities only (skip terrain), 2 = blocks only (skip entities). Was a 0/1 entitiesOnly bool; now a 3-state float. Shader gate: int m=int(cone.z+0.5); m==1&&!nonTerrain skip; m==2&&nonTerrain skip.
    vec4 vlParams;        // x = anisotropy (HG g), y = vlDensity, z = beamStrength, w = shadowTile (-1 = none; else spot atlas tile index OR point cube slot)
    vec4 cookie;          // x = gobo layer in irl_cookieArray (-1 = none), y = rotation (rad), z = scale, w = flags (bit0 = invert). SPOT-ONLY projected mask; points always write (-1,0,1,0). See [[project-spotlight-gobo-cookie-plan]].
  };
  layout(std430, binding = 7) buffer IrliteLights {
    uint irlite_lightCount;
    uint _pad0, _pad1, _pad2;   // 12B pad to vec4 alignment
    Light irlite_lights[];
  };

POINT defaults when packed (addPoint): dirType = (0,0,0,0) so type=0; cone = (1,1, lightMask, bulbSize) i.e. full cone; cookie = (-1,0,1,0) (gobo is spot-only). lightMask computed in LightRegistry.flush() = entitiesOnly?1:(blocksOnly?2:0) (entities-only wins the UI-prevented both-set case).
SPOT (addSpot): dirType.w = 1; cone.xy = cosOuter,cosInner; cookie = (cookieLayer,cookieRot,cookieScale,cookieFlags). NB: LightRegistry.registerSpot has a no-cookie overload (the BBS addon's call path) that delegates with cookieLayer=-1 — keeps the addon ABI stable across the cookie struct bump.

FRAME LIFECYCLE
- begin(): lazy init (gen buffer, alloc CAPACITY, scratch); count=0; position scratch at HEADER_BYTES.
- addPoint/addSpot: append 6 vec4; ignore if count >= MAX_LIGHTS.
- upload(): write count at offset 0; glBufferSubData [0..used]; glBindBufferBase(binding 7). Called once per frame by LightRegistry.flush().
- delete(): free GL buffer + native scratch.

NOTE: shadowTile in vlParams.w is set per-light by ShadowBaker before flush. Spot tile -> SpotlightDepthAtlas tile (0..15); point slot -> PointShadowArray cube slot (0..15). See [[addon-shadows]]. -1 = light has no baked shadow (out of range / over budget / no occluders).

Связь: irl-core-shared. LightBuffer/LightRegistry в общем irl-core (org.qualet.irl.light.*), используются обоими модами; миграция LightRegistry в core завершена на всех линиях редактора (своей копии у редактора нет). [[project-irl-sync-strategy]] и [[reference-edit-routing-by-area]] говорят ГДЕ контракт; этот файл = точный байт-лейаут 6×vec4, который инжект-GLSL зеркалит. Источник: память IRLite (факты сверены с кодом 2026-06-29).
