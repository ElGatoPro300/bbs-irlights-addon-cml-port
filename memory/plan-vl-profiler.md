---
name: plan-vl-profiler
description: "VL-профайлер РЕАЛИЗОВАН 2026-07-18 (сессия 2): задача A (чистка UI) ЗАКОММИЧЕНА addon ee0b145 / editor 16cfd21; задача B (профайлер: GL-таймеры пассов + дифф-свип) реализована, ревью 9 фиксов, end-to-end PASS автотестом quickplay — НЕ ЗАКОММИЧЕНА, ждёт подтверждения юзера. Готчи и результаты внутри."
metadata: 
  node_type: memory
  type: project
  originSessionId: 1a47d7b9-f46d-49f3-a5ce-79b7f33d8d56
---

# VL Profiler + чистка UI

## СТАТУС 2026-07-18 (сессия 2, выполнение)
- **Задача A (чистка UI) DONE + ЗАКОММИЧЕНА**: addon ee0b145 (BBSSettingsMixin −4 регистрации, L10nMixin −8 ключей), editor 16cfd21 (LightEditorPanel −3 toggleRow+поля, en/ru lang −3 ключа; у shader_light_clustering в редакторе UI-строки НИКОГДА не было). Аккессоры/поля/LightDriver/LightCollector не тронуты, поведение = всегда-вкл (null-регистрация→true). vl_dither_temporal оставлен видимым. Обе сборки зелёные.
- **Задача B (профайлер) РЕАЛИЗОВАНА, НЕ ЗАКОММИЧЕНА** (uncommitted в аддоне; ядро и редактор НЕ тронуты — republish/loom-purge не нужен). Ревью-воркфлоу (14 агентов): 9 подтверждённых находок починены (1 блокер), 1 рефьют. End-to-end автотест PASS: quickplay в мир Testing, свип отработал сам, таблица в лог+чат, конфиг восстановлен, лог чист.
- Файлы: NEW addon diag/VlProfiler.java + diag/VlSweep.java + mixin/client/iris/CompositeRendererTimerMixin.java; EDIT GameRendererLightMixin (frameTick+брекет бейка), LightCollector (override-хук после UBO-пуша), IrliteClient (HUD-гейт), irlite.client.mixins.json, build.gradle (loom runs.client: -Pquickplay / -PclientJvmArgs).
- Активация: `-Dirlite.profileVl=true`. Автотест-команда: `./gradlew runClient -Pmc=1.20.4 -Pquickplay="Testing" -PclientJvmArgs="-Dirlite.profileVl=true"` (лог run/runclient-console.log). Каналы вывода (решение юзера в чате): лог = источник правды, чат = итог свипа, HUD = live per-pass ms; BBS UI не трогаем.
- **Замер спавн-сцены Testing** (тривиальная, deferred2 0.494 ms): shadows 58% стоимости марша, noise/morph ~0, Hi-Z/cluster-cull ~0 (нечего скипать), bare march 0.205 ms; shadow-bake avg 0.12-0.25 ms (первый кадр 120 ms — lazy alloc, ожидаемо). **Загадка Hi-Z НЕ закрыта** — нужен прогон юзера в тапо-тяжёлой сцене (8+ спотов), инструмент готов.

## ГОТЧИ (сессия 2)
- **FrameProfiler НЕ СУЩЕСТВОВАЛ** — был откачен в треке gui-lag (память утверждала «каркас есть» — ложь); написан с нуля. Прецедент = profileShadows в ShadowBaker (System.out, окно 1 с).
- **Ванильный GlTimer**: при открытом F3 (GPU% дебага) MC оборачивает весь кадр в свой GL_TIME_ELAPSED → чужой begin травил бы пул навсегда. Фикс: проверка GL_CURRENT_QUERY до/после begin + сторож STUCK_FRAMES=120 в дрейне.
- Iris 1.7.2: у Pass/Program НЕТ имени — карта Program→имя строится инжектом на RETURN createProgram (ProgramSource.getName), WeakHashMap. Редиректы renderAll (Program.use / FullScreenQuadRenderer.renderQuad, пакет net.irisshaders.iris.pathways!) с require=0 expect=1 — деградация в no-op вместо краша при дрейфе Iris.
- «Пасс поверхностного света» на CR НЕ существует (mainLighting.glsl вшит в gbuffers-программы) — surface-таймер невозможен как пасс; таймится deferred2/composite1/бейк/все пассы по именам.
- Бейк строго ДО пассов Iris (renderWorld HEAD) → брекеты-сиблинги, нестинга нет; брекет вокруг FramePipeline.frame() меряет только GL бейка (collect/prioritize без GL).
- Свип: атрибуция сэмплов по кадру ВЫДАЧИ запроса (лаг чтения 2-3 кадра не смазывает конфиги), SKIP=20/конфиг, DRAIN_GRACE=10 перед отчётом, STALL_FRAMES=120 → ре-арм при потере VL-пасса. Восстановление автоматическое (LightCollector перепушивает конфиг каждый кадр). Legacy SSBO-header флаги свип НЕ переопределяет — для CR это верно (патч читает только UBO irlite_vlC.w).

