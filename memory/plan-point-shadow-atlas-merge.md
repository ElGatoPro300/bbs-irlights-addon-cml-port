---
name: plan-point-shadow-atlas-merge
description: "КОД РЕАЛИЗОВАН ЦЕЛИКОМ 2026-07-16 (та же сессия, что разблокировала гейты): Ф1-Ф7 build-гейты все PASS, НЕ ЗАКОММИЧЕНО (чекпоинт = core+addon+патч одним набором, только по подтверждению юзера). Осталось: I5 ин-гейм визуальный ретест (чек-лист в Ф7) СТРОГО на ComplementaryReimagined_IRLights + решение по коммиту. Новый MD5 live/run/prism = 5450bf3f (эталон e2f95a6b устарел). Адверсариальное ревью диффа (5 линз + верификация, wf_9bbd54ff-a34): 0 подтверждённых находок. Гейт 5 решён: LOW=512 без clamp (D1-фикс с MEDIUM+); гейт 6: query GL_MAX_TEXTURE_SIZE + clamp вниз по степеням двойки в PointDepthAtlas.setTileSize (ULTRA на 16384-лимите ляжет на 2048). Остальные 6 пропатченных паков ОЖИДАЕМО без point-теней до тиража. Дизайн: атлас 6144^2, 30 ламп {2,12,16} (было 18), PointDepthAtlas+DepthTileAtlas, core 1.1->1.2. Преемник закрытого octahedral-трека (НЕ ПЕРЕОТКРЫВАТЬ)."
metadata:
  node_type: memory
  type: project
  originSessionId: 8144b2eb-ba27-48fa-a231-960f3771d8c1
---

ЦЕЛЬ: переложить хранилище point-теней с cube-map-array (6 граней, свой VRAM-склад на лампу, изолированный от spot) на тот же плоский quadtree-атлас, что уже использует spot (SpotlightDepthAtlas), БЕЗ изменения самой проекции — каждая грань остаётся тем, чем является сейчас: обычным линейным perspective-рендером (90° FOV, lookAt по хардкод-направлению). Единственная цель — снять асимметрию 18 point / 64 spot слотов через общую вместимость. Бейк остаётся 6 проходов на лампу — это НЕ цель этого трека (см. НЕ В СКОУПЕ).

ПРОИСХОЖДЕНИЕ: прямой преемник octahedral/dual-paraboloid трека, закрытого 2026-07-13 КАК НЕРЕАЛИЗУЕМОГО (план-память того трека удалена по команде юзера, вывод зафиксирован в MEMORY.md, сессия 2026-07-13 (2) — тема НЕ ПЕРЕОТКРЫВАТЬ). Причина закрытия (верифицирована кодом+литературой): бейк печёт геометрию через ванильные per-RenderLayer шейдеры (блоки = getPositionProgram, сущности/формы self-draw в общий entity-Immediate), трансформ только матричный — нелинейный paraboloid/octahedral warp матрицей не выразить, единого вершинного шва под кастомный VS нет (десятки ванильных ShaderInstance). Плюс DPSM (dual-paraboloid) отдельно требует тесселяции (прямые линии превращаются в дуги на углах до 180°), у геометрии Minecraft тесселяции нет → light-leak. Важный нюанс, зафиксированный при закрытии: octahedral КАК ПРОЕКЦИЯ бейка — та же проблема, что и у стены; octahedral/плоский атлас КАК ХРАНЕНИЕ (печём все 6 граней линейно как сейчас, только пакуем результат по-другому) — валиден. Именно это и есть предмет этого трека. Линейной проекцией минимум = 6 граней, меньше не бывает.
Это же было первым из 4 приёмов, разобранных ещё до octahedral-эксперимента: "простое слияние атласов без смены проекции — вместимость есть, бейк-перфа нет, небольшой рантайм-налог на семплинг". Тогда его отложили в пользу octahedral именно из-за отсутствия бейк-перф выигрыша; теперь, когда octahedral мёртв, это единственный оставшийся живой вариант из тех четырёх (Virtual Shadow Maps отклонён отдельно и независимо от исхода octahedral — нужна sparse-текстурная инфраструктура уровня движка, несовместимая с моделью "аддон поверх Iris-хуков + патчи per-pack", минимальный GL 4.3).
Второй реальный рычаг, названный при закрытии octahedral-трека (ОРТОГОНАЛЬНЫЙ этому файлу, НЕ его часть) — пропуск бейка граней без кастеров (sphereTouchesFace уже считает маску) — это про бейк-перф, не про вместимость; см. НЕ В СКОУПЕ.

ПЕРЕНОС ИЗ ПРЕДЫДУЩЕГО РЕСЁРЧА (2026-07-13, воркфлоу wf_bb649fd7-be9 — факты ниже НЕ octahedral-специфичны, доразведки не требуют, только точечной ре-проверки, если код успел измениться):
- Point-стек: PointShadowTiers (оркестратор, 3 инстанса PointShadowArray по тирам) + PointShadowArray (хранилище/FBO, сейчас GL_TEXTURE_CUBE_MAP_ARRAY, layerCount=slotCount*6, layer=slot*6+face, PointShadowArray.java:85-88,130) + PointShadowPyramid + PointShadowEvsm (каждый инстанс PointShadowArray владеет своей парой). TIER_SLOTS={2,8,8}=18 (PointShadowTiers.java:29).
- Бейк-цикл (ShadowBaker.java, 5 сайтов `for(face<6)`, ~751/805/844/931/986) СТРУКТУРНО НЕ МЕНЯЕТСЯ этим треком — по-прежнему 6 итераций на лампу, меняется только ЦЕЛЬ рендера каждой грани (сейчас — layer куб-массива через ShadowRenderer.beginPointFace, будет — тайл атласа). Occluder-кап MAX_OCCLUDERS=128 (глобальный, ShadowBaker.java:44) и per-face caster-mask (sphereTouchesFace, scanInRange) не завязаны на storage backend — не трогаются.
- ShadowRenderer.beginPointFace (ShadowRenderer.java:191-243): per-face view+ОБЩАЯ 90°-perspective проекция (кэш по radius), хардкод direction/up таблица на 6 граней. Это ТОТ ЖЕ класс линейной проекции, что beginSpot уже даёт per-tile (spotProj.perspective(fovDeg, aspect=1.0, NEAR, far) + lookAt) — в отличие от octahedral-трека, здесь fill-путь НЕ нужно изобретать с нуля, он структурно совпадает с уже существующим spot-путём (разный FOV/direction-table, та же форма матрицы).
- SpotlightDepthAtlas (irl-core): НЕТ allocate/release API на самом классе (пассивный geometry-lookup над хардкод TIER_CELLS={8,6,2}, GRID 4x4=16 ячеек, потолок 64 тайла, TILE_SIZE=1024 runtime-mutable). Rank/tier/hysteresis-машинерия (acquireTile/acquireTileTiered/desiredTier/releaseOldTile/tierForIndex, Schmitt eager-promote/lazy-demote, DEMOTE_MARGIN=2, CONTENTION_HOLD_FRAMES=8) живёт в ShadowBaker, УЖЕ полностью generic и УЖЕ буквально переиспользуется для point-пула сегодня (pointSlotOwner/POINT_TIER_END, независимое flat-index-пространство от spot) — эта часть новой работы не требует вообще.
- 64-тайловый потолок атласа СЕЙЧАС ПОЛНОСТЬЮ занят спотом (8+24+32=64) — центральный вопрос ёмкости для этого трека, см. РАЗВЕДКА п.1.
- GLSL (irlite_lights.glsl, CR-пак, patches/complementaryreimagined.irlights): уже существует `irlite_cubeFaceUV(vec3 dir, out vec2 uv)` (L382-391) — рабочая программная копия GL cube-face-select спек-таблицы, сейчас используется только вспомогательно (pyramid pyrLayer, gather билинейный remix), НЕ как основной depth-fetch. Для атлас-мержа эта функция становится ОСНОВНЫМ путём вместо аппаратного `texture(samplerCubeArray, vec4(dir,layer))`.
- vlParams.w — decode структурно не меняется (sentinel w<0, int(w+0.5), tier/localSlot piecewise, "FROZEN MIRROR CONTRACT" в PointShadowTiers.java:11-19 + GLSL IRL_PT_END0/1 + копия в патче) — меняется только ЧТО адресует итоговый local-слот (сейчас база куб-слоя, будет база блока тайлов атласа). В отличие от octahedral-трека, доп. per-light состояние (типа hemisphere-флага) здесь НЕ нужно вообще — один float на глобальный слот продолжает работать без напряжения с ограничением "layout не меняется".
- Подтверждено (decode-сайты): binding7 (LightBuffer SSBO) и binding6 (ClusterGridBuffer) НЕ завязаны на тип/backend хранения point-теней — не трогаются этим треком.
- "6 граней на слот" — по-прежнему magic-number без единой константы в 7+ местах (PointShadowArray, PointShadowPyramid/Evsm текст compute-шейдера, ShadowBaker циклы, GLSL pyrLayer=layer*6+face) — при смене адресации (cube-layer stride -> atlas-tile stride) каждый сайт трогается индивидуально, тот же blast-radius список актуален и здесь.

СКОУП (открыт, не решён — см. РАЗВЕДКА):
- Ветки: НЕ решено, переиспользовать ли `optimization/octahedral-point-shadows` (сейчас мёртвая ветка без коммитов поверх core@2e57f8d/addon@b9c52ee) или начинать с чистого main/master — этот трек архитектурно менее рискованный (линейная проекция, без экспериментального душка), возможно не нуждается в отдельной ветке вообще. Решить перед кодом.
- Шейдер-пак: по умолчанию предлагается тот же CR (ComplementaryReimagined), как во всех текущих экспериментах — не зафиксировано явно.
- РЕДАКТОР (irlights) НЕ трогаем по умолчанию, как и раньше — если не будет отдельной команды.

ЧТО НУЖНО РАЗВЕДАТЬ (новые вопросы, НЕ покрытые прошлым ресёрчем — он был заточен под octahedral):
1. Ёмкость: 18 point-слотов × 6 граней = до 108 тайлов при нынешних тирах — против уже занятых 64 тайлов спота. Варианты: (а) урезать тиры/разрешение point, чтобы влезть в существующий грид без ABI-брейка; (б) расширить GRID_X/GRID_Y/TIER_CELLS (сам класс называет это "ABI break with every generated patch"); (в) отдельный ПАРАЛЛЕЛЬНЫЙ инстанс того же класса/формы для point (свой GL-текстура, тот же геометрический API, без конфликта со спотом, но тогда "общий атлас" по факту означает "общая СХЕМА хранения", не общая VRAM-текстура). Нужно предложить конкретные числа и явный выбор (а)/(б)/(в).
2. Одна общая GL-текстура (interleaved point+spot тайлы) vs. параллельный инстанс SpotlightDepthAtlas-подобного класса под point — прямое следствие п.1, но отдельный дизайн-вопрос (влияет на код: shared allocate/release против raздельного).
3. Border/bleeding на границах тайлов: аппаратный seamless cubemap сейчас не даёт читать соседний, неродственный контент при PCF/gather у края грани — плоский атлас так не умеет, у соседнего тайла может лежать вообще другая лампа. Нужен padding/border-texel вокруг каждой face-тайла ИЛИ явный clamp в UV-ремапе перед PCF/gather-диском. У spot этой проблемы никогда не было (один фрустум на тайл, никогда не читает через грань) — это НОВЫЙ риск, специфичный для point, требует отдельного дизайна.
4. Проверить (не считать данностью), что PointShadowPyramid/PointShadowEvsm НЕ требуют редизайна — гипотеза: их cross-face seam-remap математика (faceDir, гауссов блюр через грани) работает в face-local UV-пространстве и топологически не зависит от способа хранения (куб остаётся кубом, меняется только "куда положить" каждую грань) — значит их придётся только переадресовать (cube-layer -> atlas-tile-index), не переписывать с нуля, КАК ЭТО БЫЛО БЫ ПРИ octahedral. Прочитать реальный код обоих классов и подтвердить или опровергнуть.
5. Рантайм-стоимость: программный face-select + атлас-UV-ремап вместо аппаратного `samplerCubeArray`-фетча — посчитать/прикинуть реальную ALU/texture-fetch разницу на tap, с поправкой на adaptive PCF (~28-40 тапов на высоком IRLITE_SHADOW_QUALITY) и на то, что блюр (MSM/EVSM/pyramid) в этом треке, В ОТЛИЧИЕ от octahedral-трека, по умолчанию НЕ выключается (топология не меняется, редизайн не нужен — см. п.4) — то есть полный путь с префильтром остаётся в игре, и рантайм-налог накладывается на него целиком, не на урезанный hard-PCF путь.
6. GLSL: конкретные call-сайты, которые нужно перевести с `samplerCubeArray`-диспетчера на `sampler2DArray`/atlas-lookup — типовые uniform-объявления (irl_pointShadowArray/1/2, irl_pointEvsm/1/2), функции-обёртки (irlPtDepthFetch/irlPtDepthGather/irlPtEvsmLod) и их ~9-10 колл-сайтов внутри irlite_pointShadow — список уже частично есть в прошлом ресёрче (decode-сайты, категория 2), нужно только пере-подтвердить актуальность и добавить п.3/п.5 находки.

