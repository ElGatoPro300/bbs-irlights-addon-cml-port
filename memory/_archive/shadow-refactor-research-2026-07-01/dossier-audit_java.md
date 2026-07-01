# Аудит бейк-стороны теней irl-core

Все пути ниже относительно `C:/Users/Qualet/Documents/Project/Minecraft/BBS/irl-core/src/main/java/org/qualet/irl/light/shadow/`.

## 1. Форматы текстур, разрешения, layout

### Спот-атлас (SpotlightDepthAtlas.java)
- Формат: `GL_DEPTH_COMPONENT32F` — 32-битный float depth, единственное вложение; color-attachment НЕТ (`glDrawBuffer(GL_NONE)` / `glReadBuffer(GL_NONE)`): SpotlightDepthAtlas.java:139-141, 155-156.
- Layout: сетка `GRID_X=4 x GRID_Y=4` = `MAX_TILES=16` тайлов, тайл i по пиксельному origin `((i%4)*TILE_SIZE, (i/4)*TILE_SIZE)`: SpotlightDepthAtlas.java:29-32, 80-88. Т.е. максимум 16 теневых спотов одновременно.
- Атлас = ДВЕ физические текстуры: LIVE (сэмплится шейдером) + STATIC (база статичного контента, аллоцируется лениво при первом overlay-бейке): SpotlightDepthAtlas.java:16-25, 34-40.
- Рендер в тайл: viewport+scissor на прямоугольник тайла: ShadowRenderer.java:117-127.

### Point cube-array (PointShadowArray.java)
- Формат: `GL_TEXTURE_CUBE_MAP_ARRAY`, `GL_DEPTH_COMPONENT32F`, `LAYER_COUNT = 6 * MAX_SHADOWS = 96` слоёв: PointShadowArray.java:34-37, 155-159. `MAX_SHADOWS=16` point-источников с тенью.
- Грань f слота i = слой `i*6+f`: PointShadowArray.java:81-84. Тоже две копии LIVE+STATIC.
- Color-attachment нет (`glDrawBuffer(GL_NONE)`): PointShadowArray.java:175-176.
- Seamless cubemap включён глобально: PointShadowArray.java:39, 170.

### Разрешения по пресетам (IRLShadowQuality.java:13-18)
| Пресет | point face | spot tile | атлас спотов целиком |
|---|---|---|---|
| LOW | 256² | 512² | 2048² |
| MEDIUM (дефолт) | 512² | 1024² | 4096² |
| HIGH | 1024² | 2048² | 8192² |
| ULTRA | 2048² | 4096² | 16384² |

Смена пресета: `setTileSize`/`setFaceSize` -> `delete()` обеих слоёв, ленивая пересоздача на следующем доступе: SpotlightDepthAtlas.java:197-206, PointShadowArray.java:219-228, IRLShadowQuality.java:31-36.

## 2. Ре-бейк, ключи кэша, инвалидация; Softness

### Ключи кэша (ShadowBaker.java)
Кэш per-light, ключ = стабильный `LightRegistry.getId` (не слот): ShadowBaker.java:70-98.

Свет считается dirty, если (спот: ShadowBaker.java:370-373; point: 561-564):
1. первый бейк (`!lastTile.containsKey(id)`);
2. сигнатура изменилась: `lastSig != sig`, где sig = FNV-хэш геометрии света (позиция, направление, range, cosOuter, флаг shadows — lightGeomSig, ShadowBaker.java:993-1001) + XOR avalanche-хэшей in-range model-block окклюдеров (ostatichash) + счётчик статиков (ShadowBaker.java:338-339);
3. блок-лист сменился: **reference-compare** `lastBlocks.get(id) != blocks` — BlockShadowCache возвращает тот же List-instance до изменения блока в радиусе (BlockShadowCache.java:14-40 doc, 101-104);
4. переназначен тайл (`lastTile.get(id) != myTile`).

