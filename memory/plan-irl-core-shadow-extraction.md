---
name: plan-irl-core-shadow-extraction
description: "КАНОН теней. Ф2 ВЫПОЛНЕНА 2026-06-25 (тени физически в irl-core, оба мода build-зелёные на 1.20.4). Оркестрация теней (14 файлов) вынесена в per-version Loom-ядро irl-core; шов ShadowCasterSource + 5 инвариантов + per-mod casters. Осталось: Ф3 in-world (фактически подтверждён) + Ф4 тиражирование на порт-ветки (открыто; per-version core пока только на 1.20.4). Контекст Ф2: [[project-irl-sync-strategy]]. Карта: [[reference-edit-routing-by-area]]. Репо: [[project-github-repos]]."
metadata:
  node_type: memory
  type: project
  originSessionId: PLACEHOLDER
---

Вынос движка теней в irl-core (per-version Loom-ядро). Цель пользователя: сделать irl-core полноценным движком-бэкендом, чтобы редактор и аддон были тонкими обёртками (GUI + интеграция с хостом). Свет + патчер УЖЕ в core. Этот план = тени.

КАНОН: архитектура теней + контракт шва (survivor-сводка)
Эта заметка = единственный выживший источник по архитектуре/шву теней. Вся «shadow seam»-сага (Ф0–Ф4) СВЁРНУТА сюда; детали-фазы ниже.
- ГДЕ ЖИВУТ ТЕНИ: оркестрация (14 файлов) физически в irl-core, пакет org.qualet.irl.light.shadow. Core = per-version Loom-модуль, собирается НАИСТАРШИМ Loom (1.9) среди потребителей (newer-check Loom односторонний: бьёт только когда артефакт новее потребителя). Потребление = modClientImplementation + include (JiJ) через mavenLocal; per-MC core требует publishToMavenLocal ПЕРЕД сборкой модов. Ф2 done 2026-06-25.
- КОНТРАКТ ШВА: единственная BBS-связь спрятана за интерфейсом ShadowCasterSource (collect + emitOccluder). Оркестрация общая; КАСТ per-mod. Канон = redactor-main (1.20.4). Per-mod касты: RedactorEntityCasterSource (vanilla EntityRenderDispatcher) / IRLiteBbsCasterSource (BBS Form/Film/Morph). Spec: irl-core/docs/shadow-caster-seam-spec.md. (Доставка config/source — через holder ShadowEngine.install(source,config) + интерфейс ShadowConfig, см. Ф1 ниже.)
- 5 ИНВАРИАНТОВ (видны только in-world): (1) порча матриц -> условный re-establish (establishLightMatrices перед каждым emit); (2) static/dynamic-теги развязаны через oStatic[]; (3) staticHash avalanche-fold; (4) изоляция упавшего кастера через mark/drain; (5) bounding-sphere = описанный радиус.
- ИЗВЕСТНЫЕ БАГИ: MAJOR-A — self-drawing формы портят modelview/depth -> ре-пин depth/blend/матриц перед каждым emit (см. [[fix-shadow-depthstate-repin]]); MAJOR-B — feet-pivot AABB недооценивает габарит при повороте -> эмитить bounding-СФЕРУ на feet-center через sink.emit (не сырой AABB). INV-2 пока консервативен (isStatic=false везде; static-кэш всё равно работает на блоках).
- ИСТОРИЯ (1 строка): seam-рефактор Ф0–Ф4 (вкл. Ф4 option C+ lockstep-верификатор verify-shadow-lockstep.py) -> СУПЕРСЕДЕД этой физической экстракцией; lockstep-верификатор RETIRED.

