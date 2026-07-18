---
name: plan-vl-profiler
description: "ПЛАН на новую сессию (код НЕ начат): VL-профайлер — GL timer queries по пассам Iris + автоматический дифференциальный свип по live-флагам UBO; заодно чистка 4 UI-тумблеров и ответ на загадку Hi-Z +1 FPS."
metadata: 
  node_type: memory
  type: project
  originSessionId: 1a47d7b9-f46d-49f3-a5ce-79b7f33d8d56
---

# VL Profiler + чистка UI (план новой сессии, код НЕ начат)

СТАТУС 2026-07-18: план утверждён юзером (уровни 1+2 одобрены концептуально, чистка UI подтверждена через AskUserQuestion). Все фазы VL-рефактора до 3b включительно ЗАКОММИЧЕНЫ (3b: core 38e017c / addon a45c35a / editor cbcc76c), деревья чистые. Fable-агенты для кода РАЗРЕШЕНЫ юзером (сессия 2026-07-18).

## NEXT-SESSION PROMPT
«Делаем по plan-vl-profiler: сначала задача A (чистка UI, маленькая, свой коммит), затем задача B (профайлер). Рантайм-проверки через runClient -Pmc=1.20.4».

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
