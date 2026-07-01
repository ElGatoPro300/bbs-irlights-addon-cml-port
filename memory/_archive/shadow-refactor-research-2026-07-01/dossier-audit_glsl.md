# Аудит PCSS-сэмплинга irlite_lights.glsl (Photon vs BSL)

Файлы:
- Photon: `C:/Users/Qualet/Documents/Project/Minecraft/BBS/bbs-irlights-addon/Shadres/Modification/Photon/shaders/include/irlite/irlite_lights.glsl` (840 строк)
- BSL: `C:/Users/Qualet/Documents/Project/Minecraft/BBS/bbs-irlights-addon/Shadres/Modification/BSL/shaders/lib/irlite/irlite_lights.glsl` (905 строк)

Ключевой вывод по сравнению: ядро теневого сэмплинга (`irlite_vogel`, `irlite_distFromDepth01`, `irlite_rotationPhi`, `irlite_depthBias`, `irlite_spotGatherTap`, `irlite_spotShadow`, `irlite_pointShadow`) в обоих файлах **побайтно идентично по логике** (Photon строки 133-396, BSL 81-347). Diff-дельты по теням — только обвязка:
- Гейты компиляции: Photon — `PROGRAM_DEFERRED4 || (PROGRAM_COMPOSITE0 && VOLUMETRIC && VL_SHADOWS)` (стр. 67); BSL — `IRLITE_SHADOWS && (SURFACE_PASS || (COMPOSITE_PASS && OUTLINE) || (VL_PASS && ...))` (стр. 76) + внешний гейт `IRLITE_ACTIVE` (VOXY_PATCH/IRLITE_SKIP, стр. 46) + явные `#extension` (стр. 53, 83-84).
- Cookie (6-й vec4 в SSBO + `irlite_cookie`) есть только в Photon; в BSL структура 5×vec4.
- VL-тени: BSL имеет `IRLITE_VL_SHADOW_STRIDE 2` (тап раз в N шагов марша с кэшем `shadowVis`, стр. 863-877) и `IRLITE_VL_RESOLUTION`; Photon тапает каждый шаг (стр. 815-823). VL_STEPS default: BSL 10, Photon 14.
- BSL зовёт shadow-функции ещё и из outline-пасса (`irlite_outlineInk`, стр. 435-439) — т.е. до 2 полных PCSS-вычислений на пиксель на свет при включённом OUTLINE.

## 1. Blocker search
- Тапов: `IRLITE_BLOCKER_TAPS` = 6/10/14/20 для QUALITY 1/2/3/4 (Photon 140-152). QUALITY 0 — blocker search отсутствует (жёсткий 1 тап).
- Паттерн: **Vogel/sunflower disk** — `irlite_vogel()` (Photon 155-160): `r = sqrt((i+0.5)/n)`, золотой угол 2.39996323, поворот на phi.
- Шум/ротация: `irlite_rotationPhi()` (Photon 170-173) — классический hash `fract(sin(dot(gl_FragCoord.xy, vec2(12.9898,78.233)))*43758.5453)`, **чисто пространственный, без temporal** (комментарий «no temporal shimmer» — осознанный выбор).
- Радиус поиска: spot — `searchNdc = max((lightSize*(dist-near)/dist)*worldToNdc, texelNdc)` (стр. 268); point — аналог в world units на касательной плоскости (стр. 362).
- avgBlockerDepth: сумма **линеаризованных** дистанций (`irlite_distFromDepth01`, инверсия перспективной кодировки, стр. 163-167) по тапам, где `refDepth - bias > stored`, делённая на счётчик (стр. 288/378). `blockerCount == 0` → ранний выход 1.0 (полностью освещён).
- Сэмплы blocker-фазы — обычный `texture().r` (NEAREST), без gather.

## 2. Penumbra estimation
- Формула — классические similar triangles (Photon 289): `penumbraWorld = min(lightSize * (dist - blockerDist) / max(blockerDist, near), lightSize)`.
- `lightSize` = per-light `bulbSize` (cone.w) если > 0, иначе глобальный `IRLITE_SHADOW_SIZE` (это и есть Softness-слайдер, 0.0-0.8); clamp сверху `range*0.5` (стр. 263).
- Пол: `penumbraNdc = max(penumbraWorld * worldToNdc, 1.5 * texelNdc)` (стр. 290); point — `max(..., 1.5*texelWorld)` (стр. 379-380). Потолок: `min(..., lightSize)` — penumbra никогда не шире размера лампы.
- Radius blocker search тоже зависит от lightSize → при Softness 0.0 весь PCSS вырождается: `lightSize=0` → searchNdc = texelNdc (пол), penumbra = пол — фактически 1.5-texel PCF.