ЧТО НЕ В СКОУПЕ ЭТОГО ТРЕКА:
- Сокращение числа бейк-проходов (остаётся 6 на лампу, не 1-2) — это ОТДЕЛЬНЫЙ, ортогональный рычаг: не печь грани без кастеров. По прошлому ресёрчу это УЖЕ частично сделано (overlay-dynamic-only ветка, ShadowBaker.java ~986-990, "skip whole face when zero casters"), но НЕ во всех 5 ветках бейка (остальные 4 всегда шлют все 6 GL-проходов, с обязательным clear даже при нуле кастеров — "vacated face would keep a phantom shadow"). Формализация этого рычага по всем 5 веткам — отдельная задача, НЕ часть этого файла; завести отдельную память, если юзер попросит.
- Virtual Shadow Maps — остаётся отклонённым по причинам, не связанным с octahedral (см. [[plan-octahedral-point-shadow]] ПРОИСХОЖДЕНИЕ).
- Тираж на другие паки/редактор — как и всегда, отдельной командой после оценки на CR.

РЕЗУЛЬТАТЫ РЕСЁРЧА (2026-07-13, воркфлоу wf_a1d01c2b-6f0, 6 агентов, все читали код целиком, файлы: irl-core PointShadowTiers/PointShadowArray/PointShadowPyramid/PointShadowEvsm/ShadowBaker/ShadowRenderer/SpotlightDepthAtlas/IRLShadowQuality.java + patches/complementaryreimagined.irlights):

П.1 ЁМКОСТЬ (проверенные числа, не прежние прикидки): point = TIER_SLOTS{2,8,8}=18 слотов x6 граней = 108 фейс-тайлов (12@F tier0, 48@F/2 tier1, 48@F/4 tier2; F runtime-preset 512/1024/2048/4096). Маппинг на гранулярность spot-атласа (tier0=1 ячейка, tier1=4, tier2=16) -> point нужно 27 ячеек; spot сам занимает все 16 из 16 (0 запаса, код бросает исключение при >64 тайлов — завязано на 64-битный `long dirtyMask` в SpotShadowPyramid/Evsm). Комбинированный спрос 43 ячейки vs 16 доступных = дефицит 2.7x.
- (а) урезать point под текущую сетку без ABI-брейка = урезание ~70% лампового бюджета point (18->5-6) — отклонено.
- (б) расширить GRID/TIER_CELLS до >=7x7=49 ячеек = ABI-брейк во ВСЕХ уже сгенерированных .irlights патчах (по памяти минимум 7 пайплайнов) — дорого, класс сам называет это "frozen mirror contract".
- (в) отдельный параллельный инстанс атлас-класса под point (свой grid ~6x5=30 ячеек, своя GL-текстура) — не трогает spot вообще, VRAM ~377 MiB на MEDIUM против текущих ~324 MiB point cube-array (сопоставимо).
РЕКОМЕНДАЦИЯ агента: (в).

П.2 SHARED vs PARALLEL (прямое следствие п.1): обе развилки могут переиспользовать 5 generic hysteresis/tier-функций ShadowBaker (acquireTile/tierForIndex/desiredTier/acquireTileTiered/releaseOldTile) БЕЗ изменений — они уже generic и уже обслуживают point независимо сегодня. Разница только в блаcт-радиусе:
- ОБЩАЯ физическая текстура = слияние owner/active/tierEnd массивов в ОДИН, новый единый priority-ranking между point/spot (кого выселять при конфликте — не существует сегодня), расширение 64-битного dirtyMask в уже ОТГРУЖЕННЫХ SpotShadowPyramid/Evsm (риск регрессии рабочего кода), потеря независимых точка/спот пресетов качества (сейчас 2 разных рычага в IRLShadowQuality).
- ОТДЕЛЬНЫЙ параллельный инстанс = механический форк SpotlightDepthAtlas (кодовая база УЖЕ использует этот паттерн Point*/Spot* twin-class), ноль правок в уже отгруженном коде spot.
РЕКОМЕНДАЦИЯ агента: параллельный инстанс (в)/(ii) — тот же вывод, что и в п.1, независимо подтверждён вторым агентом.
Общий для ОБОИХ вариантов новый обязательный слой (ни один не избегает): "выделить 6 тайлов одного тира как единицу" — сегодня это бесплатно даёт формула `slot*6+face`, у плоского хранилища такой группировки нет вообще, нужно проектировать отдельно. Плюс: SpotlightDepthAtlas сейчас — чистый static-singleton (приватный ctor, все поля static) — второй инстанс требует сначала рефактора в instantiable-класс.

П.3 BORDER/BLEEDING — НОВЫЙ РИСК, пересматривает исходное допущение плана: фиксированный паддинг НЕ решает проблему. У spot тапы PCF/blocker — это 2D-смещения внутри известного NDC-фрустума, клэмпятся в [-1,1] ДО маппинга в UV атласа — поэтому spot никогда не читает через границу тайла. У point тапы — это возмущения 3D-НАПРАВЛЕНИЯ (`sd = dir + T*off.x + B*off.y`), которые сегодня просто скармливаются аппаратному samplerCubeArray и бесшовно резолвятся GL. При дефолтных настройках (IRLITE_SHADOW_SIZE=0.10) и refDist=0.5 блока (обычный сценарий — лампа у стены) угловое отклонение тапа ~11° = ~102 тексела реального захода на tier2-грани (256px) — это ~40% всей грани; на максимальном слайдере (0.80) при refDist=0.5 тап может уйти на ~58°, покидая исходную грань совсем. Это НЕ тексель-ограниченная проблема — паддинг любой разумной ширины её не покроет.
Реальный фикс = архитектурный, по образцу spot: пересчитывать грань через уже существующую `irlite_cubeFaceUV` и клэмпить тапы той же грани в свой тайл (0 физических бордер-текселей нужно — как у spot). Для тапов, ушедших на другую грань — либо (i) дешёвый клэмп на референсную грань (риск видимых швов, уже ОТКЛОНЁН однажды на bake-стороне, PointShadowEvsm.java:27-32 задокументировал именно этот провал), либо (ii) полная 6-тайловая таблица на лампу в LightBuffer SSBO (ABI-правка + доп. ветвление в горячем цикле до ~50 тапов/лампу на Q4). Отдельно: MSM/EVSM trilinear-ветка (L844) СЕЙЧАС ВООБЩЕ без клэмпа — самый рискованный путь, нужен spot-образный lod-scaled UV inset (L577-582 у spot).