### Двухслойная схема
- Чисто статичный свет: бейк прямо в LIVE-тайл только при dirty: ShadowBaker.java:366-407.
- Overlay-режим (в радиусе есть entity/replay): STATIC-тайл ре-бейкается только при изменении статики; каждый кадр `copyStaticToLive` (GPU glCopyImageSubData: SpotlightDepthAtlas.java:93-110; PointShadowArray.java:89-126 — с per-face маской T1.2), затем поверх дорисовываются только динамические кастеры: ShadowBaker.java:408-486 (спот), 601-716 (point).
- Динамические кастеры вообще НЕ входят в сигнатуру — они перерисовываются каждый кадр; `wasDynamic` (ShadowBaker.java:81-87) закрывает случай ухода субъекта.

### Инвалидация
- Блоки: `BlockShadowCache.invalidateAt(pos)` из WorldBlockChangeMixin через секционный индекс 16³ + точный тест центра блока против сферы сбора: BlockShadowCache.java:122-163. Сфера сбора квантуется до 1-блочной ячейки (SNAP_PAD=0.87), чтобы плавно движущийся свет не пере-собирал каждый кадр: BlockShadowCache.java:66-99.
- Тайлы sticky per id; кража только у владельца, неактивного >= STALE_FRAMES=2 кадров, с purge его dirty-состояния: ShadowBaker.java:138-173, 762-800.
- Смена пресета качества сбрасывает ВСЁ (resetTileState): ShadowBaker.java:241-247, 839-851; выключение шейдеров — то же + delete текстур: ShadowBaker.java:857-865.
- Бюджет: <= `shadowBakeBudget` (дефолт 4, ShadowConfig.java:33, 52) отложимых full-static бейков за кадр; обязательные (первый бейк / переназначение тайла) вне бюджета: ShadowBaker.java:159-164, 811-819.
- Отключаемый кэш целиком: `shadowCache()` false -> всё в live каждый кадр: ShadowBaker.java:345-364, 534-556.

### Softness
Слова «softness» в Java-коде shadow-пакета (и всём irl-core/src/main/java) НЕТ вообще (grep пуст). Ни `lightGeomSig`, ни `ShadowConfig` (5 геттеров: quality/cache/bakeBudget/blocks/blockRadius — ShadowConfig.java:23-41) её не знают. **Вывод: Softness — чисто сэмплинг-параметр GLSL (PCSS-радиус), её смена НЕ триггерит ре-бейк и вообще не видна бейк-стороне.** Значит «зернистый квадрат» при высокой Softness не может быть следствием пере-бейка; бейк-сторона влияет лишь константным содержимым карты (разрешение тайла, NEAREST-сэмплер спота, far-plane clear).

## 3. Рендер окклюдеров

Одна depth-only проходка на тайл/грань, внутри — три подпасса в фиксированном порядке:

1. **Entity/model-кастеры** (batched, T2.2): `beginCasterBatch -> emitCaster* -> endCasterBatch` — все кастеры копятся в общем entity-Immediate и флашатся ОДНИМ draw на пасс: ShadowRenderer.java:200-298. Рисуются через ванильный/BBS рендер-диспатчер (полные модели, не боксы) — через шов `ShadowCasterSource.emitOccluder`. Перед КАЖДЫМ emit ре-пин матриц света + depth/blend-состояния (MAJOR-A hardening): ShadowRenderer.java:245-269. Изоляция исключений per-caster: ShadowRenderer.java:271-281.
2. **Cutout-блоки** (двери, листва, решётки): тесселируются один раз в текстурированные VBO per RenderLayer (CUTOUT/CUTOUT_MIPPED, ключ id+list-instance), перерисовываются под родным cutout-шейдером с alpha-discard; цвет падает «в никуда» (нет color-attachment), пишется только depth: ShadowRenderer.java:353-377, 560-753.
3. **Opaque-блоки**: VoxelShape AABB -> POSITION-only quad-box VBO per lamp (Stage B кэш, тот же reference-compare листа), рисуется шейдером `getPositionProgram` с ОТКЛЮЧЁННЫМ culling (обе стороны пишут depth): ShadowRenderer.java:339-502, 772-824.

