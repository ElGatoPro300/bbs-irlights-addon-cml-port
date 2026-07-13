---
name: plan-shadow-lod-tiers
description: "CHECKPOINT 2026-07-13: LOD-тиры I1-I4 + caster fix закоммичены (core 2e57f8d, addon 700b60c, editor 08df3f6); spot 64, point 18, caster pool nearest-128; I4 adversarial PASS, I5 визуальные гейты pending. Тираж на другие шейдеры/версии только по отдельной команде пользователя."
metadata:
  node_type: memory
  type: project
---

Importance-based shadow resolution (LOD-тиры карт теней). Выбор юзера 2026-07-12 после закрытия Phase 3 кластеризации ([[plan-perf-fix-cluster-phase3]]). Актуальный статус — checkpoint-блок в конце файла; ранний recon ниже сохранён как история.

ЦЕЛЬ: поднять число одновременных теней (сегодня жёстко 16 point + 16 spot) без взрыва VRAM/бейка: ближние лампы (по уже существующему C1-приоритету) получают высокое разрешение, дальние — низкое. Синергия: tier0=1024 для ближних point ЗАКРЫВАЕТ D1 «зернистый квадрат» ([[project-point-shadow-square-root-cause]]) — единственный вариант, дающий и количество, и качество одновременно.

RECON DONE 2026-07-12 (read-only workflow wf_b562c5da-9ce, 4 агента L1-L4; все клеймы = file:line):

L1 POINT-СТЕК (дорогая половина):
- 3 GL-текстуры на слот, всё от одного мутабельного PointShadowArray.FACE_SIZE (:36; ОБЯЗАН оставаться степенью 2 — findMSB-математика пирамиды/EVSM): депт-куб D32F (cube-map array, MAX_SHADOWS=16 final :35, LAYER_COUNT=96, layer=slot*6+face); MSM-моменты PointShadowEvsm RGBA32F base=F/2 полный mip-чейн, хранится 2D_ARRAY + cube-array texture VIEW поверх (ноль доп. VRAM, пак сэмплит viewId); пирамида PointShadowPyramid RG32F base=F/2. БАЙТЫ на LIVE-куб = 72*F^2 (депт 24*F^2 * (1 + 2/3 пирамида + 4/3 EVSM)); static-overlay слой лениво добавляет +24*F^2. Аллокация ВСЕГДА на весь LAYER_COUNT независимо от занятости слотов. delete() каскадит все три.
- РЕАЛЬНЫЙ VRAM СЕГОДНЯ (MEDIUM 16x512 live) = 288 MiB (96 депт + 64 пир + 128 EVSM) — старая оценка «~160MiB» в [[addon-shadows]] устарела (писана до MSM/пирамиды). Per-cube LIVE: 1024=72 MiB, 512=18, 256=4.5, 128=1.125.
- КЛЮЧЕВОЙ ЭНЕЙБЛЕР: шейдер узнаёт разрешение через textureSize(irl_pointShadowArray,0) (CR irlite_lights.glsl:563), НЕ через uniform => у каждого тир-массива своя textureSize, texel-floor/size-gate математика самоадаптируется per tier.
- Мульти-тир = N ОТДЕЛЬНЫХ cube-array (GL: слои одной текстуры одноразмерны; sub-viewport в кубе нежизнеспособен — направляющий сэмплинг адресует весь [-1,1]^2 face). Сегодня классы = static-синглтоны => нужен instance-per-tier рефакторинг (главная core-стоимость). Сэмплеры: +3 GLSL-юниформа на тир (array/pyramid/evsm); IrlSamplers-регистр (:60-73) чисто аддитивен, ОБА мижина (ProgramSamplersBuilderMixin/SamplerBindingCubeArrayMixin) data-driven и НЕ меняются. Лимит слоёв: GL 4.3 min 2048 => до 341 куба — не ограничение.
- Редактор трогает только константу MAX_SHADOWS (LightDriver.java:91-93, UI-клэмп) => сохранить суммарный total-slots аксессор.

