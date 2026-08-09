# Отчёт: что ещё вынести в irl-core, чтобы он был полноценной библиотекой для аддона и редактора

## 1. Вердикт

Из 21 рассмотренного кандидата **19 подтверждены как жизнеспособные** (viable=true по правилу «выжил хотя бы у одной линзы после adversarial-опровержения») и **2 отвергнуты** (C6 GuideGeometry, C13 AbstractIrisPatcherHost). Но «жизнеспособный» ≠ «стоит делать»: у 8 кандидатов линза ценности вынесла отдельный вердикт `viable=false` (цена > выгоды), поэтому по совокупности линз чистых Tier-1 рекомендаций — 5, ещё 6 в Tier-2 (выгодно, но с блокерами), остальные — Tier-3 (спорно/низкий ROI).

Главный вывод о состоянии границы core/моды: **граница уже спроектирована хорошо** — патчер (`org.qualet.irl.patcher.*`), свет-SSBO (`LightBuffer`/`LightRegistry`) и MC-типизированная оркестрация теней (`org.qualet.irl.light.shadow.*`) физически живут ТОЛЬКО в ядре, а два per-mod шва (`PatcherHost`, `ShadowCasterSource`) — образцовые интерфейсы. Проверено: дубликатов `IrlPatch*`/`PatchEngine`/`ShadowBaker` в модах НЕТ, ядро реально BBS-free/ImGui-free (grep `mchorse.bbs`/`imgui`/`irlredactor` по `irl-core/src` пуст).

Тем не менее найдено **8 общих файлов аддон↔редактор** (не один `CookieArray`, как утверждала память), из которых `IrisShadersState` байт-идентичен, а mixin-пары различаются 0–15 строками. И — самое серьёзное — обнаружен **корневой инфраструктурный дефект, гейтящий почти все выносы**: редактор пинит `irl-core:1.0-obt` (`irlights/build.gradle:61-62`), а аддон — `1.1` (`bbs-irlights-addon/build.gradle:109-110`); jar `1.0-obt` физически не содержит 4 shadow-класса F1/F2-стека, из-за чего **редактор в текущем пине вообще не компилируется** (его миксины импортируют отсутствующие `PointShadowEvsm`/`Pyramid`/`SpotShadowEvsm`/`Pyramid`). Пока это не починено (C19), уптейк любого Java/enum-выноса редактором невозможен.

---

## 2. Подтверждённые кандидаты

| ID | Что | Подсистема | Effort | Tier |
|----|-----|-----------|--------|------|
| C19 | Фикс version-pin дрейфа ядра (editor stuck on 1.0-obt) + git-tag per publish | build-infra | S | 1 |
| C1 | `IrisShadersState` → core (байт-идентичный дубль) | iris | S | 1 |
| C4 | `FramePipeline` — тело per-frame оркестрации в core | frame | S | 1 |
| C8 | `LightMath` — spot-конус math (normalize + deg→cos) в core | light-model | S–M | 1 |
| C21 | Правка README/докстрингов ядра под реальную build-схему | build-infra | S | 1 |
| C12 | Типизированный `Outcome` enum на `PatchResult` вместо string-sniffing | patcher | S | 2 |
| C14 | `outputName`/`packMatchesTarget`/`norm` → core `Shaderpacks` (чинит потерю +DOF) | patcher | S | 2 |
| C2 | `CookieArray` — GL/decode-половина в core за `CookieSource`-швом | cookie | M | 2 |
| C3 | Iris sampler-bind: registry-половина в core (helper — отложить) | iris | S–L | 2 |
| C5 | `WorldBlockChangeMixin` (+ удалить мёртвый accessor редактора) | world-invalidation | S–M | 2 |
| C18 | `ShadowConfig.of(...)`-фабрика вместо двух shim-адаптеров | config | S | 2 |
| C11 | Единый источник `.irlights`-патчей в core | patcher-hosts | M | 3 |
| C15/C16 | Единый `irlite_lights.glsl` / SSBO-struct в core (C15 поглощается C16) | glsl-contract | L | 3 |
| C7 | `LightParams` value-object (унификация набора полей света) | light-model | M | 3 |
| C17 | `ShadowSettingSpec` (defaults + min/max + labels) в core | config | S | 3 |
| C9 | `BlockLightDefs` → core (editor-only таблица, задел под addon auto-light) | auto-light | S | 3 |
| C10 | `AutoLightScanner` → core (editor-only движок скана) | auto-light | M–L | 3 |
| C20 | Формализация per-MC split ядра | build-infra | M | 3 |

### Tier 1 — очевидная выгода, низкий риск