## 3. PCF-фаза
- Тапов: `IRLITE_PCF_TAPS` = 10/18/28/40 (QUALITY 1-4). Тот же Vogel-диск с `phi + 1.0` (декорреляция от blocker-фазы, стр. 295).
- Compare: **НЕ hardware sampler2DShadow**. Spot: filter-after-compare вручную — `irlite_spotGatherTap` (стр. 182-189): `textureGather` 2×2 → `step(cmpDepth, g)` → ручная билинейка бинарных результатов. Т.е. каждый PCF-тап spot = 4 depth-сравнения со сглаживанием. Point: **чистый бинарный compare** `(refDepth - bias > s) ? 0.0 : 1.0` (стр. 388) — без gather, без билинейки.
- Dither/temporal: НЕТ. Только статичная per-pixel ротация. Никакого TAA-джиттера/blue noise/frame-index.
- Выход за NDC [-1,1] в PCF spot считается lit (`sum += 1.0`, стр. 297); в blocker — просто skip (`continue`, стр. 275) — асимметрия, см. слабые места.

## 4. Spot-атлас vs point cube-array
| | Spot (`irlite_spotShadow`) | Point (`irlite_pointShadow`) |
|---|---|---|
| Хранилище | 4×4 2D-атлас перспективных тайлов, tile = int(vlParams.w+0.5) | samplerCubeArray, layer = int(vlParams.w+0.5) |
| Проекция | ручная реконструкция view-proj из SSBO (lookAt+persp, near 0.05, far=range, aspect 1) | dominant-axis depth `max(|x|,|y|,|z|)` (стр. 336), НЕ евклидова длина (иначе 6-лучевая звезда самозатенения) |
| Оффсеты тапов | 2D NDC внутри тайла, clamp полтексель внутрь тайла | касательная плоскость T/B вокруг dir (стр. 356-358), 3D-направления |
| Тап | textureGather (4 fetch + blend) | 1 texture fetch, binary |
| Ранние выходы | за конусом NDC / за near / refDepth вне [0,1] → 1.0 | refDist вне (0.001, radius) → 1.0 |
| Стоимость на пиксель на свет (Q2) | 10 blocker (обычный fetch) + 18 gather-тапов (=72 сравнения) | 10 + 18 cube-fetches |
| Fast-путь (`fast=true` / Q0) | 1 gather-тап | 1 бинарный тап |
- VL-путь (Photon 639-681, BSL 691-732): всегда 1 жёсткий тап на шаг марша (`irlite_vlSpotStep` — без gather, чистый binary; `irlite_vlPointStep`); BSL дополнительно страйдит.
- Итоговая стоимость на поверхность: N видимых теневых источников × (BLOCKER+PCF) тапов на КАЖДЫЙ пиксель — нет screen-space разрежения, нет LOD по дистанции, нет понижения качества с расстоянием до света.

## 5. Bias
- **Depth bias**: `IRLITE_SHADOW_BIAS 0.05` (world units) → `irlite_depthBias()` (стр. 176-179): `worldBias * far*near / (dist^2 * (far-near))` — перевод мирового смещения в перспективный depth-домен, эффективно slope-agnostic constant world bias. Настоящего **slope-scaled bias нет** (нет зависимости от NdotL).
- **Normal-offset**: `IRLITE_SHADOW_NORMAL_OFFSET 0.05` → сдвиг receiver'а ДО проекции по `(normalize(n) + normalize(toLight)) * normalOff` (стр. 211-217, 321-327) — двойной оффсет (по нормали + к свету).
- **Texel floor**: `normalOff = max(NORMAL_OFFSET, 1.5*texelWorld)` — оффсет растёт с дистанцией от света (угловое разрешение карты); 0.0 у пользователя отключает и оффсет, и floor.
- cmpDepth = refDepth - bias применяется единожды (стр. 248), в blocker-фазе тоже `refDepth - bias > st`.

## 6. #define / пресеты, влияющие на тени
- `IRLITE_SHADOWS` (master on/off), `IRLITE_SHADOW_QUALITY 0-4` (0 = hard 1-tap, 1-4 = 6+10/10+18/14+28/20+40 тапов), `IRLITE_SHADOW_SIZE` (Softness, 0.0-0.8), `IRLITE_SHADOW_BIAS` (0.0-0.4), `IRLITE_SHADOW_NORMAL_OFFSET` (0.0-0.4).
- VL: `IRLITE_VL_SHADOWS`, `IRLITE_VL_STEPS`; BSL-only: `IRLITE_VL_SHADOW_STRIDE 1-4`, `IRLITE_VL_RESOLUTION 100/50/25`.
- Per-light: `cone.w` (bulbSize, переопределяет SHADOW_SIZE), `vlParams.w` (tile/layer, -1 = теней нет).
- Гейты компиляции: Photon `IRLITE_COMPILE_SHADOWS` (стр. 67-69); BSL стр. 76-78 + `IRLITE_ACTIVE`.