Point-свет: 6 отдельных пассов `beginPointFace` (перепривязка `glFramebufferTextureLayer` на слой slot*6+face: PointShadowArray.java:59-84), блок-VBO перерисовывается на каждую грань, кастеры фильтруются per-face маской сферы против 90°-фрустума (ShadowBaker.java:1121-1139, 1174-1188).

**Можно ли добавить пост-процесс пасс над картой после бейка?** Архитектурно — да, естественные точки:
- сразу после `ShadowRenderer.endPass()` последнего пасса света (спот: ShadowBaker.java:395/445/459; point: после цикла граней) — известен tile/layer, FBO-инфраструктура и state save/restore уже есть (savePassState/endPass: ShadowRenderer.java:826-888);
- двухслойная схема STATIC->LIVE уже реализует «источник + приёмник»: `copyStaticToLive` можно заменить с glCopyImageSubData на fullscreen-blur-пасс static->live — каркас ping-pong фактически готов.
Чего нет: никакого fullscreen-quad/blur-шейдера в бейк-коде, никакой второй вспомогательной текстуры кроме static-слоя, и depth-attachment нельзя сэмплить-и-писать одновременно (нужен отдельный таргет).

## 4. Сэмплеры и mip

- Спот-атлас: `MIN/MAG = GL_NEAREST`, `CLAMP_TO_EDGE`, `TEXTURE_COMPARE_MODE = GL_NONE` (ручное сравнение в GLSL, hardware PCF НЕ используется), `BASE_LEVEL=MAX_LEVEL=0` — mip нет: SpotlightDepthAtlas.java:144-150.
- Point cube-array: то же, но `MIN/MAG = GL_LINEAR` (внимание: linear на depth без compare-mode даёт фильтрацию глубин, не теневого результата), WRAP_R clamp, compare GL_NONE, mip нет: PointShadowArray.java:161-168.
- Единственный mip-уровень аллоцируется (level 0 в glTexImage2D/3D), mipmap-цепочки нет нигде.

## 5. Память (обе копии live+static, 4 байта/пиксель D32F)

Формула: спот = 2 x (4*TILE)² x 4Б; point = 2 x 96 x FACE² x 4Б. Static-слой ленивый — без динамических субъектов возле ламп платится только половина.

| Пресет | спот live+static | point live+static | итого (обе копии) | только live |
|---|---|---|---|---|
| LOW | 32 MiB | 48 MiB | ~80 MiB | ~40 MiB |
| MEDIUM | 128 MiB | 192 MiB | ~320 MiB | ~160 MiB |
| HIGH | 512 MiB | 768 MiB | ~1.25 GiB | ~640 MiB |
| ULTRA | 2 GiB | 3 GiB | ~5 GiB | ~2.5 GiB |

(Комментарий IRLShadowQuality.java:7-11 приводит цифры для одной копии.) Потолки: 16 спотов + 16 point-кубов жёстко; ULTRA c обеими копиями — за пределами 8 GiB карт с запасом «на всё остальное», HIGH уже тяжёлый. Замечание для VSM: RG32F стоит 2x от D32F при том же разрешении, +1/3 на mips.

## 6. Точки расширения / ограничения для смены техники фильтрации