L2 SPOT (дешёвая половина, главная находка recon):
- Статичное квадродерево В ТОМ ЖЕ атласе: внешняя сетка 4x4 ячеек НЕ меняется; ячейка = либо 1 тайл full-size (tier0), либо 2x2 суб-тайла half-size (tier1), либо 4x4 quarter (tier2). Разбиение ФИКСИРОВАНО per quality preset (константы N0/N1/N2), меняется только владелец слота (как сегодня). Суб-деление сохраняет тексели точно (1024^2 = 4x512^2 = 16x256^2) => НОЛЬ доп. VRAM; пирамида/EVSM спота сайзятся от ФИЗИЧЕСКОГО атласа (SpotShadowPyramid:238-239, SpotShadowEvsm:334-335), не от числа тайлов => их бюджеты не меняются вовсе.
- vlParams.w НЕ НУЖНЫ НОВЫЕ БИТЫ: tier = чистая функция плоского индекса слота (piecewise по N0/N1/N2 — компайл-константы уровня GRID_X=4); диапазон растёт 0..15 -> 0..99+, float точен до 2^24; декод-идиома `гард <0 ДО int(w+0.5)` не меняется. Явный per-light rect понадобился бы только при ДИНАМИЧЕСКИХ границах пулов — отдельная большая фича, не брать.
- Тот же zero-bit трюк обобщается на POINT: глобальный номер point-слота сквозь тиры (0..K0-1 tier0, K0..K0+K1-1 tier1, ...) => (tier, localSlot) выводятся из диапазонов; layer=localSlot*6+face в своём массиве. SSBO-контракт binding7 НЕ меняется вообще.

L3 БЕЙКЕР:
- Точка врезки тира: верх обоих циклов сразу после i=orderedIndex(k) (spot :300-301, point :560-561). КРИТИЧЕСКИЙ НЮАНС: k — глобальный ранг по ВСЕМ лампам, циклы скипают чужой тип => тир считать по PER-TYPE счётчику (spotRank++/pointRank++), иначе 12 ближних point-ламп выдавят первый спот в tier1.
- acquireTile => per-(type,tier) пулы owner/active (тир = другая текстура, не слот того же атласа).
- Смена тира = полный ре-бейк (spot 1 тайл, point 6 граней + static-слой), уже классифицируется как BAKE_MANDATORY через lastTile!=myTile (:405/:413/:645, гейт :910-944) => троттлится C2-пулами, worst case = 1-кадровый SHADOW_PENDING-гэп (омит из SSBO, не засвет). Значит флипы должны быть РЕДКИМИ: скопировать паттерн C1 HYSTERESIS=8.0 (LightRegistry.java:80, скидка к score был-в-наборе :378-381) как ранговый Schmitt-триггер на границах тиров.
- Пресеты: IRLShadowQuality.apply() (:39-44) -> setFaceSize/setTileSize -> delete()+lazy realloc — механизм переиспользуется per-tier (пресет задаёт вектор размеров тиров + N0/N1/N2). Блок-кэши/сигнатуры резолюшн-НЕзависимы — смена тира инвалидирует только GPU-текстуру, CPU-списки живут.
- OPEN-2 occluder-32 ПАРОВОЗОМ (без него тиры бессмысленны — произвольная отсечка кастеров убивает выигрыш): дроп в SINK (:1331-1333/:1348-1350), порядок обхода арбитрарен по дистанции; арки УЖЕ считают dist^2 для COLLECT_DIST-гейта => bounded selection nearest-32 дёшев (вставка в отсортированный по dist^2 массив на 32).

