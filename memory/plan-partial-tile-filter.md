---
name: plan-partial-tile-filter
description: "Partial-tile filter спотов: AABB-проекция + крутилка shadow_pose_reach DONE+ЗАКОММИЧЕНЫ 2026-07-18 (core 0e236e2 / addon 7ae6626): ultra bake 16.0 → 12.9-13.0 ms, rect-share 36% → ~100%; клип на дефолте 0.9 РЕАЛЕН — юзер тюнит слайдером. ВЕРНЁМСЯ: калибровка дефолта, апроны/lod, тираж."
metadata: 
  node_type: memory
  type: project
  originSessionId: 8b021185-5229-471b-baa9-fa3bdf45267e
---

# Partial-tile filter спотов

## ЗАКОММИЧЕНО 2026-07-18 (сессия 6): core 0e236e2 / addon 7ae6626 — ПО КОМАНДЕ ЮЗЕРА, ТЕМА ОТКРЫТА («мы ещё вернёмся»)
Визуал-гейт показал РЕАЛЬНЫЙ клип на дефолте 0.9 (юзер: «реально обрезается» — BBS-формы/позы шире хитбокса; сфера 1.5 это прятала). Ответ = крутилка: BBS настройки → вкладка irlite → «Shadow pose margin» (shadow_pose_reach, ValueFloat 0..2, деф. 0.9, live per-bake): core ShadowConfig.shadowPoseReach() — ОПЦИОНАЛЬНЫЙ default-геттер (редактор не переопределяет, Builder-сеттер опционален; спека config-source-injection дополнена), ShadowBaker санирует NaN; addon: IrliteConfig + BBSSettingsMixin + IrliteShadowConfig + L10nMixin (EN тултип). Обе оси клампятся orad → максимум слайдера = ровно старый sphere-box (доказанно без клипа), теряется только перф. Насыщение для гуманоида ~1.2-1.3.
ВЕРНУТЬСЯ (next session по этой теме):
1. Калибровка: спросить юзера, на каком значении слайдера клип ушёл в его сценах → возможно поднять дефолт 0.9 (и в core default-геттере, и в BBS-слайдере, и в фолбэках — 3 места + спека).
2. Если клипает ВЕРТИКАЛЬ (прыжки/руки вверх у крупных форм) — добавить поза-запас и на ось Y (сейчас только hv+0.5, кламп сферой).
3. Перф-ожидание 6-9 ms НЕ добито (факт 12.9-13.0): следующие рычаги = апроны EVSM/pyr (+4/+8) и глубокие lod'ы; замерить их долю прежде чем пилить.
4. Тираж на порт-ветки core 1.21.x (Ф4-паттерн) — отдельно по команде; редактор получает дефолт 0.9 автоматически без UI.
Прогон с крутилкой: sp.rect 98-100% в тяжёлом окне (~25 ламп), ошибок irlite 0 (NPE UITexturePicker и alex_bends.png = внутренние BBS, не наши).

