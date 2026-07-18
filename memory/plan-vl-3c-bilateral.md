---
name: plan-vl-3c-bilateral
description: "ПЛАН на новую сессию (код НЕ начат): 3c — depth-aware bilateral upsample VL (colortex10) + переход на RESOLUTION 0.5. По данным профайлера это ГЛАВНЫЙ рычаг VL-пасса (~3-4× срез полного марша). Весь рекон, контракт, протокол проверки и готчи внутри."
metadata: 
  node_type: memory
  type: project
  originSessionId: 1a47d7b9-f46d-49f3-a5ce-79b7f33d8d56
---

# VL 3c: bilateral upsample + полурез (план новой сессии, код НЕ начат)

СТАТУС 2026-07-18: план утверждён юзером. Все предыдущие фазы VL-рефактора ЗАКОММИЧЕНЫ (последние: профайлер addon 4197173, морф-дефолт-OFF core b4a9988 / addon 4baaf40 / editor 5e85fb0). Деревья чистые. Fable-агенты для кода разрешены. Пилот = Complementary ONLY (тираж отдельно).

## NEXT-SESSION PROMPT
«Делаем по plan-vl-3c-bilateral: depth-aware bilateral upsample для VL + валидация полуреза. Рантайм-проверка = профайлер (-Dirlite.profileVl=true) до/после + визуальный тест краёв».

## ПОЧЕМУ ЭТО ГЛАВНЫЙ РЫЧАГ (данные профайлера 2026-07-18, реальная сцена юзера)
deferred2 (VL-марш) на RESOLUTION **1.0** (так у юзера) = 2.761 ms; «голый марш» без единого тапа = 1.494 ms (54% = ALU-пол). ВСЁ это масштабируется с числом пикселей → 0.5-рез режет ~в 4× число пикселей марша: ожидание ~0.8-1.0 ms вместо 2.76. Это больше, чем все точечные оптимизации вместе (cluster-cull 0.44 ms, Hi-Z 0.18 ms, morph 0.36 ms). Bilateral делает полурез визуально бесплатным (убирает ореолы на краях). Разбивка для справки: тени 1.486 ms (53.8%), шум 0.164, морф 0.361 (теперь деф. OFF), Hi-Z экономит 0.182, cluster-cull 0.441. Bake теней 3.4-4 ms GPU — ОТДЕЛЬНЫЙ будущий трек (C10 per-face cull + пропуск граней без кастеров), НЕ этот.

## РЕКОН (верифицирован 2026-07-18, content-якоря; номера строк могли дрейфнуть)
- **Композит-сайт**: `Shadres/Modification/ComplementaryReimagined/shaders/program/composite1.glsl` ~:344-354, гейт `#if defined IRLITE_ACTIVE && defined IRLITE_VOLUMETRIC`: `vec3 irliteVL = texture2D(colortex10, texCoord).rgb;` — ПРОСТОЙ bilinear, в LINEAR-пространстве ПОСЛЕ `color = pow(color, vec3(2.2))` (:342), затем underwater mult / lava zero, затем `color += irliteVL;`. composite1 = full-res (DRAWBUFFERS:0). colortex10 = RGB16F (`lib/pipelineSettings.glsl:11`), nearest-флага нет.
- **Согласованная глубина на сайте**: `float z1 = texelFetch(depthtex1, texelCoord, 0).r;` (composite1 ~:105-106) — deferred2 марширует по OPAQUE-глубине (его depthtex0 на deferred-стадии = opaque; «beams deliberately continue behind translucents»), в composite1 согласованная = **depthtex1** (НЕ z0!). Линеаризатор в том же файле (~:51-53): `float GetLinearDepth(float depth) { return (2.0*near)/(far+near-depth*(far-near)); }`. Рядом есть viewPos1/lViewPos1 через gbufferProjectionInverse (~:196-199) и DH/Voxy min-merge dhDepthTex1/vxDepthTexOpaque (~:202-214) — учесть при выборе метрики.
- **Готового bilateral в паке НЕТ** (grep bilateral|depthWeight|edgeWeight = 0). Ближайший образец идиомы reject: `lib/materials/materialMethods/reflectionBlurFilter.glsl` (normal-similarity reject + дремлющий depth-reject `abs(GetLinearDepth(...)-linearZ0)*far > 2.0`). Образец gap-fix: composite1 ~:146-160 (4-диагональный re-sample для half-res отражений, не depth-aware).
- **RESOLUTION-плумбинг**: `#define IRLITE_VL_RESOLUTION 0.5 // [1.0 0.5 0.25]` (lib :41 — ВНИМАНИЕ: дефайн-дефолт 0.5, но у юзера в Iris-конфиге стоит 1.0); `size.buffer.colortex10 = IRLITE_VL_RESOLUTION IRLITE_VL_RESOLUTION` (shaders.properties:133); program-тумблеры :121-123. RESOLUTION структурен (buffer alloc) — остаётся компильным, переключение через экран Iris с F3+R (одноразово).
- **Reduced-res готчи deferred2** (уже дважды кусали): texCoord = full-screen uv (noperspective, deferred2:28), gl_FragCoord = pass-res, viewWidth/viewHeight = full-res; прецедент решения = uv-параметризованный irlite_clusterMaskFetch(vec2 uv) в либе.
- **Дизер-сайт** (для опционального stretch-пункта): deferred2.glsl :56-68, blue-noise texelFetch по gl_FragCoord & 127, дизер входит ТОЛЬКО стартовым офсетом марша; per-step стратификация конфликтует со stride-кэшами shadowVis/noiseVal.

