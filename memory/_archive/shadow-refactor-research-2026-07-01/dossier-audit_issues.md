# Аудит базы знаний: тени IRLite/irl-core — полный каталог проблем

Источники: живые файлы `memory/` (addon-shadows, shadow-distance-quality-plan, project-shadow-bake-perf-audit, fix-shadow-depthstate-repin, shader-shadow-sampling, shader-volumetric, ref-betterlights-shadow-comparison, photon-bugfix, plan-irl-core-shadow-extraction) + `_archive/` (project-shadow-softness-pcss-fix, shadow-entity-parity-findings, plan-shadow-bake-perf, plan-shadow-1211-fwdport-c10, project-shadow-1211-real-models, shadow-seam-фазы).

---

## 1. КАТАЛОГ ПРОБЛЕМ

### 1.1 Открытые (open)

| # | Симптом | Диагноз / подозрение | Слой | Статус |
|---|---------|----------------------|------|--------|
| P1 | «Зернистый квадратный ореол» вокруг тени при росте Shadow Softness (IRLITE_SHADOW_SIZE или per-light cone.w=bulbSize); point хуже, spot слабее; эталон IRLEngine бага НЕ имеет | Шейдер-PCSS и фильтр сэмплера ЭМПИРИЧЕСКИ ИСКЛЮЧЕНЫ (пост-мортем 2026-06-18, всё откачено). Остался единственный подозреваемый: БЕЙК — содержимое cube-карт. Рабочая гипотеза: бейк рисует light-facing грань cull OFF → пол сам пишет себя в глубину → при широком PCF-ядре грейзинг-тапы на боковых гранях куба читают пол под скользящим углом (плохая точность перспективной глубины) → self-shadow acne на шве грани −Y/боковая = квадрат. НЕ проверено в игре. Следующий шаг по памяти: ВИЗУАЛЬНОЕ заземление (скрин/runClient), хватит чинить вслепую. Оракул паритета: IRLEngine `photon_IRLights/.../ir_lights.glsl` (IR_PointShadowSample ~:1060) | бейк Java (подозрение) | OPEN, 3 неудачные попытки откачены |
| P2 | Block self-shadow acne / bob-flicker: мерцание освещённой зоны при view-bobbing — может вернуться | Terrain self-shadow acne НЕЗАГАРЖЕН: GL_FRONT second-depth cull откачен обратно в disableCull (peter-pan), lit-поверхность сидит на пороге depth-compare, боббинг флипает сравнение. Предписанный фикс на случай возврата: маленький КОНСТАНТНЫЙ depth bias на block-пассе. НЕ front-cull, НЕ slope-scaled glPolygonOffset (взрывался на grazing-углах cube-граней point-света — толкал глубину за far plane = нет теней вообще) | бейк Java | WATCH (photon-bugfix), фикса нет |
| P3 | Спайк бейка ~305–327ms на static-bake-кадрах (профайлер подтвердил; есть на main 1.20.4 И port/1.21.11) | C10: renderBlocksDepth рисует ВЕСЬ per-light блок-VBO по разу на КАЖДУЮ из 6 граней point-света без per-face frustum-куллинга блоков (куллятся только entity-кастеры) → ~6× лишних вершин. Идея: T1.1-style per-face подвыборка (партиционирование VBO по граням / index-диапазоны / 6 под-VBO, тест AABB-vs-face-frustum). Опирается на shortlist/face-mask инфраструктуру T1.1. Профайлер до/после обязателен | бейк Java | OPEN — единственный незакрытый перф-остаток (Сессия E не начата) |
| P3a | (Побочный вопрос C10) возможный VBO-лик | onShadersDisabled-хука нет — eviction блок-VBO только через retainBlockVbos(liveIds); не проверено, что зовётся при тогле шейдеров | бейк Java | OPEN-вопрос |
| P4 | Model-block с in-place морф-анимацией (морф на месте, без изменения transform) — тень ЗАМОРОЖЕНА | OPEN-1 (addon-shadows): нет per-form anim-токена; model-blocks static-by-transform ради кэшируемости декоративных ламп; age как токен НЕЛЬЗЯ (тикает безусловно). Кандидат-фикс: per-form «is animating» сигнал в static hash model-block | бейк Java (dirty-кэш) | OPEN (не регрессия — так было и при старом хэше) |
| P5 | При >32 окклюдеров в радиусе — произвольная отсечка вместо ближайших; энтити-цикл может съесть все 32 слота до model-blocks/replays (голодание) | OPEN-2: first-32 в порядке world.getEntities(), а не nearest-32; IRLEngine EntityShadowTracker.insertSorted мержил ВСЕ типы по дистанции. Фикс: восстановить insertSorted через collect/collectModelBlocks/collectFilmReplays | бейк Java | OPEN, edge-case (нужно 32+ рядом), низкий приоритет |
| P6 | Свет, полностью за камерой, даёт НЕзатенённые VL-лучи (Photon, VL shadows ON) | Behind-camera bake-cull ставит shadowTile=-1, а VL теперь сэмплит карту → de-risk «volumetric не сэмплит тени» ИНВАЛИДИРОВАН. Фикс = ослабить behind-camera cull при включённых VL-тенях | контракт (bake-cull ↔ шейдер-потребление) | OPEN, accepted/deferred по скоупу юзера |
| P7 | Качество теней на дальней дистанции: Ф3 плана distance-quality | Архитектурный рычаг разрешения: preset bump (HIGH/ULTRA = 640MiB/2.5GiB VRAM) либо настоящий фикс = per-light resolution классы (variable atlas allocator + несколько cube-array + class index в SSBO) — Java-рефактор текстур/аллокатора, отдельная сессия. Практический совет сейчас: для дальних теней предпочитать SPOT (~3.5× плотнее по texel, d/886 vs d/256 на MEDIUM) | бейк Java + контракт SSBO | OPEN (Ф1-2 done, Ф3 not started) |
| P8 | IterationRP-инжект ОТСТАЁТ от Photon: нет texel floors, нет spot gather-PCF, VL БЕЗ теней | Photon-дельты (973d112, 9c19e47, f20aa3f) не портированы в iterationrp.irlpatch; при порте VL учесть camera-relative receiver (= cameraPosition+pos). Также нормал-оффсет фикс 2eef284 «всё ещё портировать» по photon-bugfix | сэмплинг GLSL / per-pack контракт | OPEN port-tail («портировать при следующем касании») |
| P9 | patches/photon.irlpatch надо регенерировать из финальной Modification; IRLITE_VL_SHADOWS не заведён в shaders.properties/lang (settings-UI) | Хвосты VL-shadows работ | контракт per-pack / настройки | OPEN follow-ups |
| P10 | MAJOR-B: feet-pivot AABB недооценивает габарит при повороте энтити → тень «молча врёт» | Фикс предписан (эмитить bounding-СФЕРУ на feet-center через sink.emit, не сырой AABB — инвариант 5); в plan-irl-core-shadow-extraction всё ещё числится в «ИЗВЕСТНЫЕ БАГИ» | бейк Java (шов эмита) | статус подтверждения фикса в памяти не зафиксирован — считать открытым/проверить |
| P11 | INV-2 консервативен: isStatic=false везде для энтити-кастеров | static-кэш работает только на блоках; часть two-layer выгоды не выбрана | бейк Java | OPEN (принятая недооптимальность) |
| P12 | port/1.21.11 редактора: отдельный raw-GL ShadowRenderer НЕ из core — не проверено, нужен ли там аналог ре-пина depth (MAJOR-A) | Вероятно нет BBS-форм-каста, но depth-стейт стоит сверить | бейк Java (порт-ветка) | OPEN хвост fix-shadow-depthstate-repin |
| P13 | Ф4 shadow-extraction: порт-ветки (redactor port/1.21.1·1.21.4·1.21.11·1.20.1, addon port/1.21.1) НЕ получили per-version core с вынесенными тенями (сидят на composite includeBuild против main-core) | Тиражирование не сделано; per-version core только на 1.20.4 | контракт/инфраструктура | OPEN |
| P14 | GPU-bound лаг GUI при десятках источников | Рычаг 2 (collect-cache) done; рычаг 1 GPU-cull/LOD ОТКРЫТ. Per-pixel сэмплинг = главный рычаг | сэмплинг GLSL / кадр | OPEN (project-gui-lag-gpu-bound-diagnosis) |
| P15 | Tier-3 verified-low перф-остатки | gpu-2 удвоение VRAM статик-слоем (ULTRA ~5ГиБ, не задокументировано юзеру); gpu-3 очистка 96 слоёв по одному (только при смене quality); no-distance-lod; bcc-1 ре-walk сферы при драге; bcc-4 аллокации на miss; frompolar-alloc; registry O(N²); skip entity-walk для block-only (fix-риск HIGH) | бейк Java | OPEN, сознательно отложены (low) |
| P16 | Durable-кавеат Сессии E (1.21.11): возможен десинк GL-кэша MC при бинде атласа в баке | opaque-дисциплина snapshot/restore доказана, но текстуры — новая территория | бейк Java | WATCH |