П.4 PointShadowPyramid/Evsm — гипотеза ЧАСТИЧНО ОПРОВЕРГНУТА (не чисто подтверждена, как ожидал план): seam-continuity блюр (EVSM's tap(), L106-164) ПОДТВЕРЖДЁН как уже flat/face-local, независим от backend — 0 правок. НО raw-depth ingestion-проходы (CUBE_SRC у Pyramid L58-91, CONVERT_SRC у Evsm L70-103) СЕГОДНЯ используют samplerCubeArray + аппаратный face-select через направление — это НЕ redirect указателя, а реальный (хоть и маленький, ~30-40 строк GLSL на класс) рерайт: смена типа сэмплера, новые per-face tile-rect uniform'ы (сегодня их нет — `layer=slot*6+face` бесплатная формула), возможно разбивка одного 6-wide dispatch на per-face. Small but real — отдельная строка в оценке, не "бесплатно".

П.5 РАНТАЙМ-СТОИМОСТЬ — план недооценивал ("небольшой рантайм-налог"), по факту НЕ маленькая: spot хоистит tile-rect ОДИН РАЗ на лампу (1 проекция), point не может — направление тапа меняется на каждый тап и может пересечь грань, поэтому face-select у point НЕОТДЕЛИМО per-tap. Оценка: +20-30 ALU/тап там, где сейчас 0 (blocker-search, EVSM lookup), +8-16 там, где gather уже частично платит face-select. Документированный "горячий" путь высокого качества (Q4+prefilter: 10 blocker-тапов + 1 EVSM lookup = 11 фетчей, НЕ весь 28-40-таповый PCF, как думал план) -> +45-70% инструкций на этом пути. Фолбэк full-PCF (до 40 тапов) -> примерно удвоение. EVSM/MSM теряет бесплатную аппаратную бесшовную trilinear-фильтрацию через грани, нужна ручная mip-clamp схема как у spot (+30-40 доп. ops). Числа — ручной подсчёт по исходнику, не дизассемблер, ±30-50% погрешность разумна.

П.6 GLSL-КАТАЛОГ (только CR, complementaryreimagined.irlights): 9 uniform-объявлений меняют тип сэмплера (irl_pointShadowArray/1/2, irl_pointShadowPyramid/1/2, irl_pointEvsm/1/2, L185-198). ВСЕ обращения к сырым point-сэмплерам идут ТОЛЬКО через 7 tier-dispatch wrapper-функций (irlPtFaceRes/DepthFetch/DepthGather/PyrSize/PyrFetch/EvsmRes/EvsmLod, L253-306) — подтверждено грепом, ноль прямых вызовов снаружи — хорошая инкапсуляция, локализует правку. Самые рискованные call-сайты — тангенциальные тапы L799 (blocker), L883/885 (PCF), L844 (EVSM), способные пересечь грань. Готовый образец для копирования — уже существующий spot-путь (irlite_spotShadow, L421-645: quadtree-декод, tileUvMin/tileUvSize, gather-clamp, EVSM mip-clamp) — весь нужный механизм уже реализован и работает для spot, точка просто должна его переиспользовать по аналогии.
НОВАЯ НАХОДКА: соседние шейдер-паки (bliss/bsl/iterationrp/photon/rethinkingvoxels/solas) РАСХОДЯТСЯ с CR — у них НЕТ ни одной из 7 wrapper-функций и tier-массивов (грепом 0 совпадений в rethinkingvoxels.irlights), они дёргают cube-array напрямую в рассеянных местах инлайн. Подтверждает, что I4 LOD-tier фаза раскатана только на CR (по памяти). Тираж на соседей — структурно другая и БОЛЬШАЯ задача (нет wrapper-слоя для точечной правки) — вне скоупа, если явно не попросят.

ИТОГ РЕСЁРЧА: п.1 и п.2 независимо сошлись на одной рекомендации — параллельный точка-only атлас-класс (форк SpotlightDepthAtlas, свой GRID/TIER_CELLS, своя GL-текстура), НЕ единая физическая текстура со spot. Формально это всё ещё "shared capacity model" из цели плана, но как общая СХЕМА/форма класса, а не общий VRAM-пул — более узкое прочтение, чем можно было понять из названия трека. Гейт по п.1-2 остаётся НЕ снят — нужно явное решение юзера, ресёрч только даёт рекомендацию.

РЕШЕНИЕ ЮЗЕРА (2026-07-13, подтверждено явно): развилка п.1-2 ЗАКРЫТА — принят вариант "параллельный point-only атлас" (форк SpotlightDepthAtlas, своя GL-текстура, НЕ общая физическая текстура со spot). Юзер также подтвердил переход к дизайн-пассу прямо сейчас.

ДИЗАЙН-ПАСС ЗАВЕРШЁН (2026-07-13, 2 прогона воркфлоу — wf_238ea8c7-ac8 и переисполнение того же runId после фикса бага; финальные 5 результатов согласованы между собой на реальных числах). ВНИМАНИЕ — БАГ ПЕРВОГО ПРОГОНА (для будущих сессий, не переоткрывать): в скрипте воркфлоу интерполяция `${JSON.stringify(alloc,...)}` была случайно обёрнута в лишние одинарные кавычки (`${'${...}'}`) — 3 из 5 агентов (dirty-mask/border-fix/ingestion) получили НЕ реальный результат задачи 1, а буквальный нераскрытый текст плейсхолдера, и восстанавливали числа по косвенным уликам (сами это честно пометили в risks). После фикса кавычек и переисполнения с resumeFromRunId (задачи 1+2 взялись из кэша, 3 зависимые пересчитаны) — все 5 результатов ниже согласованы. Если увидите в артефактах старые числа "18 блоков" или "36 ячеек" — это мусор первого прогона, игнорировать.

ФИНАЛЬНЫЙ ДИЗАЙН (все 5 пунктов):

1. РАСКЛАДКА ГРАНЕЙ — новый класс `PointDepthAtlas` (форк формы SpotlightDepthAtlas). Блок = 6 граней одного света, физически СМЕЖНЫ (contiguous), раскладка 3 колонки x 2 ряда на блок (FACE_COL={0,1,2,0,1,2}, FACE_ROW={0,0,0,1,1,1}, порядок граней = ShadowRenderer.beginPointFace's face-switch). "Суперячейка" = 1 блок-контейнер (аналог cell у spot, но всегда целиком одного света, никогда не шарится). TIER_SUPERCELLS={2,3,1} (было TIER_SLOTS={2,8,8}), блоков по тирам {2,12,16} = **30 блоков всего (было 18, +67%)**. Grid 2x3 суперячейки, атлас 6144x6144 при TILE_SIZE=1024 (tier0 не трогали — там уже стоит фикс 1024 из project-point-shadow-square-root-cause). VRAM: ~144 MiB live (было ~108 MiB raw-depth) = +33% за +67% ламп. ShadowBaker's 5 generic hysteresis-функций (acquireTile/tierForIndex/desiredTier/acquireTileTiered/releaseOldTile) переиспользуются БЕЗ ИЗМЕНЕНИЙ — блок всегда получают ОДНИМ вызовом acquireTileTiered на свет (НЕ 6 раз), "6-ness" даёт формула (block,face)->pixel-rect внутри PointDepthAtlas, а не allocator. PointShadowTiers.java и PointShadowArray.java УДАЛЯЮТСЯ целиком (роль полностью замещена). Открытый вопрос: финальные 30 блоков — не привязаны к продуктовому таргету, просто zero-waste упаковка под "27-36 ячеек" из ресёрча; если нужно другое число ламп — retune тривиален.

2. ДЕ-СИНГЛТОНИЗАЦИЯ SpotlightDepthAtlas — рекомендация (a): извлечь grid/tier/rect-math в новый инстанцируемый класс `DepthTileAtlas` (имя предварительное), SpotlightDepthAtlas становится тонким static-фасадом (форма уже есть в кодовой базе — PointShadowTiers это тот же паттерн для point). PointDepthAtlas — второй пользователь DepthTileAtlas. НОЛЬ правок в ShadowBaker/ShadowRenderer/IrlSamplers/IRLShadowQuality/GLSL/.irlights (подтверждено чтением живого GLSL-контракта — он зависит только от числовых констант и GL texture id, не от static/instance организации Java). Единственная не чисто-косметическая правка: публичное static-поле TILE_SIZE читается напрямую в 3 местах (SpotShadowEvsm.java, SpotShadowPyramid.java) — нужен новый геттер `getTileSize()`. Риск на будущее (не блокирует эту задачу): проверка `count>64` в static-блоке защищает КОНКРЕТНО 64-битный dirtyMask spot'а — если её слепо скопировать в generic DepthTileAtlas как обязательный инвариант, конструктор point-инстанса (30 блоков — ок, но если retune за 64) упадёт; не переносить как жёсткий инвариант класса. Эффорт: малый-средний, 1-2 часа.

3. DIRTY-MASK — подтверждено кодом (не предположение): все 6 граней блока ВСЕГДА мажутся/чистятся одновременно (ни одного call-site с меньшей гранулярностью нигде в ShadowBaker/Pyramid/Evsm). Рекомендация: один `static long dirtyMask`, бит на БЛОК (не на тайл/грань) — 30 блоков комфортно влезает в 64 бита (34 бита запаса). PointShadowPyramid/PointShadowEvsm переходят instance->flat-static (та же форма, что уже есть у SpotShadowPyramid/Evsm). Точный API: markDirty(block)/isDirty(block)/clearBit(block)/clearAll(). Companion-гард blockCount()<=64 нужен внутри PointDepthAtlas (п.1), иначе `1L<<block` при block>=64 молча алиасится (сдвиг по модулю 64) вместо явного краша. Эффорт: малый, чисто механический (~16 touch-точек в 2 файлах).

4. BORDER/BLEEDING FIX — ФИНАЛЬНОЕ РЕШЕНИЕ (сильно лучше первого прогона): вариант (iii), причём при НУЛЕВОМ ABI/SSBO-koste, а не как дешёвый компромисс. Ключевая находка: раз раскладка граней (п.1) АБСОЛЮТНАЯ и ОДИНАКОВАЯ для всех светов (FACE_COL/FACE_ROW — глобальные константы, не per-light данные), то "таблица на 6 граней" — это шейдерная константа (0 байт на свет), а не запись в SSBO. Значит вариант (ii) из ресёрча (полная per-light 6-тайловая таблица в LightBuffer) был бы просто тем же самым (iii), но с избыточным дублированием одинаковых данных на каждый свет — отклонён как чистый waste, не как "менее правильный". Новая GLSL-функция `irlite_pointAtlasUV(block, tapDir, out face, out tileMin, out tileMax)` — слияние существующих irlite_cubeFaceUV (L391-400) и spot-decode (L428-434), под НОВЫЕ константы IRL_PT_* (зеркало PointDepthAtlas). Вызывается на КАЖДЫЙ тап (не только cross-face — сегодня направление вообще не резолвится в грань, аппаратный cube делает это бесплатно; теперь резолвить придётся всегда). LightBuffer.java — 0 изменений (прочитан целиком, подтверждено). ВАЖНО: EVSM/MSM wide-penumbra trilinear-fetch — ОТДЕЛЬНАЯ, НЕ покрытая этим решением проблема (continuous-filter, не per-tap redirect; у неё уже есть bake-time прецедент в PointShadowEvsm.java:27-32, нужен свой follow-up дизайн при переводе Pyramid/Evsm на flat-static). Новое поведение: half-texel clamp появляется даже на same-face тапах (сегодня его нет, аппаратный cube не клэмпит) — визуально заметное отличие, требует отдельной проверки на I5-ретесте.

5. INGESTION-РЕРАЙТ (CUBE_SRC/CONVERT_SRC) — меньше правок, чем ожидал ресёрч: нужен ТОЛЬКО ОДИН новый uniform (`blockOrigin`, ivec2) + compile-time константа FACE_OFF[6] — второй uniform под tile-size НЕ нужен, потому что `srcTileSize = dstSize.x*2` уже вычисляется в существующем коде как `srcRes` (тот же трюк для CUBE_SRC и CONVERT_SRC). `uniform int slot` не трогается (он только про dst-layer). Один glDispatchCompute(x,y,6) на блок ВЫЖИВАЕT без изменений — разбивка на 6 отдельных диспатчей не нужна. faceDir() удаляется из CUBE_SRC/CONVERT_SRC, но НЕ из EVSM's BLUR_SRC (другой, всё ещё нужный механизм для seam-remap на СОБСТВЕННОМ хранилище MSM, не на сыром depth-источнике) — риск: легко случайно удалить не ту копию. Downstream (MIP_SRC обоих классов, EVSM's cube-view viewId) подтверждено читают только СОБСТВЕННОЕ хранилище классов, не сырой depth — 0 изменений. Не имеет順序-зависимости от instance->static рефакторинга (п.3) — можно делать в любом порядке. Эффорт: малый, ~15-20 строк GLSL + ~5-8 строк Java на класс, <1 часа на класс.

ИТОГ: дизайн-пасс закрыт, все 5 пунктов взаимно согласованы, блокирующих неизвестных для перехода к имплементации не осталось. Открытые вопросы — все некритичные (имена классов/полей, точная упаковка FACE_COL/ROW-порядка, нужно ли сайзить под будущий рост сверх 30). СУПЕРСИДЕНО 2026-07-16: имплементационный план готов (секция ИМПЛЕМЕНТАЦИОННЫЙ ПЛАН ниже) — порядок фаз там СКОРРЕКТИРОВАН относительно этого абзаца: де-синглтон DepthTileAtlas идёт ПЕРВЫМ (PointDepthAtlas — фасад над ним). Код НЕ начат.

ГЕЙТЫ: коммиты — только по чекпоинтам с подтверждением юзера ([[commit-checkpoints]]); ничего не пушится без отдельного разрешения; тираж на другие паки/мерж в main/master — отдельным решением после оценки; ветка/branch-стратегия — решить ДО начала кода (см. СКОУП).

Связь: закрытый octahedral/dual-paraboloid трек — см. MEMORY.md сессия 2026-07-13 (2), отдельного файла-памяти больше нет (удалён по команде юзера). [[plan-shadow-lod-tiers]] (текущая point/spot архитектура, SpotlightDepthAtlas источник), [[project-shadow-bake-perf-audit]] (skip-empty-face — отдельный рычаг, не этот трек), [[addon-shadows]] (движок бейка), [[shader-irlite-glsl]] / [[shader-shadow-sampling]] (decode-контракт vlParams.w), [[plan-perf-fix-cluster-phase3]] (binding6, подтверждённо не связан).

## СТАТУС РЕАЛИЗАЦИИ (2026-07-16, сессия имплементации)

ВЕСЬ КОД НАПИСАН, все build-гейты PASS, НЕ ЗАКОММИЧЕНО (обе репы dirty на optimization/octahedral-point-shadows). Факты:
- Ф1 PASS: DepthTileAtlas.java (НОВЫЙ, package-private, инстанцируемый, cellAspectX/Y для неквадратной ячейки, БЕЗ гарда >64 — гард у фасадов), SpotlightDepthAtlas -> static-фасад, getTileSize() вместо TILE_SIZE (3 читателя + комменты). Компиляция PASS, спот бит-в-бит.
- Ф2 PASS: PointDepthAtlas.java (НОВЫЙ static-фасад, {2,3,1}, GRID 2x3, aspect 3x2, FACE_COL/ROW, оба FROZEN MIRROR javadoc). Скретч-тест RectCheck: 30 блоков, tier-ранги [0,2)[2,14)[14,30), zero-waste 6144^2, ориджины кратны f, GLSL-зеркало END0=2/END1=14/CELL1=2/CELL2=5 == Java pixel-rect точно на всех 180 гранях.
- Гейт 5 РЕШЁН: LOW=512 остаётся, БЕЗ скрытого clamp (D1-фикс тем самым действует только с MEDIUM+). Гейт 6 РЕШЁН: PointDepthAtlas.setTileSize = query GL_MAX_TEXTURE_SIZE + clamp вниз по степеням двойки + System.err warning (ULTRA при лимите 16384 -> 2048); вызов только из ShadowBaker.bake():331 (GL-тред, проверено) — class-init GL-free сохранён.
- Ф3-Ф5 PASS: Pyramid/Evsm flat-static (texId[3]/levels[3], long dirtyMask по ГЛОБАЛЬНОМУ блоку, markDirty/isDirty/clearBit/clearAll public по gate 7, Evsm: viewId[3]+единый tempId (T/2)^2x6+blockFar[30]); ingestion srcCube->srcAtlas (имя+тип) с ПАРНЫМИ Java-лукапами P9/E13; blockOrigin uniform + FACE_OFF[6]; seamless 0x884F glEnable в PointShadowEvsm.ensureResources (единственный сайт, греп подтверждён); flushDirty по тирам последовательно, барьер-баланс = 3 старым флашам; ShadowBaker 9 сайтов (myLayer->myBlock, POINT_TIER_END {2,14,30} из PointDepthAtlas, 2-строчный flushDirty, PointDepthAtlas.delete в onShadersDisabled); ShadowRenderer.beginPointFace = atlas-FBO + per-face viewport/scissor; IrlSamplers 12->10 (irl_pointShadowAtlas GL_TEXTURE_2D, Array1/2 удалены, Pyramid/Evsm 0/1/2 static-suppliers); IRLShadowQuality на setTileSize + новая VRAM-javadoc (~288·F² point); PointShadowTiers/PointShadowArray УДАЛЕНЫ (git rm). Грепы пусты: srcCube, PointShadowTiers|PointShadowArray (core+addon), TILE_SIZE (кроме коммента). LightBuffer:20 коммент обновлён.
- Ф6 PASS: irlite_lights.glsl переписан (сэмплеры, END1=14+CELL1/CELL2, irlPtFaceRes=atlas/(6*2^tier), irlPtDepthFetch/Gather УДАЛЕНЫ, irlite_cubeFaceUV безусловный, НОВАЯ irlite_pointAtlasUV+clamp на каждом depth-тапе, pointGatherTap(block,dir,atlasSize,cmp), blocker/PCF/hard/VL рерайты, vlPointStep(toLight,dist,radius,block), hoist shTier/shLayer удалён — shTile=block). Патч перегенерирован (1950 строк, 21 op), byte-proof PASS (дифф только irlite_patched.txt маркер — норма), run+prism ре-синканы ПОЛНОЙ копией применённого дерева (prism был протухший с 5 июля). MD5 live/run/prism = 5450bf3f6be92b5f877fda3551d70962.
- Ф7 build-гейты PASS: core 1.1->1.2 (gradle.properties + addon build.gradle:109-110), publishToMavenLocal (JDK 17 для core, АДДОН ТРЕБУЕТ JDK 21 — loom 1.15.5), addon build -Pmc=1.20.4 --refresh-dependencies PASS, runClient: irl-core 1.2 загружен, CR_IRLights пак скомпилирован Iris без ошибок, сессия ~3 мин без крешей/GL-ошибок.
- Адверсариальное ревью диффа (воркфлоу wf_9bbd54ff-a34, 5 линз contracts/indexing/gl-state/glsl-semantics/plan-completeness + верификация, ~958k токенов): 0 подтверждённых находок, 1 опровергнута (spot ULTRA clamp — не входил в скоуп, spot 4*4096=16384 == лимит валиден).
- ОСТАЛОСЬ: (1) I5 ин-гейм визуальный ретест по чек-листу Ф7 — ТОЛЬКО ComplementaryReimagined_IRLights (остальные 6 паков ожидаемо без point-теней); отдельно проверить конфиг со всеми F-фичами OFF (риск Р15) и hard-путь/VL (NEAREST vs старый LINEAR, риск Р9). (2) Коммит-чекпоинт core+addon+патч одним набором — ТОЛЬКО по подтверждению юзера. (3) Follow-up гейты 10-12 без изменений.

## ИМПЛЕМЕНТАЦИОННЫЙ ПЛАН (2026-07-16)

Происхождение: воркфлоу wf_d5e731ba-4fc (10 агентов: 5 ресёрч-зон с полным чтением кода -> синтез -> 3 адверсариальные линзы полнота/контракты/порядок+GL -> ревизия). Верификация нашла 3 major-находки (2x потеря единственного enable-сайта GL_TEXTURE_CUBE_MAP_SEAMLESS при удалении PointShadowArray — нужен EVSM cube-view; 1x молчаливый no-op при переименовании ingestion-uniform srcCube->srcAtlas без парной Java-правки glGetUniformLocation) — ВСЕ вшиты в текст плана ниже, отдельного списка находок не требуется. План САМОДОСТАТОЧЕН: следующая сессия работает только по нему, не перечитывая дизайн/ресёрч выше. Все file:line якоря — из живого чтения 2026-07-16.

# Имплементационный план: point-тени cube-array → плоский атлас PointDepthAtlas

Дизайн утверждён юзером (5 пунктов), не пересматривается. Все якоря file:line — из живого чтения кода ресёрч-агентами 2026-07-16, ключевые точки повторно спот-проверены при синтезе (SpotlightDepthAtlas.java:63/:96-100, ShadowBaker.java:257-261, IrlSamplers.java:58-83, irlite_lights.glsl:245-246 — совпали); правки ревью 2026-07-16 верифицированы отдельным чтением (PointShadowArray.java:40/:218, PointShadowPyramid.java:237, PointShadowEvsm.java:352/:403-413, IrlSamplers.java:61-82). План самодостаточен: следующая сессия работает только по нему.

Пути:
- Ядро: `C:/Users/Qualet/Documents/Project/Minecraft/BBS/irl-core/src/main/java/org/qualet/irl/light/` (далее `light/`).
- Живой GLSL: `C:/Users/Qualet/Documents/Project/Minecraft/BBS/bbs-irlights-addon/Shadres/Modification/ComplementaryReimagined/shaders/lib/irlite/irlite_lights.glsl` (далее `irlite_lights.glsl`; 1494 строки).
- Патч: `bbs-irlights-addon/patches/complementaryreimagined.irlights` (генерат, руками не править).
- Ген-скрипт: `bbs-irlights-addon/tools/gen-complementary-patch.ps1` (не меняется, только перегнать).

## Порядок фаз и точки компиляции

Порядок скорректирован относительно базового порядка дизайна по выявленной ресёрчем зависимости: **де-синглтонизация (DepthTileAtlas) идёт ПЕРВОЙ**, т.к. PointDepthAtlas — фасад над ней.

- Ф1 DepthTileAtlas + фасад SpotlightDepthAtlas → **компилируемый чекпоинт core** (spot бит-в-бит).
- Ф2 PointDepthAtlas → **компилируемый чекпоинт core** (новый класс, старые PointShadowTiers/Array живы).
- Ф3 (Pyramid/Evsm flat-static + dirty + ingestion) + Ф4 (интеграция) + Ф5 (удаление Tiers/Array) — **один неделимый компиляционный блок**: рерайт Pyramid/Evsm ломает call-сайты ShadowBaker/IrlSamplers/PointShadowArray, компилировать имеет смысл только после Ф5.
- Ф6 GLSL + регенерация патча — **обязана лечь в один чекпоинт/коммит с Ф3-Ф5** (двойной ABI-брейк: реестр сэмплеров + смена адресации depth-тапов = визуальная регрессия в промежуточном состоянии).
- Ф7 верификация (build-гейты + ин-гейм).

Коммит-дисциплина: коммиты только в чекпоинты и только по подтверждению юзера (commit-checkpoints). Целевой чекпоинт серии = core+addon+патч одним согласованным набором (MD5-дисциплина live/run как в I4; текущий эталон e2f95a6b этой правкой устаревает).

---

## Ф1. DepthTileAtlas + де-синглтонизация SpotlightDepthAtlas

**Файлы:** НОВЫЙ `light/shadow/DepthTileAtlas.java`; правка `light/shadow/SpotlightDepthAtlas.java`; правки `SpotShadowPyramid.java`, `SpotShadowEvsm.java`.

### 1.1 Новый класс DepthTileAtlas (инстанцируемый)
Конструктор:
```java
public DepthTileAtlas(String debugName, int gridX, int gridY,
                      int cellAspectX, int cellAspectY,
                      int[] tierCells, int initialTileSize)
```
Перенос из SpotlightDepthAtlas (всё instance):
- `TILE_SIZE` (:63) → `private int tileSize` + `public int getTileSize()`; `GRID_X/GRID_Y` (:64-65) → ctor; `TIER_CELLS` (:71) → ctor; `TILE_COUNT`, `TIER_FIRST_TILE` (:73-76); layout-таблицы `tileCellX/tileCellY/tileSubX/tileSubY/tileSizeDiv` (:79-83).
- static-init (:85-132) → тело конструктора. Переносятся валидации `tierCells.length==3` и `sum(tierCells)==gridX*gridY` (:87-95). Проверка `count>64` (:96-100) **НЕ переносится** — она защищает long-dirty-маски конкретных потребителей, а не rect-математику; каждый фасад несёт свой гард в своём static-init.
- GL-стейт live+static (:134-140).

Методы (обобщение на неквадратную ячейку `cellW = tileSize*cellAspectX`, `cellH = tileSize*cellAspectY`):
`getAtlasWidth()` (= tileSize\*cellAspectX\*gridX, было :145-148), `getAtlasHeight()` (:150-153), `getGlTextureId()` (lazy, :156-159), `getFboId(boolean staticLayer)` (:163-178), `tileCount()` (:182-185), `tierStartTile(int)` (:189-192), `tierTileCount(int)` (:195-198), `tileTier(int)` (:220-227), `tilePixelX/Y(int)` (обобщение :201-210), `unitSizePx(int)` (= tileSize/tileSizeDiv[tile], обобщение :213-216), `copyStaticToLive(int)` (rect unit\*aspectX × unit\*aspectY, обобщение :232-250), **НОВЫЙ `copyStaticToLiveRect(int x, int y, int w, int h)`** — обязан лениво инициализировать ОБА слоя как текущий :234-241 (иначе overlay per-face копия первого кадра ударит по textureId=0), `delete()` (только свои текстуры/FBO, :324-339 без каскада :319-320), `setTileSize(int)` (early-return + this.delete(), :343-351 без каскада), `init()/initStatic()/createAtlas()` (:252-315; createAtlas параметризован размерами, debugName в сообщении FBO-ошибки :301).

Tex-параметры createAtlas: **NEAREST + CLAMP_TO_EDGE, DEPTH_COMPONENT32F, COMPARE_MODE NONE, MAX_LEVEL 0, полная очистка в 1.0** — как у текущего spot (:286-287 и далее). Не параметризуется (см. «Разрешённые противоречия», П1).

Инвариант (сохранить в javadoc): origin тайла кратен собственному extent в ОБОИХ аспектах — на нём стоят lockstep-сдвиги фильтров (SpotShadowPyramid.java:142-146, SpotShadowEvsm.java:182-185).

### 1.2 SpotlightDepthAtlas → тонкий static-фасад
```java
private static final DepthTileAtlas INSTANCE =
    new DepthTileAtlas("spot", 4, 4, 1, 1, new int[]{8, 6, 2}, 1024);
static { if (INSTANCE.tileCount() > 64) throw new IllegalStateException(...); } // текст ошибки прежний (:97-99)
```
- Static-делегаты со старыми именами: `getAtlasWidth/Height`, `getGlTextureId`, `getFboId(boolean)`, `tileCount`, `tierStartTile`, `tierTileCount`, `tileTier`, `tilePixelX/Y`, `tileSizePx(int)` (= INSTANCE.unitSizePx — имя сохраняется ради call-сайтов), `copyStaticToLive(int)`.
- **НОВЫЙ `public static int getTileSize()`** — замена public-поля.
- УДАЛИТЬ: `public static int TILE_SIZE` (:63), `GRID_X/GRID_Y` (:64-65; внешних читателей нет — греп подтверждён), весь приватный layout/GL-стейт.
- `delete()`: порядок как сейчас (:317-340): `SpotShadowPyramid.delete(); SpotShadowEvsm.delete(); INSTANCE.delete();`.
- `setTileSize(int)`: early-return при равенстве → фасадный `delete()` (с каскадом на фильтры!) → выставить размер INSTANCE. Каскад обязателен: levels/temp фильтров зависят от tileSize.
- FROZEN MIRROR javadoc (:21-49) остаётся на фасаде; в DepthTileAtlas — обобщённая формула без GLSL-упоминаний.
- Static-init фасада GL-free (конструктор DepthTileAtlas строит только таблицы) — порядок class-init безопасен, `SpotShadowEvsm.java:52` (`new float[tileCount()]`) работает без правок.

### 1.3 Читатели TILE_SIZE → getTileSize() (ровно 3 места, полный греп)
- `SpotShadowPyramid.java:250` — формула levels.
- `SpotShadowEvsm.java:344` — формула levels.
- `SpotShadowEvsm.java:363` — `glTexStorage2D(..., getTileSize()/2, getTileSize()/2)` (дважды в строке).
- Комментарии-упоминания: SpotShadowEvsm:41,235,360; SpotShadowPyramid:189,249 — обновить текст.

### 1.4 Внешние call-сайты — 0 правок (проверено грепом)
IrlSamplers.java:61; IRLShadowQuality.java:51; ShadowBaker.java:167-168, :255, :650, :1343; ShadowRenderer.java:151-154 — все ходят через сохранённый static-API фасада. Аддон и редактор Java-ссылок на SpotlightDepthAtlas/TILE_SIZE не имеют.

**Критерий готовности Ф1:** irl-core компилируется; греп `TILE_SIZE` по core/аддону/редактору пуст (кроме комментариев); spot-поведение бит-в-бит (аспект (1,1) ⇒ cellW==tileSize, unitSizePx == старому tileSizePx — арифметическая эквивалентность, проверяется чтением дифа).

---

## Ф2. PointDepthAtlas

**Файл:** НОВЫЙ `light/shadow/PointDepthAtlas.java` — static-фасад над вторым инстансом DepthTileAtlas.

### 2.1 Константы и инстанс
```java
public final class PointDepthAtlas {
    private static final int[] TIER_SUPERCELLS = { 2, 3, 1 };  // блоков по тирам {2,12,16} = 30
    private static final int GRID_X = 2, GRID_Y = 3;           // 2+3+1 == 2*3
    // Блок = 6 граней одного света, 3 колонки x 2 ряда; порядок граней = face-switch
    // ShadowRenderer.beginPointFace (:226-234): 0=+X,1=-X,2=+Y,3=-Y,4=+Z,5=-Z.
    public static final int[] FACE_COL = { 0, 1, 2, 0, 1, 2 };
    public static final int[] FACE_ROW = { 0, 0, 0, 1, 1, 1 };
    private static final DepthTileAtlas INSTANCE =
        new DepthTileAtlas("point", GRID_X, GRID_Y, /*aspX*/3, /*aspY*/2, TIER_SUPERCELLS, 1024);
    static { if (INSTANCE.tileCount() > 64) throw new IllegalStateException(
        "PointDepthAtlas: blockCount ... > 64 (downstream dirty masks are long)"); }
    private PointDepthAtlas() {}
}
```
Class-init обязан быть GL-free (только таблицы) — статическая инициализация ShadowBaker (:169-170, :257-261) и `blockFar` у Evsm идут вне GL-потока.

### 2.2 Геометрия (зафиксировать в javadoc как НОВЫЙ канон FROZEN MIRROR)
- Атлас 6T×6T (6144² при T=1024). Суперячейка = 3T×2T. Грань тира t: `f = T >> t` (1024/512/256). Flat-блоки: тир 0 → [0,2), тир 1 → [2,14), тир 2 → [14,30).
- `(block,face)→pixel-rect`: `div = 1<<tier`, `f = T/div`, `X0 = tilePixelX(block) = scX*3T + subX*3f`, `Y0 = tilePixelY(block) = scY*2T + subY*2f`; `faceX = X0 + FACE_COL[face]*f`, `faceY = Y0 + FACE_ROW[face]*f`, extent = f (квадрат).
- Инварианты: faceX/faceY кратны f (lockstep-сдвиги фильтров точны); блок 3f×2f физически смежен (один glCopyImageSubData); ориджины пиксель-выровнены (на этом стоит эквивалентность gather-весов в GLSL).
- Сюда же переезжает FROZEN MIRROR контракт vlParams.w из PointShadowTiers.java:11-19 (sentinel w<0, int(w+0.5), piecewise decode) с новыми порогами; лестница разрешений = чистые F, F/2, F/4 (старый clamp `max(64, F>>t)` из PointShadowTiers.java:127-130 недостижим при пресетах ≥512 — паритет; зафиксировать в javadoc, что при гипотетическом пресете <256 поведение разошлось бы).

### 2.3 Публичный API (канонические имена — см. «Противоречия», П2)
```java
getTileSize(); getAtlasWidth(); getAtlasHeight();      // 6*T x 6*T
getGlTextureId();                // lazy 0; для IrlSamplers (GL_TEXTURE_2D, без cube-array rebind)
getFboId(boolean staticLayer);
blockCount();                    // 30
tierStartBlock(int t); tierBlockCount(int t); blockTier(int block);
tierFaceSizePx(int t);           // getTileSize() >> t — для аллокации хранилищ фильтров (Ф3)
blockPixelX(int block); blockPixelY(int block);        // X0/Y0 = origin грани (col0,row0) -> uniform blockOrigin
faceSizePx(int block);           // f = INSTANCE.unitSizePx(block)
facePixelX(int block, int face); facePixelY(int block, int face);
copyStaticToLive(int block);                 // весь блок 3f x 2f ОДНОЙ копией (замена PointShadowArray:137-152)
copyStaticFaceToLive(int block, int face);   // = INSTANCE.copyStaticToLiveRect(faceX, faceY, f, f) (замена :158-174)
delete();      // PointShadowPyramid.delete(); PointShadowEvsm.delete(); INSTANCE.delete(); — чейн подключается в Ф3
setTileSize(int newSize);        // образец spot-фасада: delete()-каскад обязателен (levels/temp фильтров зависят от T)
```
Что ИСЧЕЗАЕТ относительно PointShadowArray: per-layer clear-цикл (:232-238) → один clear всего атласа в createAtlas. **`GL_TEXTURE_CUBE_MAP_SEAMLESS` НЕ исчезает, а ПЕРЕЕЗЖАЕТ**: PointShadowArray.java:218 — единственный enable-сайт во всём core+аддоне (греп подтверждён; Iris и ванильный MC его не включают), а seamless — контекст-глобальный опт-ин, нужный EVSM cube-view (hardware-seamless трилинейная фильтрация на всех мипах — PointShadowEvsm.java:403-404, javadoc :27-32) и ПОСЛЕ этой серии. Depth-тапы атласа seamless больше не требуют (шов решает GLSL clamp Ф6), но enable переносится в PointShadowEvsm.ensureResources (Ф3, E13); PointDepthAtlas сам куб-текстур не держит и seamless не трогает.

**Критерий готовности Ф2:** core компилируется (старые классы живы); rect-формулы проверены на бумаге/скретч-тестом: для каждого block∈[0,30) faceX/faceY кратны faceSizePx, блоки не пересекаются, укладываются в 6144².

---

## Ф3. PointShadowPyramid/PointShadowEvsm: flat-static + dirty long + ingestion-рерайт

**Файлы:** `light/shadow/PointShadowPyramid.java`, `light/shadow/PointShadowEvsm.java`. Компиляция после этой фазы сломана до конца Ф5 — ожидаемо.

### 3.1 Целевая структура хранения
Хранилище фильтров остаётся **per-tier** (три комплекта GLSL-сэмплеров — контракт IrlSamplers:66-82), но классы flat-static с per-tier массивами:
```java
// PointShadowPyramid
private static int[] texId, levels;              // [3]
private static long dirtyMask = 0L;              // бит на ГЛОБАЛЬНЫЙ блок
// PointShadowEvsm
private static int[] texId, viewId, levels;      // [3]
private static int tempId = 0;                   // ОДИН общий ping-pong по крупнейшему тиру (образец Spot:358-367)
private static long dirtyMask = 0L;
private static final float[] blockFar = new float[PointDepthAtlas.blockCount()];  // образец SpotShadowEvsm:52
```
Раскладка при T=1024: t0 = 2 блока, база 512, 12 слоёв; t1 = 12, база 256, 72 слоя; t2 = 16, база 128, 96 слоёв. Аллокация: `glTexStorage3D(..., base(t), base(t), tierBlockCount(t)*6)`, `levels[t] = log2(tierFaceSizePx(t))`, где base(t) = tierFaceSizePx(t)/2.

### 3.2 Dirty-mask API (в каждом из двух классов)
```java
public static void markDirty(int block)                 // Pyramid; гард 0<=block<blockCount(), dirtyMask |= 1L<<block
public static void markDirty(int block, float radius)   // Evsm; + blockFar[block] = max(radius, 0.1f)
public static boolean isDirty(int block); public static void clearBit(int block); public static void clearAll();
```
Образец — SpotShadowPyramid:95-101/SpotShadowEvsm:140-147. flushDirty сохраняет consume-паттерн `long mask = dirtyMask; dirtyMask = 0L;` (Spot:112-113). delete() → clearAll(). Грануляция бит-на-блок подтверждена кодом: per-face маркировки нигде нет (ShadowBaker :805-819, :844-858, :963-1022). isDirty/clearBit внешних потребителей не имеют — публикуются по букве дизайна (gate 7).

### 3.3 PointShadowPyramid — touch-точки (P1-P11)
- P1 :44 удалить `owner`; P2 :46-48 поля → static (см. 3.1); P5 :116-119 ctor → `private PointShadowPyramid() {}`; P6 :123-126 → `static int getGlTextureId(int tier)` → texId[tier]; P7 :130-136 markDirty (см. 3.2); P10 :270-292 buildProgram без изменений; P11 :297-306 delete → static, цикл по texId[t], dirtyMask=0L, raw glDeleteTextures остаётся (обоснование :294-296 сохранить).
- P3 :54 — добавить uniform-локацию `uCubeBlockOrigin`; P9 :228-268 ensureResources → static: линковка uCubeBlockOrigin (после :239), **перелинковка `uCubeSrc = GL20.glGetUniformLocation(progCube, "srcAtlas")` (:237 — сейчас строка "srcCube"; выбран вариант ПЕРЕИМЕНОВАНИЯ uniform в GLSL, см. P4, поэтому Java-строка ОБЯЗАНА смениться синхронно: glGetUniformLocation по старому имени вернёт -1, glUniform1i(-1,·) — молчаливый no-op, сэмплер останется на unit 0 и будет читать чужую текстуру без единой GL-ошибки — риск Р21)**, аллокация циклом по тирам (:258-260 → per-tier).
- **P4 рерайт CUBE_SRC (:58-91):** удалить `uniform samplerCubeArray srcCube` (:62), функцию `faceDir` целиком (:65-73), UV-реконструкцию+textureLod (:85-86). **Зафиксировано: uniform переименовывается srcCube → srcAtlas (тип + имя); парная Java-правка строки лукапа — P9.** Добавить:
```glsl
uniform sampler2D srcAtlas;
uniform ivec2 blockOrigin;                       // пиксельный origin блока (грань col0,row0)
const ivec2 FACE_OFF[6] = ivec2[6](ivec2(0,0), ivec2(1,0), ivec2(2,0),
                                   ivec2(0,1), ivec2(1,1), ivec2(2,1));
...
int srcRes = dstSize.x * 2;                      // был float (:79)
ivec2 faceBase = blockOrigin + FACE_OFF[face] * srcRes;
float d = texelFetch(srcAtlas, faceBase + g * 2 + ivec2(i, j), 0).r;
```
`uniform int slot` НЕ трогается (:63) — остаётся ЛОКАЛЬНЫМ индексом блока в тире, питает только целевой слой `slot*6+face`; imageStore (:90) без изменений. `int face = int(gl_GlobalInvocationID.z)` (:78) сохраняется, `glDispatchCompute(x,y,6)` (:188) выживает.
- **P8 flushDirty (:140-226) → static:** consume long; источник :148 → `PointDepthAtlas.getGlTextureId()`; из save/restore удалить `prevCube` (:162/:216), добавить `prevTex = glGetInteger(GL_TEXTURE_BINDING_2D)` (образец Spot:127); cube-bind :178 → `GlStateManager._bindTexture(atlasTex)`. Обход — по тирам последовательно (блоки tier-contiguous): image-bind texId[t], dstSize=base(t), внутри дырявый обход `b ∈ [tierStartBlock(t), tierStartBlock(t)+tierBlockCount(t))` по mask; uniforms: `uCubeSlot = b - tierStartBlock(t)` (:187), `glUniform2i(uCubeBlockOrigin, blockPixelX(b), blockPixelY(b))`. Mip-часть :193-212 per-tier (levels[t], uMipLayerBase = local*6, z=6 :209 выживает); prevArr остаётся (:195).
- MIP_SRC (:93-114) — 0 изменений.

### 3.4 PointShadowEvsm — touch-точки (E1-E14)
- E1 :47 удалить owner; E2 :49-53 поля → static (единый tempId — gate 9, рекомендация принята); E3 :54 → `blockFar` (см. 3.1); E8 :185-189 private ctor; E9 :194-197 → `static getGlTextureId(int tier)` → viewId[tier]; E10 :200-207 markDirty(block, radius); E14 :442-461 delete → static (view первыми, как :444-448; + tempId; dirtyMask=0L).
- **E5 рерайт CONVERT_SRC (:70-103):** зеркально CUBE_SRC — удалить srcCube (:71), faceDir (:75-83), UV+textureLod (:94-95) → `texelFetch(srcAtlas, faceBase + g*2 + ivec2(i,j), 0).r`; `srcRes` → int (:89). **Uniform тоже переименовывается srcCube → srcAtlas; парная Java-правка — E13.** НЕ меняется ни байта: uniform `far` (:72), `slot` (:73), вся moment-математика :96-100, imageStore :102. Хранимая глубина — тот же перспективный z01 (проекция граней не меняется) — линеаризация переносится 1:1.
- E4 :59 + `uCvBlockOrigin`; E13 :342-414 ensureResources → static: линковка uCvBlockOrigin (после :355), **перелинковка `uCvSrc = GL20.glGetUniformLocation(progConvert, "srcAtlas")` (:352 — сейчас строка "srcCube"; тот же молчаливый-no-op риск Р21, для MSM validity-гейт mm.z<0 регрессию НЕ поймает — отрицательный третий момент пишется в любом случае)**, texId[t] per-tier (:383-391), view :405-413 → `viewId[t] = glTextureView(..., texId[t], ..., 0, levels[t], 0, tierBlockCount(t)*6)`, temp :393-399 — ОДИН, sized `(getTileSize()/2)^2 x 6`. **Сюда же ПЕРЕНОС seamless-enable: рядом с созданием cube-view добавить приватную константу `GL_TEXTURE_CUBE_MAP_SEAMLESS = 0x884F` (из PointShadowArray.java:40) и `GL11.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)` (из PointShadowArray.java:218 — единственный enable-сайт в core+аддоне; без переноса EVSM cube-view после Ф5 фильтруется per-face CLAMP_TO_EDGE и wide-penumbra MSM тихо регрессирует полосами блюра на ±45° швах — ровно та регрессия, которую cube-view вводили лечить, javadoc :27-32).**
- **E11 flushDirty (:211-310) → static:** :219 источник → PointDepthAtlas.getGlTextureId(); :232 prevCube → prevTex 2D; convert-блок :253-260: `uCvFar = blockFar[b]`, `uCvSlot = local`, + uCvBlockOrigin, bind :258 → GlStateManager._bindTexture(atlas), image :259 → texId[tier], dispatch :260 (groups,groups,6) выживает; mip-цепь :267-297 per-tier (uMpLayerBase = local*6 :282, z=6 :283). Внешний обход по тирам последовательно — баланс барьеров = трём сегодняшним независимым flush'ам (ShadowBaker:1038-1043), перф-паритет.
- E12 :314-340 `blurSlotLevel` → `private static blurBlockLevel(int unit, int tier, int local, int lod, int w)`: :322/:333 texId[tier], :323/:332 tempId, :326/:337 layerBase = local*6, z=6 диспатчи :328/:338 выживают.
- **E6 BLUR_SRC (:106-164) — НЕ ТРОГАТЬ**: его faceDir (:114-122) + dominant-axis инверсия — seam-remap блюра по СОБСТВЕННОМУ MSM-хранилищу, обязан жить. **E7 MIP_SRC (:167-183) — НЕ ТРОГАТЬ.** Cube-view механизм не меняется (только кратность per-tier).
- Javadoc-шапки обоих файлов (:14-41/:14-44) переписать (упоминания owner/instance).

### 3.5 Подключить delete-чейн
`PointDepthAtlas.delete()` → `PointShadowPyramid.delete(); PointShadowEvsm.delete(); INSTANCE.delete();` (образец SpotlightDepthAtlas:319-320). setTileSize через этот delete — temp/levels пересоздаются при смене пресета.

**Критерий готовности Ф3:** оба файла переписаны по таблицам; faceDir удалён ТОЛЬКО из CUBE_SRC/CONVERT_SRC; BLUR_SRC/MIP_SRC/cube-view нетронуты (diff-проверка); обе Java-строки лукапа "srcCube" заменены на "srcAtlas" (греп `"srcCube"` по core пуст); glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS) присутствует в PointShadowEvsm.ensureResources; компиляция отложена до Ф5.

