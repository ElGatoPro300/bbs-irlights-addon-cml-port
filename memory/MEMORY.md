# Memory Index

Объединённая база: 2 мода — IRLite (BBS-аддон), IRL-redactor (standalone ImGui-редактор) — + общее ядро irl-core. Старт «поменяй X» -> [reference-edit-routing-by-area](reference-edit-routing-by-area.md).
Завершённые done-логи (порт-планы паков, фазы shadow-seam, перекрытые аудиты, выполненные план-файлы) лежат в `_archive/` — НЕ индексируется; поднять оттуда при необходимости.
Консолидация 2026-06-29: сверены контракты по коду (SSBO 96б/6×vec4 c cookie, MAX_LIGHTS 2048, LightRegistry в irl-core); 5 завершённых план-файлов -> `_archive/`, durable-такеуэи свёрнуты в канон.
Переформат 2026-06-29: со всех активных файлов снят декор (эмодзи/жирный/цитаты/strikethrough), описания поджаты, хвостовые «Связь»-секции сжаты в 1 строку — строгий вид под чтение LLM. Бэкап до правок: scratchpad/memory-backup-preformat.
Инфра памяти 2026-07-01: 3 memory-дира трилогии (irlights/addon/core) = ОДИН физ. склад через Windows junctions (реальный = ключ ...BBS-irlights) -> [reference-memory-junctions](reference-memory-junctions.md); сессия в addon/core грузит ту же базу, правки видны всем трём.

## Маршрутизация и стратегия (читать первой)
- [reference-edit-routing-by-area](reference-edit-routing-by-area.md) — карта что-где менять (патчер+свет+тени-оркестрация=irl-core; caster/UI per-mod; .irlights owner IRLite); команды сборки, что НЕ трогать.
- [project-github-repos](project-github-repos.md) — 3 приватных репо под owner quaIett (заглавная I): irl-core/addon/editor; origin+ветки, gh CLI.
- [project-irl-sync-strategy](project-irl-sync-strategy.md) — карта дрейфа аддон<->редактор (что shareable vs форк); Ф0+Ф2 done; единств. недовынесенный шов = CookieArray; универс-jar отменён -> per-MC.
- [tool-build-trilogy-script](tool-build-trilogy-script.md) — build-trilogy.ps1: вся трилогия на все версии MC -> Desktop\IRLights; per-MC core = publishToMavenLocal.
- [tool-build-bbs-pack-script](tool-build-bbs-pack-script.md) — build-bbs-pack.ps1: irl-core(v1.1)+4 универс-1.20.x аддона -> Desktop\bbs_pack (маппинг имён папок).

## IRL-redactor

### Тени (оркестрация физически в irl-core)
- [plan-irl-core-shadow-extraction](plan-irl-core-shadow-extraction.md) — КАНОН теней: оркестрация в irl-core (Ф2 2026-06-25) + шов ShadowCasterSource + 5 инвариантов + per-mod casters. Ф4 тиражирование на порт-ветки открыто (per-version core пока только на 1.20.4).
- [project-shadow-bake-perf-audit](project-shadow-bake-perf-audit.md) — ЕДИНСТВЕННЫЙ живой док перфа бейка (канон-таксономия; план-файлы в `_archive/`). Все Tier-1/2 done на всех линиях; открыт только C10 per-face block-cull (~320ms спайк).
- [addon-shadows](addon-shadows.md) — референс бейк-движка (ShadowBaker/Renderer, пресеты LOW–ULTRA, кэш, cull, spot-атлас+point cube-array) + 2 open-issue (anim-token freeze, occluder-32).
- [fix-shadow-depthstate-repin](fix-shadow-depthstate-repin.md) — MAJOR-A (ре-пин depth/blend/матриц перед каждым emit) + MAJOR-B (feet-pivot AABB->сфера); GL-диаг glGetBoolean.
- [shadow-distance-quality-plan](shadow-distance-quality-plan.md) — качество теней на дали (Ф1-2 done в шейдере, Ф3 open) + known-open «зернистый квадрат» Softness (диагноз найден 2026-07-02 -> point-shadow-square-root-cause).
- [project-point-shadow-square-root-cause](project-point-shadow-square-root-cause.md) — сравнение с IRLEngine (2026-07-02): корень «зернистого квадрата» = дефолт разрешения point 512 vs 1024 (D1); cull/polygon-offset/кодировка/биндинг исключены; сэмплинг-дельты = усилители; эксперименты E1-E4 open.