## СТАТУС 2026-07-18 (сессия 6, AABB-ПРОЕКЦИЯ): РЕАЛИЗОВАНА, e2e PASS
Правки ТОЛЬКО core ShadowBaker.java (шов НЕ менялся; аддон/редактор — пересборка): SoA orh[] (0.5·hypot(ex,ez)·scale, yaw-инвариант) + ohv[] (0.5·ey·scale) из emitFromBox; сферный emit() пишет rh=hv=radius → model-блоки/редактор получают sphere-box байт-в-байт. computeSpotDynRect: горизонталь min(rh + max(0.5, POSE_REACH·hv), orad), вертикаль min(hv+0.5, orad) — кламп сферой гарантирует «не хуже прежнего». POSE_REACH=0.9: рекон закрыл открытый вопрос — arm1 передаёт entity.getBoundingBox(), arm3 hitbox-бокс формы (НЕ поза-расширенные); T-поза гуманоида ≈1.19 блока от центра > rh+0.5=0.92 → плоский M=0.5 гарантированно клипал бы; 0.42+0.9·0.9=1.23 покрывает. NaN/отриц rh/hv → деградация в sphere-box. coversMost 7/10 → 15/16 на ОБОИХ сайтах (общие COVERS_MOST_NUM/DEN). Cull потребляет только orad — не тронут; point-путь не тронут.
ЗАМЕР (лог runClient, та же ultra-сцена 25 спотов, юзер гонял сам): **bake 16.0-16.1 → 12.86-13.02 ms (−19%; от исходных 17.8 = −27%)**; **sp.rect == sp.copy == 800 → rect-share 100%** (было 36%); evsm 7.1 / pyr 2.9 / spot-дро 2.9; vram evictions +0; ошибок irlite 0. Ожидание плана 6-9 ms НЕ достигнуто: апроны +4/+8, глубокие lod'ы и поза-маржин (полуразмеры 1.23/1.4 vs плановые 0.92) съедают часть — дальнейшее ужатие = ручка POSE_REACH, трогать только по визуальному тесту.
ГЕЙТ ДО КОММИТА: визуал-стресс юзера — анимации с широкими позами (руки в стороны): края теней НЕ подрезаны; уход актёра — без призраков. Killswitch прежний: -Dirlite.noPartialFilter=true.

## СТАТУС 2026-07-18 (база): DONE + ЗАКОММИЧЕН (core 3eaebae; addon кодом не менялся)
Замер юзера, та же ultra-сцена 25 спотов: **bake 17.8-18.4 → 16.0-16.1 ms (−10-12%)**; rect-share **стабильно 36%** (9/25 ламп partial — их фильтры почти бесплатны; 16/25 схлопываются в FULL: актёры ВПЛОТНУЮ под лампами, cull-сфера (полудиагональ+0.5 ≈ 1.5 блока) на 2-3 блоках проецируется >70% тайла → coversMost(7/10) отдаёт FULL). Тривиальная сцена: bake 1.46 → 0.32 ms (×4.5). Ошибок/артефактов 0. Ревью 11 агентов → 2 фикса: lastDynRect.remove в !cache-ветке (призрак после тогла shadowCache); FULL-fallback при |коорд|>2^23 (float SoA vs double-дро).

## NEXT SESSION: ПРОЕКЦИЯ РЕАЛЬНОГО AABB ВМЕСТО СФЕРЫ
ПРОМПТ: «Делаем AABB-проекцию по plan-partial-tile-filter, ультракод, но субагентов экономим (лимиты)».
ЦЕЛЬ: 16 FULL-ламп → partial; ожидание bake 16 → ~6-9 ms. РЕЖИМ: рекон/имплементация ИНЛАЙН (не фан-аут); максимум ОДИН лёгкий ревью-агент или селф-ревью.
ДИЗАЙН (готов, выведен из рекона этой сессии):
1. OccluderSink.emitFromBox УЖЕ получает Box+scale (шов НЕ меняется!); SINK в ShadowBaker (~L2150+) схлопывает в сферу: rad=0.5·diag·scale+0.5, center=interp+ey/2. Добавить SoA-массивы полуразмеров: **rh[] = hypot(hx,hz)·scale** (горизонтальная полудиагональ — инвариант к yaw, для гуманоида ~0.42 vs сфера 1.5) и **hv[] = (ey/2)·scale**; emit() (sphere-путь, редактор) пишет rh=hv=radius.
2. computeSpotDynRect: углы бокса = center ± (rh+M, hv+M, rh+M), M=OVERLAP_MARGIN 0.5. Остальное (матрицы, near-тест w<0.05, +1px слак, clamp, coversMost, гард 2^23) БЕЗ изменений. Сциссор остаётся hard-bound: ошибка → видимый клип, не коррупция.
3. ОТКРЫТЫЙ ВОПРОС (единственный рекон next-сессии, инлайн): что за Box передаёт IRLiteBbsCasterSource.emitFromBox (entity.getBoundingBox()? form-bounds?) и покрывает ли Box+0.5 широкие позы BBS-анимаций — сверить с irl-core/docs/shadow-caster-seam-spec.md INV-5 (слак сферы это прятал). Если поз-выходы реальны — поднять M горизонтали (например max(0.5, 0.5·rh)) по визуальному тесту.
4. Протокол: та же ultra-сцена; ждём sp.rect 36% → 80-100%, bake → 6-9 ms; ВИЗУАЛ-стресс: анимации с широкими позами (руки в стороны) — края теней не подрезаны; уход актёра — без призраков.
5. Заодно поднять coversMost(7/10) → (15/16) (мелкий бонус оставшимся в 70-95%).