Постановка и пересмотр прежнего решения
Перенос теней в core — это ровно option A (общий shadow-модуль), которую отклонили 2026-06-17 (см. archived project-shadow-seam-phase4-lockstep) в пользу lockstep-верификатора (option C+). Тогда против было 3 причины:
1. Loom 1.9 (аддон) <-> Loom 1.15 (редактор) composite-трение.
2. redactor-main не на irl-core LightRegistry. -> СНЯТО (миграция сделана; подтверждено: ShadowBaker редактора импортит org.qualet.irl.light.LightRegistry, своих копий LightRegistry/LightBuffer у редактора НЕТ).
3. MC-типы не лезут в plain-Java core. -> СНИМАЕТСЯ через per-version: ядро на каждую версию = Loom-модуль под свою MC, MC-типы в нём легальны.
Ключевой инсайт юзера: версионное ядро убивает причину №1 — в рамках одной версии все на одном Loom, Loom<->Loom между версиями не возникает (они в разных артефактах/ветках). Плюс 14 файлов уже байт-идентичны (lockstep) -> переезжают почти как есть.

Зафиксированные решения (2026-06-25):
1. Механизм = ВЕРСИОННОЕ MC-ядро (НЕ plain-java абстракция за интерфейсами). MC-код живёт прямо в ядре под версию; за интерфейс выносим только разницу аддон/редактор (уже есть шов ShadowCasterSource).
2. Версии = эталон 1.20.4 в первой итерации (там и аддон master, и редактор main, реал-модельные тени, уже lockstep). Тиражирование 1.21.1 -> потом; 1.21.11 (raw-GL, редактор-онли) — последним/отдельно.
3. Граница = в ядро только ОРКЕСТРАЦИЯ. Блок-каст (cutout/AABB, BBS-free) ПОКА оставляем per-mod.
4. Объём версионности = ВСЁ ядро per-version (патчер+свет+тени), полная симметрия с модами. Цена (озвучена): рабочий plain-java патчер+свет станет Loom/версионным — трогаем то, что работает. Юзер выбрал симметрию сознательно (отверг гибрид common-единый+shadow-per-version).
5. Loom-развязка = Ф0 PoC пробует ОБА: mavenLocal-publish (ремапнутый intermediary-артефакт, развязывает Loom) ПЕРВЫМ, fallback = выравнивание редактора на Loom 1.15.5. Решение по факту зелёной сборки.
6. Темп = эта сессия: план + сразу Ф0 PoC.

Грунт-факты (проверено по коду 2026-06-25, ветки: core=main@7677d77, редактор=main@318529f, аддон=master@8d5f3be):
- core сейчас: java-library (НЕ loom), group org.qualet, version 1.0-obt, --release 17, compileOnly lwjgl 3.3.1 + slf4j. Несёт fabric.mod.json id irl-core. Потребляется: includeBuild("../irl-core") (settings) + clientImplementation+include "org.qualet:irl-core:1.0-obt" (build) в ОБОИХ модах. Один jar на все версии (нет MC-типов).
- Loom-версии РАЗНЫЕ на одной MC: редактор main = fabric-loom 1.9-SNAPSHOT; аддон master = fabric-loom 1.15.5. Это и есть риск №1. (Память reference-edit-routing говорила «аддон master Loom 1.9» — УСТАРЕЛО, в коде 1.15.5.)
- 14 файлов оркестрации (список = LOCKSTEP_FILES в irl-core/tools/verify-shadow-lockstep.py): BlockShadowCache/Collector/Entry, CasterType, ImmediateOccluderBatch, IRLShadowQuality, OccluderBatch, OccluderSink, PointShadowArray, ShadowBakeState, ShadowBaker, ShadowCasterSource, ShadowRenderer, SpotlightDepthAtlas. Уже байт-идентичны редактор<->аддон (после нормализации).
- 2 per-mod CasterSource (остаются в модах): redactor=RedactorEntityCasterSource (vanilla EntityRenderDispatcher), аддон=IRLiteBbsCasterSource (BBS Form/Film/Morph).
- MC-поверхность: ShadowBaker = лёгкая (ClientWorld, Box/MathHelper/Vec3d, fastutil). ShadowRenderer = тяжёлая GL (blaze3d.RenderSystem/VertexSorter, ~12× net.minecraft.client.render.*, MatrixStack, lwjgl GL11/GL30, joml).
- Швы для абстрагирования при переезде (видно из нормализатора + импортов ShadowBaker):
  - LightRegistry -> уже общий (irl-core).
  - LightConfig(редактор) <-> IrliteConfig(аддон) -> нужен НОВЫЙ интерфейс ShadowConfig (геттеры качества/бюджета).
  - хардкод SOURCE = new …CasterSource() внизу ShadowBaker -> нужна инъекция CasterSource от мода в client-init.