### Порты / редактор / движок / интеграции
- [project-port-1211](project-port-1211.md) — порт 1.20.4->1.21.11 (продакшн, in-world PASS) + дельты 1.21.1/1.21.4 + карта API 1.21.x; 1.21.11 тени = реал-модели через capture-queue.
- [project-port-1201](project-port-1201.md) — порт 1.20.1 (только деп-матрица + LWJGL-пин; НОЛЬ правок .java). runClient PASS.
- [project-irlite-base-ported](project-irlite-base-ported.md) — КАНОН движка+редактора: BBS-free свет (шов LightScene/PlacedLight/LightDriver) + фичи редактора (gizmo/persist/guides/локализация/ImGui-скин) — feature-complete.
- [project-editor-vs-replay-screen-conflict](project-editor-vs-replay-screen-conflict.md) — редактор оверлеем в Replay Mod (1.20.4+1.21.11, in-replay PASS); raw-GLFW keybind; Фаза 3 курсор в облёте open.
- [project-flashback-irlights-plan](project-flashback-irlights-plan.md) — PLAN-only (кода нет, не начат): отдельный аддон IRLights->Flashback replay (Moulberry); MC 1.21.11+Flashback 0.39.5, свет=keyframe-треки; two-mod Yarn+Mojmap; kill-switch=выживание SSBO b7 под export. Спасён из мёртвой IRL-redactor базы 2026-07-01.
- [project-imgui-axiom-collision](project-imgui-axiom-collision.md) — краш ImGuiImplGl3.init рядом с Axiom (unrelocated imgui-java); try/catch+fallback, все ветки.
- [project-auto-block-lights](project-auto-block-lights.md) — авто-свет от эмиссивных блоков; инкрем-скан; по умолч OFF; MAX_LIGHTS->2048 (irl-core).
- [project-gui-lag-gpu-bound-diagnosis](project-gui-lag-gpu-bound-diagnosis.md) — лаг GUI при многих источниках = GPU-bound (не потоки); рычаг 2 (collect-cache) done; рычаг 1 GPU-cull/LOD открыт. Инструмент FrameProfiler.
- [project-spotlight-gobo-cookie-plan](project-spotlight-gobo-cookie-plan.md) — gobo/cookie-маска (6-й vec4 SSBO; done+in-world все версии+аддон; PBO/UNPACK-traps). Открыт back-fill cookie-GLSL в Shadres.

### Forge / Sinytra Connector
- [project-forge-connector-compat](project-forge-connector-compat.md) — аддон на Forge 1.20.1 через Connector beta.48 (правка fmj fabricloader >=0.15.0).

### Референсы / правила работы
- [reference-bbs-fs-not-refreshed](reference-bbs-fs-not-refreshed.md) — для BBS-кода референс = bbs-fs, не форк refreshed.
- [feedback-no-per-session-branch](feedback-no-per-session-branch.md) — НЕ создавать git-ветку под сессию; работать в текущей; новую только по прямой просьбе.
- [feedback-memory-strict-style](feedback-memory-strict-style.md) — память вести в строгом LLM-стиле (ноль эмодзи/жирного/цитат; description=1 строка; таблицы ок).
- [reference-imgui-font-glyph-range](reference-imgui-font-glyph-range.md) — шрифт редактора = Latin-1+кириллица; галочка/крест/предупр/стрелка/многоточие/em-dash = тофу, статус давать цветом.
- [iris-source-library](iris-source-library.md) — локальные исходники Iris: PRIMARY 1.20.1 + fallback 1.7.2-1.20.4; класс-карта.
- [ref-betterlights-shadow-comparison](ref-betterlights-shadow-comparison.md) — BetterLights vs IRLite: IRLite дешевле+качественнее; BL полнее по окклюдерам (chunk-VBO).