---

## Ф4. Интеграция ShadowBaker / ShadowRenderer / IrlSamplers / IRLShadowQuality

### 4.1 ShadowBaker.java — 9 сайтов, ни один из 5 render-циклов for(face<6) не перестраивается
(циклы :805-817, :844-856, :931-943, :986-995, :1007-1015 — структурно без изменений; clear-политика per-face переносится 1:1, phantom-shadow гарантия сохраняется тремя опорами: scissored clear грани, no-static ветка :1007-1015, полноатласный clear при createAtlas)

| Якорь | Сейчас | Становится |
|---|---|---|
| :169-170 | `new long/int[PointShadowTiers.totalSlots()]` | `new ...[PointDepthAtlas.blockCount()]` |
| :257-261 | `POINT_TIER_END[t] = PointShadowTiers.tierBase(t) + tier(t).slotCount()` → {2,10,18} | `PointDepthAtlas.tierStartBlock(t) + tierBlockCount(t)` → **{2,14,30}**; размер массива = 3 (жёстко, как SPOT) |
| :790-791 | `myArr`/`myLocal` через PointShadowTiers | удалить целиком; `myLayer` → переименовать в `myBlock`, использовать напрямую |
| :818-819, :857-858, :1021-1022 | `myArr.pyramid().markDirty(myLocal); myArr.evsm().markDirty(myLocal, radius);` | `PointShadowPyramid.markDirty(myBlock); PointShadowEvsm.markDirty(myBlock, radius);` — **ГЛОБАЛЬНЫМ блоком** (ревью этих 3 пар строк обязательно — риск Р4) |
| :966 | `myArr.copyStaticToLive(myLocal)` | `PointDepthAtlas.copyStaticToLive(myBlock)` |
| :970-977 | `copyStaticFaceToLive(myLocal, face)` по copyMask | `PointDepthAtlas.copyStaticFaceToLive(myBlock, face)` |
| :1039-1043 | per-tier цикл flushDirty | две строки: `PointShadowPyramid.flushDirty(); PointShadowEvsm.flushDirty();` (зеркало spot :704-705) |
| :1344 | `PointShadowTiers.deleteAll()` | `PointDepthAtlas.delete()` (рядом с :1343) |