Фазы:
- Ф0 — PoC сборки (разблокирующая). Минимальный Loom-модуль с одним MC-типизированным классом, потребляемый ОБОИМИ модами (редактор Loom 1.9 + аддон Loom 1.15.5) с корректным ремапом + JiJ + рантайм. mavenLocal-путь первым. Гейт: оба build зелёные, JiJ ок, ремап ок, runClient бутится. Нет — план не идёт (откат к lockstep).
- Ф1 — заморозить 2 шва: ShadowConfig + механизм регистрации ShadowCasterSource. ВЫПОЛНЕНА (см. секцию «Ф1 — контракт заморожен» ниже + irl-core/docs/shadow-config-source-injection-spec.md).
- Ф2 — перенос 14 файлов в core (канон = редактор main, он уже засеамлен). package->org.qualet.irl.light.shadow, LightConfig->ShadowConfig, снять хардкод SOURCE. Удалить дубли из модов, подключить per-mod impl. verify-shadow-lockstep.py выводим из эксплуатации (код стал общим).
- Ф3 — in-world 1.20.4 обоих модов (тени блоков; формы/энтити не замерзают; у аддона совпадает с BBS-превью). Гейт: юзер.
- Ф4 — тиражирование: ветки core port/1.21.1 -> форвард-порт; 1.21.11 отдельно последним.

Ф0 PoC — ВЫПОЛНЕН (2026-06-25): сборочный гейт пройден, путь жизнеспособен.
- Создан временный Loom-модуль BBS/irl-core-poc (вне репов; класс org.qualet.irl.shadow.PocProbe трогает net.minecraft.util.math.Box), публикуется в mavenLocal как remapped jar.
- ГЛАВНАЯ НАХОДКА: Loom вшивает свою версию в артефакт и ОТКАЗЫВАЕТСЯ принимать мод, собранный БОЛЕЕ НОВЫМ Loom (IllegalStateException: Mod was built with a newer version of Loom (1.15.5), you are using Loom (1.9.2)). Проверка ОДНОСТОРОННЯЯ (бьёт только когда артефакт новее потребителя). -> РЕШЕНИЕ: собирать общий core НАИСТАРШИМ Loom среди потребителей (1.9.2). Тогда редактор-1.9 принимает (равный), аддон-1.15.5 принимает (старый артефакт ок). Подтверждено: после пересборки poc под Loom 1.9-SNAPSHOT редактор-сборка ПРОШЛА проверку версии. НЕ требует трогать рабочие моды (fallback «выравнивать редактор на 1.15.5» НЕ нужен).
- consumption = modClientImplementation (ремап intermediary->named) + include (JiJ), + mavenLocal() repo. Plain clientImplementation НЕ годится для MC-типизированного артефакта.
- ДОСОБРАНО (юзер закрыл dev-MC, державший лок на yarn mappings.jar): редактор (Loom 1.9, MC 1.20.4) + аддон (Loom 1.15.5, -Pmc=1.20.4) ОБА build ЗЕЛЁНЫЕ с poc-Loom-1.9. remapJar прошёл у обоих; JiJ подтверждён: META-INF/jars/irl-core-poc-1.0-poc.jar в jar обоих модов. Ремап через границу подтверждён статически: published poc = intermediary (javap -> net/minecraft/class_238, field_1320…). runClient-рантайм НЕ гонялся (near-certain по build+remapJar success; опц. финал-гейт за юзером).
- Метод потребления = publish-to-mavenLocal (НЕ composite includeBuild Loom->Loom — тот не тестировали; mavenLocal развязывает Loom-версии и подтверждён). Dev-workflow: publish core в mavenLocal ПЕРЕД сборкой модов (дополнить build-trilogy.ps1). Semver: 1.0-poc/1.0-obt дают warning «not valid semver» — в реале версии сделать semver (1.0.0).
- Угол 1.20.4 = ТРУДНЕЙШИЙ (Loom 1.9 vs 1.15.5); решён. На 1.21.1 оба мода Loom 1.15 -> проще (один Loom, конфликта нет).
- intermediary версионно-специфичны -> аддон надо собирать -Pmc=1.20.4 (НЕ дефолт 1.20.1), иначе intermediary mismatch с poc-1.20.4. СЛЕДСТВИЕ для плана: универсальный jar аддона (1.20.1+1.20.4 один jar) НЕ совместим с MC-версионным core — на каждую MC-версию свой core (это и есть «всё per-version»; 1.20.1 и 1.20.4 = разные углы).
- ОТКАЧЕНО: git restore build.gradle обоих модов (status чистый), удалён BBS/irl-core-poc/ + mavenLocal org/qualet/irl-core-poc. Репо в исходном состоянии. НЕ трогали IRLRedactorClient (probe-вызов не добавлялся).