**C19 — версионирование ядра (делать ПЕРВЫМ, гейтит всё остальное).** Выносим: не код, а build-схему — общий `irl_core_version`-property в `irl-core/gradle.properties`, поднять editor-пин `1.0-obt → 1.1`, git-tag per publish. Evidence: аддон `irl-core:1.1` (`build.gradle:109-110`) vs редактор `1.0-obt` (`irlights/build.gradle:61-62`); прямая распаковка mavenLocal-jar показала **21 shadow-класс в 1.0-obt vs 25 в 1.1** (delta 4 = `Point/SpotShadowEvsm`+`Point/SpotShadowPyramid`); `git -C irl-core tag` пуст → `1.0-obt` невоспроизводим. **Ключевая находка value-линзы: это не «тихий рантайм-дрейф», а КОМПАЙЛ-БРЕЙК** — редакторские миксины `ProgramSamplersBuilderMixin.java:10-13` уже импортируют отсутствующие в 1.0-obt классы. Риск асимметричен-нулевой (бамп может только починить сломанную сборку). Блокер: бамп безопасен только пока оба потребителя на MC 1.20.4 (сейчас так). **Коррекция:** подпункт (c) `--refresh-dependencies` следует выкинуть — grep показал, что этой обвязки нет ни у одного мода; реальный баг — устаревшая hardcoded-строка версии, её чинят (a)+(b).