**Что уже есть (плюс для любой техники):**
- Единая точка аллокации формата/параметров: `createAtlas()` (SpotlightDepthAtlas.java:130-175) и `createArray()` (PointShadowArray.java:146-197) — смена internal format / фильтров / добавление mip локализованы здесь.
- Готовый двухтекстурный ping-pong (static/live) + GPU-copy API (`glCopyImageSubData`, GL4.3 уже требуется: SpotlightDepthAtlas.java:105; PointShadowArray.java:99, 121) — blur-пасс static->live встаёт на место копии почти без изменений оркестрации.
- Чёткий пер-световой lifecycle «бейк завершён» (после endPass в ShadowBaker) — сюда вешается downsample/mip/blur ровно для перебейканного тайла/куба, амортизируясь тем же кэшем (пост-процесс только при dirty, что по условию почти бесплатно).
- Точная гранулярность: per-tile scissor у спота, per-face layer у point — пост-процесс можно делать только над изменённой областью.
- Контракт слота в SSBO — один int тайла/слоя (`LightRegistry.setShadowTile`: ShadowBaker.java:343, 532), не зависит от формата текстуры; смена формата не трогает layout SSBO.

**Чего нет / ограничения:**
- Нет color-attachment вовсе: VSM/EVSM (RG32F/RGBA32F) требует (а) добавить color-текстуру к FBO и включить draw buffer, (б) шейдер записи моментов — сейчас depth пишется фиксированной функцией, кастеры рисуются РОДНЫМИ шейдерами слоёв (position, cutout, entity-layers: ShadowRenderer.java:424, 640-641, immediate.draw), т.е. moment-запись «из вершинных данных» потребует либо отдельный конверсионный пасс depth->moments после бейка (проще всего: сохраняет все родные кастер-шейдеры нетронутыми), либо подмену фрагментных шейдеров всех трёх путей (дорого и хрупко).
- Нет mip: `MAX_LEVEL=0` захардкожен (SpotlightDepthAtlas.java:145; PointShadowArray.java:162); для VSM-mips нужно аллоцировать цепочку + glGenerateMipmap/ручной downsample после бейка.
- Нет hardware-PCF: compare mode GL_NONE, спот NEAREST. Дешёвый промежуточный шаг «до VSM» — sampler2DShadow/samplerCubeArrayShadow c COMPARE_REF_TO_TEXTURE + LINEAR = бесплатный 2x2 PCF, меняется только createAtlas/createArray + сэмплинг-GLSL; но это ломает текущее ручное чтение depth (PCSS blocker search читает сырые глубины) — понадобится либо два сэмплера на одну текстуру (texture view / sampler objects), либо отказ от blocker search.
- Несогласованность LINEAR у point vs NEAREST у spot (PointShadowArray.java:163-164 vs SpotlightDepthAtlas.java:146-147) — при raw-depth чтении LINEAR интерполирует глубины соседних текселей, что само по себе кандидат в причины артефактов на гранях.
- Волюметрика сэмплит те же карты (Photon): смена формата/семантики (depth -> moments) меняет и её контракт; но unshadowed-VL паков (все кроме Photon) не затронется.
- Единый GLSL на 7 паков #120..#430: техника должна выражаться в GLSL 1.20-совместимом ядре с #define-дельтами; textureLod/texelFetch на новых версиях доступны через расширения (cube-array уже так подключён), но `textureGather` и im-based трюки на #120-паках — только через те же ext-гарды.
- Ограничение budget-механики: пост-бейк blur надо считать частью «единицы бюджета» бейка (allowStaticBake: ShadowBaker.java:811-819), иначе спайк C10 (~320ms per-face block-cull) усугубится.

**Итоговая оценка для VSM/EVSM:** переиспользуемо — ленивая двуслойная аллокация, кэш/инвалидация (без изменений: сигнатуры не знают о формате), sticky-тайлы, per-tile/per-face гранулярность, GL4.3 baseline. Добавить придётся: color-таргет RG32F(+mips) на оба layout'а, конверсионный пасс depth->moments (рекомендуемый путь, не трогающий 3 кастер-пути), separable blur (можно вместо copyStaticToLive для overlay-огней), и переписать сэмплинг-ветку GLSL с PCSS на Chebyshev с per-pack гардами.