L4 ШЕЙДЕР + ТИРАЖ:
- irlite_spotShadow (215 строк) БАЙТ-В-БАЙТ идентичен во всех 7 паках; irlite_pointShadow (246 строк) сверен там же (Photon: working copy Shadres/Modification/Photon СТЕЙЛ на ~10ч/51 строку против dev-копии run/shaderpacks/1/photon_v1.3b_IRLights — известный дрейф из [[plan-shadow-filtering-refactor]], синкнуть при тираже). Один шаблон тиража реален.
- Выбор сэмплера по тиру: массив сэмплеров + dynamically-uniform индекс легален (GL_ARB_gpu_shader5 уже enable'нут в файле :172; per-light значение из SSBO одинаково для всех активных лейнов итерации; кластерный continue Phase 3 динамическую юниформность НЕ ломает — считается по активным), НО это UB-без-диагностики при нарушении => ДЛЯ ПИЛОТА безопаснее явная if-цепочка по 2-3 тирам (компилятор проверяет), массив — микро-опт потом.
- Декод-сайты vlParams.w: spot :339-340, point :556-557, VL :1275-1276 — все три получают range-piecewise (одинаковая формула, констант N* из пресета). Iris-экран сегодня: IRLITE_SHADOWS/QUALITY/SIZE/BIAS/NORMAL_OFFSET + VL_SHADOWS/STRIDE (screen.IRLIGHTS_SHADOWS, shaders.properties:62; гоча sliders= — второй обязательный гейт). Тир-константы N0/N1/N2 — компайл-дефайны в паке, синхронные с Java-пресетом (вариант: гнать через header нового буфера как в кластере — решить на дизайне; дефайны проще, но каждый пресет = репатч… НЕТ: дефайны от пресета НЕ зависят, если пулы фиксированы на MAX-раскладке, а пресет меняет только размеры — уточнить на дизайне).

РЕКОМЕНДОВАННЫЕ КОНФИГИ POINT (live, без static):
- C iso-VRAM: 2x1024 + 8x512 + 8x256 = 324 MiB (~= сегодняшние 288), 18 слотов, D1 закрыт для ближних. Кандидат в дефолт MEDIUM.
- B умеренный: 4x1024 + 8x512 + 16x256 = 504 MiB, 28 слотов (+75% VRAM, 1.75x количество).
- A жирный: 8x1024 + 16x512 + 32x256 = 1008 MiB, 56 слотов (HIGH/ULTRA-территория).
- D вариация: дальний тир depth-only (без пирамиды/EVSM => жёсткая тень без пенумбры, 256-куб = 1.5 MiB вместо 4.5) — структурная опция, решить на дизайне.
Spot: любое разбиение 16 ячеек бесплатно (напр. 4 full + 8 half(2x2=32 тайла)... => счёт тайлов растёт без VRAM).

ПОРЯДОК РЕАЛИЗАЦИИ (фазы следующей сессии):
I1 core: instance-per-tier рефакторинг point-тройки (PointShadowArray/Evsm/Pyramid) БЕЗ смены поведения (1 тир = текущее), редактор компилится (total-slots аксессор для LightDriver:91-93).
I2 core: spot квадродерево-пулы (tilePixelX/Y piecewise, beginSpot viewport под суб-тайл) + per-(type,tier) acquireTile.
I3 core: раздача тиров по per-type рангу + ранговый Schmitt-гистерезис + пресеты как вектор размеров/раскладки; occluder-32 nearest-fix паровозом.
I4 шейдер CR-пилот: range-piecewise декод в 3 сайтах + if-цепочка сэмплеров point + N*-константы; реген патча + PatchHarness round-trip + run-пак синк.
I5 ин-гейм: D1-проверка (зернистый квадрат ушёл на ближней), отсутствие дребезга на границах тиров (бег по стресс-сцене), cold-start без фриза (C2 наследуется), FPS-замер. Image-gen EXPECTED/REGRESSION по [[feedback-visual-test-image-prompts]].
Тираж на 6 паков — как всегда строго по прямой команде (двойной шаблон: spot+point байт-идентичны).

ГЕЙТЫ/РИСКИ: смена тира у лампы обязана быть невидимой кроме резкости (никаких миганий — гистерезис); тень НЕ имеет права исчезать при флипе дольше 1 кадра (SHADOW_PENDING-омит, не засвет); суммарный texture-unit бюджет программ (+3 юниформа/тир поверх текущих 7 IRL-сэмплеров) — проверить на CR при дизайне; НЕ ломать SSBO binding7 (диапазоны — интерпретация, не layout), cluster binding6, дефайны, якоря патчей, shadow-стек Phase 1/2/3.

ПРОЦЕДУРНЫЕ КОНСТАНТЫ: сборка/запуск/кэши/коммиты — как в [[plan-perf-fix-cluster-phase3]] (JAVA_HOME=Temurin-21 jdk-21.0.11.10, publishToMavenLocal + чистка loom-remap irl-core-*/1.1 в аддоне И редакторе + байт-маркеры; runClient Git Bash '-Pmc=1.20.4' лог run/runclient-console.log в фоне; Shadres/ gitignored => Bash grep, НЕ штатный Grep; коммиты только в чекпоинты по подтверждению, без веток под сессию; ultracode: recon/impl workflow + adversarial-ревью до передачи юзеру).

ПРОМПТ СЛЕДУЮЩЕЙ СЕССИИ (готов к вставке):
---
Продолжаем перф/качество-трек IRLite: importance-based shadow resolution (LOD-тиры теней). Прочитай память: [[plan-shadow-lod-tiers]] (ГЛАВНЫЙ — recon done, дизайн и порядок I1-I5 внутри), [[plan-perf-fix-cluster-phase3]] (Phase 3 done, процедурные константы), [[addon-shadows]] (бейк-движок), [[project-point-shadow-square-root-cause]] (D1). Режим ultracode.
СТАРТ (сверь git): irl-core main@6b51e61, addon master@d86138d, оба чистые; mavenLocal irl-core:1.1 = 6b51e61 (Phase 3 кластер). Ничего не запушено.
ЦЕЛЬ: тиры разрешения теней по C1-приоритету (point: N cube-array-инстансов; spot: статичное квадродерево в том же атласе), рост числа теней + фикс D1 (tier0 point = 1024). Дефолт-конфиг MEDIUM = «C iso-VRAM» (2x1024+8x512+8x256 point; spot-разбиение предложи на дизайне).
ШАГ 0: короткий дизайн-гейт по OPEN-решениям из план-файла (депт-only дальний тир?, N*-константы дефайнами vs буфер-хедером, спот-раскладка, тир-счётчики per-type) — подтверждаю выбор Я, потом код.
ПОРЯДОК: I1 -> I5 из план-файла, каждая фаза = impl workflow + adversarial-ревью (корректность слот-диапазонов/piecewise-декода/гистерезиса; не сломан ли SSBO7/cluster6/якоря/Phase1-3). Пилот CR, тираж 6 паков ТОЛЬКО по моей команде. occluder-32 nearest-fix — паровозом в I3.
ГЕЙТ КАЧЕСТВА: D1-квадрат ушёл на ближней лампе; ноль миганий на границах тиров при беге; тень при флипе тира пропадает максимум на 1 кадр; FPS не хуже Phase 3 базлайна на 64-ламповой сцене.
НЕ ЛОМАТЬ: SSBO binding7 layout, cluster binding6, #define-контракты, якоря патчей, shadow-стек Phase 1/2/3, editor-компиляцию (LightDriver MAX_SHADOWS-клэмп).
---

Связь: [[plan-perf-fix-cluster-phase3]] (предыдущая фаза + процедуры), [[addon-shadows]] (движок; поправить там VRAM-строку пресетов при консолидации — реальный MEDIUM live = 288 MiB), [[project-point-shadow-square-root-cause]] (D1 закрывается tier0), [[project-shadow-bake-perf-audit]] (бейк-перф канон), [[shader-shadow-sampling]] (декод-идиома vlParams.w), [[plan-shadow-filtering-refactor]] (MSM/EVSM стек + Photon-дрейф для тиража), [[addon-light-buffer-ssbo]] (binding7 не трогаем).

CHECKPOINT 2026-07-13 (АКТУАЛЬНЫЙ):
- I1-I4 DONE; I4 adversarial review PASS по mirror-parity, GLSL semantics/frozen anchors и pipeline/PatchHarness. GLSL в review/fix-round не менялся. Modification == run-пак MD5 `e2f95a6bd05b6bdcf9775b54c152b58f`; binding7/binding6 и Phase 1/2/3 целы.
- Раскладки: spot 8/24/32 = 64 тайла; point 2/8/8 = 18 слотов; MEDIUM point tier0=1024. Это лимиты shadow maps, отдельно от caster pool.
- I5 runtime root cause для наблюдаемых 10–17 теней: общий caster pool 32 плюс pure-light ghost ModelBlock. Targeted fix: bounded nearest-128 с cached argmax + finite guard, pure Point/Spot tree filtering, caster horizon 72->256. nearest-harness/adversarial/build/byte-proof PASS.
- Коммиты: core `2e57f8d`, addon code/shader `700b60c`, editor `08df3f6`; push нет. mavenLocal соответствует core checkpoint; addon/editor clean build PASS.
- I5 визуально не закрыт: pending повторный shadow-count на 64 spot после fix, отлёт 20–30 блоков, D1, tier-flicker, flip <=1 кадр, FPS >=112, cold-start и image-gen EXPECTED/REGRESSION.
- Разнос на другие шейдеры и версии — отдельный этап ТОЛЬКО по новой прямой команде пользователя. До неё не трогать остальные паки/port-ветки/release-раскладки.

ВОЗОБНОВЛЕНИЕ: сверить clean git core `2e57f8d`, addon `700b60c` плюс memory-коммит, editor `08df3f6`; сначала завершить I5 визуальный ретест на CR-пилоте либо ждать отдельной команды на тираж. Не смешивать эти этапы.