Generic-пятёрка `acquireTile/tierForIndex/desiredTier/acquireTileTiered/releaseOldTile` (:1097-1253) — **0 изменений**; «один acquireTileTiered на свет = блок» — уже текущая форма (:768), замена чисто номенклатурная (слот 0..17 → блок 0..29). Handoff-hold :769-786, publish :796/:781/:874, contentionHold — без изменений.

### 4.2 Encode vlParams.w — 0 механических правок Java
Цепочка не трогается: `LightRegistry.setShadowTile` (ShadowBaker:796 и др.) → `LightRegistry.java:513` `addPoint(..., (float) shadowTile[i], ...)` → `LightBuffer.java:93`. Меняется только СЕМАНТИКА значения (индекс блока 0..29). Обновить контракт-комментарии (LightBuffer.java:20 и javadoc-якоря на PointShadowTiers где встречаются).

### 4.3 ShadowRenderer.java — заменить ТОЛЬКО :206-211
```java
GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, PointDepthAtlas.getFboId(toStatic));
int px = PointDepthAtlas.facePixelX(block, face);
int py = PointDepthAtlas.facePixelY(block, face);
int ts = PointDepthAtlas.faceSizePx(block);
GL11.glViewport(px, py, ts, ts);
GL11.glEnable(GL11.GL_SCISSOR_TEST);
GL11.glScissor(px, py, ts, ts);
```
Образец — beginSpot :151-162 (scissor ограничивает clear тайлом). Clear-блок :212-216, pointProj-кеш :218-223, **face-switch :226-234 (порядок граней — канон FACE_COL/ROW)**, lookAt :236-241, applyMatrices :242, endPass :884-910, savePassState :912-946 — байт-в-байт. Сигнатура beginPointFace сохраняется (slot = блок). Javadoc :186-190 обновить.

