# Memory Index

## Правило загрузки памяти
- Эта инструкция предназначена исключительно для моделей ChatGPT. Если модель не является ChatGPT, она должна пропустить эту строку.
- В начале каждой сессии по этому проекту читать внешний источник памяти: `C:\Users\Qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-irlights\memory\MEMORY.md`.

СЕССИЯ 2026-07-18 (7, BLOCK-REBAKE GATE): триггер ребейка блок-теней огейчен, ЗАКОММИЧЕН (core 7ad518a / addon 598e497). Корень: сервер ресинкает оба interaction-блока ПОСЛЕ КАЖДОГО клика (даже пустой рукой) → same-state setBlockState → HEAD-миксин инвалидировал лампы до ванильного identity-отбоя. Фикс: (1) миксин аддона — old==state гейт (identity, стейты интернированы) + height-гейт; (2) core BlockShadowCache.invalidateChange + BlockShadowCollector.sameSilhouette — section-проверка ДО классификации, скип silhouette-neutral свопов (грасс→дёрн, печка lit, fluid-тики=INVISIBLE, листва distance=тот же baked model instance); зеркалит collectForLight, сомнение/throw=инвалидация. Старый invalidateAt(pos) жив (редактор на нём). ОТКРЫТО: тот же old==state гейт в миксин редактора (irlights WorldBlockChangeMixin) + тираж на порт-ветки — по команде.
СЕССИЯ 2026-07-18 (5, HALF-RES EVSM): пилот на CR DONE+ЗАКОММИЧЕН (core 01160ad / addon 6457312+c913299): ultra bake 34-42→17.8 ms, FPS 17-26→29-35, EVSM ×2.7 (atlas/4 через SpotlightDepthAtlas.evsmShift, ratio-aware гейт ТОЛЬКО в CR — остальные 6 паков на ultra гаснут в PCF до тиража); сплит pyr/evsm дал атрибуцию (25.3/4.3 из старых 29.6); +VRAM-телеметрия "[irlite] vram:" (NVX evictions); аномалия «линейная деградация → F11 сброс» запаркована. NEXT = partial-tile filter (Java-only). Детали: [plan-shadow-bake-track](plan-shadow-bake-track.md).
СЕССИЯ 2026-07-18 (4, BAKE Ф0): профайлер-разбивка бейка РЕАЛИЗОВАНА+e2e PASS+ЗАКОММИЧЕНА (core 4e4e490 / addon 06d7de9; editor кодом не менялся, пересобран) (core: ShadowBakeProbe/ShadowEngine/ShadowBaker+4 фильтра; addon: VlProfiler switchPass+counters, PASS_BAKE→"bake-head"); ревью 13 агентов = 1 фикс (off-by-one первого окна). Сегменты bake-spot/-spot-filter/-point/-point-filter/-tail + счётчики per-tier в "[irlite] bake:". ГЕЙТ ПРОЙДЕН: ultra-сцена 25 спотов → bake 34-42 ms (кап FPS), **spot-filter = 86%** (25 overlay-тайлов ре-фильтруются целиком каждый кадр), point=0 → Ф2 point-гранулярность НЕ рычаг этой сцены; кандидаты: partial-tile filter / half-res EVSM на ultra / cadence. C10-спайк 318 ms виден на загрузке. Детали+вердикт: [plan-shadow-bake-track](plan-shadow-bake-track.md).
СЕССИЯ 2026-07-18 (3, VL 3c BILATERAL): ЗАВЕРШЕНА+ЗАКОММИЧЕНА (core db0d3e2 / addon f8d2fb7 / editor 5dd3207). Bilateral upsample VL на CR (bit6 деф.ON, истинная view-Z метрика, fetch-bias +0.25, килл-свитч -Dirlite.vlNoBilateral); протокол юзера PASS: deferred2 3.40→0.97 ms (×3.5) на 0.5, bilateral +0.09 ms, края чистые; half=дефолт. ГОТЧА: prism-форк CR_IRLights+DOF = СТАРОЕ поколение IRLite, при тираже re-patch, НЕ слепой синк. NEXT SESSION = bake-трек (C10 + пропуск граней без кастеров). Детали: [plan-vl-3c-bilateral](plan-vl-3c-bilateral.md).
СЕССИЯ 2026-07-18 (2, ПРОФАЙЛЕР): задача A (чистка UI) ЗАКОММИЧЕНА addon ee0b145 / editor 16cfd21; VL-профайлер (GL-таймеры пассов + дифф-свип, -Dirlite.profileVl=true) РЕАЛИЗОВАН, ревью 9 фиксов, e2e-автотест PASS (quickplay-инфра в build.gradle) — НЕ ЗАКОММИЧЕН, ждёт подтверждения; загадка Hi-Z открыта до прогона юзера в тяжёлой сцене. Статус/готчи: [plan-vl-profiler](plan-vl-profiler.md).
СЕССИЯ 2026-07-17→18 (VL-РЕФАКТОР, главный трек): мега-исследование (36 агентов) → пилот на CR ЗАВЕРШЁН до 3b, ВСЁ ЗАКОММИЧЕНО (Fable-агенты разрешены). Итог: zero-recompile хребет (UBO b7, слайдеры BBS+ImGui), blue-noise, cluster-cull (+10 FPS), time-morph (деф.OFF), Hi-Z (страховка). Статусы/коммиты/готчи (loom-purge!): [plan-vl-refactor-research](plan-vl-refactor-research.md); далее: [plan-vl-3c-bilateral](plan-vl-3c-bilateral.md).
СЕССИЯ 2026-07-17 (2): баг «прыгающих теней» починен + ретест PASS; закоммичен вместе с atlas-merge (core eb3b229 / addon 38bf5e7+e11886e). Детали: [fix-shadow-slot-rank-stability](fix-shadow-slot-rank-stability.md).
СЕССИЯ 2026-07-16: atlas-merge point-теней РЕАЛИЗОВАН ЦЕЛИКОМ, I5 закрыт 07-17, закоммичен той же связкой. Статус: [plan-point-shadow-atlas-merge](plan-point-shadow-atlas-merge.md).
СЕССИЯ 2026-07-13 (2): octahedral/dual-paraboloid point-тени ЗАКРЫТЫ КАК НЕРЕАЛИЗУЕМЫЕ — НЕ ПЕРЕОТКРЫВАТЬ (бейк = ванильные per-RenderLayer шейдеры, только матричный трансформ → нелинейный warp невозможен; DPSM требует тесселяции). Атлас как хранение — реализован 07-16. Открыто: пропуск бейка граней без кастеров (sphereTouchesFace уже считает). Топик-файл удалён — эта строка = канон.
СЕССИЯ 2026-07-13: LOD-тиры I1-I4 + caster fix закоммичены (2e57f8d/700b60c/08df3f6); I5 закрыт 07-17; тираж на другие шейдеры — по команде. Детали: [plan-shadow-lod-tiers](plan-shadow-lod-tiers.md).
СЕССИЯ 2026-07-12: Phase 3 кластеризация DONE + PASS 67→112 FPS (binding 6). Детали: [plan-perf-fix-cluster-phase3](plan-perf-fix-cluster-phase3.md).
СЕССИЯ 2026-07-10 (3): Phase 2 core DONE (C1/C2/C3). Детали: [plan-perf-fix-core-phase2](plan-perf-fix-core-phase2.md).
СЕССИЯ 2026-07-10: перф-аудит (P0 = per-fragment цикл) → [project-perf-audit-irlite-2026-07-10](project-perf-audit-irlite-2026-07-10.md); Phase 1 лечения done, ЧЕКПОИНТ master a43d46b → [plan-perf-fix-cr-phase1](plan-perf-fix-cr-phase1.md).
СЕССИЯ 2026-07-08 (2): унификация трилогии ЗАКРЫТА (per-version ядро, версия 1.1, 13/13, запушено) → [project-trilogy-unify-11](project-trilogy-unify-11.md).
СЕССИЯ 2026-07-08: линия 1.21.11 ЗАКРЫТА (core 3527d63) → [project-port-12111-refresh](project-port-12111-refresh.md). Готча mavenLocal per-MC — в routing-файле.
Фаза 2026-07-02 (директива юзера): чиним ТОЛЬКО main/master; порт на ветки/редактор — в конце, строго по команде.
2026-07-03: point-стек сведён (MSM4+cube-view), Photon 30040cf PASS. HANDOFF: [project-point-shadow-fix-backlog](project-point-shadow-fix-backlog.md).
СЕССИЯ 2026-07-04: тираж фильтрации 6/6 (CR 69ecbda, RV f3b3f37, BSL 697373c, Solas 19e6b4e, Bliss 283256b). IterationRP ЛОКАЛЬНО/gitignored — коммит НЕ пере-предлагать.
СЕССИЯ 2026-07-06/07: линия 1.21.4 ЗАКРЫТА (1ce93fc/a88d05a) → [project-port-1214](project-port-1214.md).
СЕССИЯ 2026-07-06: порт 1.21.1 ЗАКРЫТ+ЗАКОММИЧЕН (80a3986/688afda/f85113e) → [plan-port-1211-workflow](plan-port-1211-workflow.md).
СЕССИЯ 2026-07-05: Tier1+2 выносы в core закоммичены (cf4ad94 v1.1 / 3c3ef3f / fa71093) → [plan-irl-core-library-extraction](plan-irl-core-library-extraction.md); editor e147571.