## КОНТРАКТ (заморозить в начале сессии, детали дорешает имплементация)
1. **Bilateral upsample в composite1** вместо простого texture2D: 4 соседних texel-центра low-res colortex10 (координаты через textureSize(colortex10,0)), вес = bilinear-вес × depth-вес; depth-вес = гауссиан/rational от |GetLinearDepth(z1_на_uv_центра_сэмпла) − GetLinearDepth(z1_текущего)| (глубина сэмпла = depthtex1 В ТОЧКЕ uv low-res центра — отдельного low-res depth НЕ существует и НЕ нужен); нормировка суммой весов; **degenerate-фолбэк**: если сумма depth-весов < eps (суб-тапная геометрия: заборы/листва) → чистый bilinear (как сейчас). Сигма глубины — в блоках, стартовое ~1.0-2.0.
2. **Управление**: flags bit6 = bilateral enable, DEFAULT ON, БЕЗ UI-тумблера (политика чистки UI 2026-07-18: технические фичи без ручек; core деф. флагов 0x3F → 0x7F). Сигму МОЖНО положить в свободный irlite_vlD.y (0 = использовать дефолт), UI-слайдер НЕ добавлять без просьбы юзера.
3. **bit6-off путь бит-идентичен** текущему bilinear (регрессионный гейт, как во всех фазах).
4. Пилот CR; Photon при тираже = MISS by design (нет RESOLUTION, нативный quarter-res fog — из mega-research).
5. **Рекомендация юзеру по итогам**: переключить IRLITE_VL_RESOLUTION 1.0 → 0.5 в экране Iris (одноразовый F3+R) — с bilateral это и есть главный выигрыш. 0.25 = opt-in per-shot (вердикт judges: на 1/16 тонкие лучи могут регрессировать даже с bilateral).
6. **Опциональный stretch** (если сессия идёт легко): stratified per-step jitter БЕЗ tent-реконструкции (вердикт judges mega-research: jitter отгружать один, tent прототипировать отдельно); помнить конфликт со stride-кэшами — стратифицировать только march-позицию, кэш-каденции не трогать.