**C1 — `IrisShadersState` в core.** Выносим весь класс вербатим в `org.qualet.irl.light.iris.IrisShadersState` (public static `shadersDisabled()` + приватный `broken` fail-open latch). Evidence: тело строк 2–48 байт-идентично между модами, различие ТОЛЬКО package-строка + LF/CRLF (появлялся у 5 finder'ов независимо). Импортирует исключительно `net.irisshaders.iris.*` — ноль BBS/irlite/redactor. Шов/API: оба мода меняют import и удаляют копию; потребители — `GameRendererLightMixin` ×2 + editor `AutoLightManager.java:168`. **Блокер:** ядро сегодня Iris-free (grep `net.irisshaders` по `irl-core/src` пуст; `build.gradle:43-53` без Iris) → нужно добавить `modCompileOnly` на Iris, что перфорирует заявленную «Iris-free» позу (это ОДНОРАЗОВАЯ цена, оба потребителя уже парно несут Iris). Пакет `...light.iris` предпочтительнее `...light` — изолирует Iris-зависимый код. **Коррекция value-линзы:** benefit «unblocks AutoLightManager» завышен (тот лишь читает флаг); батчить с C3, чтобы Iris-dep вводился в ядро один раз.

**C4 — `FramePipeline` в core.** Выносим тело per-frame шва (dormant-gate → clear/uploadEmpty/onShadersDisabled; compute cameraPos/forward; collect via callback; `entityRenderDispatcher.configure`; `ShadowBaker.bake`; `LightRegistry.flush`) в `FramePipeline.frame(float, BooleanSupplier shadersDisabled, LightSource source, Runnable onDormant)`. Evidence: два `GameRendererLightMixin` совпадают вербатим кроме 4 точек — package, imports, editor-only `LightDriver.resetAutoShadowRamp()` (`editor:46-48`), collect-таргет (`addon:63` vs `editor:66`); общее тело >90% (~40/44 строк). Обе collect-сигнатуры дословно `(ClientWorld, Vec3d, float)`. Ядро УЖЕ MC-типизировано (`ShadowBaker.java:8-11,201`) → новой зависимости нет. **Критическая коррекция (единогласно у линз):** `shadersDisabled` держать `BooleanSupplier`, НЕ звать core `IrisShadersState` напрямую — иначе тащим Iris-dep в Iris-free ядро. `@Mixin(GameRenderer.class)` остаётся per-mod (тонкий делегат в 1 строку).

**C8 — `LightMath` в core.** Выносим spot-конус математику: `normalizeDir(d3, fbx,fby,fbz)` + `coneCosHalf(deg) = cos(rad(deg*0.5))` + `inner=min(inner,outer)`. Evidence: логика байт-эквивалентна в `LightCollector.emitSpot:370-382` и `LightDriver.emitSpot:130-149`; комментарии редактора буквально: `matches LightCollector`, `exactly as IRLite did`. Чистая float-арифметика, ноль MC/BBS/Iris. **Коррекции (усиливают кандидата):** копий больше заявленного — finder'ы нашли **3 копии cone-cos** (пропущена 4-я в аддоне `SpotlightFormRenderer.java:91-93`) и **6 копий dir-normalize с ТРЕМЯ разными fallback** ((0,0,1) в драйверах; (0,-1,0) в `LightGuideRenderer.java:123` и `LightEditorPanel.orientationFromDir:744-748`; ОТСУТСТВИЕ fallback в `LightEditorPanel:725-730` — латентный баг). **Разбить API на две части:** `coneCosHalf` — pure, выносить смело; `normalizeDir` — ПОВЕДЕНЧЕСКИЙ (унификация fallback меняет визуальный дефолт конуса при вырожденном dir), выносить с ин-гейм re-verify гайдов. Effort реалистичнее M.

**C21 — правка докстрингов ядра.** Чистая документация: `irl-core/README.md` и header `build-trilogy.ps1` описывают несуществующую схему. Evidence: README.md:7,24 «zero Minecraft, zero Loom» ЛОЖНО (`build.gradle:13-14` fabric-loom, `:46-47` minecraft:1.20.4+yarn, 8 shadow-классов с `import net.minecraft`); README.md:41-51 `includeBuild` мёртв (оба `settings.gradle` явно ретайрят composite); badge `:11` = 1.0-obt при `version='1.1'`; `build-trilogy.ps1:10-12` противоречит `build-bbs-pack.ps1:168-188`. **Коррекции:** число «26 MC-typed shadow classes» завышено — фактически 20 файлов в пакете, из них 8 напрямую тянут `net.minecraft`; badge не хардкодить (placeholder `<v>`), т.к. зависит от C19; `build-trilogy.ps1` — untracked локальный скрипт (BBS-root не git-репо), поэтому основная ценность — README (в git-репо ядра, виден потребителям).

### Tier 2 — выгодно, но есть блокеры

**C12 — типизированный `Outcome` на `PatchResult`.** Оба UI дублируют идентичную string-sniffing классификацию по подстрокам англ-summary (`UIPatcherSection.java:336-358`, `PatcherPanel.java:430-452` — те же подстроки в том же порядке). Весь затрагиваемый код уже в `org.qualet.irl.patcher`, enum locale-нейтрален. **Коррекции:** `PatchException` — private nested в `IrlPatchApplier` (`:309-315`), ловится в том же файле → не летит наружу, блокер мнимый; расширить enum (добавить `TARGET_NOT_FOUND`/`READ_ERROR`/`ANCHOR_AMBIGUOUS`/`ANCHOR_NOT_FOUND` из `PatchEngine.java:98/109/142/151` — сейчас все схлопываются в `NO_FIT`); `already_patched` рождается в `PatchEngine`, outcome ставить на самой ошибке, а не парсить агрегат `report():221`. Блокер: гейтится C19 (editor на 1.0-obt enum не увидит).

**C14 — helpers в core `Shaderpacks`.** `norm()` и `packMatchesTarget()` байт-идентичны (diff rc=0); `outputName()` **уже дрейфнул как шипнутый баг**: editor `PatcherPanel.java:363` — сигнатура `outputName(String packName)` вообще без `IrlPatch`, поэтому DoF-combo паки теряют `+DOF`-суффикс, который аддон добавляет (`UIPatcherSection.java:301`). Дрейф доказан коммит-историей (аддон fd1b262 2026-07-01; редактор застыл на c666d22 2026-06-21). Все методы чисто-строковые/NIO, `Shaderpacks` уже в ядре с `dir()/list()/packPath()`, оба UI его уже импортируют. **Коррекция:** описание «оба static, разница только +DOF» неточно — editor-метод инстанс с `newPackEachTime` ImBoolean, addon static с `createNew`-полем; параметр `createNew` в API это нормализует. Блокер: uptake редактором гейтится C19.

**C2 — `CookieArray` GL/decode-половина в core.** GL upload с PBO/UNPACK-guard (чинил реальный `EXCEPTION_ACCESS_VIOLATION`) байт-идентичен (`addon:200-236` vs `editor:193-231`); `init()`, decode/resample, `RES=512`, `delete()` совпадают. Прецедент железный: `PointShadowPyramid` и др. — уже core GL_TEXTURE_2D_ARRAY-классы с тем же `getGlTextureId()`. **Предпочесть ALT/surgical:** вынести `CookieArrayBase{RES, getGlTextureId, protected uploadLayer(ByteBuffer,int) с PBO-guard, ensureInit, delete}` + pure `decode()`-helper; каждый мод держит СВОЙ подкласс с `resolve()/cache/source`. Причина: полный `resolve(String, CookieSource)` с фиксированным `MAX_LAYERS=32+LRU` **молча изменил бы поведение редактора** (у него 16+без эвикции). **Найден латентный баг редактора:** на полном массиве кэширует `-1` навсегда → куки, впервые появившийся при полном массиве, помнится «сломанным» даже после освобождения (у редактора эвикции нет вообще) — сам по себе аргумент за единый core-impl. Блокеры: BBS `Link`/`BBSMod` не должны попасть в ядро (решается швом); cap/eviction — осознанный product-choice, не хардкодить.

**C3 — Iris sampler-bind: registry-половина.** `SamplerBindingCubeArrayMixin` различается РОВНО 1 строкой (CookieArray import), тело `bindCubeArrayInsteadOf2D` + `@Shadow`-поля байт-идентичны; `ProgramSamplersBuilderMixin` — import + коммент-дрейф, все 7 `addDynamicSampler`-вызовов идентичны; 6 из 7 таргетов уже core-static. **Разбить на 2 уровня:** (1) БЕЗОПАСНО СЕЙЧАС — plain-registry `IrlSamplers{register(name,IntSupplier,glTarget), forEach, glTargetFor}` (только `java.util.function`+int, БЕЗ Iris-типов) устраняет 7 хардкод-веток; (2) helper-половина (`bindAll`/`tryRebind`) держит Iris-internal типы (`ProgramSamplers.Builder`, `IrisRenderSystem`) → ждёт C1 (modCompileOnly Iris). ALT full-lift миксинов в core ОТКЛОНИТЬ (требует первого mixins.json в ядре). Value-линза дала `viable=false` (дрейф застыл после MSM4, тела byte-identical, ~40 строк не окупают ввод Iris в ядро), но duplication/coupling подтверждают registry-часть.

**C5 — `WorldBlockChangeMixin` (+ удалить мёртвый accessor).** `WorldBlockChangeMixin` различается package + 2-строчным комментом, тело `if(isClient) BlockShadowCache.invalidateAt(pos)` идентично; `BlockShadowCache.invalidateAt` уже в core. `WorldBlockEntityTickersAccessor` редактора **МЁРТВ** (зарегистрирован `irl-redactor.client.mixins.json:12`, grep по `irlights/src` находит только объявление). **Рекомендация (единогласно у coupling/dup, value дал viable=false):** приоритет — SMALLER SAFE VARIANT: **удалить мёртвый accessor редактора (чистый локальный win, effort S, без core)**; сам вынос mixin в core требует ПЕРВОГО mixins.json в ядре (fabric.mod.json без `mixins`-ключа) — отложить, сложить бесплатным райдером если core обзаведётся mixins.json по более сильной причине.

**C18 — `ShadowConfig.of(...)`-фабрика.** Оба мода пишут одинаковый shim из 5 @Override-геттеров-делегатов (`IrliteShadowConfig.java:19-23`, `LightConfig.java:16-23`), 5/5 тел идентичны. Прецедент в самом файле: `ShadowConfig.DEFAULTS` — анонимная реализация. **Коррекция (важно):** позиционная сигнатура `of(IntSupplier,BooleanSupplier,IntSupplier,BooleanSupplier,IntSupplier)` footgun-prone (тихая перестановка bake/blocks/radius, компилятор не поймает). Value-линза: `viable=false` — код ~10 строк, оба shim + интерфейс = 1 коммит каждый (никогда не менялись), стиль автора = именованные impl'ы; делать только райдером к уже-запланированному core-релизу.

### Tier 3 — спорно, на усмотрение автора

**C11 — единый источник `.irlights` в core.** Дрейф РЕАЛЕН (photon addon 88616B vs editor 48714B; EOL-нормализованный diff: общность 55–87%), rethinkingvoxels только в аддоне. НО **предусловие — сначала свести разошедшиеся тела GLSL в один канон (C16)**, иначе перенос директории лишь замораживает дрейф в новом месте. Гейтится C19 + требует правки билда ОБОИХ модов + `iterationrp.irlights` gitignored в обоих. Value-линза `viable=false`: дрейф editor патчей НАМЕРЕННЫЙ (директива fix-only-main, editor patches заморожены 2 коммитами) → единый источник навязал бы editor нежеланный стек.

**C15/C16 — единый `irlite_lights.glsl`/SSBO-struct в core.** C15 (только SSBO-struct блок) **поглощается C16** (весь файл) — рекомендация оркестратору слить. SSBO-struct: 17 байт-идентичных копий (md5 `057be7a...`) + IterationRP-вариант отличается 2 комментами (layout идентичен — это НЕ C15-дрейф). Активный addon↔editor дрейф ТЕЛА: solas addon 1413 стр vs editor 1020, diff 697 — editor не имеет F1/F2 стека (`irlite_chebyshev`/`msmHamburger`/PYRAMID/EVSM). Блокеры тяжёлые: DSL без `@include` (`IrlPatchParser.java:125-130` инлайнит вербатим); editor не имеет gen-скриптов вообще (у addon 6, у editor 0); 4 плейсхолдеров мало — расхождение в самой shading-математике (depth-reconstruct, gamma, guard-macro). Value: `viable=false` — это PROCESS-gap (editor лишён gen-пайплайна), решается дешевле ре-синком editor .irlights из addon-вывода без core-API. **Важная коррекция памяти:** editor Java-рантайм УЖЕ биндит весь стек (`ProgramSamplersBuilderMixin` импортирует все EVSM/Pyramid) — отстаёт ТОЛЬКО GLSL-тело, а не «сознательный lightweight-профиль».

**C7 — `LightParams` value-object.** Набор полей дублируется ТРИЖДЫ с совпадающими дефолтами (`PlacedLight:41-60`, `PointLightForm`/`SpotlightForm`, `LightStore.Dto`); подтверждён дрейф единиц cookieRotation (form DEGREES vs PlacedLight RADIANS). НО главный провал: формы ОБЯЗАНЫ остаться BBS `Value*` (кейфрейм-анимация) → `LightParams` НЕ удаляет дубль полей аддона, лишь добавляет маппинг-слой + 3-й источник дефолтов; диапазоны слайдеров непортируемы (blocks vs degrees); `register(LightParams)` вводит per-frame аллокацию в горячем цикле до 2048 источников. **Реальный дешёвый win без ядра:** слить `Dto` в `PlacedLight` локально в репо редактора + 1-строчный фикс единиц cookieRotation. Value `viable=false`.

**C17 — `ShadowSettingSpec` в core.** Defaults 1/true/4/true/24 дублированы трижды, ranges дважды. НО headline-benefit — миф: **MED vs MEDIUM дрейф НАМЕРЕННЫЙ** (`LightEditorPanel:579` считает `segW=avail/4` — «MEDIUM» не влезает в узкую сегмент-кнопку). Budget-range single-site (у редактора нет budget-виджета), quality-range редактора уже array-derived. Constants никогда не менялись. Value `viable=false`; если делать — только defaults-часть (не labels) райдером к плановому релизу.

**C9 — `BlockLightDefs` в core.** Чистый move-for-reuse (файл только у редактора, 185 строк, только vanilla MC-типы). Coupling/dup подтверждают тривиальность (ядро уже импортирует `net.minecraft.block.BlockState`). НО value `viable=false`: benefit «unlocks addon auto-light for free» иллюзорен — переносится только ТАБЛИЦА (10%), движок `AutoLightManager` (426 строк) editor-bound; git log = 1 коммит за всю историю (нулевой дрейф); аддон не показывает ни следа желания auto-light.

**C10 — `AutoLightScanner` в core.** Editor-only амортизированный скан. Coupling/dup подтверждают: 4 блокера обходятся швами (`Sink`/`Params`), зависит от C1+C9. Value `viable=false`: второго потребителя нет и не запланирован; `proposed_core_api` требует рерайта static-синглтона (~20 static-полей) в instance-класс; заявленная perf-выгода ~12ms архитектурно завязана на переиспользование инстансов `PlacedLight`, которое нейтральный `Sink` ломает; premature abstraction.

**C20 — формализация per-MC split.** Coupling/dup подтверждают факты (ядро hardcode 1.20.4, addon имеет mcVersions-карту, ядро не имеет). Value `viable=false`: **премиса ложна** — editor port/1.21.x ветки НЕ заблокированы, они уже несут собственное 10-файловое shadow-дерево (`org.qualet.irlredactor.light.shadow.*`) и потребляют core только для MC-нейтральных `LightBuffer`/`LightRegistry`/patcher. Параметризация заменила бы рабочий self-contained форк на кросс-репо per-MC-классифайер-coupling.

---

## 3. Отвергнутые кандидаты

| ID | Кандидат | Почему линзы убили |
|----|----------|--------------------|
| C6 | `GuideGeometry` — вынос wireframe-примитивов (ring/cone-cap/fat-line/cross) + cone-trig | duplication+value=false. Два `LightGuideRenderer` — ОСОЗНАННЫЙ форк, не copy-paste: расходятся с 1-й строки (TRIANGLES via BBS `Draw.fillBoxTo` vs DEBUG_LINES; per-form FormRenderer vs `WorldRenderEvents.LAST`-scene loop; 48 vs 20 сегментов; local vs world space). Реально общего — ~1 строка cone-trig `tan(rad(angle*0.5))*range`; 3 из 4 предложенных эмиттеров — single-consumer. Чтобы обслужить оба, API обязан нести И triangle-, И line-mode. Цена (реимплементация `fillBoxTo`/`Axis` в core + переписывание двух стабильных файлов + public-изация `spotRingZ` с cross-repo вызовом) >> выгоды (дедуп 1 строки в почти-неизменяемых файлах). |
| C13 | `AbstractIrisPatcherHost` — свернуть Iris/Fabric-boilerplate host'ов в core-базу | coupling+value=false (duplication=true, но перевешено). `gameDir/shaderpacksDir/listShaderpacks` байт-идентичны (33 строки, diff пуст), НО методы зовут `net.irisshaders.iris.Iris.*` → core-база форсирует ПЕРВЫЙ Iris-dep в ядре, прокалывая единственный документированно-чистый Iris-free шов патчера (`PatcherHost.java:11` «pure Java, no Minecraft, no Iris, no BBS»). Инфра guarded-модуля ОТСУТСТВУЕТ (ядро = один root-проект, второго sourceset нет — создавать с нуля, не S). Iris-пины дрейфуют (addon мультиверсионный vs editor хардкод). Git: выносимое тело не менялось НИ РАЗУ. Цена (прокол Iris-барьера + инфра guarded-модуля с нуля + синхро-релиз 3 репо) > 33 строк кросс-репо. |

---

## 4. Здоровье irl-core как библиотеки

### Положительное
- **Швы образцовые.** `PatcherHost` (Path/List/InputStream, ноль MC/BBS-типов) и `ShadowCasterSource` (frozen 2-method seam, документирован + 5 инвариантов) — fail-fast holder-паттерн (`Patcher.install`/`ShadowEngine.install`), `DEFAULTS`-фолбэк у `ShadowConfig`. Ядро реально BBS-free/ImGui-free (grep пуст).
- **Config-слой уже хорошо вынесен.** `applyFromSetting` + инвалидация кэша — единственный экземпляр в `ShadowBaker.java:241-246`, НЕ дублирован; разрешения shadow-map тоже не дублируются. Дублируются только числовые defaults/ranges/labels (C17).

### API-гигиена (проблемы)
- **Телескопические сигнатуры.** `LightRegistry.registerSpot` — 24 позиционных float-аргумента (`LightRegistry.java:78`), `LightBuffer.addSpot` — 23 (`:99`). Крайне хрупко к порядку, оба мода вызывают руками. Главный аргумент за `LightParams`/builder (но это рефактор API, не дедуп — C7).
- **Хардкод имени мода в нейтральном слое.** `PatchEngine.MARKER_FILE="irlite_patched.txt"` (`:30`), `IrlPatch.marker` default `"IRLITE"` (`:49`), DSL `@irlite`, расширение `.irlights`, sysprop `irlite.profileShadows` (`ShadowBaker.java:183`), лог-теги `[irlite]`. Течёт имя одного из двух потребителей в общий слой. При реальном ребрендинге в «IRLights» — привести к нейтральному (затрагивает формат marker-файла → совместимость пропатченных паков).
- **Стейл-комментарии контракта.** `LightBuffer.java:38` «16 + 2048*80 ≈ 164 KB» — устарел (было 5 vec4/80B до cookie; реально `LIGHT_BYTES=96`, ~192 KB). `LightRegistry.java:11` и editor `LightConfig.java:72` пишут «256» при реальном `MAX_LIGHTS=2048`. std430-контракт — единственный публичный контракт, который GLSL обязан зеркалить; стейл-доки тут особенно вредны.
- **Утечка типобезопасности в shadow-шве.** `ShadowCasterSource.emitOccluder(Object caster, int type, ...)` — caster как `Object`, тип каста как «голый» int (`CasterType.ENTITY/MODEL_BLOCK/REPLAY` не enum). Осознанный allocation-free дизайн (документирован), но компилятор не поймает перепутанный ENTITY/MODEL_BLOCK → тихо не тот draw-arm. Известный trade-off.

### Глобальный стейт
- **Практически ВСЁ ядро — static-only синглтоны** с приватными static-полями: `LightBuffer`/`LightRegistry` (десятки параллельных primitive-массивов на MAX=2048), `ShadowBaker`, все 4 `Pyramid/Evsm`, `SpotlightDepthAtlas/PointShadowArray`, `IRLShadowQuality.current`. НЕ баг при текущем деплое (addon и editor — разные MC-инстансы, каждый JiJ-ит свою копию ядра → «два потребителя в одной JVM» физически не возникает). НО как библиотека: полностью не-реентерабельно, нельзя два независимых light-контекста, нельзя тестировать в изоляции. Латентный смелл; критичен только при in-JVM co-load (напр. Flashback-аддон в том же клиенте).
- **`LightRegistry.slot()` — линейный поиск O(count)** по identity на каждую регистрацию (`:99-119`); при 2048 источников + auto-lights → O(n²) на кадр в редакторе. Узкое место API, задетое сбором.
- **`PatchLibrary.extracted`** (`:23` private static volatile) — процесс-глобальный latch, НЕ per-host: первый `dir()` ставит `extracted=true`, второй хост молча пропускает extractBundled. Баг ЛАТЕНТНЫЙ, не живой: editor `fabric.mod.json` объявляет `breaks{bbs:*}` → два хоста в одной JVM невозможны + каждый мод JiJ-ит свою копию ядра (стейт даже не расшарен). При появлении 3-го хоста в одной JVM — вынести в `Set<Path> alreadyExtractedDirs`.

### Версионирование контрактов
- `CONTRACT_VERSION=1` (`IrlPatch.java:18`) проверяется в `IrlPatchApplier.checkContract:201-208` против `@irlite N` — корректный гейт. **ДЫРА:** сам std430-layout существует в ДВУХ местах (Java `LightBuffer.addPoint/addSpot` + GLSL-патчи), но `CONTRACT_VERSION` НЕ привязан к раскладке — сменил порядок полей в `LightBuffer` → версия не бампнется автоматически (ручная дисциплина). Плюс патчи БЕЗ `@irlite` (`irliteVersion==0`) проходят всегда. Нет golden-теста соответствия Java↔GLSL; рассинхрон = тихий мусор в SSBO без compile-error.
- **Версии ядра дрейфуют между потребителями** (корневая проблема, см. C19): addon `1.1` vs editor `1.0-obt` → правка контракта в ядре видна модам в разное время.

---

## 5. Поправки к памяти проекта

| Claim (из MEMORY.md/контекста) | Вердикт | Evidence |
|---|---|---|
| «единственный недовынесенный шов = CookieArray» (project-irl-sync-strategy) | НЕВЕРНО | `comm -12` по basename дал **8 общих файлов**: CookieArray, IrisShadersState (БАЙТ-идентичен), LightGuideRenderer, GameRendererLightMixin, WorldBlockChangeMixin, WorldBlockEntityTickersAccessor, ProgramSamplersBuilderMixin, SamplerBindingCubeArrayMixin |
| Две копии `CookieArray` — функционально эквивалентные форки | НЕВЕРНО | Разошлись поведенчески: addon `MAX_LAYERS=32`+LRU+failed-set, editor `MAX_LAYERS=16`, БЕЗ эвикции, кэширует `-1` на полном массиве навсегда (латентный баг) |
| irl-core — ядро с шейдер-интеграцией; Iris-биндинг частично там | НЕВЕРНО на сегодня | grep `net.irisshaders` по `irl-core/src` пуст; `build.gradle:43-53` без Iris; `fabric.mod.json` без `mixins`; 0 `@Mixin` в ядре. Вся Iris-интеграция per-mod |
| Дрейф аддон↔редактор ограничен CookieArray-швом | НЕВЕРНО для GLSL | Общий `irlite_lights.glsl` разошёлся на 697 строк (addon solas 1413 vs editor 1020); в editor отсутствует весь F1/F2 стек фильтрации + VL_NOISE + богатый OUTLINE |
| Расхождение redactor↔addon патчей — только по rethinkingvoxels | НЕВЕРНО (сильно недооценено) | ВСЕ 6 одноимённых .irlights различаются на десятки КБ: photon 88616 vs 48714B, bsl 85162 vs 57589, solas 94117 vs 61515, и т.д. |
| Оба мода потребляют одну версию irl-core через mavenLocal | НЕВЕРНО | addon `irl-core:1.1` (`build.gradle:109-110`) vs editor `1.0-obt` (`irlights/build.gradle:61-62`); jar 1.0-obt = 21 shadow-класс vs 25 в 1.1 (нет F1/F2 стека) → **editor в пине 1.0-obt не компилируется** |
| README/докстринги: ядро plain-Java, zero MC, zero Loom, composite includeBuild | НЕВЕРНО (стейл post-Ф2) | `build.gradle:44-47` minecraft:1.20.4+yarn; 20 MC-typed shadow-классов; оба `settings.gradle:11-15` «composite includeBuild no longer works» |
| Java-классы патчера дублируются в редакторе (кандидаты на дубли) | НЕВЕРНО для рабочего дерева | В `irlights/src/` только `RedactorPatcherHost`; дубли `IrlPatch*` только в git-worktree `.claude/worktrees/` (не рабочий код). Движок уже общий |
| auto-block-lights — фича трилогии (в irl-core) | ЧАСТИЧНО НЕВЕРНО | Реализовано ТОЛЬКО у редактора (`AutoLightManager`+`BlockLightDefs`); в аддоне нуль полей `autoLight*`, нуль скана блоков. Механика в редакторе, не в ядре; завязана на editor `PlacedLight`/`LightConfig` |
| `LightGuideRenderer.java` — дублированная пара (кандидат на дедуп) | ВВОДИТ В ЗАБЛУЖДЕНИЕ | Одноимённые, но НЕ дубли — независимые осознанные форки (TRIANGLES vs DEBUG_LINES; FormRenderer callback vs WorldRenderEvents scene-loop). Doc-comment редактора: «BBS-free stand-in for IRLite's dropped LightGuideRenderer» |
| `iterationrp.irlights` gitignored только в аддоне | НЕВЕРНО — симметрично | addon `.gitignore:134`, editor `.gitignore:30` — оба прячут |
| «@dof знает только irl-core, не старый tools/build» (риск: подразумевает второй патчер в tools/) | ВВОДИТ В ЗАБЛУЖДЕНИЕ | `irl-core/tools/` = ОДИН файл `verify-shadow-lockstep.py` (retired stub, exit 0). Второго патчера под tools/ нет; `@dof` парсит только `IrlPatchParser.java:117` |
| MAX_LIGHTS=2048, SSBO 96B/6×vec4, binding 7 | ПОДТВЕРЖДЕНО | `LightBuffer.java:39/32/41/42`; GLSL `bliss.irlights:87-99` зеркалит. НИТ: стейл-комменты «256»/«80B» (см. §4) |
| Оркестрация теней в irl-core, шов ShadowCasterSource | ПОДТВЕРЖДЕНО | `ShadowBaker` импортирует MC-типы; `ShadowCasterSource` — 2-метод интерфейс; impl'ы per-mod (`IRLiteBbsCasterSource`/`RedactorEntityCasterSource`) |
| Универс-jar отменён, ядро per-MC, только для 1.20.4 | ПОДТВЕРЖДЕНО | `build.gradle` header + `minecraft:1.20.4`+`yarn:1.20.4+build.1`, `version=1.1` |

---

## 6. Рекомендуемый порядок вывоза

Зависимости между кандидатами: **C19 гейтит уптейк редактором всех Java/enum-выносов** (C1, C3, C4, C8, C12, C14, C18); **C1 гейтит helper-часть C3** (Iris-dep в ядро); **C16 — предусловие C11**; **C9+C1 — предусловие C10**.

**Фаза 0 — разблокировка (обязательна первой).**
1. **C19** — поднять editor-пин `1.0-obt → 1.1` + общий `irl_core_version`-property + git-tag. Без этого редактор не компилируется и не увидит ни одного выноса. Effort S, риск нулевой.
2. **C21** — переписать README/докстринги ядра под реальную схему (независимо, но естественно рядом с C19, т.к. badge-версия завязана). Effort S.

**Фаза 1 — дешёвые чистые выносы (после C19).**
3. **C1** `IrisShadersState` — вводит `modCompileOnly` Iris в ядро (одноразовая цена; батчить с C3-registry в один core-bump).
4. **C4** `FramePipeline` — тонкий делегат, MC-типизирован, `BooleanSupplier`-шов.
5. **C8** `coneCosHalf` (pure-часть) — сразу; `normalizeDir` (поведенческая) — отдельно с ин-гейм re-verify гайдов.
6. **C14** helpers в `Shaderpacks` — чинит шипнутый +DOF-баг попутно.
7. **C12** `Outcome` enum на `PatchResult` — расширить до 10 значений.

**Фаза 1.5 — локальные winы БЕЗ ядра (параллельно, не гейтятся ничем).**
8. Удалить мёртвый `WorldBlockEntityTickersAccessor` редактора (часть C5-safe-variant).
9. Слить `LightStore.Dto` в `PlacedLight` локально в репо редактора + 1-строчный фикс единиц cookieRotation (дешёвая часть C7).

**Фаза 2 — среднее (после C1).**
10. **C3 registry-половина** (`IrlSamplers`) — сразу после C1; helper-половина (`bindAll`/`tryRebind`) — тогда же, раз Iris-dep уже введён.
11. **C2 `CookieArrayBase`** (surgical ALT) — консолидирует crash-prone PBO/UNPACK upload без изменения поведения модов.
12. **C18** `ShadowConfig.of` — только райдером к уже-идущему core-релизу этой фазы (не отдельным bump'ом), с именованными параметрами не позиционными.

**Фаза 3 — тяжёлое/спорное (по явному решению автора).**
13. **C16** (единый `irlite_lights.glsl` шаблон) — предусловие для C11; но сначала решить PROCESS-вопрос (дать редактору gen-пайплайн ИЛИ ре-синкнуть editor .irlights из addon-вывода) — возможно дешевле без core-API.
14. **C11** (единый источник .irlights) — только после сведения тел (C16) и решения по намеренному editor-заморозку.
15. **C15** — НЕ отдельно, слить в C16 (struct — часть файла).
16. **C9→C10** (`BlockLightDefs`→`AutoLightScanner`) — только если/когда аддон реально начнёт делать auto-light; сейчас нет второго потребителя.
17. **C7** (полный `LightParams`), **C17** (`ShadowSettingSpec` labels), **C20** (per-MC split) — низкий ROI, делать только при явной необходимости.

---

## 7. Что расследование не покрыло

- **ПРОПУЩЕННЫХ КАНДИДАТОВ НА ВЫНОС НЕ НАЙДЕНО.** Пройден весь файловый инвентарь (addon 40 .java + 4 ресурса; editor 33 .java + 14 ресурсов; core 31 .java), каждый файл сверен против списков. Лично проверены и обоснованно НЕ-кандидаты: `FilmsAccessor.java` (BBS-специфичен, у редактора твина нет), весь ImGui-слой редактора (`Widgets`/`EditorStyle`/`ImGuiInput`/`ImGuiRuntime`/`Lang`/`LightState`/`LightSync`/`LightEditorScreen`/`LightEditorPanel`/`PatcherPanel` — ImGui-only, у addon твина нет по построению), `minecraft.ttf` (editor-only). Плюс: `IRLRedactorClient.worldKey()`+sanitize (editor-only персист), color-hex packing (в addon = стенсил-индекс, в editor = ImGui-палитра — разные назначения), install-последовательность (намеренная передача per-mod impl'ов в вынесенные швы, не дубль-тело), `LightStore` (editor-only JSON-персист).

- **API-РЕВЬЮ КАЧЕСТВА самого irl-core как библиотеки** (вторичная цель задачи) почти не отражено в списке кандидатов, кроме C19–C21 (версионирование/per-MC/README). НЕ проанализированы как API-поверхность для полноценного рефакторинга (это отдельная тема от «вынести дубль»): (1) `LightRegistry` — статический синглтон с 24 параллельными primitive-массивами и `registerPoint`/`registerSpot` на 15–24 позиционных аргумента — тяжёлый хрупкий контракт, кандидат на builder/record; (2) `LightBuffer`/`LightRegistry`/`ShadowBaker` — все static-only (глобальное изменяемое состояние), что делает ядро непригодным для >1 инстанса пайплайна и неявно требует single-threaded вызова. Это предмет отдельного API-ревью-агента, флагуется как непокрытая зона.

- **Ин-гейм/рантайм-верификация** не проводилась (расследование чисто статическое по коду). Поведенческие выносы (C8 `normalizeDir` fallback-унификация; любой GLSL-вынос C16) требуют отдельного ин-гейм re-verify перед мержем — особенно визуальный дефолт конуса гайдов при вырожденном dir и весь shadow/VL-стек (только что стабилизирован 108-агентным ревью).