## ЗАМЕР ЮЗЕРА В РЕАЛЬНОЙ СЦЕНЕ 2026-07-18 (свип deferred2, baseline 2.761 ms, n=100/конфиг)
shadows 1.486 ms (53.8%), noise 0.164 ms (6.0%), morph 0.361 ms (13.1%), Hi-Z saves 0.182 ms (6.6%), cluster-cull saves 0.441 ms (16.0%), bare march 1.494 ms (54% = ALU-пол). В более тяжёлом ракурсе той же сессии deferred2 доходил до 8.2 ms avg; shadow-bake стабильно 3.4-4.0 ms avg GPU (заметный потребитель, вне свипа).
**ЗАГАДКА Hi-Z ЗАКРЫТА = гипотеза (б)**: марш ALU-bound (голый марш без единого тапа = 54% стоимости), теневые тапы всего 1.49 ms, и Hi-Z скипает лишь ~12% их стоимости (0.18 из 1.49) → потолок выигрыша мал by construction. 0.18 ms ≈ +1-2 FPS при 70-90 FPS — совпадает с замером «+1 FPS» из 3b. Hi-Z = страховка, оставлен ON (решение юзера в 3b подтверждено данными). Чинить гарды смысла нет — даже идеальный Hi-Z ограничен 1.49 ms.

## NEXT
1. Коммит задачи B по подтверждению юзера (чекпоинт) + memory-коммит.
2. Дальше по plan-vl-refactor-research: 3c bilateral (рекон готов), затем тираж. Инсайт для приоритетов: cluster-cull уже даёт 16%, тени = главный рычаг (54% марша), bake 3.4-4 ms GPU — кандидат в оптимизацию отдельным треком.

## Задача A — чистка UI (маленькая, отдельный коммит)
Убрать 4 технических тумблера из UI ОБОИХ модов (поведение = всегда-вкл): shader_light_clustering, vl_blue_noise, vl_cluster_cull, vl_shadow_hiz.
- Аддон: удалить 4 регистрации в BBSSettingsMixin + 8 l10n-ключей в L10nMixin. Аккессоры в IrliteConfig ОСТАВИТЬ (null-регистрация → default true — поведение всегда-вкл автоматически). LightCollector НЕ трогать.
- Редактор: удалить 4 toggleRow из LightEditorPanel vlGroup + ключи из en_us/ru_ru. Поля LightConfig ОСТАВИТЬ (=true). LightDriver НЕ трогать.
- «vl_dither_temporal» ОСТАВИТЬ видимым в обоих модах (творческая ручка, риск ряби на записи без TAA — решение юзера).
- Флаги/биты в core НЕ трогать вообще.