Объединённая база: 2 мода — IRLite (BBS-аддон), IRL-redactor (ImGui-редактор) — + ядро irl-core. Старт «поменяй X» -> [reference-edit-routing-by-area](reference-edit-routing-by-area.md).
Done-логи в `_archive/` — не индексируется. Инфра: 3 memory-дира = ОДИН склад через junctions -> [reference-memory-junctions](reference-memory-junctions.md).

АКТИВНО СЕЙЧАС: [plan-partial-tile-filter](plan-partial-tile-filter.md) — AABB-проекция + крутилка ЗАКОММИЧЕНЫ 2026-07-18 сессия 6 (core 0e236e2 / addon 7ae6626): ultra bake 16.0 → 12.9-13.0 ms, rect-share 36% → ~100%, coversMost → 15/16; клип на дефолте 0.9 РЕАЛЕН → слайдер «Shadow pose margin» (shadow_pose_reach 0..2, live) в BBS→irlite. ТЕМА ОТКРЫТА, ВЕРНЁМСЯ: калибровка дефолта по слайдеру юзера, вертикальная ось при нужде, апроны/lod (6-9 ms не добито), тираж на порт-ветки — список внутри.

## Маршрутизация и стратегия (читать первой)
- [reference-edit-routing-by-area](reference-edit-routing-by-area.md) — что-где менять (патчер+свет+тени=irl-core; caster/UI per-mod; .irlights owner IRLite); команды сборки.
- [project-github-repos](project-github-repos.md) — 3 приватных репо под owner quaIett (заглавная I); origin+ветки, gh CLI.
- [project-irl-sync-strategy](project-irl-sync-strategy.md) — карта дрейфа аддон<->редактор; универс-jar отменён -> per-MC.
- [plan-irl-core-library-extraction](plan-irl-core-library-extraction.md) — Tier1+2 выносы РЕАЛИЗОВАНЫ+ЗАКОММИЧЕНЫ 2026-07-05; core-API список внутри; Tier3 не делалось.
- [plan-port-1211-workflow](plan-port-1211-workflow.md) — порт 1.21.1 done 2026-07-06; статус-блок внутри.
- [tool-build-trilogy-script](tool-build-trilogy-script.md) — build-trilogy.ps1: трилогия на все MC -> Desktop\IRLights; per-MC core = publishToMavenLocal.
- [tool-build-bbs-pack-script](tool-build-bbs-pack-script.md) — build-bbs-pack.ps1: core+4 аддона 1.20.x -> Desktop\bbs_pack.