### 4.4 IrlSamplers.java:58-83 — 12 core-записей → 10 (с per-mod cookie: 13 → 11); удаляются ТОЛЬКО irl_pointShadowArray1/2, irl_pointShadowArray переименовывается в irl_pointShadowAtlas (GL_TEXTURE_2D)
(единственная Java-правка binding-слоя; заголовок = фактический итог, прежняя формулировка «13 → 7» была артефактом отвергнутой binding-спецификации — см. П3)
- `irl_pointShadowArray` (:62) → **`irl_pointShadowAtlas`**, target GL_TEXTURE_2D, supplier `PointDepthAtlas::getGlTextureId` (переезжает на «spot-путь», rebind не нужен).
- `irl_pointShadowPyramid` (:66) → supplier `() -> PointShadowPyramid.getGlTextureId(0)`, target GL_TEXTURE_2D_ARRAY без изменений. Записи `irl_pointShadowPyramid1/2` (:78, :81) и `irl_pointEvsm1/2` (:79, :82) **ОСТАЮТСЯ** с новыми static-suppliers `getGlTextureId(1)/getGlTextureId(2)`: по дизайну п.5 pyramid/EVSM сохраняют per-tier хранилища и ТРИ комплекта GLSL-сэмплеров (irlite_lights.glsl:194-202 не меняются). Удаляются ТОЛЬКО `irl_pointShadowArray1/2` (:77, :80). Итог: 10 core-записей (1 point-depth-атлас + 3 pyramid + 3 evsm + 3 spot) + cookie per-mod = 11. Binding-спецификация, схлопывавшая тир-сэмплеры до «7», отвергнута — противоречит дизайну п.5 и GLSL-спецификации (Pyr*/Evsm* врапперы :281-309 живут). См. «Противоречия», П3.
- `irl_pointEvsm` (:70) → supplier `() -> PointShadowEvsm.getGlTextureId(0)`, target GL_TEXTURE_CUBE_MAP_ARRAY (view) без изменений.
- Удалить import PointShadowTiers. `IrlSamplersBind`, оба миксина аддона (ProgramSamplersBuilderMixin, SamplerBindingCubeArrayMixin) — **0 изменений** (data-driven от реестра). SamplerBindingCubeArrayMixin ОБЯЗАН остаться (через него едут irl_pointEvsm* cube-view, irl_pointShadowPyramid* 2D_ARRAY, irl_cookieArray).

### 4.5 IRLShadowQuality.java
- :50 `PointShadowTiers.applyFaceSize(...)` → `PointDepthAtlas.setTileSize(this.pointFaceSize)` (+ clamp по gate 5/6: LOW и ULTRA — по решению юзера).
- Javadoc :8, :14-25, :34-35: переписать VRAM-таблицу под атлас (live-depth = 144·T² байт = 144 MiB @1024, было 108·T²; static лениво удваивает) и layout {2,12,16}.

**Критерий готовности Ф4:** все таблицы применены; греп `PointShadowTiers|PointShadowArray` по core вне двух удаляемых файлов пуст.

---

## Ф5. Удаление PointShadowTiers.java / PointShadowArray.java

- Удалить `light/shadow/PointShadowTiers.java` (131 стр.) и `light/shadow/PointShadowArray.java` (281 стр.) целиком.
- Перед удалением проверить: FROZEN-контракт из PointShadowTiers.java:11-19 переехал в javadoc PointDepthAtlas (Ф2); защитный инвариант «кап масок» живёт как guard blockCount()<=64 (Ф2); **перенос seamless-enable выполнен — `glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)` + константа 0x884F живут в PointShadowEvsm.ensureResources (Ф3 E13), греп `CUBE_MAP_SEAMLESS` по core находит ровно новое место** (PointShadowArray.java:218 был единственным enable-сайтом; удалить его без переноса = тихая регрессия EVSM wide-penumbra на швах без единой GL-ошибки).
- Редактор НЕ трогаем (не-цель): LightDriver.java:10,92,94 сломает только ПЕРЕСБОРКУ редактора — см. gate 12 (рекомендация: не пересобирать + бамп версии core).
- Опционально: упоминания в `irl-core/docs/*.md` (shadow-caster-seam-spec.md:43 и др.).

**Критерий готовности Ф5 (= компиляционный гейт всего Java-блока):** `irl-core` компилируется; греп обоих имён по core+аддону пуст.

---

## Ф6. GLSL border-fix (irlite_lights.glsl) + регенерация патча

Порядок правок сверху вниз по файлу (диф-план ~120-130 строк, net ≈ +25):

### 6.1 Сэмплеры :187-202
- :189-191 УДАЛИТЬ три `samplerCubeArray irl_pointShadowArray/1/2` → ОДНА декларация (образец spot :188): `uniform sampler2D irl_pointShadowAtlas;` (+комментарий про 2x3-supercell атлас; хак про runtime-rebind с :189 к depth больше не относится, но нужен для irl_pointEvsm*).
- :194-196 (Pyramid) и :200-202 (Evsm) — БЕЗ изменений типов/имён/количества; только комментарии (layer = LOCAL block*6+face / local block, ёмкости {2,12,16}).
- Extensions :182-185 — НЕ трогать (cube_map_array нужен EVSM, gpu_shader5 нужен findMSB :515/:578/:717/:762/:846, texture_array нужен пирамиде).

### 6.2 Константы :244-250
`IRL_PT_END0 = 2` (без изменений), **`IRL_PT_END1 = 10 → 14`**, + новые `IRL_PT_CELL1 = 2`, `IRL_PT_CELL2 = 5`. Комментарии :245-246 пере-якорить с PointShadowTiers.java на PointDepthAtlas.java. Spot-константы :247-250 не трогать. Синхронность с POINT_TIER_END {2,14,30} (Ф4.1) — критична (риск Р2).

### 6.3 Врапперы :252-310
- Шапка :252-256: dispatch по тирам теперь только для pyramid/EVSM.
- `irlPtFaceRes` :257-262 — рерайт: `return float(textureSize(irl_pointShadowAtlas, 0).x) / (6.0 * float(1 << tier));` (6144/6=1024, /12=512, /24=256).
- `irlPtDepthFetch` :264-269 — УДАЛИТЬ (колл-сайты :732, :803, :889, :1281 переходят на clamp+texture).
- `irlPtDepthGather` :271-278 — УДАЛИТЬ с #ifdef-парой (единственный колл-сайт :415 переписывается).
- `irlPtPyrSize/PyrFetch/EvsmRes/EvsmLod` :281-309 — БЕЗ ИЗМЕНЕНИЙ (только комментарии local block).

### 6.4 irlite_pointAtlasUV
- **Снять условный гейт :393/:406** вокруг `irlite_cubeFaceUV` (:395-404) — face-декод теперь нужен каждому depth-тапу, включая VL и конфиг со всеми F-фичами off.
- Сразу после — новая функция (полный текст из GLSL-спецификации, включить как есть):
```glsl
vec2 irlite_pointAtlasUV(int block, vec3 tapDir, vec2 atlasSize,
                         out int face, out vec2 tileMin, out vec2 tileMax)
{
    int cell, sub, div;
    if (block < IRL_PT_END0)      { cell = block;                 sub = 0;        div = 1; }
    else if (block < IRL_PT_END1) { int j = block - IRL_PT_END0;  cell = IRL_PT_CELL1 + j / 4;  sub = j % 4;  div = 2; }
    else                          { int j = block - IRL_PT_END1;  cell = IRL_PT_CELL2 + j / 16; sub = j % 16; div = 4; }
    float faceUv = 1.0 / (6.0 * float(div));
    vec2 blockMin = vec2(float(cell % 2) * 0.5, float(cell / 2) * (1.0 / 3.0))
                  + vec2(float(sub % div), float(sub / div)) * (vec2(0.5, 1.0 / 3.0) / float(div));
    vec2 uv;
    face = irlite_cubeFaceUV(tapDir, uv);
    vec2 faceOrigin = blockMin + vec2(float(face % 3), float(face / 3)) * faceUv;
    vec2 halfTexel = 0.5 / atlasSize;
    tileMin = faceOrigin + halfTexel;
    tileMax = faceOrigin + vec2(faceUv) - halfTexel;
    return faceOrigin + uv * faceUv;
}
```
FACE_COL/ROW вырождаются в `(face%3, face/3)` — таблицы не нужны; комментарий-шапка = FROZEN MIRROR PointDepthAtlas.java (текст в GLSL-спецификации). Формула обязана быть зеркалом Java pixel-rect в viewport-пространстве (bottom-left) — риск Р5.