### 1.2 Закрытые, но хрупкие (не ломать при рефакторе)

| # | Что было | Чем закрыто | Хрупкость |
|---|----------|-------------|-----------|
| F1 | «Shadows toggle bakes once & freezes»: выключенный shadow-toggle сэмплил stale карту tile 0 | Guard `if (vlParams.w < 0.0) return 1.0;` ДО int-округления | GLSL int() усекает к нулю: int(-1.0+0.5)=0 — сентинель -1 проскакивает как tile 0. Guard обязан стоять ПЕРВЫМ во всех копиях инжекта |
| F2 | Staircase-acne на ребристых полах | Normal-offset bias (2eef284): fragWorld += (N+L)*0.05 — зеркалит IRLEngine ir_lights.glsl:1253 | Инжект однажды УЖЕ уронил этот оффсет при порте — это и был баг. Не заменять на slope-scale depth bias (см. §2) |
| F3 | Тень model-block пропадает при предмете в руке (1-е лицо) | MAJOR-A ре-пин depth/blend/матриц перед КАЖДЫМ emit (irl-core d21320c): depthMask(true)+enableDepthTest+disableBlend | Self-drawing BBS-формы портят shared per-pass GL-стейт (disableDepthTest внутри emitOccluder игрока) — любой новый emit-путь обязан ре-пиниться. Диагностика: GL-лог glGetBoolean на входе/выходе emit |
| F4 | «Второй cutout-слой гасит тени» + пропадание block-теней с ванильным ModelBlock | Фиксы 23ac3f4 (opaque-матрицы) + 6e5c3fd (выставить view/proj света ДО cutout-цикла: getBuffer(newLayer) авто-флашит прошлый слой с испорченными матрицами) | Immediate fallback BufferBuilder делится между RenderLayer — матрицы должны быть выставлены до цикла |
| F5 | Mass-rebake при повороте камеры (сдвиг тайлов) | Sticky tiles: свет ВЛАДЕЕТ тайлом; steal только у owner'а, неактивного ≥ STALE_FRAMES=2 | 2-кадровый пол защищает от ping-pong'а двух ламп за один тайл (ре-бейк обеих каждый кадр). Смена preset обязана звать resetTileState (иначе доверие старым sig = сэмпл пустых карт — латентный баг уже ловили) |
| F6 | ~5 glGet* синков на пасс | Single GL snapshot per bake (passStateSaved) | WARNING в памяти: если beginBake() однажды не вызвать — passStateSaved залипает true, снапшот замерзает → restore stale-стейта |
| F7 | B-OUTLINE-TRANSLUCENT (Photon) | Закрыт УДАЛЕНИЕМ нового outline, возвращён старый Fresnel-rim | WATCH: старый rim тоже считает в d4 по combined_depth_tex → solids-only, translucent-модели могут не давать кромку |
| F8 | 6-лучевая звезда самозатенения point-света | Dominant-axis perspective depth (max|dir|), НЕ euclidean length | Евклидов face-agnostic компэр повторно ОТКЛОНЁН в softness-сессии (затапливает пол, ~42680 флипов). НЕ трогать |
| F9 | Gather-PCF лезет в соседний тайл атласа | UV тапа клампится на полтексела внутрь тайла (tileMin/tileMax) | textureGather игнорирует фильтр сэмплера — атлас обязан оставаться NEAREST, а blocker search — на raw texture() (нужны точные глубины) |

