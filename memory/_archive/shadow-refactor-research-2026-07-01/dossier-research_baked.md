# Техники теней, эксплуатирующие статичность карт (бейк+кэш) — отчёт для IRLite/irl-core

Контекст оценки: spot -> 2D depth-атлас, point -> cube-map array; бейк амортизирован (препроцесс почти бесплатен), рычаг = per-pixel стоимость в кадре; сэмплинг-GLSL един для 7 паков (#120..#430, GL4-фичи доступны через расширения); бейкер Java/GL 4.5+ (там можно всё, включая compute); волюметрика сэмплит те же карты.

Ключевой общий факт, вокруг которого крутится всё ниже: **сырую depth-карту нельзя префильтровать** (среднее глубин ≠ среднее результатов теста), поэтому любая пре-блюр/мип-техника требует перейти к префильтруемому представлению — моментам (VSM/EVSM/MSM) или экспоненте (ESM). Статичность карт делает этот переход почти бесплатным: конвертация и вся цепочка фильтров делаются один раз на бейке.

---

## (1) Пре-вычисленные mip/blur-цепочки + выбор LOD по estimated penumbra

Идея: на бейке строится цепочка уровней с нарастающим блюром (mip или отдельная blur-chain); в кадре оценивается ширина полутени (PCSS-формула) и делается 1 трилинейный fetch между двумя соседними уровнями вместо N×N PCF.

- **Что известно из литературы.** Класс «filtering-oriented soft shadows» систематизирован в диссертации [Soft Shadow Mip-Maps (Shen, Yuksel, Utah)](https://collections.lib.utah.edu/dl_files/31/8d/318d6e6639f1eea9ae33c396a52af19007e45742.pdf): фильтр-сайз привязывается к mip-уровням (×2 на уровень), выбор уровня — по оценке blocker depth. Каноническая реализация идеи «prefiltered convolution soft shadows» — [Annen et al., Real-Time All-Frequency Shadows in Dynamic Scenes, SIGGRAPH 2008](https://jankautz.com/publications/ConvolutionSoftShadows_SIG08.pdf) (CSSM): и средняя глубина блокеров, и сам фильтр берутся из префильтрованных (mip) представлений за O(1). Мобильный вариант той же идеи — [Variable Penumbra Shadow Mapping for Mobile Devices](https://www.researchgate.net/publication/265331407_Variable_Penumbra_Shadow_Mapping_for_Mobile_Devices) (mip-chain dilation + переменная полутень по occluder distance). UE5 для spot-VSM тоже использует единый шадоумап с mip-цепочкой вместо клипмап ([Virtual Shadow Maps, UE doc](https://dev.epicgames.com/documentation/en-us/unreal-engine/virtual-shadow-maps-in-unreal-engine)).
- **Качество полутени.** Contact hardening есть (ширина фильтра из PCSS-оценки). Опасность: «boxy artifacts» при пер-пиксельном клампе mip-уровня на обычной mip-цепочке — GPU Gems 3 прямо предупреждает, что mipmapped VSM даёт «extremely objectionable boxy artifacts» при динамическом выборе уровня ([GPU Gems 3, ch.8](https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-8-summed-area-variance-shadow-maps)). Лечится: (а) трилинейная интерполяция между уровнями (не кламп), (б) blur-chain с гауссом вместо box-mip (у нас бейк бесплатный — можно позволить полноценный сепарабельный гаусс на каждый уровень).
- **Стоимость в кадре.** 1 fetch на penumbra-оценку (см. технику 2) + 1-2 трилинейных fetch'а из цепочки = **2-4 fetch'а** против 16-64 у текущего PCSS.
- **Стоимость бейка.** Тривиальна: конверт depth->моменты + сепарабельный блюр на уровень; для нас амортизирована в ноль.
- **Память.** Mip-цепочка = +33%. Но главный расход — формат моментов (см. технику 6). Критичная деталь для нашего **атласа**: mip-уровни тайлового атласа требуют gutter/padding вокруг тайлов, иначе на грубых уровнях соседние тайлы протекают друг в друга. Для cube-array mips — штатно, GL4.
- **GLSL #120..430.** `textureLod` есть с #130; для #120-паков — `texture2DLod` через `GL_ARB_shader_texture_lod` (контекст всё равно GL4.5, Iris это переваривает; cube-array и так живёт на расширениях). Реализуемо единым файлом с #define-дельтой.
- **Вердикт: да, но не как самостоятельная техника, а как составная часть гибрида (6).**

## (2) Min/max depth mip-пирамида для мгновенного blocker search

Идея: на бейке строится двухканальная (min,max) пирамида глубины; в кадре одно чтение грубого уровня, накрывающего search-регион, даёт консервативные границы глубин.

- **Что известно.** [NVIDIA GDC'08 Advanced Soft Shadow Mapping](https://developer.download.nvidia.com/presentations/2008/GDC/GDC08_SoftShadowMapping.pdf) и [Soft Shadows Using Hierarchical Min-Max Shadow Maps (NVIDIA GDC'07)](https://developer.download.nvidia.com/presentations/2007/gdc/SoftShadows.pdf) — иерархический спуск с отсечением поддеревьев; двухканальная текстура min+max. Для волюметрики — [Chen et al., Real-Time Volumetric Shadows using 1D Min-Max Mipmaps, I3D 2011](https://groups.csail.mit.edu/graphics/mmvs/): min-max пирамида даёт ускорение «на порядок» против наивного марша. [VSSM (Yang et al., PG 2010)](https://flycooler.com/PG2010_VSSM.html) решает ту же задачу иначе — средняя глубина блокеров одним запросом к SAT/мипам через VSM-неравенство (см. 5).
- **Три выигрыша за одну текстуру:**
  1. **Early-out**: если depth_receiver < min(регион) — пиксель полностью освещён, весь PCSS/PCF пропускается; если > max при полном покрытии — полностью в тени. В типичной сцене это убивает 70-90% работы, т.к. полутень — узкая полоса. Прямая атака на «GPU-bound при десятках источников».
  2. **Blocker search за 1-2 fetch'а**: avg-канал (третий, обычная усреднённая пирамида) даёт приближение средней глубины блокеров в духе VSSM; min-канал — консервативную оценку ближайшего блокера (максимальная полутень).
  3. **Волюметрика**: тот же min/max по лучу ray-march'а (Chen 2011) — резко дешевле VL-теней в Photon, и контракт не меняется (отдельная вспомогательная текстура рядом с картой).
- **Качество.** Не меняет фильтр вообще — чистый акселератор, нулевой риск для картинки (оценка полутени по avg — то же допущение, что в PCSS/VSSM).
- **Кадр:** 1-2 `textureLod`. **Бейк:** пирамида за log(N) fullscreen-пассов, копейки. **Память:** RG16F/RGB16F пирамида ≈ +33-50% от карты в её же битности; для cube-array — per-face пирамида (регион search'а почти всегда внутри грани; на границе — консервативный fallback).
- **GLSL:** одна `textureLod`, портабельно везде, включая #120 (см. выше).
- **Вердикт: лучшее соотношение выигрыш/усилия из всех семи. Внедрять первой.**

## (3) Distance transform / «shadow distance field» из depth map

Идея: на бейке — 2D EDT (например, jump flooding) по карте: расстояние до ближайшего силуэта блокера (+ глубина блокера); в кадре 1 fetch -> аналитическая мягкость по расстоянию до края тени.

- **Что известно.** [Utilizing Jump Flooding in Image-Based Soft Shadows (NUS)](https://www.comp.nus.edu.sg/~tants/softShadow/jfaSoftShadow-techreport.pdf) — JFA для внутренней+внешней полутени из shadow-map силуэтов; [Edge Distance Shadow Mapping (Kenzel, TU Graz, master thesis)](https://diglib.tugraz.at/download.php?id=576a749d4e4fe&location=browse); [Smooth Penumbra Transitions with Shadow Maps](https://www.researchgate.net/publication/220494122_Smooth_Penumbra_Transitions_with_Shadow_Maps); родственная screen-space ветка — [Screen Space Soft Shadows (Gumbau et al.)](https://www.researchgate.net/publication/328838373_Screen_space_soft_shadows). Аналитическая мягкость по «расстоянию до окклюдера» — та же логика, что в SDF-тенях [Inigo Quilez, raymarched soft shadows](https://iquilezles.org/articles/rmshadows/) и [RTSDF](https://arxiv.org/pdf/2210.06160), но у нас поле 2D по карте, а не 3D по сцене.
- **Фундаментальная проблема.** «Край тени» в shadow-map-space не определён без глубины ресивера: EDT по карте фиксирует силуэты для одного слоя, а полутень зависит от (d_receiver − d_blocker). При многослойных окклюдерах (типичный Minecraft-интерьер: мебель над полом под потолком) расстояние до ближайшего силуэта относится не к тому блокеру, который затеняет данный ресивер -> неверная ширина/форма полутени, артефакты «мягкость не от того объекта». Частично лечится хранением (distance, blocker_depth) и отбраковкой по глубине, но честного contact hardening в общем случае нет.
- **Кадр:** 1-2 fetch'а — абсолютный минимум среди всех техник. **Бейк:** JFA = log(N) пассов ping-pong FBO, без compute — Java-бейкеру идеально. **Память:** +1-2 канала (R16F/RG16F). **GLSL:** тривиально, любой #version.
- **Вердикт: не как основная техника. Кандидат на LOW-пресет и/или дальний LOD (там ошибки полутени незаметны), где 1 fetch решает.** Заметь: UE хранит именно distance field, но в **receiver-space** (см. 7) — там проблема многослойности не возникает.

## (4) Precomputed penumbra maps / prefiltered multilayer soft shadows

- **[Penumbra Maps (Wyman, Hansen, EGSR 2003)](https://cwyman.org/papers/egsr03_PenumbraMaps.pdf):** из силуэтных рёбер (от центра area-света) строится доп. карта полутени; в кадре 2 fetch'а (shadow map + penumbra map). Минусы: только внешняя полутень (умбра завышена, контактное затвердевание неполное), бейк требует извлечения силуэтов из геометрии — в нашем бейкере (растеризация BBS-моделей) это отдельный нетривиальный слой; техника 2003 года, по качеству уступает всему списку. **Вердикт: нет.**
- **[Filtering Multi-Layer Shadow Maps for Accurate Soft Shadows (Selgrad et al., CGF 2015)](https://selgrad.org/publications/2015_cgf_SDMS.pdf)** ([DOI](https://dl.acm.org/doi/10.1111/cgf.12506)): per-texel списки фрагментов переменной длины, префильтрация прогрессивным слиянием списков; лучшее качество класса при перекрывающихся окклюдерах на разных глубинах (ровно наша больная точка у EDT/VSM). Но: linked lists / переменные структуры в сэмплинге — несовместимо с единым инжект-файлом под #120-паки; сложность имплементации research-grade. **Вердикт: нет (как источник идей — да: подтверждает, что вся боль префильтрации — в многослойности).**

## (5) SAT поверх VSM (Summed-Area Variance Shadow Maps)

Идея: на бейке — VSM (depth, depth²) -> summed-area table; в кадре точная box-фильтрация **произвольного** радиуса за 4 fetch'а: `sum = SAT[max] − SAT[xmin,ymax] − SAT[xmax,ymin] + SAT[min]`.

- **Что известно.** Канон — [GPU Gems 3, гл. 8 (Lauritzen)](https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-8-summed-area-variance-shadow-maps): 4 fetch'а на регион, стоимость не зависит от радиуса, пер-пиксельный размер фильтра без «boxy» артефактов (в отличие от mip-clamp). Precision: SAT съедает log2(W×H) бит — 512² съедает 18 бит, у fp32-мантиссы остаётся 5; лечится int32 (wraparound, предпочтителен), смещением −0.5 и распределением по каналам. Быстрее PCF при динамических радиусах (40 vs 25 fps на 1600×1200 в их тесте). Демо/обсуждение: [Beyond3D](https://forum.beyond3d.com/threads/summed-area-variance-shadow-maps-demo.34999/), реализация: [github timurson/Summed-AreaVSM](https://github.com/timurson/Summed-AreaVSM). Blocker search поверх SAT — [VSSM (Yang et al. 2010)](https://onlinelibrary.wiley.com/doi/10.1111/j.1467-8659.2010.01800.x): средняя глубина блокеров одним SAT-запросом через VSM-неравенство + сабдивизия кернела против light bleeding; «на порядок быстрее PCSS при большой полутени», 100+ fps.
- **Качество.** Contact hardening полноценный (VSSM-оценка + точный переменный фильтр). Light bleeding VSM-класса (перекрывающиеся окклюдеры) — уменьшается remap'ом хвоста и сабдивизией, не исчезает.
- **Кадр:** ~4 fetch'а blocker-оценка + 4 fetch'а фильтр (+ fallback-сабдивизия) = **8-16 fetch'ей худший случай**, не зависит от Softness — идеально против «десятков тапов при высокой Softness».
- **Бейк:** VSM-конверт + recursive doubling (Hensley) — log-число пассов; на нашем GL4.5 можно и compute (бейк наш). Java-бейкеру ок.
- **Память:** RG32F/RGBA32F или RG32I = 8-16 Б/тексель (сырой D24 — 4 Б), mips не нужны. Для атласа — **SAT строить per-tile** (это ещё и спасает precision: биты ест размер тайла, а не всего атласа 4-8K).
- **Ахиллесова пята — point/cube.** SAT определён на 2D-прямоугольнике; на cube-map он «per-face», и фильтр-регион, пересекающий границу грани, ломается. Для нашего PointShadowArray это системная проблема (у spot-атласа её нет). Обходы (перепроекция в октаэдр-карту и SAT по ней) — отдельный проект.
- **GLSL:** fp32-SAT сэмплится обычным `texture2D` — работает даже в #120; int32-SAT требует isampler (#130+) — можно сделать пресетной/паковой дельтой.
- **Вердикт: сильный кандидат для spot-атласа на HIGH/ULTRA; для point cube-array — нет.**

## (6) Гибрид: пре-блюренная EVSM для широкой полутени + PCF-тапы для контактной зоны

Идея: два режима в одном сэмплере. Дальняя/широкая полутень — 1 трилинейный fetch из пре-блюренной EVSM mip/blur-цепочки (выбор уровня по penumbra-оценке из техники 2); контактная зона (оценка полутени меньше ~1-2 текселей уровня 0) — 3-9 PCF-тапов по сырой depth-карте, как сейчас. Плавный blend по оценке ширины.

- **Что известно.** EVSM = VSM с экспоненциальным варпом, почти убирает light bleeding: [Martin Cap, EVSM overview](https://www.martincap.io/project_detail.php?project_id=9), [Lousodrome EVSM notes](https://lousodrome.net/blog/light/tag/evsm/). Практика от MJP: [Shadow Sample Update](https://therealmjp.github.io/posts/shadow-sample-update/) — EVSM4 fp32 = 128 бит/тексель (максимум качества), fp16-вариант вдвое дешевле, но больше bleeding и требования к bias; anisotropic+trilinear+mips штатно работают. По скорости: EVSM с блюром 9×9 + генерацией mips **каждый кадр** всё равно 2× быстрее наивного PCF 9×9 ([gamedev.net demo](https://www.gamedev.net/forums/topic/619034-demo-exponential-variance-shadow-map-with-mip-maps/)) — а у нас блюр и mips вообще уходят в бейк. Альтернатива моментов с меньшим bleeding при 64 бит/тексель — [Improved Moment Shadow Maps (Peters, Klein, JCGT)](https://www.jcgt.org/published/0006/01/03/paper-lowres.pdf) (MSM32 у MJP чуть медленнее EVSM32 по качеству/скорости — паритет, выбор инженерный).
- **Качество.** Лучший практический компромисс: широкая полутень — гладкая, без шума и без «зернистого квадрата» (пре-блюр на бейке физически стирает пер-тексельный шум бейка — прямое попадание в наш known-issue при высокой Softness); контактная зона — честный hardening от PCF. Бонус: EVSM не использует depth-compare bias -> в EVSM-зоне исчезает self-shadow acne (наш Photon view-bobbing acne лечится там, где работает EVSM-ветка).
- **Кадр:** penumbra-оценка 1 fetch (техника 2) + либо 1 трилинейный fetch (широкая), либо 4-9 тапов (контакт). Средневзвешенно по сцене — **в разы меньше текущего PCSS**, и стоимость перестаёт расти с Softness.
- **Волюметрика:** главный скрытый выигрыш — ray-march сэмплит EVSM **одним fetch'ом на шаг без PCF и почти без bias-борьбы** (стандартная практика для VL). Контракт волюметрики меняется мягко: тот же атлас/массив, другой формат текстуры.
- **Бейк:** конверт depth->EVSM (exp-варп) + сепарабельный гаусс + mips; всё fullscreen-пассами, compute не нужен. Для **cube-array**: блюрить per-face с gutter'ами/дублированием бордюров (швы на границах граней — известная возня, но решаемая; сэмплинг сглаживает seamless cube filtering, GL 3.2+ core).
- **Память:** EVSM2 fp16 = 4 Б/тексель (паритет с D32), EVSM4 fp32 = 16 Б; +33% на mips. Пресетно: LOW/MEDIUM — EVSM2 fp16, HIGH/ULTRA — EVSM4 fp32 либо MSM 64-бит. Сырую depth-карту храним рядом только для контактной PCF-ветки и текущих потребителей (можно в половинном разрешении не надо — она уже есть).
- **GLSL #120..430:** сэмплинг = `texture2DLod/textureLod` + exp/чебышёв-арифметика — обычная float-математика, единый файл с #define-дельтами реалистичен; cube-array EVSM = тот же samplerCubeArray, но float-формат вместо depth (миксины биндинга в irl-core уже наши — ProgramSamplersBuilderMixin/SamplerBindingCubeArrayMixin перенастраиваемы).
- **Вердикт: рекомендованная целевая архитектура. Покрывает и спот, и поинт, и волюметрику, и оба known-issue (зернистость Softness, acne).**

## (7) Как AAA бейкают тени статических локальных источников

- **Unreal (Lightmass, stationary lights):** для статической геометрии бейкается **distance field shadow map** — но в **lightmap-UV-space ресивера**, не в light-space: хранится знаковое расстояние до границы тени, что даёт гладкие переходы «even at lower resolutions» и «very little runtime cost» ([Stationary Lights doc](https://docs.unrealengine.com/4.26/en-US/BuildingWorlds/LightingAndShadows/LightMobility/StationaryLights), [Shadowing in UE](https://dev.epicgames.com/documentation/unreal-engine/shadowing-in-unreal-engine)). До 4 перекрывающихся stationary-светов — по каналам одной текстуры (graph coloring). Отдельно бейкается **static shadow depth map** (грубая depth-карта от статики, ~тексель/метр, `StaticShadowDepthMapTransitionSampleDistance*` в BaseLightmass.ini) — используется для полупрозрачности/дешёвых ресиверов.
- **Unity (Shadowmask):** бейкается **occlusion-текстура RGBA в lightmap-space** — по одному каналу затухания тени на свет, максимум 4 перекрывающихся; при переполнении свет падает в fully-baked ([Unity Manual: Shadowmask](https://docs.unity3d.com/Manual/LightMode-Mixed-Shadowmask.html), [Catlike Coding: Shadow Masks](https://catlikecoding.com/unity/tutorials/custom-srp/shadow-masks/)).
- **Смысл для нас:** оба движка хранят **per-receiver visibility/расстояние, а не per-light depth** — это возможно только при наличии уникальных lightmap-UV статической геометрии. У Minecraft+BBS (динамические актёры, реплеи, воксельный мир без UV-атласа) этого нет, прямой перенос невозможен. Но принцип подтверждает стратегию: **раз препроцесс бесплатен — бейкать надо уже готовую «мягкость» (моменты/расстояния), а не сырую глубину, которую потом дорого фильтровать в кадре.** Наш аналог per-light-space этого принципа = техники (5)/(6).

---

## Сводная таблица

| # | Техника | Полутень / contact hardening | Кадр (fetches) | Бейк | Память (× к D24/D32) | Cube-array | GLSL #120..430 | Вердикт |
|---|---|---|---|---|---|---|---|---|
| 2 | min/max(+avg) пирамида | не меняет фильтр; early-out + blocker за 1 fetch | +1-2, минус 70-90% работы | log-пассы, копейки | +0.3-0.7× | per-face, ок | тривиально | **делать первой** |
| 6 | EVSM-цепочка + контактный PCF | отличная широкая + честный контакт; лечит acne и зернистость | 2-4 (широкая) / 5-10 (контакт) | конверт+гаусс+mips | 1× (fp16) — 5× (fp32+mips) | да (per-face блюр с gutter) | да, #define-дельты | **целевая архитектура** |
| 1 | пре-блюр mip-LOD | как (6), но без контактной ветки риск boxy | 2-4 | копейки | как (6) | да | да | поглощается (6) |
| 5 | SAT-VSM (+VSSM blocker) | точный переменный box-фильтр, независим от Softness | 8-16 | log-пассы (recursive doubling) | 2-4×, без mips | **нет (швы граней)** | fp32-SAT — да; int — #130+ | опция HIGH/ULTRA только для spot-атласа |
| 3 | EDT / shadow distance field | аналитическая, но неверна при многослойных окклюдерах | 1-2 | JFA, log-пассы | +0.25-0.5× | per-face условно | тривиально | только LOW-пресет / дальний LOD |
| 4 | penumbra maps / multilayer prefilter | 2003: неполная; Selgrad: отличная, но списки фрагментов | 2 / переменная | силуэты / fragment lists | +1× / много | нет | нет (multilayer) | нет |
| 7 | UE/Unity baked (receiver-space) | эталонная дешевизна | ~1 | Lightmass/Enlighten | lightmap-scale | n/a | n/a | непереносимо (нет lightmap-UV), но подтверждает стратегию |

## Рекомендуемый порядок внедрения

1. **Min/max/avg-пирамида (2)** — чистый акселератор без смены контракта картинки: early-out решает GPU-bound по источникам, avg-канал заменяет blocker search (десятки fetch'ей -> 1-2). Параллельно 1D-min/max для VL-марша Photon ([Chen 2011](https://groups.csail.mit.edu/graphics/mmvs/)).
2. **Гибрид EVSM (6)**: сначала на spot-атласе (проще), затем cube-array с per-face блюром. Пресеты: LOW = только EVSM2 fp16 1-fetch (вообще без PCF), MEDIUM/HIGH = гибрид, ULTRA = EVSM4 fp32 или MSM. Побочно закрывает «зернистый квадрат» Softness и Photon-acne, упрощает VL.
3. **SAT-VSM (5)** — опционально позже, как «точный» режим для spot-атласа, если гибриду не хватит качества на экстремальной Softness.
4. **EDT (3)** — микро-режим для дальнего LOD (связка с открытым «качество теней на дали»), если после (2)+(6) ещё будет нужда.

Отдельно: обе выбранные техники требуют **gutter/padding тайлов spot-атласа** под mip/blur (иначе протечки между тайлами на грубых уровнях) — это правка бейкера и layout-контракта атласа, планировать сразу.

## Источники

- [GPU Gems 3, ch.8 — Summed-Area Variance Shadow Maps (Lauritzen)](https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-8-summed-area-variance-shadow-maps)
- [Yang et al. — Variance Soft Shadow Mapping, PG 2010 (project page)](https://flycooler.com/PG2010_VSSM.html) / [CGF DOI](https://onlinelibrary.wiley.com/doi/10.1111/j.1467-8659.2010.01800.x)
- [Annen et al. — Real-Time, All-Frequency Shadows in Dynamic Scenes, SIGGRAPH 2008 (PDF)](https://jankautz.com/publications/ConvolutionSoftShadows_SIG08.pdf)
- [Shen — Soft Shadow Mip-Maps, Univ. of Utah thesis (PDF)](https://collections.lib.utah.edu/dl_files/31/8d/318d6e6639f1eea9ae33c396a52af19007e45742.pdf)
- [NVIDIA GDC'07 — Soft Shadows Using Hierarchical Min-Max Shadow Maps (PDF)](https://developer.download.nvidia.com/presentations/2007/gdc/SoftShadows.pdf)
- [NVIDIA GDC'08 — Advanced Soft Shadow Mapping (PDF)](https://developer.download.nvidia.com/presentations/2008/GDC/GDC08_SoftShadowMapping.pdf)
- [Chen et al. — Real-Time Volumetric Shadows using 1D Min-Max Mipmaps, I3D 2011](https://groups.csail.mit.edu/graphics/mmvs/)
- [MJP — Shadow Sample Update (EVSM/MSM практика)](https://therealmjp.github.io/posts/shadow-sample-update/)
- [Peters, Klein — Improved Moment Shadow Maps, JCGT (PDF)](https://www.jcgt.org/published/0006/01/03/paper-lowres.pdf)
- [EVSM demo with mipmaps — gamedev.net](https://www.gamedev.net/forums/topic/619034-demo-exponential-variance-shadow-map-with-mip-maps/)
- [Martin Cap — Exponential Variance Shadow Maps](https://www.martincap.io/project_detail.php?project_id=9)
- [Wyman, Hansen — Penumbra Maps, EGSR 2003 (PDF)](https://cwyman.org/papers/egsr03_PenumbraMaps.pdf)
- [Selgrad et al. — Filtering Multi-Layer Shadow Maps for Accurate Soft Shadows, CGF 2015 (PDF)](https://selgrad.org/publications/2015_cgf_SDMS.pdf) / [DOI](https://dl.acm.org/doi/10.1111/cgf.12506)
- [NUS — Utilizing Jump Flooding in Image-Based Soft Shadows (PDF)](https://www.comp.nus.edu.sg/~tants/softShadow/jfaSoftShadow-techreport.pdf)
- [Kenzel — Edge Distance Shadow Mapping, TU Graz thesis](https://diglib.tugraz.at/download.php?id=576a749d4e4fe&location=browse)
- [Gumbau et al. — Screen Space Soft Shadows](https://www.researchgate.net/publication/328838373_Screen_space_soft_shadows)
- [Inigo Quilez — Raymarched soft shadows (SDF)](https://iquilezles.org/articles/rmshadows/)
- [RTSDF — Real-time Signed Distance Fields for Soft Shadow Approximation (arXiv)](https://arxiv.org/pdf/2210.06160)
- [Variable Penumbra Shadow Mapping for Mobile Devices](https://www.researchgate.net/publication/265331407_Variable_Penumbra_Shadow_Mapping_for_Mobile_Devices)
- [UE — Stationary Lights (distance field shadow maps, каналы)](https://docs.unrealengine.com/4.26/en-US/BuildingWorlds/LightingAndShadows/LightMobility/StationaryLights)
- [UE — Shadowing in Unreal Engine](https://dev.epicgames.com/documentation/unreal-engine/shadowing-in-unreal-engine)
- [UE — Virtual Shadow Maps (spot = один VSM с mip-цепочкой)](https://dev.epicgames.com/documentation/en-us/unreal-engine/virtual-shadow-maps-in-unreal-engine)
- [Unity Manual — Lighting Mode: Shadowmask](https://docs.unity3d.com/Manual/LightMode-Mixed-Shadowmask.html)
- [Catlike Coding — Shadow Masks (Custom SRP)](https://catlikecoding.com/unity/tutorials/custom-srp/shadow-masks/)
- [timurson/Summed-AreaVSM (реализация)](https://github.com/timurson/Summed-AreaVSM)
- [Beyond3D — SAVSM demo discussion](https://forum.beyond3d.com/threads/summed-area-variance-shadow-maps-demo.34999/)