## IRLite — ядро BBS-аддона
- [addon-architecture](addon-architecture.md) — пустые энтрипоинты, всё через миксины; per-frame GameRendererLightMixin->collect->bake->flush(SSBO7) до Iris.
- [addon-forms](addon-forms.md) — модель форм на BBS Form: PointLightForm/SpotlightForm (поля/диапазоны; spot radius=угол°; blocks_only/entities_only->lightMask).
- [addon-light-collection](addon-light-collection.md) — сбор света за кадр: SCANNER (ModelBlock) vs RENDER (актёры/реплеи), дедуп; MAX_LIGHTS=2048.
- [fix-bone-attached-light-deadzone](fix-bone-attached-light-deadzone.md) — свет на кости (BodyPart.bone) не регал НИ scanner НИ render-path (мёртвая зона на стыке владения); фикс master: render-path забирает bone-свет всегда (form.getParent() instanceof BodyPart).
- [addon-ui-config](addon-ui-config.md) — UI+конфиг IRLite на BBS: IrliteConfig, BBSSettings-категории, L10nMixin, in-world гайды, form-editor панели.
- [plan-interactive-spot-guides](plan-interactive-spot-guides.md) — интерактивные гайды спота в превью редактора форм (drag кольца=radius/inner, диск торца=range) через стенсил-пикинг BBS + миксин UIPickableFormRenderer; in-game PASS 2026-07-02; open: Ф2 in-world, тюнинг толщин, коммит.
- [project-refactor-origin](project-refactor-origin.md) — IRLite = рефактор IRLEngine (uniform->std430 SSBO binding7 + anchor-патчер); IRLEngine = только поведенч. референс.
- [commit-checkpoints](commit-checkpoints.md) — (feedback) коммитить только в чекпоинты, ждать подтверждения, не авто-коммит; gitignore shaders/->git add -f.
- [feedback-addon-runclient-command](feedback-addon-runclient-command.md) — (feedback) рантайм аддона ВСЕГДА runClient -Pmc=1.20.4 (лог run/runclient-console.log, в фоне); Prism-деплой НЕ используется; в PowerShell квотить '-Pmc=1.20.4'.

## irl-core — общее ядро
- [patcher](patcher.md) — семантика патчера + DSL .irlights (@target/@packversion/@marker, after/before/replace, ?, |); validate-first, dry-run/rollback. CONTRACT_VERSION=1.
- [addon-light-buffer-ssbo](addon-light-buffer-ssbo.md) — std430-контракт LightBuffer: SSBO binding7, header 16B + 6×vec4/96б (incl. cookie); MAX_LIGHTS=2048; инжект-GLSL зеркалит байт-в-байт.