---

## 2. (а) Что уже пробовали чинить в PCSS и чем кончилось

Работающие фиксы (закоммичены, user-verified):
- Normal-offset bias 0.05 (commit 2eef284, 2026-06-09) — НАСТОЯЩИЙ анти-acne фикс; перпендикулярный пространственный сдвиг receiver'а, без peter-panning.
- Texel floors, Ф1 distance-quality (commit 973d112, 2026-06-10): normal offset / PCSS penumbra флорятся на 1.5 texel карты, blocker search на 1 texel; размер texel из textureSize() — авто-адаптация к пресетам. Лечит возврат staircase и коллапс PCF в бинарный hard tap на дистанции. VL fast-таппы сознательно НЕ флорены.
- Spot gather-PCF, Ф2 (commit 9c19e47): irlite_spotGatherTap = textureGather 2×2 + step + билинейный микс четырёх БИНАРНЫХ результатов (filter-AFTER-compare), hard path + все PCF-тапы. Скоуп сознательно SPOT ONLY (point cube уже LINEAR compare-after-filter; cube-gather = per-face UV/sign математика ради маргинального выигрыша — скипнуто, опц. Ф2b).

Пробовали и ОТКАТИЛИ (не повторять):
- Slope-scaled depth bias (2026-06-09) — только меняет acne на peter-panning (depth bias = одноосевой пуш к свету). IRLITE_SHADOW_BIAS = ПЛОСКАЯ константа, не трогать.
- GL_FRONT second-depth cull на block-пассе — убирал bob-flicker acne, но peter-pan на толщину окклюдера; откачен в disableCull (проверенный режим IRLEngine).
- Slope-scaled glPolygonOffset на block-пассе — взрывался на grazing-углах cube-граней (глубина за far plane = теней нет вообще).
- Softness-сессия 2026-06-18 (пост-мортем, ВСЁ откачено, HEAD e925a07): (1) irlite_pointShadow приведён байт-в-байт к эталону IR_PointShadowSample (сняты texel-полы, texel-scaled offset, кламп penumbra) — квадрат ОСТАЛСЯ → PCSS-алгоритм исключён; (2) форс LINEAR через bindSamplerToUnit(unit, 0) в cube-array миксине — визуально ноль → фильтр исключён; (3) round-1 капы maxFilterNdc/maxFilterTan + drop-and-divide + угловой футпринт и round-2 feather по blockerFrac — жёсткий квадрат на 0.6, feather протащил его на 0.0. Два workflow-диагноза признаны НЕВЕРНЫМИ («out-of-domain PCF тапы = lit» и «binary blocker gate blockerCount==0→1.0» — оба механизма есть и в работающем эталоне).
- Вывод сессии: подозреваемый остался один — бейк (Java-содержимое cube-карт); следующий заход начинать с визуального заземления, а не с гипотез.