## РЕАЛИЗОВАННЫЙ ДИЗАЙН (референс)

ЦЕЛЬ: steady overlay-кадр 25 ultra-спотов = evsm 9.5 + pyr 4.3 + дро/копии 4.4 ≈ 18 ms → фильтровать/копировать только rect динамиков. Java-only (GLSL пакам не виден). Killswitch: -Dirlite.noPartialFilter=true → полнотайловые пути.

## РЕКОН-ФАКТЫ (якоря)
- Rect-копия ЕСТЬ: DepthTileAtlas.copyStaticToLiveRect(x,y,w,h) (:245-260, абс. пиксели, glCopyImageSubData; PointDepthAtlas уже юзает per-face). Фасад SpotlightDepthAtlas НЕ экспонирует — добавить.
- Матрицы спота приватны в ShadowRenderer, но реконструируемы в спот-цикле ShadowBaker: A=round(lx) (==currentOriginX), eye=L-A, lookAt(eye, eye+dir, pickStableUp: |dy|>0.99?(0,0,1):(0,1,0)), perspective(toRadians(max(outerDeg,1)), 1.0, NEAR=0.05, max(range,0.15)). ShadowRenderer.beginSpot :131-181.
- Сциссор: beginSpot ставит viewport+scissor на весь тайл (:151-157), между begin и endPass никто не трогает → суб-rect сциссор для dyn-дро чист (viewport полный — он задаёт NDC-маппинг; сциссор сузить).
- beginSpot(clear=false) пропускает только glClear → «клиром» live служит копия. Сужение копии ⇒ вне ректа остаются ПРОШЛОКАДРОВЫЕ силуэты ⇒ копировать union(curRect, lastDynRect).
- Сферы динамиков в SoA: shortIdx/shortCount + ox/oy/oz/orad (+OVERLAP_MARGIN 0.5 уже внутри), oStatic-фильтр; scan==render инвариант. Интерполяция поз до scan → bbox честный, но анимация поз может вылезать за hitbox-сферу — margin + сциссор-как-hard-bound.
- Спот-overlay (ShadowBaker ~:734-836): копия :779 безусловная (у спота НЕТ флага bakedStatic — завести, зеркало поинта :1105/:1149); dyn-дро :788; markDirty :816-817.