## 7. Слабые места (кандидаты на «зернистый квадрат» и прочее)
1. **Статичный hash-шум без temporal** (`irlite_rotationPhi`, Photon 170-173). Один и тот же поворот Vogel-диска на пиксель навсегда → при большой penumbra (высокая Softness) 18 тапов не покрывают широкий диск и остаточный шум ротации **застывает в стабильный пространственный узор** — визуально «зернистый квадрат/структурный шум», который не усредняется TAA (Photon имеет TAAU, но шум не меняется по кадрам, ему нечего накапливать). Это сильный кандидат на известный баг «зернистый квадрат при высокой Softness» даже без участия бейка.
2. **Point-путь без filter-after-compare** — бинарный compare на тап (стр. 388) против gather-билинейки у spot. При равных тап-каунтах point-тени заметно более ступенчато-шумные; и hard-путь point (стр. 348-349) даёт чистый алиасинг 1-битного теста, тогда как spot hard-путь сглажен gather'ом. Несогласованность качества между типами источников.
3. **Texel-floor'ы делают Softness нелинейной по дистанции**: `penumbraNdc = max(penumbraWorld*worldToNdc, 1.5*texelNdc)` (стр. 290) и floor нормал-оффсета `max(OFFSET, 1.5*texelWorld)` — на дали пол доминирует, реальная мягкость перестаёт зависеть от слайдера; вблизи слайдер работает. Плюс clamp `min(lightSize, range*0.5)` (стр. 263) — на маленьких radius Softness тихо срезается наполовину радиуса. Переходы через эти clamp'ы = видимые «переломы» градиента мягкости.
4. **Blocker search на NEAREST fetch по тому же mip 0**: при 20 тапах на диске, чей радиус ~ lightSize, avgBlockerDepth сильно шумит на сложных окклюдерах → шум penumbra-оценки → пятнистость ширины полутени соседних пикселей (второй источник «зерна» именно при высокой Softness — радиус поиска растёт вместе с SHADOW_SIZE). Классическое лекарство — blocker search по мипованной/min-карте — недоступно, т.к. атлас не мипуется.
5. **Дивергенция варпов**: ранние выходы `blockerCount==0 → return 1.0` (стр. 285), `if (hard)`, per-light `continue` по маскам/конусу, `if (refDepth-bias > st)` внутри цикла — соседние пиксели на границе полутени идут по разным путям; при 2048 потенциальных светах цикл `for i < count` с расходящимися внутренними ветками — основной GPU-bound фактор. Внутри одного света PCF-петля с ранним `continue` по NDC-границе также дивергентна.
6. **Асимметрия обработки края тайла**: blocker-тап за NDC → `continue` (не учитывается, стр. 275), PCF-тап за NDC → `sum += 1.0` (считается lit, стр. 297), а gather-тап при этом ещё и clamp'ится внутрь тайла (стр. 184) — три разных политики. На краю конуса это даёт систематическое высветление/бандинг полутени у границы фрустума спота.
7. **Дублирование PCSS в BSL при OUTLINE**: `irlite_outlineInk` (BSL 435-439) зовёт полный `irlite_spotShadow/irlite_pointShadow` с `fast=false` во втором пассе — на пиксель с outline тени считаются дважды в полном качестве; для рим-света хватило бы `fast=true` (1 тап).
8. **Нет ранней LOD-деградации**: `fast`-параметр существует, но surface-путь всегда передаёт `false` (Photon 515-516, BSL 576-577). Дальние/тусклые источники (attenuation ~ 0.01) платят полный PCSS. Дешёвый выигрыш: fast=true при малом attenuation или большом dist/radius.
9. **Blocker и PCF используют один и тот же phi (+1.0)** — сдвиг фазы на 1 радиан слабо декоррелирует последовательности с одинаковым золотым углом; корреляция blocker- и PCF-паттернов усиливает структурность шума. Дешевле было бы phi и phi+PI/2 с разным масштабом или независимый второй hash.
10. **VL-путь Photon без stride** (стр. 815-823): до 14 шагов × 1 тап × N светов на четверть-разрешении каждый кадр; BSL уже имеет stride-кэш (стр. 863-877) — несинхронизированная оптимизация, стоит бэкпортировать в Photon (актуально, т.к. VL-тени пока только Photon по факту включения).
11. **`irlite_vlPointStep` нормал-оффсет `dist - 2.0*NORMAL_OFFSET`** (стр. 668/719) — константа в мировых единицах без texel-floor'а: на дальних дистанциях от света шаг марша самозатеняется об собственный тексель (полосатость god-rays), тот же класс проблемы, что и п.3, но в VL.
12. Мелочь: `irlite_distFromDepth01` предполагает симметричную NDC-кодировку глубины [0,1]→[-1,1]; если бейк когда-нибудь перейдёт на reversed-Z/0..1 clip — линеаризация в blocker-фазе тихо сломает penumbra (контракт неявный, нигде не задокументирован рядом).