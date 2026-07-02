---
name: project-point-shadow-square-root-cause
description: "Итог сравнения point-теней IRLEngine (эталон, без артефакта) vs irl-core (с артефактом): бейк-пайплайны идентичны кроме дефолта разрешения (старый HIGH 1024, новый MEDIUM 512) — главный подозреваемый «зернистого квадрата»; cull/polygon-offset/кодировка исключены; сэмплинг-дельты = усилители; эксперименты E1-E4 не выполнены."
metadata: 
  node_type: memory
  type: project
  originSessionId: f849db2c-eb98-4fbd-85b4-28c752db93e6
---

Сравнение 2026-07-02 (workflow: 4 ридера контрактов + дифф 24 дельт + 16 адверсариальных верификаций). Полные контракты/дельты/вердикты: `_archive/point-shadow-comparison-2026-07-02/`. Симптом: point-лампа у стены — чистый квадрат в центре пятна (граница = смена доминантной оси, +-45 град), вокруг зерно self-shadow без окклюдеров; сейчас замаскирован EVSM (Ф2b), корень был не найден. Ключевой вход: byte-port старого шейдера НЕ убрал квадрат -> корень на Java/текстурной стороне.

ИСКЛЮЧЕНО (оси идентичны в обеих системах; все три главных подозреваемых ТЗ отпали):
1. Двусторонний depth-рендер блоков: старая ТОЖЕ рисует опак-блоки с RenderSystem.disableCull, обе стороны квада пишут depth (old ShadowRenderer:443 + коммент :362-363 «Winding doesn't matter because culling is disabled»; new :419 + :770). Ресивер (стена/пол) лежит в карте ОБЕИХ систем, во все 6 граней, без bias — отсутствие acne в старой обеспечивалось НЕ исключением ресивера.
2. glPolygonOffset / бейк-bias: отсутствует в ОБЕИХ (grep 0 hits по обоим деревьям).
3. Кодировка глубины: dominant-axis perspective в ОБЕИХ. Бейк: одинаковые perspective(90, aspect 1, near 0.05, far max(radius,0.15)) + одинаковая таблица face dir/up. Сэмплинг: одинаковая формула refDepth (old ir_lights.glsl:1078-1079 vs new irlite_lights.glsl:516-521), одинаковый near 0.05, far=radius; паритет bake-far==sample-far подтверждён (D14).
4. Текстура: обе GL_TEXTURE_CUBE_MAP_ARRAY DEPTH_COMPONENT32F, MIN/MAG LINEAR, CLAMP_TO_EDGE, COMPARE_MODE NONE (ручной compare), seamless on, MAX_LEVEL 0.
5. Биндинг под Iris (D2 REFUTED): старая биндила тем же механизмом — addDynamicSampler как TEXTURE_2D + миксин SamplerBindingCubeArrayMixin (cancel + bindTextureToUnit CUBE_MAP_ARRAY); файлы есть в IRLEngine/mixin/client/iris/. Общий (не дельта) риск обеих: ci.cancel() в HEAD пропускает и bindSamplerToUnit(unit,0) — чужой GL sampler-object на юните может перекрывать LINEAR; учесть кэш IrisRenderSystem.samplers[unit] при диагностике.
6. Порядок draw внутри грани одинаков: кастеры -> cutout-блоки (cull on) -> опак AABB-VBO (cull off); depthMask/depthTest/blend пинятся одинаково; depthFunc не ставится нигде в обеих.

КОРЕНЬ (D1 CONFIRMED, единственная существенная бейк-дельта, согласованная с byte-port тестом): дефолт разрешения. Старая: default HIGH — point face 1024 (old PointShadowArray:40 FACE_SIZE=1024; IrlightsAddon:30 shadow_quality default 2=HIGH; javadoc «HIGH is the prior hardcoded behavior»), spot tile 4096. Новая: default MEDIUM — point face 512 (new PointShadowArray:36; IRLShadowQuality current=MEDIUM; аддон BBSSettingsMixin:21 default 1), spot tile 1024. Кубо-тексель вдвое крупнее (спот — вчетверо). Механизм: на боковых гранях стена видна под касательным углом, depth-ошибка ~slope*texelWorld/2 на тексель; при 512 она превышает мировые константы (bias 0.02-0.05, offset 0.05) -> бинарный compare мерцает = зерно; на фронтальной грани (в лоб, +-45 квадрат) глубина по грани почти константна -> чисто. Полностью объясняет byte-port пост-мортем, если тест шёл на дефолтном MEDIUM (наиболее вероятно по контексту памяти): старый шейдер на 512-картах даёт квадрат, на 1024 — нет.

УСИЛИТЕЛИ (сэмплинг-дельты; не корень по byte-port, но проявители зерна на 512 в родном новом шейдере): D3 gather filter-after-compare (старая: 1 texture() LINEAR тап = билинейное усреднение глубин ДО compare; для планарного ресивера интерполяция в точке реконструирует глубину плоскости -> анти-acne по построению; новый gather сравнивает 4 сырых текселя по отдельности); D4 penumbra floor 1.5*texelWorld + sharpen (старая penumbra->0 при blocker==receiver, тапы схлопывались в точку; новая принудительно сэмплит соседние тексели); D5 searchRadius floor 1 тексель (старая без floor); D6 пирамидная ветка cmpP>pyrMax -> жёсткий 0.0; D21 early-out квантование на 4 тапах. D8: новый bias 0.05 УЖЕ 2.5x старого 0.02 -> гипотеза «мало биаса» сама по себе не работает, дело в размере текселя.

ОБЩАЯ СЛАБОСТЬ обеих систем (кандидат в дешёвый фикс, НЕ дельта): bias считается от dist=refDist (евклид), а refDepth кодируется от zPersp (доминантная ось); refDist/zPersp до sqrt(3) у углов -> из-за dist^2 в знаменателе фактический depth-bias занижен до ~3x РОВНО на боковых гранях/диагоналях — геометрия занижения совпадает с рисунком артефакта. На 1024 запаса хватало, на 512 — нет.

ЭКСПЕРИМЕНТЫ (по одному переключению; для видимости PCF-пути закомментировать IRLITE_SHADOW_PREFILTER — EVSM сейчас глотает point-acne; НЕ выполнены, код не менялся):
- E1 (главный дискриминатор): пресет Shadow Quality -> HIGH (1024) в BBS-настройках (проверить текущее значение — персистится). Зерно/квадрат должны уйти к паритету со старой системой. Подтверждает/опровергает D1 как корень.
- E2 (на MEDIUM): IRLITE_SHADOW_BIAS слайдер 0.05 -> 0.20/0.40 из Iris UI. Гаснет -> подтверждение depth-error механизма (ценой peter-panning); не гаснет -> пересмотр в пользу стейта.
- E3 (на MEDIUM): выключить IRLITE_POINT_GATHER (возврат compare-after-filter texture()). Ожидание: зерно заметно мягче, но не ноль.
- E4 (на MEDIUM): в point-пути bias от zPersp вместо refDist (1 строка). Таргетно бьёт +-45-паттерн; кандидат в постоянный фикс дешевле подъёма пресета.

Durable-фиксы-кандидаты (решение за юзером): (а) дефолт point-пресета обратно HIGH/1024 — live VRAM 96->384 MiB + пирамида/EVSM бюджеты (см. new IRLShadowQuality javadoc); (б) остаться на 512 + zPersp-bias (E4) и/или slope-aware floor.

Связь: [[shadow-distance-quality-plan]] (KNOWN-OPEN квадрат — закрыт этим диагнозом), [[plan-shadow-filtering-refactor]] (Ф2b EVSM = маска, не лечение), [[addon-shadows]] (бейк-референс), [[shader-shadow-sampling]] (контракт чтения), [[ref-irlengine-photon-patch]].