## Задача B — VL Profiler
### Уровень 1: GL timer queries по пассам
Миксин вокруг выполнения программ Iris: замерить GPU-время (GL_TIME_ELAPSED, glBeginQuery/glEndQuery) минимум для: deferred2 (наш VL-марш), пасс поверхностного света (mainLighting = gbuffers/deferred — уточнить реконом, где инжект), наш shadow-бейк (mod-side, там свой GL). Асинхронное чтение результатов (GL_QUERY_RESULT_AVAILABLE, пул query-объектов, читать с лагом 2-3 кадра — НЕ стопорить конвейер). Прецеденты миксинов Iris у нас: ProgramSamplersBuilderMixin, SamplerBindingCubeArrayMixin, CapturedRenderingStateClusterMixin (bind timing binding-6). Рекон: класс Iris, гоняющий composite/deferred пассы (pipeline пакет, Source-libary/Iris-1.20.1) — обернуть per-pass рендер. Каркас FrameProfiler уже существует (появился в треке project-gui-lag-gpu-bound-diagnosis) — найти и переиспользовать/расширить.
### Уровень 2: дифференциальный свип по live-флагам (уникально разблокирован zero-recompile архитектурой)
Мод сам циклит конфиги флагов UBO кадр-за-кадром (каждый конфиг K кадров, брать медиану замера уровня 1 для deferred2): baseline всё-вкл → −shadows (bit0) → −noise (bit1) → −morph (vlD.x=0) → −Hi-Z (bit5) → −clusterCull (bit4) → выключенный VL-shadows целиком как «потолок». Итог — таблица в лог (и/или ImGui-панель редактора): «VL total X ms; тени Y ms; шум Z ms; морф W ms; Hi-Z экономит V ms». Свип запускать по хоткею/кнопке/системному флагу (-Dirlite.profileVl=true — по образцу существующего -Dirlite.profileShadows=true). После свипа флаги вернуть в пользовательские значения (читать из конфигов, НЕ кэшировать вслепую).
### Контракт данных (актуальный, из VlGlobalsBuffer)
std140 UBO binding 7 (64Б): vec4 irlite_vlA (intensity,maxDist,tipBoost,tipRadius); vec4 irlite_vlB (noiseAmount,noiseScale,noiseSpeed,frameIndex wrap 4096); uvec4 irlite_vlC (stepMax,shadowStride,noiseStride,flags: bit0 shadows, bit1 noise, bit2 blueNoise, bit3 ditherTemporal, bit4 clusterCull, bit5 hiZSkip); vec4 irlite_vlD (noiseMorph 0..3, yzw reserved). Свип может дергать VlGlobalsBuffer.set(...) с изменёнными флагами per-frame — канал уже per-frame, ничего нового в GLSL НЕ требуется.
### Что профайлер должен объяснить
Загадка Hi-Z: +1 FPS даже в тапо-тяжёлой сцене (8+ перекрывающихся спот-лучей, steps 64, stride 1, noise off). Гипотезы: (а) классификация редко срабатывает (гарды в irlite_lights.glsl, блок «Phase 3b spot Hi-Z segment skip»; narrow-cone member уже ослаблялся 1e-3→1e-4); (б) тапы дёшевы, марш ALU-bound. Свип «−shadows» даст потолок сразу. Если (а) — чинить гарды; если (б) — задокументировать «Hi-Z = страховка» в plan-vl-refactor-research. Чип task_1eaddadd (дебаг-тинт) СУПЕРСИДЕН этим планом.

## Build/процесс (готчи)
- JAVA_HOME = C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot, ВСЕ gradlew с ним.
- core: gradlew publishToMavenLocal; затем ПУРЖ loom-кэшей (same-version 1.2 не подхватывается): аддон .gradle/loom-cache/remapped_mods/remapped/org/qualet/irl-core-a73a62ac/1.2; редактор .gradle/loom-cache/remapped_mods/net_fabricmc_yarn_1_20_4_1_20_4_build_1_v2/org/qualet/irl-core/1.2. Если лок-ошибка кэша — gradlew --stop.
- аддон: gradlew build -Pmc=1.20.4; редактор: gradlew build. Рантайм: runClient -Pmc=1.20.4, Git Bash, лог run/runclient-console.log, фоном.
- Если правится GLSL (для профайлера НЕ ожидается): реген tools/gen-complementary-patch.ps1 + byte-proof (java -cp tools/build PatchHarness ... + strict diff --ignore-cr-at-eol) + синк run+Prism + powershell -File irlights/tools/copy-patches.ps1 (pwsh НЕ существует).
- Коммиты только чекпоинтами по подтверждению юзера; память синкается в repo memory/ и коммитится отдельным «memory:»-коммитом.

Связь: [[plan-vl-refactor-research]] (статус всех фаз + рекон 3c bilateral — СЛЕДУЮЩИЙ трек после профайлера), [[addon-iris-integration]], [[reference-edit-routing-by-area]].