Ф1 — контракт заморожен (2026-06-25, sign-off юзера ПОЛУЧЕН).
Артефакт: irl-core/docs/shadow-config-source-injection-spec.md. Код НЕ тронут. Грунт: HEAD совпали с планом (core 7677d77 / редактор main 318529f / аддон master 8d5f3be), verify-shadow-lockstep.py = PASS все 14 на этих коммитах.
Грунт-факты (проверено по коду):
- Config дёргает ТОЛЬКО ShadowBaker (прочие 13 lockstep-файлов config не трогают). РОВНО 5 геттеров, построчно идентичны редактор<->аддон (отличие = имя класса): shadowQuality()/shadowCache()/shadowBakeBudget()/shadowBlocks()/shadowBlockRadius(). Сайты (редактор main@318529f): L242/256/259/745/969/973 + javadoc L160/963. Дефолты ИДЕНТИЧНЫ в обоих: 1/true/4/true/24.
- LightConfig (редактор) = mutable static-поля под ImGui; IrliteConfig (аддон) = null-safe обёртки над BBS Value*. Оба статические util (private ctor) -> PULL (нельзя push без листенеров на BBS-стороне).
- SOURCE (хардкод new …CasterSource() в ShadowBaker L1195) используется в 3 точках: collect() L1249 + 2× emitCaster(SOURCE,…) L1112/1137. Имя impl-класса нигде больше в 14 файлах нет. Оба impl = final … implements ShadowCasterSource, пустой ctor, без состояния -> инъекция тривиальна.
- client-init симметричен: IRLRedactorClient.onInitializeClient L51 + IrliteClient.onInitializeClient L12, оба зовут Patcher.install(...). ShadowBaker.bake зовётся ТОЛЬКО из GameRendererLightMixin (renderWorld, нужен мир) -> client-init всегда ДО первого bake.
Замороженный контракт (2 шва + гейт):
1. ShadowConfig (интерфейс в core, пакет org.qualet.irl.light.shadow) — 5 pull-геттеров (выше). Core несёт ShadowConfig.DEFAULTS (1/true/4/true/24) как fallback. Каждый мод даёт тонкий делегат-синглтон (реком. LightConfig.SHADOW / IrliteConfig.SHADOW) в свой статический config. НЕ входят (остаются per-mod, ни один из 14 файлов их не читает): showGuides() (оба -> LightGuideRenderer) + редактор autoLights* (6 шт -> AutoLightManager). ShadowConfig = строгое ПОДМНОЖЕСТВО.
2. Регистрация source — holder ShadowEngine в core (зеркалит Patcher: volatile+fail-fast), несёт ОБА инжекта: install(ShadowCasterSource src, ShadowConfig cfg), source() (fail-fast IllegalStateException если не установлен — нет фолбэка), config() (никогда null -> DEFAULTS). Один вызов в client-init рядом с Patcher.install. Интерфейс ShadowCasterSource НЕ переоткрывается (заморожен в shadow-caster-seam-spec.md) — меняется лишь доставка инстанса (хардкод new -> инъекция).
3. Гейт ПРОЙДЕН: 2 шва закрывают обе семантические подстановки нормализатора (LightConfig/IrliteConfig->Cfg; …CasterSource->SourceImpl); 6 пакет/import-подстановок закрываются самим переездом в общий пакет (две копии->один исходник). Остатка нет (таблица соответствия в спеке).
Зафиксированные решения (sign-off юзера 2026-06-25, не переоткрывать без причины): pull-интерфейс; один holder ShadowEngine.install(source,config) симметрично Patcher; source fail-fast / config=DEFAULTS; дефолты -> core DEFAULTS, per-mod литералы оставлены как есть (минимализм Ф2). 3 ревью-пункта (имя holder'а / форма адаптера / дедуп дефолтов) ПРИНЯТЫ в пользу рекомендаций.
Дельта ShadowBaker в Ф2 (справочно): снять import LightConfig + поле SOURCE; ShadowCasterSource source = ShadowEngine.source() (поле/проброс в renderInRange* для 2× emitCaster); 5× LightConfig.x()->ShadowEngine.config().x(); 2× javadoc {@link LightConfig#…}->{@link ShadowConfig#…}. После — 0 per-mod идентификаторов -> переезд в core байт-чистый.

Ф2 — ВЫПОЛНЕНА (2026-06-25): тени переехали в irl-core, оба мода зелёные на 1.20.4.
Грунт-коммиты на старте: core main@7677d77 / редактор main@318529f (канон) / аддон master@8d5f3be. ЗАКОММИЧЕНО 2026-06-25 (юзер подтвердил runClient-редактор in-world: свет поставлен/забейкан/сохранён, 0 крашей, fail-fast НЕ сработал => install прошёл): core main@1b96e74 / редактор main@d8705f4 / аддон master@3e5066c. НЕ запушено (юзер сказал «коммитим», не пушим).
Сделано (этапы A–F, каждый с build-гейтом):
- A. irl-core plain-java -> Loom-модуль (MC 1.20.4, fabric-loom 1.9-SNAPSHOT, Java17). build.gradle: minecraft+yarn 1.20.4+build.1+fabric-loader 0.16.14 + maven-publish (publishToMavenLocal->remapped intermediary jar). settings.gradle +pluginManagement (fabric maven). fabric.mod.json (id irl-core) сохранён. Патчер+свет под Loom без правок. Gradle 9.2.0 + Loom 1.9 = та же связка, что у редактора (wrapper не трогали).
- B. Оба мода: settings.gradle убран includeBuild("../irl-core"); build.gradle +mavenLocal() + clientImplementation->modClientImplementation (include JiJ остался). Композит больше нельзя (MC-типы нужен Loom-ремап). PoC-путь валидирован на реальном core ДО переноса теней.
- C. 14 lockstep-файлов скопированы редактор(канон)->core пакет org.qualet.irl.light.shadow. 2 новых шва: ShadowConfig (5 геттеров + DEFAULTS 1/true/4/true/24) + ShadowEngine (holder, зеркалит Patcher: install(src,cfg) / source() fail-fast / config()->DEFAULTS). Дельта ShadowBaker: убран import LightConfig + поле SOURCE; 6× LightConfig.x()->ShadowEngine.config().x(); 3× SOURCE->ShadowEngine.source(); 2× javadoc->ShadowConfig#. PointShadowArray: {@link …iris.SamplerBindingCubeArrayMixin} (per-mod, нет в core)->{@code}-текст.
- D. Редактор: 14 дублей удалены; RedactorEntityCasterSource ПЕРЕМЕЩЁН в пакет org.qualet.irl.light.shadow (физически в редакторе src/client — тот же пакет, что core-соседи => без импортов соседей + package-private сохранён). LightConfig.SHADOW nested-делегат. IRLRedactorClient: ShadowEngine.install(new RedactorEntityCasterSource(), LightConfig.SHADOW). 5 потребителей (миксины GameRendererLight/WorldBlockChange/2×iris + LightDriver): import-префикс irlredactor.light.shadow->irl.light.shadow.
- E. Аддон симметрично. IRLiteBbsCasterSource->org.qualet.irl.light.shadow. Config-делегат = ОТДЕЛЬНЫЙ client-класс qualet.irlite.client.IrliteShadowConfig (НЕ nested: IrliteConfig в main source set, core виден лишь в client). IrliteClient: ShadowEngine.install(new IRLiteBbsCasterSource(), new IrliteShadowConfig()). 5 потребителей (AbstractLightFormRenderer вместо LightDriver).
- F. verify-shadow-lockstep.py->retired-stub (тени одной копией). Доки: lockstep-md=RETIRED, spec=Ф2-IMPLEMENTED.
Гейт Ф2: core publishToMavenLocal зелёный (ремап подтверждён javap->net.minecraft.class_*). Редактор build + аддон build -Pmc=1.20.4 зелёные (clean). jar-чистота: каждый мод несёт ТОЛЬКО свой caster в org.qualet.irl.light.shadow; оркестрация+швы в core JiJ (META-INF/jars/irl-core-1.0-obt.jar); per-mod casters НЕ в core JiJ.
Грунт-находки (несущие):
- Loom-граница: core собран наистаршим Loom 1.9.2 -> Loom 1.15.5 аддон принимает старый артефакт (PoC-вывод подтверждён в реале через mavenLocal).
- Аддон потерял универсальный jar: MC-версионный core => per-MC, только -Pmc=1.20.4 (intermediary 1.20.4). 1.20.1 = отдельный core-1.20.1 в Ф4.
- Dev-loop: gradlew publishToMavenLocal в core ПЕРЕД сборкой модов. Версия 1.0-obt не semver -> JiJ-warning (безвреден) + mavenLocal-кэш: пересборка core той же версией требует модам --refresh-dependencies (или сделать версию *-SNAPSHOT/семвер — отложено в Ф4). build-trilogy.ps1 ДОПОЛНИТЬ publish-шагом (НЕ сделано).
- Орфаны: инкрементальный Gradle оставляет старые .class удалённых исходников -> clean build обязателен после переезда (иначе дубль shadow-классов в jar).

Осталось (текущее открытое состояние, 2026-06-29): Ф3 in-world фактически подтверждён (редактор: свет поставлен/забейкан/сохранён, 0 крашей; депт-фикс [[fix-shadow-depthstate-repin]] задеплоен в Prism через standalone irl-core, подтверждён на ране). Ф4 ТИРАЖИРОВАНИЕ на порт-ветки — ОТКРЫТО: на 2026-06-25 ТОЛЬКО линия 1.20.4 (redactor main + addon master) тянет per-version core с вынесенными тенями из mavenLocal; порт-ветки (redactor port/1.21.1·1.21.4·1.21.11·1.20.1 + addon port/1.21.1) всё ещё на composite includeBuild против main-core, оркестрацию теней per-version НЕ получили. Гибридная матрица + ручной publish-шаг = [[tool-build-trilogy-script]].

Открытые под-развилки (рекомендации):
- Структура core per-version: один Loom-модуль на ветку (патчер+свет+тени вместе) — рекоменд., ложится на «всё per-version». Альтернатива: core multi-project :common(plain)+:shadow(loom).
- Потребление: mavenLocal-publish (рекоменд., развязывает Loom) vs composite includeBuild Loom->Loom.
- Имя пакета теней в core: org.qualet.irl.light.shadow (зеркалит текущее …light.shadow).
- mod-id ядра: оставить irl-core (depends работает), версия в fabric.mod.json per-version.

Что НЕ переезжает (остаётся per-mod, даже при максимуме): CasterSource-impl (BBS-форма vs vanilla-модель), Iris-миксины (версионные, Loom-AW требует в репо мода), light-адаптеры (LightCollector/IRLightPositionResolver у аддона; LightScene/PlacedLight/LightDriver у редактора), весь UI.