## IRL-redactor

### Тени (оркестрация физически в irl-core)
- [plan-irl-core-shadow-extraction](plan-irl-core-shadow-extraction.md) — КАНОН теней: оркестрация в irl-core + шов ShadowCasterSource + 5 инвариантов; Ф4 тираж на порт-ветки открыт.
- [project-shadow-bake-perf-audit](project-shadow-bake-perf-audit.md) — живой док перфа бейка; Tier-1/2 done; открыт C10.
- [plan-shadow-bake-track](plan-shadow-bake-track.md) — ПЛАН новой сессии: Ф0 профайлер-разбивка бейка (steady 3.4-4ms = overlay-цепочка Pyramid/EVSM z=6, спайки 320ms = C10) → C10 → per-face фильтры → BBS-probe статики; рекон-якоря и вердикты «не переоткрывать» внутри.
- [addon-shadows](addon-shadows.md) — референс бейк-движка (ShadowBaker/Renderer, пресеты, кэш, cull); open anim-token freeze; caster cap = nearest-128.
- [fix-shadow-depthstate-repin](fix-shadow-depthstate-repin.md) — ре-пин depth/blend/матриц перед emit + feet-pivot AABB->сфера.
- [fix-shadow-slot-rank-stability](fix-shadow-slot-rank-stability.md) — фикс «прыгающих» теней при спросе>пула: rank-стабильность + spare-режим; ЗАКОММИЧЕН, ретест PASS 2026-07-17.
- [shadow-distance-quality-plan](shadow-distance-quality-plan.md) — качество на дали (Ф1-2 done, Ф3 open).
- [project-point-shadow-square-root-cause](project-point-shadow-square-root-cause.md) — корень «зернистого квадрата» = point 512 vs 1024 (D1); закрыт tier0=1024.
- [plan-shadow-lod-tiers](plan-shadow-lod-tiers.md) — LOD-тиры I1-I4 + caster fix закоммичены 2026-07-13; тираж отдельно по команде.
- [plan-point-shadow-atlas-merge](plan-point-shadow-atlas-merge.md) — PointDepthAtlas 30 ламп; РЕАЛИЗОВАН+ЗАКОММИЧЕН 2026-07-16/17; имплем-план внутри = референс тиража.
- [plan-cluster-heatmap-debug](plan-cluster-heatmap-debug.md) — IDEA: дебаг-heatmap ClusterGridBuffer; prompt внутри.
- [plan-shadow-filtering-refactor](plan-shadow-filtering-refactor.md) — point-фильтрация ЗАВЕРШЕНА (MSM4+cube-view); open: overlay-перф, спот на MSM.
- [project-point-shadow-fix-backlog](project-point-shadow-fix-backlog.md) — бэклог А-Д (А done, Б-Д нет) + HANDOFF 2026-07-03 + НЕ ТРОГАТЬ.

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
- [project-gui-lag-gpu-bound-diagnosis](project-gui-lag-gpu-bound-diagnosis.md) — лаг GUI = GPU-bound; рычаг = кластеризация (done); его FrameProfiler ОТКАЧЕН (профайлер VL написан заново).
- [project-spotlight-gobo-cookie-plan](project-spotlight-gobo-cookie-plan.md) — gobo/cookie done; LRU done; per-pack recheck open.

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
- [addon-light-buffer-ssbo](addon-light-buffer-ssbo.md) — std430 LightBuffer: binding7, header 16B + 6×vec4/96б; MAX_LIGHTS=2048; GLSL зеркалит байт-в-байт. Рядом UBO IrliteVlGlobals — контракт в plan-vl-3c-bilateral.
- [plan-perf-fix-cluster-phase3](plan-perf-fix-cluster-phase3.md) — Phase 3 кластеризация DONE 2026-07-12 (binding 6, 67->112 FPS); статус-блоки внизу; тираж отложен.