---

## 3. (б) Инварианты и контракты, которые нельзя ломать

5 инвариантов shadow-extraction (plan-irl-core-shadow-extraction; видны только in-world):
1. Порча матриц self-drawing кастерами → establishLightMatrices (условный re-establish) перед КАЖДЫМ emit. Расширение MAJOR-A: там же ре-пин depth/blend (depthMask true, depthTest on, blend off).
2. Static/dynamic-теги развязаны через oStatic[].
3. staticHash — avalanche-fold (порядконезависимая сумма хэшей model-block).
4. Изоляция упавшего кастера через mark/drain (один сбой не валит бейк/батч; в батч-режиме — флаш в одиночку).
5. Bounding-СФЕРА = описанный радиус на feet-center (не сырой AABB — MAJOR-B).

Контракт шва (заморожен, sign-off 2026-06-25): ShadowCasterSource (collect + emitOccluder) НЕ переоткрывается (spec: irl-core/docs/shadow-caster-seam-spec.md); доставка = ShadowEngine.install(source, config), source() fail-fast, config() → DEFAULTS; ShadowConfig = ровно 5 pull-геттеров (shadowQuality/shadowCache/shadowBakeBudget/shadowBlocks/shadowBlockRadius), DEFAULTS 1/true/4/true/24. Оркестрация (14 файлов) — одна копия в irl-core, пакет org.qualet.irl.light.shadow; кастеры per-mod. Core собирается НАИСТАРШИМ Loom потребителей (1.9); potребление modClientImplementation+include через mavenLocal, publishToMavenLocal ПЕРЕД сборкой модов; clean build после переездов (орфан-.class).

Контракт SSBO (addon-light-buffer-ssbo): binding 7, std430, header 16Б + 6×vec4 = 96Б на свет (вкл. cookie), MAX_LIGHTS 2048; инжект-GLSL зеркалит байт-в-байт. Тени в нём: vlParams.w = tile (spot 0..15) / layer (point), −1 = нет карты; guard `< 0.0` ДО int() (см. F1). cone.w = per-light bulbSize (override IRLITE_SHADOW_SIZE), cap range*0.5.

Контракт сэмплинга (shader-shadow-sampling):
- Имена сэмплеров ТОЧНЫЕ (биндит ProgramSamplersBuilderMixin по имени): irl_spotShadowAtlas (4×4 атлас, DEPTH32F, NEAREST, manual compare), irl_pointShadowArray (cube-map array, 16 кубов × 6 = 96 слоёв, DEPTH32F, LINEAR, seamless, COMPARE NONE).
- Реконструкция spot view-proj в шейдере обязана ТОЧНО совпадать с Java-бейком: lookAt + perspective(outerDeg, aspect 1, near 0.05, far=range), правило up-вектора |ld.y|>0.99 ? +Z : +Y. Любая правка матриц бейка = зеркальная правка GLSL.
- Point: dominant-axis perspective depth (НЕ euclid), near 0.05, far=radius.
- Любой невалидный сэмпл (за фрустумом / за near / нет карты) → return 1.0 (никогда не затемнять без валидной глубины).
- Ротация тапов spatial-only (без temporal-члена — нет шиммера; анти-паттерн = BetterLights с frameCounter-grain).
- VL fast-таппы (irlite_vlSpotStep/PointStep): аналитический shFY воспроизводит цепочку полной функции точно (cap 114.58865 = 1/tan(0.5°)); receiver-nudge передан алгебраически. Photon = absolute world, IterationRP = camera-relative — не перепутать при порте.
- IRLITE_COMPILE_SHADOWS gate: shadow-блок компилится в deferred4 И в c0_vl; блок обязан зависеть только от SSBO + 2 depth-сэмплеров.

