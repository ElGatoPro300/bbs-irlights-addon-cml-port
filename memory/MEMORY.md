# Memory Index

## Правило загрузки памяти
- Эта инструкция предназначена исключительно для моделей ChatGPT. Если модель не является ChatGPT, она должна пропустить эту строку.
- В начале каждой сессии по этому проекту читать внешний источник памяти: `C:\Users\Qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-irlights\memory\MEMORY.md`.

СЕССИЯ 2026-07-18: VL CP1 ХРЕБЕТ РЕАЛИЗОВАН+ПРОВЕРЕН+ЗАКОММИЧЕН (Fable-агенты разрешены юзером). Фаза 0: Э1 intensity через header offset 4 PASS; Э2 occupancy A/B замер юзера 72/72/140/152 → числа runtime, тяжёлые тумблеры dual-gate (#define+runtime-флаг); чекпоинт core 700f255/addon 32f52d2. Фаза 1: std140 UBO binding 7 (48Б, A/B/C-вектора), core VlGlobalsBuffer, 9 слайдеров BBS + vlGroup ImGui-редактора (dep 1.1->1.2), CR-патч 78953d30 полностью на UBO-чтениях, ин-гейм PASS (UBO скомпилирован Iris с первого раза); чекпоинт core 468610d/addon 4e9e90d/editor 13cb0df. ГОТЧА loom: same-version republish core требует purge .gradle/loom-cache/remapped_mods (кэш по GAV). Фаза 2 (CP2 blue-noise dither) ЗАПУЩЕНА. Детали: [plan-vl-refactor-research](plan-vl-refactor-research.md).
СЕССИЯ 2026-07-17 (2): БАГ «ПРЫГАЮЩИХ ТЕНЕЙ» ДИАГНОСТИРОВАН+ПОЧИНЕН+РЕТЕСТ ЮЗЕРА PASS, ПЛАН ЗАКРЫТ (заодно фактически закрыт I5 atlas-merge — тест шёл на живом атлас-коде CR). НЕ закоммичено (ждёт чекпоинт-подтверждения; дерево = atlas-merge + фикс вперемешку, коммитить вместе). Диагноз (34-агентный wf, подтверждён рантайм-тестом юзера: 25 ламп стабильно, >30 прыгают): behind-cull потреблял ранг ДО rank++ (поворот пересдавал ранги через границы пула 30) + TIER_NONE мгновенно гасил тень. Фикс в ShadowBaker (спот+поинт): rank-стабильность (behind потребляет ранг, keep-alive гейт desired<=lastTier), pastPool-баунд Schmitt через contentionHold, SPARE-режим (age-edge стамп fI-1, тень умирает только при фактической краже), releaseOldTile=pool-scan (сирота через SHADOW_PENDING), holdCap(id) джиттер, профайлер demand X/30. 3 ревью-раунда (51 агент): foreign-w доказан, сходимость доказана. Builds PASS. Флаг -Dirlite.profileShadows=true СУЩЕСТВУЕТ (:272). Детали+лимиты: [fix-shadow-slot-rank-stability](fix-shadow-slot-rank-stability.md).
СЕССИЯ 2026-07-17: VL-РЕФАКТОР МЕГА-ИССЛЕДОВАНИЕ DONE (wf_65d83526-4c8, 36 агентов Opus, research-only, код НЕ начат; atlas-merge оставлен как есть по команде юзера). Вывод: zero-recompile невозможен через Iris-опции (любая = #define → Iris.reload(), верифиц. исходниками) — единственный путь = демоция глобальных IRLITE_VL_* в runtime-данные (SSBO header binding-7 / std140 UBO / CommonUniforms-mixin); per-light vlParams уже так работают. Победившая связка: CP1 runtime-globals-spine (strong/strong) + CP2 STBN blue-noise dither (viable/strong, через IrlSamplers 11-й сэмплер) + CP4 cluster-cull VL-петли (binding 6; VL сейчас петляет ВСЕ лампы) + spot-Hi-Z + bilateral@0.5. Отсеяны: temporal-TAA-feed (нет ping-pong у Iris + движущиеся лампы), analytic airlight (губит gobo/cone), froxel (R&D, далёкая перспектива). Главная находка критика: мод владеет позициями ламп → light-velocity motion vectors разблокируют temporal для движущихся ламп (ни один шейдер-пайплайн так не может). Фаза 0 = эксперименты Э1/Э2/Э7 до любого кода. Полный отчёт+план: [plan-vl-refactor-research](plan-vl-refactor-research.md).
СЕССИЯ 2026-07-16 (2): atlas-merge КОД РЕАЛИЗОВАН ЦЕЛИКОМ, Ф1-Ф7 build-гейты PASS, НЕ ЗАКОММИЧЕНО (обе репы dirty на optimization/octahedral-point-shadows). PointDepthAtlas 30 ламп {2,12,16} 6144^2 + generic DepthTileAtlas (spot = фасад, бит-в-бит); Pyramid/Evsm flat-static, ingestion srcAtlas+blockOrigin, seamless переехал в Evsm.ensureResources; IrlSamplers 12->10 (irl_pointShadowAtlas 2D); GLSL: irlite_pointAtlasUV per-tap face re-select + clamp; патч перегенерирован, byte-proof PASS, run+prism синк, MD5 = 5450bf3f (старый e2f95a6b УСТАРЕЛ). Гейт 5: LOW=512 без clamp; гейт 6: GL_MAX_TEXTURE_SIZE query+clamp. core 1.1->1.2 published, addon build PASS (аддону нужен JDK 21), runClient PASS без ошибок. Адверсариальное ревью диффа: 0 находок. ОСТАЛОСЬ: I5 ин-гейм ретест (только CR-пак; остальные 6 паков ожидаемо без point-теней до тиража) + коммит-чекпоинт по подтверждению. Статус+чек-лист: [plan-point-shadow-atlas-merge](plan-point-shadow-atlas-merge.md).
СЕССИЯ 2026-07-16 (1): atlas-merge имплем-план готов (wf_d5e731ba-4fc, 10 агентов; major-находки вшиты: seamless-перенос, srcCube->srcAtlas релинк); блокирующие гейты 1-4,9 решены юзером (текущая ветка, DepthTileAtlas, irl_pointShadowAtlas, бамп 1.2, единый blur-temp). СУПЕРСИДЕНО сессией (2) — код написан.
СЕССИЯ 2026-07-13 (2): octahedral/dual-paraboloid point-тени ЗАКРЫТЫ КАК НЕРЕАЛИЗУЕМЫЕ в этом пайплайне — НЕ ПЕРЕОТКРЫВАТЬ. Причина (верифиц. кодом+литературой): бейк печёт геометрию через ванильные per-RenderLayer шейдеры (blocks=getPositionProgram, entities/forms self-draw в общий entity-Immediate), трансформ только матричный — нелинейный paraboloid/octahedral warp матрицей не выразить, единого вершинного шва под кастомный VS нет (десятки ванильных ShaderInstance). Плюс DPSM требует тесселяции (прямые→дуги на 180°), у геометрии MC её нет → light-leak. Octahedral как ПРОЕКЦИЯ бейка = та же стена; octahedral/плоский атлас как ХРАНЕНИЕ (печём 6 граней линейно, пакуем) — валиден. Линейной проекцией минимум = 6 граней. Реальные рычаги (совместимы с кубом): (а) плоский общий 2D-атлас для граней → снять асимметрию 18/64 + единый склад со спотом; (б) пропуск бейка граней без кастеров (sphereTouchesFace уже считает) → бейк-перф. План-память трека и правки этой сессии удалены/откачены по команде юзера; ветки optimization/octahedral-point-shadows (core+addon) остались.
СЕССИЯ 2026-07-13: LOD-ТИРЫ I1-I4 + CASTER FIX CHECKPOINT COMMITTED. I4 adversarial review PASS (mirror/GLSL/pipeline), GLSL без новых фиксов, live/run MD5 `e2f95a6b`. Раскладки: spot 64, point 18; отдельный runtime fix поднял global caster pool 32->nearest-128, убрал pure-light ghost ModelBlock и horizon 72->256. Коммиты: core `2e57f8d`, addon `700b60c`, editor `08df3f6`; builds/byte-proof PASS, push нет. I5 визуальный ретест после caster fix pending. Разнос на другие шейдеры/версии — только по отдельной команде. Детали и актуальное ВОЗОБНОВЛЕНИЕ: [plan-shadow-lod-tiers](plan-shadow-lod-tiers.md).
СЕССИЯ 2026-07-12: PHASE 3 кластеризация DONE + ин-гейм PASS 67->112 FPS (binding 6, masked-continue CR). НЕ закоммичено (core Phase2+3, аддон, патч CR). Тираж/хвосты отложены по команде. Детали: [plan-perf-fix-cluster-phase3](plan-perf-fix-cluster-phase3.md).
СЕССИЯ 2026-07-10 (3): Phase 2 core DONE — C1 cap+приоритизация, C2 mustBake-троттлинг (SHADOW_PENDING-омит), C3 hashmap slot(); гамма-предрасчёт отклонён. НЕ закоммичено. Детали: [plan-perf-fix-core-phase2](plan-perf-fix-core-phase2.md).
СЕССИЯ 2026-07-10: перф-аудит (P0 = per-fragment цикл; 9 DRIFT) -> [project-perf-audit-irlite-2026-07-10](project-perf-audit-irlite-2026-07-10.md); Phase 1 лечения done v2, ре-тест PASS, ЧЕКПОИНТ master a43d46b -> [plan-perf-fix-cr-phase1](plan-perf-fix-cr-phase1.md).
СЕССИЯ 2026-07-08 (2): унификация трилогии ЗАКРЫТА (per-version ядро, версия 1.1, build-trilogy 13/13, всё запушено; коммиты внутри) -> [project-trilogy-unify-11](project-trilogy-unify-11.md).
СЕССИЯ 2026-07-08: линия 1.21.11 обновлена и ЗАКРЫТА (core 3527d63, RawOccluderBatch) -> [project-port-12111-refresh](project-port-12111-refresh.md). mavenLocal после per-MC сборок = intermediary той ветки; re-publish перед сборкой других линий.
Фаза 2026-07-02 (директива юзера): чиним ТОЛЬКО main/master; порт на ветки/редактор — в конце, строго по команде.
2026-07-03: point-стек сведён (MSM4+cube-view; 108-агентное ревью 0/0 = готов-к-тиражу), Photon сведён + патч 30040cf in-game PASS; NORMAL_OFFSET re-exposure uncommitted — коммитить первым при возврате. HANDOFF: [project-point-shadow-fix-backlog](project-point-shadow-fix-backlog.md).
СЕССИЯ 2026-07-04: тираж фильтрации 6/6 (5 в git: CR 69ecbda, RV f3b3f37, BSL 697373c, Solas 19e6b4e, Bliss 283256b; PASS). IterationRP по решению юзера ЛОКАЛЬНО/gitignored, коммит НЕ пере-предлагать; DoF-комбо в bbs-dof-addon 736fa7d.
СЕССИЯ 2026-07-06/07: линия 1.21.4 ЗАКРЫТА (1ce93fc/a88d05a) -> [project-port-1214](project-port-1214.md).
СЕССИЯ 2026-07-06: порт 1.21.1 ЗАКРЫТ+ЗАКОММИЧЕН (core 80a3986, addon 688afda, редактор f85113e; PASS; готчи worktree/git-identity в файле) -> [plan-port-1211-workflow](plan-port-1211-workflow.md).
СЕССИЯ 2026-07-05: Tier1+2 выносы в core закоммичены (cf4ad94 v1.1 / 3c3ef3f / fa71093) -> [plan-irl-core-library-extraction](plan-irl-core-library-extraction.md); E1 ре-синк патчей PASS, editor e147571.

Объединённая база: 2 мода — IRLite (BBS-аддон), IRL-redactor (ImGui-редактор) — + ядро irl-core. Старт «поменяй X» -> [reference-edit-routing-by-area](reference-edit-routing-by-area.md).
Done-логи в `_archive/` — не индексируется. Консолидация+переформат 2026-06-29 (бэкап scratchpad/memory-backup-preformat). Инфра 2026-07-01: 3 memory-дира = ОДИН склад через junctions (реальный = ...BBS-irlights) -> [reference-memory-junctions](reference-memory-junctions.md).

## Маршрутизация и стратегия (читать первой)
- [reference-edit-routing-by-area](reference-edit-routing-by-area.md) — что-где менять (патчер+свет+тени=irl-core; caster/UI per-mod; .irlights owner IRLite); команды сборки.
- [project-github-repos](project-github-repos.md) — 3 приватных репо под owner quaIett (заглавная I); origin+ветки, gh CLI.
- [project-irl-sync-strategy](project-irl-sync-strategy.md) — карта дрейфа аддон<->редактор; универс-jar отменён -> per-MC.
- [plan-irl-core-library-extraction](plan-irl-core-library-extraction.md) — Tier1+2 выносы РЕАЛИЗОВАНЫ+ЗАКОММИЧЕНЫ 2026-07-05; core-API список внутри; Tier3 не делалось.
- [plan-port-1211-workflow](plan-port-1211-workflow.md) — порт 1.21.1 ВЫПОЛНЕН+ЗАКОММИЧЕН 2026-07-06; статус-блок = точка возобновления.
- [tool-build-trilogy-script](tool-build-trilogy-script.md) — build-trilogy.ps1: трилогия на все MC -> Desktop\IRLights; per-MC core = publishToMavenLocal.
- [tool-build-bbs-pack-script](tool-build-bbs-pack-script.md) — build-bbs-pack.ps1: core+4 аддона 1.20.x -> Desktop\bbs_pack.

## IRL-redactor

### Тени (оркестрация физически в irl-core)
- [plan-irl-core-shadow-extraction](plan-irl-core-shadow-extraction.md) — КАНОН теней: оркестрация в irl-core + шов ShadowCasterSource + 5 инвариантов; Ф4 тираж на порт-ветки открыт.
- [project-shadow-bake-perf-audit](project-shadow-bake-perf-audit.md) — живой док перфа бейка; Tier-1/2 done; открыт только C10 per-face block-cull.
- [addon-shadows](addon-shadows.md) — референс бейк-движка (ShadowBaker/Renderer, пресеты, кэш, cull) + open anim-token freeze (global caster cap поднят до bounded nearest-128 2026-07-13).
- [fix-shadow-depthstate-repin](fix-shadow-depthstate-repin.md) — ре-пин depth/blend/матриц перед emit + feet-pivot AABB->сфера.
- [fix-shadow-slot-rank-stability](fix-shadow-slot-rank-stability.md) — фикс «прыгающих» теней при спросе>пула: rank-стабильность к повороту + spare-режим; НЕ закоммичен, ретест pending.
- [shadow-distance-quality-plan](shadow-distance-quality-plan.md) — качество на дали (Ф1-2 done, Ф3 open).
- [project-point-shadow-square-root-cause](project-point-shadow-square-root-cause.md) — корень «зернистого квадрата» = point 512 vs 1024 (D1); закрывается tier0=1024 (I5 проверит).
- [plan-shadow-lod-tiers](plan-shadow-lod-tiers.md) — LOD-тиры I1-I4 + caster fix закоммичены 2026-07-13 (`2e57f8d`/`700b60c`/`08df3f6`), I4 review PASS; I5 визуальный ретест pending; тираж отдельно по команде.
- [plan-point-shadow-atlas-merge](plan-point-shadow-atlas-merge.md) — дизайн 2026-07-13 + ИМПЛЕМ-ПЛАН 2026-07-16 (самодостаточен: 7 фаз, touch-точки с якорями, риски, 12 open gates, next-session prompt внутри). PointDepthAtlas, 30 точечных ламп (было 18). Блокирующие гейты решены юзером 2026-07-16 (рекомендации приняты) — старт кода разблокирован; код НЕ начат.
- [plan-cluster-heatmap-debug](plan-cluster-heatmap-debug.md) — IDEA, код НЕ начат: дебаг-heatmap для ClusterGridBuffer (число ламп на тайл цветом). ОТДЕЛЬНЫЙ трек от atlas-merge, готовый next-session prompt внутри.
- [plan-shadow-filtering-refactor](plan-shadow-filtering-refactor.md) — point-фильтрация ЗАВЕРШЕНА (MSM4+cube-view; чекпоинт 6668f22/e4935a0/57a7dfd); ревью PASS; open: overlay-перф, спот на MSM.
- [project-point-shadow-fix-backlog](project-point-shadow-fix-backlog.md) — бэклог А-Д (А done, Б-Д нет) + HANDOFF 2026-07-03 + Photon-дрейф решён (VL_NOISE цел в 6545101) + НЕ ТРОГАТЬ.

### Порты / редактор / движок / интеграции
- [project-port-1211](project-port-1211.md) — порт 1.20.4->1.21.11 (продакшн) + дельты 1.21.1/1.21.4 + карта API; 1.21.11 тени через capture-queue.
- [project-port-12111-refresh](project-port-12111-refresh.md) — линия 1.21.11 актуализирована и ЗАКРЫТА 2026-07-08.
- [project-trilogy-unify-11](project-trilogy-unify-11.md) — унификация 2026-07-08: per-version ядро, версия 1.1, пуш; коммиты внутри.
- [project-port-1214](project-port-1214.md) — линия 1.21.4 ЗАКРЫТА; configure 3-арг жив; yaw-drop/PositionColor per-mod.
- [project-port-1201](project-port-1201.md) — порт 1.20.1: только деп-матрица + LWJGL-пин, ноль правок .java.
- [project-irlite-base-ported](project-irlite-base-ported.md) — КАНОН движка+редактора: BBS-free свет (LightScene/PlacedLight/LightDriver) — feature-complete.
- [project-editor-vs-replay-screen-conflict](project-editor-vs-replay-screen-conflict.md) — редактор в Replay Mod (PASS); Фаза 3 курсор open.
- [project-flashback-irlights-plan](project-flashback-irlights-plan.md) — PLAN-only: аддон под Flashback replay; kill-switch = SSBO b7 под export.
- [project-imgui-axiom-collision](project-imgui-axiom-collision.md) — краш ImGui рядом с Axiom; try/catch+fallback.
- [project-auto-block-lights](project-auto-block-lights.md) — авто-свет от эмиссивных блоков; OFF по умолчанию; MAX_LIGHTS->2048.
- [project-gui-lag-gpu-bound-diagnosis](project-gui-lag-gpu-bound-diagnosis.md) — лаг GUI = GPU-bound; рычаг 1 = кластеризация (done). FrameProfiler.
- [project-spotlight-gobo-cookie-plan](project-spotlight-gobo-cookie-plan.md) — gobo/cookie done все версии; LRU для флипбуков done; per-pack recheck open.

### Forge / Sinytra Connector
- [project-forge-connector-compat](project-forge-connector-compat.md) — аддон на Forge 1.20.1 через Connector beta.48 (fmj fabricloader >=0.15.0).

### Референсы / правила работы
- [reference-bbs-fs-not-refreshed](reference-bbs-fs-not-refreshed.md) — референс BBS-кода = bbs-fs, не форк refreshed.
- [feedback-no-per-session-branch](feedback-no-per-session-branch.md) — НЕ создавать ветку под сессию.
- [feedback-memory-strict-style](feedback-memory-strict-style.md) — память в строгом LLM-стиле, ноль декора.
- [feedback-visual-test-image-prompts](feedback-visual-test-image-prompts.md) — визуальные проверки = image-gen промпт (EN, EXPECTED/REGRESSION).
- [reference-imgui-font-glyph-range](reference-imgui-font-glyph-range.md) — шрифт = Latin-1+кириллица; спецсимволы = тофу.
- [iris-source-library](iris-source-library.md) — исходники Iris: PRIMARY 1.20.1 + fallback 1.7.2-1.20.4.
- [ref-betterlights-shadow-comparison](ref-betterlights-shadow-comparison.md) — BetterLights vs IRLite.

## IRLite — ядро BBS-аддона
- [addon-architecture](addon-architecture.md) — всё через миксины; per-frame collect->bake->flush(SSBO7) до Iris.
- [addon-forms](addon-forms.md) — PointLightForm/SpotlightForm на BBS Form; маски->lightMask.
- [addon-light-collection](addon-light-collection.md) — SCANNER vs RENDER, дедуп; MAX_LIGHTS=2048.
- [fix-bone-attached-light-deadzone](fix-bone-attached-light-deadzone.md) — bone-свет: render-path забирает всегда.
- [addon-ui-config](addon-ui-config.md) — IrliteConfig, BBSSettings-категории, L10nMixin, гайды.
- [plan-interactive-spot-guides](plan-interactive-spot-guides.md) — интерактивные гайды спота; PASS+коммит 2026-07-02.
- [project-refactor-origin](project-refactor-origin.md) — IRLite = рефактор IRLEngine (uniform->SSBO7 + патчер).
- [commit-checkpoints](commit-checkpoints.md) — (feedback) коммиты только в чекпоинты по подтверждению; gitignore shaders/ -> git add -f.
- [feedback-addon-runclient-command](feedback-addon-runclient-command.md) — (feedback) рантайм ВСЕГДА runClient -Pmc=1.20.4, Git Bash, лог run/runclient-console.log в фоне.

## irl-core — общее ядро
- [patcher](patcher.md) — DSL .irlights (@target/@packversion/@marker, after/before/replace); validate-first. CONTRACT_VERSION=1.
- [addon-light-buffer-ssbo](addon-light-buffer-ssbo.md) — std430 LightBuffer: binding7, header 16B + 6×vec4/96б; MAX_LIGHTS=2048; GLSL зеркалит байт-в-байт.
- [plan-perf-fix-cluster-phase3](plan-perf-fix-cluster-phase3.md) — Phase 3 кластеризация DONE 2026-07-12 (binding 6, 67->112 FPS); статус-блоки внизу; тираж отложен.

## Шейдер-инжект — общие контракты
- [plan-lens-flare](plan-lens-flare.md) — PLAN-only lens flare; open: SSBO-слот.
- [shader-irlite-glsl](shader-irlite-glsl.md) — контракт irlite_lights.glsl: struct 6×vec4, #define-опции, per-light математика.
- [shader-shadow-sampling](shader-shadow-sampling.md) — GLSL-чтение теней; гард: vlParams.w<0 ДО int().
- [shader-volumetric](shader-volumetric.md) — волюметрика Beer-Lambert/HG; VL-noise done на CR, порт в 5 паков open.
- [plan-vl-refactor-research](plan-vl-refactor-research.md) — VL-рефактор МЕГА-ИССЛЕДОВАНИЕ 2026-07-17 (research-only, код НЕ начат): zero-recompile = runtime-глобалы вместо #define; связка CP1+CP2+CP4; Фаза 0 = Э1/Э2/Э7; полный отчёт+next-session prompt внутри.
- [shader-settings](shader-settings.md) — настройки в Iris UI; гоча: boolean #define только при голом #ifdef.
- [plan-irlights-settings-unification](plan-irlights-settings-unification.md) — единый дизайн настроек + ребрендинг DONE (3b3d79a).
- [addon-iris-integration](addon-iris-integration.md) — (ref) 2 миксина биндят тени (ProgramSamplersBuilder + SamplerBindingCubeArray).
- [ref-irlengine-photon-patch](ref-irlengine-photon-patch.md) — (ref) старый IRLEngine->Photon как образец; adapt uniform->SSBO.
- [sync-workflow](sync-workflow.md) — dev-цикл шейдеров (Original/Modification/patches/run; Shadres gitignored); комменты в патчах <=1 строка.

## Шейдер-паки — пайплайны (контракт + якоря + статус порта)
- [project-photon-outline-switch-to-old](project-photon-outline-switch-to-old.md) — КАНОН outline (Fresnel rim, default OFF); Photon = 2 патча.
- [outline-target-entity-detection](outline-target-entity-detection.md) — IRLITE_OUTLINE_TARGET + per-pack entity-метки; done 5 паков; гоча PatchLibrary.extracted глобальный open.
- [photon-pipeline](photon-pipeline.md) — Photon deferred, 4 хука; порт done (20 ops). Спутник [photon-bugfix](photon-bugfix.md).
- [photon-bugfix](photon-bugfix.md) — трекер Photon; WATCH bob-flicker acne.
- [shader-iterationrp-pipeline](shader-iterationrp-pipeline.md) — IterationRP #430 native SSBO, 3 хука; done; VL unshadowed.
- [complementary-pipeline](complementary-pipeline.md) — Complementary forward #130; done (21 ops); VL half-res deferred2.
- [rethinkingvoxels-pipeline](rethinkingvoxels-pipeline.md) — RethinkingVoxels (CR-форк); done (20 ops); дельты: composite.glsl, VL=colortex15.
- [bsl-pipeline](bsl-pipeline.md) — BSL v10 #120 CRLF; done (29 ops).
- [solas-pipeline](solas-pipeline.md) — Solas #130; done (19 ops, ru_RU); irislex.
- [bliss-pipeline](bliss-pipeline.md) — Bliss #120; done (16 ops); dual-hook; MVI[3] bobbing «exactly once».

## Пользователь
- Пользователя зовут Qualet.