### 6.5 irlite_pointGatherTap :408-422 — полный рерайт (паритет с irlite_spotGatherTap :384-391)
clamp(irlite_pointAtlasUV(...), tMin, tMax) → `textureGather(irl_pointShadowAtlas, uv)` (без comp-аргумента, как spot :387) → `step(vec4(cmpDepth), g)` → веса `fract(uv * atlasSize - 0.5)` → билинейный mix. Эквивалентность весов стоит на пиксель-выровненных ориджинах (инвариант Ф2).

### 6.6 irlite_pointShadow :652-903
- Декод :654-660: `gslot` → `block`; формулы tier/layer без изменений формы (layer = local block, нужен pyramid/EVSM-диспатчу); hoist `vec2 atlasSize = vec2(textureSize(irl_pointShadowAtlas, 0));` (паритет spot :450).
- Hard-путь :729-734: GATHER-ветка → `irlite_pointGatherTap(block, dir, atlasSize, refDepth - bias)`; #else :732 → clamp+`texture(irl_pointShadowAtlas, ...).r`.
- Pyramid-блок :756-772 — 0 изменений (только комментарий :764).
- Blocker search :801-803: пер-тапный `irlite_pointAtlasUV(block, sd, ...)` + clamp + texture (тап, перелезший шов, читает ПРАВИЛЬНУЮ соседнюю грань — лучше spot-клампа); tapMargin :807 и contact-weighting :810-817 не меняются.
- PCF :885-891: GATHER-ветка → pointGatherTap; без GATHER — clamp+texture.
- EVSM-фетчи :718/:848 — НЕ трогать (cube-view seamless; hardware-seamless сохраняется переносом glEnable в PointShadowEvsm — Ф3 E13). Гейты «matching build» :711/:756/:844 переживают без правок (соотношения тир-в-тир прежние: depth 1024/512/256 ↔ EVSM/pyr-base 512/256/128).

### 6.7 VL
- `irlite_vlPointStep` :1266-1283 — рерайт: сигнатура `(vec3 toLight, float dist, float radius, int block)`; тело — из GLSL-спецификации (atlasSize + irlite_pointAtlasUV + clamp обязателен даже на одиночном тапе: face-uv на границе = 0/1 ровно).
- Hoist :1377-1379: shTier/shLayer удалить; колл :1448 → `irlite_vlPointStep(toLight, dist, range, shTile)`. Spot-половина :1370-1376 не трогается.
- Layout vlParams.w FROZEN: sentinel :654/:1367, int(w+0.5) :655/:1368 — форма не меняется.

### 6.8 EVSM/MSM wide-penumbra trilinear inset — НЕ в этой фазе
Point EVSM остаётся на cube-view (seamless на всех мипах; глобальный enable переносится в PointShadowEvsm — Ф3 E13) — inset-проблемы нет. Рецепт на будущую миграцию EVSM-хранилища зафиксирован в GLSL-спецификации (spot-образец :578/:583-586; lod-источники :717/:846) — отдельный трек (gate 10).

### 6.9 Доставка
1. Править ТОЛЬКО живой `Shadres/Modification/.../irlite_lights.glsl`.
2. Перегнать патч: `tools/gen-complementary-patch.ps1` (скрипт без правок: lib вшивается verbatim `+file`-op'ом, :27/:110; якоря остальных op'ов не задеты). Byte-proof из шапки скрипта: применение к pristine + пустой `git diff --no-index --ignore-cr-at-eol`.
3. **Ре-синк уже применённых паков** (PatchLibrary.java:72-103 обновляет только извлечённые .irlights, НЕ применённые паки): `bbs-irlights-addon/run/shaderpacks/ComplementaryReimagined_IRLights` и `C:/prismlauncher/instances/BBS/minecraft/shaderpacks/ComplementaryReimagined_IRLights` — копия файла/ре-применение. Зафиксировать новый MD5 live/run (старый эталон e2f95a6b устаревает).
4. **Правило серии: все ин-гейм проверки — ТОЛЬКО на ComplementaryReimagined_IRLights.** Впервые в этой линейке серия УДАЛЯЕТ/переименовывает записи реестра IrlSamplers (прошлые серии I3/I4 только добавляли — старые паки продолжали работать): патчи остальных 6 пропатченных паков (bliss/bsl/solas/rethinkingvoxels/photon/iterationrp — греп `irl_pointShadowArray` по patches/*.irlights, по 7 вхождений) декларируют `uniform samplerCubeArray irl_pointShadowArray`, с новым jar сэмплер остаётся несвязанным → point-тени в них дают мусор/полную тень НА РАНТАЙМЕ. **Явно донести юзеру: до пер-пак тиража остальные паки теряют point-тени с новым core/addon (spot не затронут) — это ожидаемо и лечится отдельной командой тиража; переключение на другой пак при I5-ретесте даст ложный FAIL.**

**Критерий готовности Ф6:** патч регенерирован, byte-proof пуст, live/run/prism синхронны по MD5; ручной чек: компиляция шейдера в Iris при (а) всех F-фичах ON, (б) всех OFF (снятый гейт :393 — риск Р15).

---

## Ф7. Верификация

### Build-гейты (строго в этом порядке)
1. irl-core: выставить JAVA_HOME на JDK, `./gradlew publishToMavenLocal` (Git Bash). Помнить: mavenLocal после per-MC сборок = intermediary той ветки — публиковать именно линию 1.20.4; бамп версии — gate 4.
2. Аддон: `./gradlew build -Pmc=1.20.4 --refresh-dependencies`.
3. Регенерация патча CR + byte-proof + ре-синк run/prism (Ф6.9) — до рантайма.
4. Рантайм: `./gradlew runClient -Pmc=1.20.4` из Git Bash, лог в `run/runclient-console.log`, в фоне (feedback-addon-runclient-command).

### Ин-гейм визуальный тест (финал; включает pending I5-ретест caster-fix)
**Дисциплина: только пак ComplementaryReimagined_IRLights (Ф6.9 п.4) — остальные пропатченные паки в этой сборке ОЖИДАЕМО без point-теней до отдельного тиража, их состояние не является FAIL серии.**
Чек-лист:
- Счётчик point-ламп: 30 одновременных теневых point-источников (было 18); 31-я лампа корректно вытесняется по rank (hysteresis без изменений).
- Паритет качества с текущим кубом на tier0/1/2 (1024/512/256): hard-тени, PCF, PCSS-пенумбра, EVSM wide-penumbra.
- **Швы граней (depth-путь)**: свет вплотную к стене/углу, тень пересекает границу ±45° куба — отсутствие полос/утечек на швах (half-texel clamp + пер-тапный face re-select); отдельно проверить hard-путь с выключенными PREFILTER/PYRAMID (голые depth-тапы — самый уязвимый режим).
- **Швы граней (EVSM wide-penumbra)**: отдельная проверка seamless-переноса (Ф3 E13) — Softness высокий, большая пенумбра (coarse мипы cube-view), тень через шов ±45°: отсутствие полос блюра/яркостных ступеней на границе граней (регрессия = потерян glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)).
- VL: волюметрический конус point-света через шов грани.
- Tier-переходы: отход от лампы (tier0→1→2) — отсутствие «залипших» теней (flushDirty группировка), корректный copyStaticToLive.
- Overlay/static: статик-бейк + динамический кастер поверх (T1.2 механика per-face копий); отсутствие phantom-теней от покинутых граней.
- Смена пресета качества в рантайме (MEDIUM↔HIGH): delete-каскад, пересоздание фильтров, отсутствие мусора.
- FPS-сравнение до/после (ориентир 112 fps из Phase 3).
- Формат визуальных проверок — image-gen промпт (EN, EXPECTED/REGRESSION) по feedback-visual-test-image-prompts.

Коммит — только чекпоинтом по подтверждению юзера, core+addon+патч согласованно.

---

## Разрешённые противоречия между спецификациями

- **П1. Фильтр depth-атласа: LINEAR (PointShadowArray:211-212) vs NEAREST (spot :286-287).** Интеграционная спецификация требовала «сохранить point-параметры LINEAR»; спецификации атласа и GLSL — spot-параметры NEAREST. **Принято: NEAREST** (2 из 3 + техническое обоснование: семантика тапов становится spot-образной — manual filter-after-compare, textureGather фильтро-агностичен, одиночные тапы читают `texture().r` в лоб под step-сравнение (spot-прецедент :549); LINEAR на depth усреднял бы глубины через границы окклюдеров и ломал сравнение; старый LINEAR был безвреден только потому, что textureLod бил в центры текселей). Пометка риска Р9: визуально проверить hard-путь.
- **П2. Имена API PointDepthAtlas** (спец. атласа: tierStartBlock/tierBlockCount/blockTier/faceSizePx vs спец. фильтров: tierBase/blocksInTier/tierOfBlock/blockTileSizePx). **Принят канон спецификации атласа** + добавлен `tierFaceSizePx(int t)` (= getTileSize()>>t) для аллокации хранилищ фильтров (Ф2.3). blockPixelX/Y = origin грани FACE_OFF=(0,0) — критичное требование фильтров удовлетворено формулой X0/Y0.
- **П3. Число записей IrlSamplers.** Binding-спецификация схлопнула реестр до «3 point-записей» (7 всего), что противоречит дизайну п.5 (per-tier хранилища фильтров — 0 изменений) и GLSL-спецификации (Pyr*/Evsm* врапперы :281-309 и декларации :194-202 живут). **Принято: удаляются ТОЛЬКО `irl_pointShadowArray1/2`; `irl_pointShadowPyramid1/2` и `irl_pointEvsm1/2` остаются** с flat-static suppliers getGlTextureId(1)/(2). Итог по коду (IrlSamplers.java:61-82 = 12 core-записей + cookie per-mod): 12 → 10 core, всего 13 → 11. Заголовок Ф4.4 приведён к этому итогу (ранний артефакт «→ 7» удалён).
- **П4. Порядок фаз**: де-синглтонизация раньше PointDepthAtlas (зависимость фасада от DepthTileAtlas) — скорректировано относительно базового порядка дизайна.
- **П5. «~16 touch-точек» дизайна vs 25 фактических** в Pyramid/Evsm — не противоречие, более мелкая гранулярность + javadoc-шапки; состав тот же.
- **П6. Переименование сэмплера** irl_pointShadowArray → irl_pointShadowAtlas: обе спецификации (GLSL + binding) рекомендуют одинаково; формально gate 3.
- **П7. Переименование ingestion-uniform srcCube → srcAtlas**: принято ПЕРЕИМЕНОВАНИЕ (имя+тип) в GLSL CUBE_SRC/CONVERT_SRC с ПАРНОЙ правкой Java-строк лукапа (PointShadowPyramid.java:237, PointShadowEvsm.java:352 — P9/E13). Альтернатива «оставить имя srcCube, сменить только тип» отвергнута как вводящее в заблуждение имя для sampler2D; выбор зафиксирован, полумеры (переименовать в GLSL без Java) = молчаливый no-op glUniform1i(-1,·) — риск Р21.

---

## РИСКИ (объединено, дедуплицировано, ранжировано)