## Шейдер-инжект — общие контракты
- [plan-lens-flare](plan-lens-flare.md) — PLAN-only lens flare для IRL-источников: каркас BSL-композита + тинт цветом лампы + окклюжн/фейд из старого ir_lens_flare.glsl; единый блоб как VL; open: SSBO-слот (реком. 7-й vec4).
- [shader-irlite-glsl](shader-irlite-glsl.md) — контракт irlite_lights.glsl: struct IrliteLight (6×vec4 incl. cookie), #define-опции, per-light математика (Frostbite falloff, спот-конус, lightMask).
- [shader-shadow-sampling](shader-shadow-sampling.md) — GLSL-чтение теней: spot 2D-атлас + point cube-array, PCSS, normal-offset bias. Гард: vlParams.w<0 ДО int().
- [shader-volumetric](shader-volumetric.md) — GLSL-волюметрика: single-scatter Beer-Lambert ray-march, Хеньи-Гринштейн. VL-тени пока только Photon; VL-noise (клубы, 2026-07-02) done+PASS на Complementary Modification, НА ПАУЗЕ: порт в 5 паков + реген complementary-патча open.
- [shader-settings](shader-settings.md) — инъекция настроек в Iris UI (shaders.properties+lang). Гоча: boolean #define виден только при голом #ifdef X.
- [addon-iris-integration](addon-iris-integration.md) — (ref) 2 миксина (remap=false) биндят тени: ProgramSamplersBuilderMixin + SamplerBindingCubeArrayMixin.
- [ref-irlengine-photon-patch](ref-irlengine-photon-patch.md) — (ref) старый IRLEngine->Photon как образец математики; reuse, но adapt uniform->SSBO + re-verify якоря (version drift).
- [sync-workflow](sync-workflow.md) — dev-цикл шейдеров (Shadres Original/Modification/patches/run; Shadres gitignored) + правило комментов в патчах (<=1 строка).

## Шейдер-паки — пайплайны (контракт + якоря + статус порта)
- [project-photon-outline-switch-to-old](project-photon-outline-switch-to-old.md) — КАНОН outline: старый IRLEngine LocalLightOutline (Fresnel rim) на всех 6 паках; params PIXEL_SIZE2/STR0.65/POW2.2, default OFF; Photon = 2 патча (irlights+DoF).
- [outline-target-entity-detection](outline-target-entity-detection.md) — IRLITE_OUTLINE_TARGET (0 both/1 entities/2 blocks); per-pack entity-метка (CR materialMask 254, Solas colortex3.xy≈0, BSL инжект colortex3.z, Bliss opaqueMasks 0.45 + безусл. инжект all_solid.vsh); done CR/Solas/BSL/RethinkingVoxels/Bliss (commit 5ab047c), Photon/IterationRP отдельно; BBS-морфы = дефолт-метка пака. + гоча stale-working-copy-2 (PatchLibrary.extracted irl-core = глобальный, не per-host — второй мод не рефрешит, open).
- [photon-pipeline](photon-pipeline.md) — Photon (SixthSurge) deferred + 4 хук-сайта; порт done (20 ops, byte-identical). Спутник [photon-bugfix](photon-bugfix.md).
- [photon-bugfix](photon-bugfix.md) — баг-трекер Photon: B-OUTLINE-TRANSLUCENT ЗАКРЫТ; WATCH bob-flicker self-shadow acne.
- [shader-iterationrp-pipeline](shader-iterationrp-pipeline.md) — IterationRP deferred (#version 430, native SSBO+cubeArray) 3 хука; порт TAKE-3 done; AE-crater->PRE-albedo outline; VL unshadowed.
- [complementary-pipeline](complementary-pipeline.md) — Complementary forward #130; порт done (21 ops); VL half-res deferred2 (~2.8×).
- [rethinkingvoxels-pipeline](rethinkingvoxels-pipeline.md) — RethinkingVoxels (Complementary-форк gri573, воксельный GI); порт done 2026-06-29 (20 ops), diff-clean + runtime PASS; форк-дельты: composite-шов composite.glsl (нет composite1), VL=colortex15, outline без colortex4.
- [bsl-pipeline](bsl-pipeline.md) — BSL v10 forward #120 CRLF; порт done (26 ops); reduced-res VL; iris.features dual-line bug (фикс на следующем касании).
- [solas-pipeline](solas-pipeline.md) — Solas forward #130; порт done (19 ops, ru_RU); irislex tooling; iris.features+JCPP */-comment bugfixes.
- [bliss-pipeline](bliss-pipeline.md) — Bliss deferred+forward #120; порт done (16 ops, smallest); dual-hook composite1+all_translucent; MVI[3] bobbing «exactly once».