Per-pack дельты: единый irlite_lights.glsl, per-pack различия ТОЛЬКО через #define (в т.ч. IRLITE_OUTLINE_TARGET, entity-метки per-pack); правки — только в Shadres/Modification (никогда в Original), потом sync в run + реген .irlpatch; версии GLSL #120 (BSL/Bliss) … #430 (IterationRP) — GL4-фичи доступны через расширения, но код общий. Багфикс в shared-логике = фиксить во всех инжектах.

Прочее нельзя: BBS-совместимость движка теней (motion-gate-overlay УДАЛЁН — pose-сигнатуры на внутренностях ванильного LivingEntity запрещены); point static-bake обязан рисовать world-блоки во все 6 граней (refuted-оптимизация); все 6 граней begin/clear/end даже при per-face cull кастеров (освободившаяся грань обязана чиститься — иначе фантомная тень); configure-per-frame оставить (дёшев/нужен).

---

## 4. (в) Перф-бюджеты и хотспоты

Бюджеты/лимиты:
- shadowBakeBudget = 4 полных статик-бейка/кадр (first-bake и leave-bake вне бюджета).
- MAX_OCCLUDERS = 32, COLLECT_DIST = 72 блока; shadowBlockRadius default 24 (4..96); STALE_FRAMES = 2.
- Ёмкость: 16 spot-тайлов (4×4) + 16 point-кубов (96 слоёв); лишние источники в кадре = -1 (без тени).
- VRAM пресетов LOW/MED/HIGH/ULTRA ≈ 40MiB/160MiB/640MiB/2.5GiB; статик-слой overlay УДВАИВАЕТ (лениво, только при первом overlay-бейке; ULTRA ~5ГиБ — не задокументировано юзеру).
- PCSS-тиры (per-pixel стоимость, главный рычаг в GPU-bound сценах): quality 0 = 1 tap; 1 = 6 blocker/10 PCF; 2 = 10/18 (default); 3 = 14/28; 4 = 20/40. Specular = hard fast path (IRLITE_SPECULAR_HARD).
- VL: IRLITE_VL_STEPS шагов марша × (при VL_SHADOWS) 1 depth-tap на шаг; early-out при transmittance<0.02; per-light константы hoisted из цикла.

Хотспоты:
- ОТКРЫТЫЙ №1: C10 спайк ~320ms — renderBlocksDepth × 6 граней без block-куллинга (см. P3).
- GPU-bound при десятках источников (per-fragment цикл по светам без кластеризации) — GPU-cull/LOD открыт (P14).
- Дальние тени: texelWorld растёт ~d (point d/256, spot d/886 на MEDIUM) — архитектурный потолок, лечится только разрешением (P7).

Уже сделано (не переделывать, база «система хорошо оптимизирована»): sticky tiles; two-layer static/dynamic bake + copyStaticToLive по face-маске (gpu-1/T1.1); per-light dirty-кэш; behind-camera / cone / per-face куллинг; квантованный BlockShadowCache + per-id блок-VBO; cutout-VBO (T2.3); батч-флаш кастеров 6N→1/проход (T2.2); sphere-exact инвалидация (T2.5); single GL snapshot per bake; бюджет бейков (T2.4). Инструменты: -Dirlite.profileShadows=true (avg/max ms, счётчики), FrameProfiler.

Файлы: C:/Users/Qualet/Documents/Project/Minecraft/BBS/bbs-irlights-addon/memory/{addon-shadows.md, shadow-distance-quality-plan.md, project-shadow-bake-perf-audit.md, fix-shadow-depthstate-repin.md, shader-shadow-sampling.md, shader-volumetric.md, ref-betterlights-shadow-comparison.md, photon-bugfix.md, plan-irl-core-shadow-extraction.md} и _archive/{project-shadow-softness-pcss-fix.md, shadow-entity-parity-findings.md, plan-shadow-bake-perf.md, plan-shadow-1211-fwdport-c10.md}.