## Шейдер-инжект — общие контракты
- [plan-lens-flare](plan-lens-flare.md) — PLAN-only lens flare; open: SSBO-слот.
- [shader-irlite-glsl](shader-irlite-glsl.md) — контракт irlite_lights.glsl: struct 6×vec4, #define-опции, per-light математика.
- [shader-shadow-sampling](shader-shadow-sampling.md) — GLSL-чтение теней; гард: vlParams.w<0 ДО int().
- [shader-volumetric](shader-volumetric.md) — волюметрика Beer-Lambert/HG; VL-noise done на CR, порт в 5 паков open.
- [plan-vl-refactor-research](plan-vl-refactor-research.md) — VL-рефактор: реализация до 3b ЗАКОММИЧЕНА 2026-07-18; тираж на 6 паков open; статусы/готчи/рекон 3c внутри.
- [plan-vl-profiler](plan-vl-profiler.md) — профайлер РЕАЛИЗОВАН+ЗАКОММИЧЕН 2026-07-18 (-Dirlite.profileVl=true); замеры+готчи внутри; Hi-Z закрыт (ALU-bound); чистка UI done.
- [plan-vl-3c-bilateral](plan-vl-3c-bilateral.md) — ПЛАН новой сессии: bilateral + полурез = главный рычаг VL; рекон/контракт/протокол внутри.
- [shader-settings](shader-settings.md) — настройки в Iris UI; гоча: boolean #define только при голом #ifdef.
- [plan-irlights-settings-unification](plan-irlights-settings-unification.md) — единый дизайн настроек + ребрендинг DONE (3b3d79a).
- [addon-iris-integration](addon-iris-integration.md) — (ref) 2 миксина биндят тени (ProgramSamplersBuilder + SamplerBindingCubeArray).
- [ref-irlengine-photon-patch](ref-irlengine-photon-patch.md) — (ref) старый IRLEngine->Photon как образец; adapt uniform->SSBO.
- [sync-workflow](sync-workflow.md) — dev-цикл шейдеров (Original/Modification/patches/run; Shadres gitignored); комменты в патчах <=1 строка.

## Шейдер-паки — пайплайны (контракт + якоря + статус порта)
- [project-photon-outline-switch-to-old](project-photon-outline-switch-to-old.md) — КАНОН outline (Fresnel rim, default OFF); Photon = 2 патча.
- [outline-target-entity-detection](outline-target-entity-detection.md) — IRLITE_OUTLINE_TARGET; done 5 паков; гоча PatchLibrary.extracted open.
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