## ПРОЦЕСС (выжимка всех готч сессий 2026-07-17→18)
- GLSL правится в `bbs-irlights-addon/Shadres/Modification/ComplementaryReimagined/` (composite1.glsl; либа только если нужен общий хелпер/флаг-бит — блок UBO уже объявлен в либе и включён в composite1? ПРОВЕРИТЬ: UBO-блок гейтится IRLITE_ACTIVE в либе, composite1 включает либу — да, но убедиться что irlite_vlC доступен в composite1-TU; если либа не включена в composite1 — объявить блок локально по контракту из VlGlobalsBuffer.java javadoc).
- Реген: `tools/gen-complementary-patch.ps1` → patches/complementaryreimagined.irlights; byte-proof: `java -cp tools/build PatchHarness patches/complementaryreimagined.irlights Shadres/Original/ComplementaryReimagined <scratch>/out` + `git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol <out>/shaders Shadres/Modification/.../shaders` = ПУСТО (известный residue: 1 CR-строка en_US.lang в strict-режиме без флага — игнорить). Синк либы/composite1 в run/shaderpacks/ComplementaryReimagined_IRLights И C:/prismlauncher/instances/BBS/minecraft/shaderpacks/... (md5-verify), затем `powershell -File C:/Users/Qualet/Documents/Project/Minecraft/BBS/irlights/tools/copy-patches.ps1` (pwsh НЕ существует).
- Java-правка core (bit6 деф.) → publishToMavenLocal → ПУРЖ loom-кэшей: аддон `.gradle/loom-cache/remapped_mods/remapped/org/qualet/irl-core-a73a62ac/1.2`, редактор `.gradle/loom-cache/remapped_mods/net_fabricmc_yarn_1_20_4_1_20_4_build_1_v2/org/qualet/irl-core/1.2`. Лок-ошибка кэша («файл занят другим процессом») = осиротевший демон → `gradlew --stop` в ТОМ репо, повторить.
- JAVA_HOME = C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot для ВСЕХ gradlew. Сборки: core publish; addon `build -Pmc=1.20.4`; editor `build`. Рантайм: `runClient -Pmc=1.20.4` (Git Bash, лог run/runclient-console.log, фоном).
- **Числа до/после — ПРОФАЙЛЕРОМ**: `-Dirlite.profileVl=true` (или автотест `./gradlew runClient -Pmc=1.20.4 -Pquickplay="Testing" -PclientJvmArgs="-Dirlite.profileVl=true"`); сравнивать медиану deferred2. Каналы: лог = правда, чат = итог свипа, HUD = live.
- Коммиты только чекпоинтами по подтверждению; память → repo memory/ → отдельный «memory:»-коммит; индекс MEMORY.md держать < ~17.5KB.

## ПРОТОКОЛ ПРОВЕРКИ (юзер в игре)
1. RESOLUTION 0.5 БЕЗ bilateral (bit6 off или до правки): запомнить вид краёв лучей на силуэтах (столбы/персонажи поверх луча) — ореолы/лесенка = базовая боль.
2. RESOLUTION 0.5 С bilateral: края чистые, ореолы ушли; заборы/листва в луче — без артефактов (degenerate-фолбэк).
3. Профайлер: deferred2 медиана на 1.0 vs 0.5+bilateral (ожидание ~2.76 → ~0.8-1.0 ms в той же сцене).
4. bit6 toggle (временно через код/свип, UI нет): картинка на 0.5 меняется ТОЛЬКО на краях (bilateral работает), на 1.0 — не меняется вовсе (при равных res bilinear≈bilateral, допускается бит-неидентичность только на краях).

## Контракт UBO (актуальный, зеркало VlGlobalsBuffer.java)
std140 binding 7 (64Б): vec4 irlite_vlA (intensity,maxDist,tipBoost,tipRadius); vec4 irlite_vlB (noiseAmount,noiseScale,noiseSpeed,frameIndex wrap4096); uvec4 irlite_vlC (stepMax,shadowStride,noiseStride,flags: bit0 shadows / bit1 noise / bit2 blueNoise / bit3 ditherTemporal / bit4 clusterCull / bit5 hiZSkip / bit6 → bilateral (этой фазой)); vec4 irlite_vlD (x noiseMorph деф.0, y → сигма bilateral (этой фазой; 0=дефолт), z/w reserved).

Связь: [[plan-vl-refactor-research]] (все фазы, mega-research вердикты по bilateral/stratified), [[plan-vl-profiler]] (инструмент замера + замеры 2026-07-18), [[sync-workflow]], [[reference-edit-routing-by-area]].