1. **Двойной ABI-брейк Java↔GLSL** (реестр сэмплеров: имя/target depth, удаление irl_pointShadowArray1/2; пороги END1 10→14). Java-ядро, live-GLSL и регенерат патча — строго один чекпоинт (I4-дисциплина MD5). Плюс: PatchLibrary обновляет только извлечённые .irlights — уже применённые паки в shaderpacks (run + prism) требуют ручного ре-синка, иначе визуальный ретест даст ложный FAIL. **Отдельно: удаление записей реестра впервые ломает НА РАНТАЙМЕ остальные 6 пропатченных паков (их патчи декларируют irl_pointShadowArray — сэмплер останется несвязанным, point-тени = мусор/полная тень; spot не затронут); это ожидаемая деградация до пер-пак тиража, тест-дисциплина серии = только CR (Ф6.9 п.4), юзера предупредить явно.**
2. **Рассинхрон POINT_TIER_END {2,14,30} ↔ IRL_PT_END0/1/CELL1/CELL2** — тихое сэмплирование чужого блока. Проверять парой глаз оба сайта (ShadowBaker:257-261 ↔ glsl:244-250).
3. **Потеря GL_TEXTURE_CUBE_MAP_SEAMLESS**: PointShadowArray.java:218 — ЕДИНСТВЕННЫЙ enable-сайт в core+аддоне (Iris/ваниль не включают; стейт контекст-глобальный). Удаление PointShadowArray без переноса enable = тихая регрессия EVSM wide-penumbra (cube-view остаётся и требует hardware-seamless на всех мипах — PointShadowEvsm.java:403-404): полосы блюра на ±45° швах, без GL-ошибок. Митигация: перенос в PointShadowEvsm.ensureResources (Ф3 E13) + чек перед удалением (Ф5) + целевой пункт ин-гейм ретеста (Ф7). Дополнительно: промежуточное состояние (Java без Ф6) визуально регрессирует на depth-швах — не тестировать/не коммитить между Ф5 и Ф6.
4. **Глобальный блок vs локальный слот**: markDirty теперь принимает ГЛОБАЛЬНЫЙ myBlock (ShadowBaker:818-819, :857-858, :1021-1022 — сейчас там myLocal), а uniform slot остаётся ЛОКАЛЬНЫМ (b - tierStartBlock). Перепутать = тихий баг адресации; обязательное ревью 3 пар строк + convert/pass-0 uniforms.
5. **Ориентация GLSL-зеркала**: формула irlite_pointAtlasUV обязана совпасть с Java pixel-rect в viewport (bottom-left) пространстве; перепутанный row-ориджин = зеркально переставленные грани. Ловится только визуально/бейк-дампом — целевой пункт ин-гейм теста.
6. **GL_MAX_TEXTURE_SIZE**: ULTRA point = 24576² > типичного лимита 16384 (HIGH 12288 проходит, spot ULTRA 16384 — ровно на лимите). Без clamp glTexImage2D упадёт. Решение политики — gate 6; минимум = query + clamp.
7. **Каскад setTileSize/delete**: фильтры обязаны удаляться вместе со сменой tileSize (levels = log2(T), общий blur-temp sized по крупнейшему тиру); фасадный setTileSize без delete-каскада = рассинхрон levels/temp и тихий брак мипов. Касается ОБОИХ фасадов (spot и point).
8. **GL-free class-init**: PointDepthAtlas (таблицы без GL) и `blockFar = new float[blockCount()]` у Evsm инициализируются на class-init вне GL-потока; blockCount() статичен (TIER_SUPERCELLS — компайл-константа) — сохранить это свойство, не делать layout runtime-конфигурируемым.
9. **Семантика NEAREST vs старого LINEAR** (П1): теоретический паритет обоснован, но hard-путь и VL-тап проверить визуально отдельно.
10. **copyStaticToLiveRect без lazy-init обоих слоёв** — overlay per-face копия первого кадра по textureId=0. Явно перенести идиому :234-241.
11. **Эквивалентность gather-весов** держится ТОЛЬКО на пиксель-выровненных ориджинах (кратность f) — инвариант Java-раскладки, GLSL его не проверяет; зафиксирован в javadoc Ф2.2, нарушение = поплывшая билинейка.
12. **mavenLocal / координата 1.1**: publishToMavenLocal нового core молча подменяет зависимость редактору и другим линиям; совместная загрузка старого редактор-jar и нового IRLite-jar с nested irl-core одной координаты — недетерминированный выбор loader'ом (`NoClassDefFoundError: PointShadowTiers`). Митигция — gate 4 (бамп 1.2) + не пересобирать редактор (gate 12).
13. **Unbound/нулевой атлас**: textureSize=0 → faceRes=0, halfTexel=inf; голые depth-тапы читают unbound sampler2D (обычно 0 → полная тень). Spot-паритетная экспозиция (:450), но НОВЫЙ режим отказа для point — знать при диагностике; гард — gate 8.
14. **Ошибочное удаление SamplerBindingCubeArrayMixin** («point теперь 2D») сломает irl_pointEvsm* (cube-view), irl_pointShadowPyramid* (2D_ARRAY), irl_cookieArray — миксин обязан остаться, 0 правок.
15. **Снятие гейта :393**: irlite_cubeFaceUV/pointAtlasUV становятся безусловными в IRLITE_COMPILE_SHADOWS — проверить компиляцию конфигурации со всеми F-фичами OFF.
16. **Смежность блока 3×2**: один glCopyImageSubData на copyStaticToLive стоит на физической неразрывности блока — гарантируется формулой X0/Y0 (блок внутри суперячейки), не нарушать при любом рефакторе раскладки.
17. **flushDirty ре-группировка** per-instance → per-tier внутри одного static-метода: ошибка дырявого обхода mask по [tierStartBlock, +count) = рассинхрон пирамиды/MSM с атласом (залипшие тени); барьерная структура = эквивалент трёх сегодняшних последовательных flush'ей.
18. **VRAM +33%** (live-depth 108·T² → 144·T²; static лениво удваивает) и устаревшая javadoc-таблица IRLShadowQuality:14-25 — обновить, иначе введёт в заблуждение следующий перф-аудит.
19. **Поведенческая дельта на швах**: клампленный суб-текельный футпринт (ошибка ≤0.5 текеля) вместо seamless; при отвале EVSM-гейта (несовпадающий build) широкий PCF у швов плющится о кламп — целевой пункт визуального ретеста.
20. **Лестница разрешений** F,F/2,F/4 вместо max(64,F>>t): эквивалентно при пресетах ≥512; зафиксировать в javadoc против будущих пресетов <256.
21. **Молчаливый no-op при рассинхроне имени ingestion-uniform**: переименование srcCube → srcAtlas в GLSL без парной правки Java-строк лукапа (PointShadowPyramid.java:237, PointShadowEvsm.java:352) даёт glGetUniformLocation = -1 → glUniform1i(-1,·) = no-op без GL-ошибки; сэмплер остаётся на дефолтном unit 0 и читает чужую 2D-текстуру — пирамида и MSM тихо ломаются, компиляция шейдера проходит, MSM validity-гейт mm.z<0 регрессию НЕ ловит (отрицательный третий момент пишется всегда). Митигация: обе правки внесены в P9/E13, критерий Ф3 включает греп `"srcCube"` по core = пусто.

---

## OPEN GATES (решения юзера ДО старта соответствующих фаз)

Перечислены в структурированном поле open_gates. Блокирующие старт кода: ветка (gate 1), имя DepthTileAtlas (gate 2), имя irl_pointShadowAtlas (gate 3), бамп core-версии (gate 4), blur-temp (gate 9). Блокирующие только Ф4.5/Ф2: LOW-clamp (gate 5), ULTRA-политика (gate 6). Остальные — follow-up.

## НЕ-ЦЕЛИ (жёсткие границы серии)

- Редактор irlights — НЕ трогать (ни код, ни пересборка, ни его бандл-патчи); последствия зафиксированы в gate 12 и риске Р12.
- Тираж GLSL только на ComplementaryReimagined; остальные 6 пропатченных паков — отдельной командой (до тиража они ОЖИДАЕМО теряют point-тени с новым core/addon — риск Р1, Ф6.9 п.4).
- Бейк-перф skip-empty-face (пропуск бейка граней без кастеров) — отдельный трек, сюда не входит.
- Octahedral/dual-paraboloid проекции — закрыты навсегда, не переоткрывать; 6 линейных 90°-проходов на лампу остаются.
- binding7 (LightBuffer) и binding6 (ClusterGridBuffer) — не трогать; layout vlParams.w заморожен (меняется только семантика значения).
- BLUR_SRC (seam-remap), MIP_SRC обоих фильтров, cube-view viewId у EVSM — 0 изменений (глобальный seamless-enable для cube-view переносится в PointShadowEvsm — Ф3 E13).
- EVSM/MSM trilinear wide-penumbra inset — отдельный follow-up (gate 10).
- Generic-пятёрка hysteresis ShadowBaker (:1097-1253) — переиспользуется без изменений.
- Spot-путь (GLSL и Java) — поведенчески бит-в-бит; Ф1 меняет только форму кода.

## OPEN GATES (решения юзера; блокируют СТАРТ КОДА: 1, 2, 3, 4, 9; только Ф2/Ф4.5: 5, 6; остальные — follow-up)
РЕШЕНИЕ ЮЗЕРА (2026-07-16, подтверждено явно): гейты 1, 2, 3, 4, 9 ПРИНЯТЫ по рекомендациям — ветка = текущая optimization/octahedral-point-shadows (обе репы); generic-класс = DepthTileAtlas; depth-сэмплер = irl_pointShadowAtlas (FROZEN-контракт с патчем); бамп irl-core 1.1 -> 1.2 перед publishToMavenLocal; blur-temp = один общий static по крупнейшему тиру. СТАРТ КОДА РАЗБЛОКИРОВАН. Гейты 5/6 (LOW-clamp, ULTRA-политика) остаются открытыми — блокируют только Ф2/Ф4.5, минимум по 6 = query GL_MAX_TEXTURE_SIZE + clamp; решить по ходу Ф2.
1. Ветка: рекомендация — работать на текущей optimization/octahedral-point-shadows в обеих репах (чистая, per-session ветки запрещены feedback-памятью); новая/переименованная ветка только по явной команде. Подтвердить.
2. Имя generic-класса DepthTileAtlas (в дизайне помечено предварительным) — подтвердить или заменить до старта Ф1.
3. Имя depth-сэмплера irl_pointShadowAtlas (рекомендовано двумя спецификациями; переименование обязательно де-факто: тип uniform меняется samplerCubeArray->sampler2D, старое имя дало бы тихий мусор на старых паках). Подтвердить имя — оно FROZEN-контракт с патчем.
4. Бамп версии irl-core 1.1 -> 1.2 перед publishToMavenLocal — защита редактора и других линий от молчаливого подхвата несовместимого ядра (nested jar-in-jar с одной координатой = недетерминированный выбор loader'ом). Рекомендация: бампить.
5. LOW-пресет: clamp tier0 point к 1024 (setTileSize(max(1024, F)) — тогда LOW-атлас = 6144^2 как MEDIUM, VRAM-смысл LOW для point теряется) или оставить LOW=512 и считать D1-фикс действующим только с MEDIUM.
6. ULTRA-пресет: point-атлас 24576^2 превышает типичный GL_MAX_TEXTURE_SIZE=16384. Политика: тихий clamp TILE_SIZE вниз по query GL_MAX_TEXTURE_SIZE / отказ применить пресет / запрет ULTRA для point. (Spot при ULTRA = 16384 — ровно на лимите, перепроверить попутно.)
7. isDirty(block)/clearBit(block) заданы дизайном, но не имеют ни одного внешнего call-сайта: публиковать в API 'на будущее' (буква дизайна) или сделать private. Рекомендация: оставить public по дизайну — стоимость нулевая.
8. GLSL-гард на unbound point-атлас (ранний return при textureSize<6): рекомендация — НЕ добавлять (строгий spot-паритет, у spot гарда нет); подтвердить.
9. Blur-temp PointShadowEvsm: один общий static temp по крупнейшему тиру (рекомендация, образец SpotShadowEvsm:358-367) vs три per-tier (+VRAM). Подтвердить единый.
10. EVSM/MSM wide-penumbra trilinear lod-scaled UV inset — ОТДЕЛЬНЫЙ follow-up трек (материализуется только при будущей миграции EVSM-хранилища на flat-атлас; рецепт переноса зафиксирован в плане, Ф6 прим.). Не входит в эту серию.
11. Retune числа блоков {2,12,16} (TIER_SUPERCELLS {2,3,1}) — только после ин-гейм теста Ф7, отдельным решением.
12. Судьба редактора irlights: при финальном удалении PointShadowTiers редактор перестанет собираться против нового core (LightDriver.java:10,92,94 -> totalSlots()). Варианты: (а) не пересобирать редактор до отдельной команды (он самодостаточен со старым nested core), (б) временный делегат totalSlots()->PointDepthAtlas.blockCount() в core. Рекомендация: (а) + бамп версии core.

## ПРОМПТ ДЛЯ СЛЕДУЮЩЕЙ СЕССИИ (возобновление)

«Atlas-merge point-теней РЕАЛИЗОВАН 2026-07-16 (см. СТАТУС РЕАЛИЗАЦИИ в memory/plan-point-shadow-atlas-merge.md), НЕ закоммичен — обе репы dirty на optimization/octahedral-point-shadows. Осталось: (1) I5 ин-гейм визуальный ретест по чек-листу Ф7 — строго на ComplementaryReimagined_IRLights (рантайм = runClient -Pmc=1.20.4, Git Bash, JAVA_HOME=JDK21, лог run/runclient-console.log в фоне); особо: швы depth-пути при выключенных PREFILTER/PYRAMID, швы EVSM wide-penumbra (перенос seamless), VL через шов, конфиг со всеми F-фичами OFF, hard-путь после NEAREST (риск Р9), 30 ламп/вытеснение 31-й, смена пресета MEDIUM<->HIGH. (2) По моему подтверждению — коммит-чекпоинт core+addon+патч одним набором. Прочие паки без point-теней до тиража — это норма, не FAIL.»