## ДИЗАЙН
1. **Sphere→NDC bbox** (новый хелпер в ShadowBaker): для каждого dyn из шортлиста — 8 углов AABB (C±r) в анкер-спейсе → viewProj → если w<=near-eps у любого угла → FULL RECT; иначе union NDC → тайл-локальные пиксели (депт-разрешение), clamp в тайл. Пустой rect (все вне) — возможен только при рассинхроне с cone-cull → FULL RECT fallback.
2. **ShadowBaker спот-overlay**: bakedStatic-флаг как у поинта. bakedStatic || killswitch || rect==full → текущие полнотайловые пути. Иначе: copyStaticToLiveRect(union(cur,last)); dyn-дро с суб-сциссором=curRect (новый ShadowRenderer.restrictScissor(px,py,w,h) после beginSpot, или параметр); markDirtyRect(tile, unionRect, range) в Pyramid/Evsm.
3. **lastDynRect**: Long2LongOpenHashMap id→packed rect (4×16бит ТАЙЛ-ЛОКАЛЬНЫХ depth-текселей) + хранить tile в пакете или валидировать rect только при tile==myTile (tier-флип меняет tileSizePx; любая смена тайла в overlay и так BAKE_FORCED→full). Лайфцикл СТРОГО зеркало lastFaceDynamic: purgeDirtyState, resetTileState, retainDirtyState, запись каждый overlay-кадр (leave-кадр пишет full/чистит), union при копии.
4. **SpotShadowPyramid rect**: dirtyMask остаётся + параллельный rect-аккумулятор (per-tile packed rect; markDirty(tile) без ректа = full). Диспатчи: lod0 rect = depthRect>>1 выровнять (floor/ceil на чёт), per lod >>1 с ceil+align; min/max без кросс-саппорта — только выравнивание. Рассинхрон маски/ректов недопустим: маска чистится до depthTex-чека — rect-аккумулятор чистить в ТОЙ ЖЕ строке.
5. **SpotShadowEvsm rect + scratch-конвейер (ключевое)**: mip0 хранит БЛЮРЕННЫЕ моменты и блюр in-place ⇒ наивный rect даёт blur-of-blur кромку. Решение: ВСЕГДА (и full, и rect) конвейер reduce→scratchA (rect-локально, dstOrigin=0), H: scratchA→temp, V: temp→mip L (абс. origin, write-rect). Это ТЕ ЖЕ 3 диспатча — full-tile кадры не дорожают, mip0 raw-write исчезает, clamp'ы шейдеров совпадают со scratch-границами естественно (интерьерный write-rect инсетнут от scratch-краёв на саппорт; на краю тайла scratch-край == тайл-край). Шейдер-строки БЕЗ изменений (convert/mip уже имеют srcOrigin/dstOrigin/dstSize; blur имеет srcTex/srcLod/origins/size). scratchA = вторая temp-текстура (max tile region @shift), +16 MiB @ultra.
   Саппорты: write-rect_L0 = evsmRect(=depthRect>>shift) +4; scratchA rect = write-rect +4 (raw для H±2,V±2); per lod: rect_L = ceil(rect_{L-1}/2)+4, clamp в регион тайла; глубокие lod'ы быстро схлопываются к полному (мелкие регионы) — ок.
6. **Full-rect fallback'и (обязательные)**: bakedStatic-кадр; tail-кадр (subject left, уже BAKE_FORCED-путь); первый overlay (!lastStaticTile → FORCED); смена тайла/tier; quality/preset (resetTileState+delete каскад); near-plane пересечение сферы; killswitch; rect ≥ ~70% площади тайла (не окупается).
7. **Порядок кода**: pure-static спот-путь и no-static clear-путь НЕ трогаются. Поинт НЕ трогается (его rect — потом).

## РИСКИ ИЗ РЕВЬЮ РЕКОНА (держать при имплементации)
- Сциссор = hard-bound: недооценка проекции деградирует в видимый клиппинг силуэта, НЕ в коррупцию Hi-Z (пирамида вне ректа не пересчитана — depth туда просто не пишется).
- 5 сайтов лайфцикла lastDynRect ревьюить отдельно (класс багов lastFaceDynamic).
- Инвариант «same sig ⇒ same bytes» static-слоя становится многокадровым — уже load-bearing, но окно шире.
- Выигрыш калибровать по реальной сцене; апроны (+4/+8) и глубокие lod'ы съедают часть.

Связь: [[plan-shadow-bake-track]] (родитель-трек), [[fix-shadow-slot-rank-stability]] (инварианты тайлов), [[plan-point-shadow-atlas-merge]].
