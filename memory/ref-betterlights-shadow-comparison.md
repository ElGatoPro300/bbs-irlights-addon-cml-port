---
name: ref-betterlights-shadow-comparison
description: "Анализ shadow-системы BetterLights (1.12.2 Forge/Optifine, DynamX) vs IRLite: что лучше (chunk-VBO окклюдеры всего террейна) и хуже (нет кэша, нет point-теней, fov-waste, баги PCSS-математики)."
metadata:
  node_type: memory
  mod_scope: reference-shared
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 584fd3b7-b583-4d02-b7fb-ef8004990256
---

Декомпилированный исходник: C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLEngine\Source-libary\betterlights (мод "Better Lights" 0.0.3 by Yanis, dynamx.fr, MC 1.12.2 Forge + Optifine). Только спотлайт-модель (pos/dir/inner/outer<=90, distance<=200): НЕТ point-источников, НЕТ cube-map нигде.

Хранение теней: ОДИН GL_TEXTURE_2D_ARRAY, слои = maxLights (default 30, GLSL хардкодит lights[30]), каждый shadowWidth x shadowHeight (default 1024², max 4096), DEPTH_COMPONENT24, NEAREST, CLAMP_TO_BORDER white (border=lit). Аллоцируется EAGERLY на первом кадре даже с 0 источников (~126 MB при дефолтах). Off-by-one: очередь раздаёт maxLights+1 слой-id для текстуры на maxLights слоёв.

Бейк (MixinBetterLightEntityRenderer перед renderWorldPass, на shadow-enabled источник на кадр, БЕЗ camera/distance-cull, фикс. порядок списка):
- isDynamic=true (default): clear depth -> setupTerrain hijack (MixinRenderGlobal: brute-force chunk-скан, cube radius chunkCullingRadius=64, cone-тест outer+20deg по 8 углам, reflection-вызовы на чанк) -> draw реальных chunk-VBO SOLID+CUTOUT_MIPPED+CUTOUT+TRANSLUCENT -> renderEntities (ВСЕ энтити мира). Полный ре-рендер мира на источник на кадр, ноль кэширования.
- isDynamic=false (static): нет clear/scan/entities, но всё равно рисует SOLID+CUTOUT+TRANSLUCENT со stale chunk-списком каждый кадр (безвредно для depth, лишние дро). НЕТ авто-инвалидации: правки террейна/энтити не обновляют static-карту.
- shouldBake = ручной one-frame pulse (setDynamic(true) на 1 кадр потом freeze) — ручной аналог dirty-сигнатур IRLite.
- Shadow fov = outerAngle + fovOffset (default +45deg) -> для конуса 20deg ~13x texel-area waste vs tight fov IRLite. gluPerspective near 0.05 far=distance (те же константы). С Optifine-шейдерами бейк идёт через Shaders.ProgramShadow пака + isBlShadowPass uniform.

Read-путь (lights_terrain/entities.fsh forward-replacement шейдеры, или юниформы запушенные в программы Optifine-пака): uniform SpotLight lights[30] вкл. 2x mat4 КАЖДЫЙ (shadowProjection+shadowView, не премультиплены — mat4*mat4*vec4 на фрагмент на источник); glGetUniformLocation на КАЖДЫЙ set юниформа (нет кэша локаций, ~сотни string-lookup/кадр). Per-fragment цикл по всем источникам, без кластеризации. PCSS: blocker+PCF rotated disk (uniform-массивы pcss/pcfDiskOffset[128]), дефолты 4+4 тапа (GLSL говорит 32, но config default=4), max 64+64; InterleavedGradientNoise С temporal-членом (frameCounter*1.618 -> crawling grain, без TAA в 1.12); normal offset 0.1 фикс, depth bias 0; lightSize хардкод 0.01, penumbra fudge *0.08/z; penumbra-математика МЕШАЕТ hyperbolic shadowPos.z с linearized blocker depth (неверное пространство). step()-compare на NEAREST (бинарные тапы, без gather/bilinear).

Вердикт vs IRLite (cost/quality): IRLite выигрывает bake cost (per-light dirty-сигнатуры + two-layer static/dynamic + sticky tiles + behind-camera/cone/face cull + quantized block-кэш vs ноль кэширования BL) и per-texel quality (tight fov, DEPTH32F, gather-PCF, texel floors, world-space PCSS, spatial-only rotation, point cube-тени). BL выигрывает полноту окклюдеров: реюз реальных chunk-VBO террейна = весь мир кастеры на любом масштабе даром (вкл. реальные block-меши + translucent), vs voxel-AABB блоки IRLite в пределах shadowBlockRadius (24 default/96 max) + cap 32 окклюдера. Этот chunk-реюз НЕ дёшево портируется на 1.20 Sodium/Iris (это то, что сам Iris делает для sun-теней). У BL ещё server sync/persistence источников (другой домен). Lineage: BL заимствует у Shrimple-шейдерпака («Rotation from shrimple code»); его uniform-array подход зеркалит старый IRLEngine до-SSBO ([[ref-irlengine-photon-patch]], [[project-refactor-origin]]).

Связь: внешний сравнительный референс для обоих модов; дополняет [[project-shadow-bake-perf-audit]] (cost/quality бейка) и [[plan-irl-core-shadow-extraction]] (полнота окклюдеров chunk-VBO vs voxel-AABB). Источник: портировано из памяти IRLite.
