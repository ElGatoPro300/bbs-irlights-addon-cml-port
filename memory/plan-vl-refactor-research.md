---
name: plan-vl-refactor-research
description: "Мега-исследование рефактора VL 2026-07-17 (wf_65d83526-4c8, 36 агентов Opus, 0 ошибок): zero-recompile = демоция #define в runtime-данные (SSBO header/UBO/CommonUniforms-mixin); победившая связка CP1+CP2+CP4; полный отчёт внутри; код НЕ начат."
metadata: 
  node_type: memory
  type: project
  originSessionId: 1a47d7b9-f46d-49f3-a5ce-79b7f33d8d56
---

# VL-рефактор: мега-исследование (research-only, код НЕ начат)

СТАТУС 2026-07-17: исследование ЗАВЕРШЕНО. РЕАЛИЗАЦИЯ НАЧАТА (юзер разрешил Fable 5 код-агентов). **Э1 DONE + IN-GAME PASS** (wf_91175d66-817, 4 агента, ревью 0 blocker/major): header offset 4 (бывш. irlite_pad0) = float irlite_vlIntensityRt; core LightBuffer.setVlGlobalIntensity (кламп >=1e-6, NaN-safe тернарник), слайдер BBS "vl_intensity" 0-5 деф.1.0, per-frame push в LightCollector.collect(); CR GLSL guarded fallback на #define; патч реген MD5 6def6c0b, byte-proof PASS 21 ops, run+prism синк; editor-синк (copy-patches) ОТЛОЖЕН до Фазы 1. Iris-слайдер Beam Intensity теперь ИНЕРТЕН при моде (by design). **Э2 DONE + ЗАМЕР ЮЗЕРА** (wf_590acf92-0ce, 3 агента, ревью 0 находок): header offset 8 (бывш. irlite_pad1) = uint irlite_vlFlagsRt (bit0 VL-shadows, bit1 VL-noise), LightBuffer.setVlFlags деф.0x3, BBS-тумблеры vl_shadows_live/vl_noise_live, throwaway-пак run/shaderpacks/..._E2 (A нетронут, MD5-пруф). ЦИФРЫ: A-ON 72 / B-ON 72 (ветки БЕСПЛАТНЫ) / B-runtime-OFF 140 / compile-OFF 152 (резидентность ~8%, 0.56мс). РЕШЕНИЕ: числа → runtime безоговорочно; тяжёлые тумблеры → ДВОЙНОЙ ГЕЙТ (#define остаётся + runtime-флаг внутри, как в _E2). Побочка: VL тени+шум = ~половина frame-time сцены → приоритет Hi-Z/cluster-cull подтверждён. ФАЗА 0 ЗАКРЫТА, чекпоинт: core 700f255 / addon 32f52d2 / memory ba0dc7d.
**ФАЗА 1 CP1 DONE + IN-GAME PASS 2026-07-18**, чекпоинт: core 468610d / addon 4e9e90d / editor 13cb0df (wf_1b07a4ca-7a3, 9 агентов). Механика: std140 UBO **binding 7** (UBO-неймспейс отдельный от SSBO-7; survey: индекс свободен во всех 7 паках+Iris) = vec4 irlite_vlA(intensity,maxDist,tipBoost,tipRadius) + vec4 irlite_vlB(noiseAmount,noiseScale,noiseSpeed,reserved0) + uvec4 irlite_vlC(stepMax,shadowStride,noiseStride,flags bit0-shadows/bit1-noise). Core VlGlobalsBuffer: контракт-сеттер 11 арг, кламп intensity>=1e-6 NaN-safe, **quantize noiseSpeed 0.25 (wind-инвариант)**, clamp steps [1,96]/strides [1,8], дефолты=CR-дефайны, upload() из LightBuffer.upload(), delete() из delete(). Аддон: 9 новых опций vl_steps/max_dist/shadow_stride/tip_boost/tip_radius/noise_amount/noise_scale/noise_speed/noise_stride (ranges из sliders-листа пака), один VlGlobalsBuffer.set-push в collect(), legacy header-пуши сохранены (снять при тираже). Редактор: LightConfig 12 полей + LightDriver push в collect() + LightEditorPanel vlGroup (ru+en lang), core dep 1.1->1.2. GLSL CR: все числовые дефайны->UBO (adaptive-steps формула алгебраически та же), dual-gate из Э2 (флаги из irlite_vlC.w), #define-экран Iris цел но ИНЕРТЕН, legacy header-члены объявлены/не читаются; +`#extension GL_ARB_uniform_buffer_object` (страховка ревью; UBO скомпилирован Iris с первого раза, лог чист). Патч MD5 **78953d30**, byte-proof PASS (21 ops), синк run+prism+editor-бандл (copy-patches 7/7 match), _E2-пак и его txt УДАЛЕНЫ, run/config/iris.properties возвращён на основной пак, bin/patch-harness перекомпилирован под core 1.2.
**ГОТЧА (build-infra):** same-version republish irl-core НЕ подхватывается аддоном — loom кэширует remapped-jar по GAV в .gradle/loom-cache/remapped_mods/.../irl-core-*/1.2; лечение = удалить каталог версии в кэше. 1.20.1-линия (ceaf4ab3) пуржена — перед любой 1.20.1-сборкой сначала republish core её ветки.
Принятые отклонения: двойной 0.25-снап noiseSpeed в панели редактора (идемпотентен, лучше UX), drag-readout показывает сырое значение до отпускания.
ДАЛЬШЕ: Фаза 2 = CP2 blue-noise dither (STBN/void-and-cluster 128² R8 через IrlSamplers «irl_blueNoise», frameIndex в irlite_vlB.w, флаги bit2 blue-noise-on/bit3 temporal-rotation; Э4 = статик TAA-off + видео движущейся лампы). Prism jars СТАРЫЕ (Jul 5) — тесты только через runClient. Тираж на 6 паков — отдельно по команде.
Provenance: workflow wf_65d83526-4c8 (6 фаз: Recon 4 / Research 5 / Brainstorm 4×24 raw / Consolidate 10 canon / Verify 2 судьи×10 / Synthesize), journal: `C:\Users\Qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-bbs-irlights-addon\1a47d7b9-f46d-49f3-a5ce-79b7f33d8d56\subagents\workflows\wf_65d83526-4c8\journal.jsonl` (полные дайджесты R1-R4/W1-W5, все 24 raw-предложения, вердикты судей).

## NEXT-SESSION PROMPT
«Фаза 0 VL-рефактора: go/no-go эксперименты из plan-vl-refactor-research. Э1: один глобал (IRLITE_VL_INTENSITY) в header binding-7 на Complementary — слайдер двигает без F3+R + FPS-дельта. Э2: де-пермутация #ifdef SHADOWS/NOISE/COOKIE→runtime if() на CR, frametime A/B everything-ON (occupancy). Э7: byte-proof surface-pass на выросшем header (при сомнении → pivot std140 UBO). Пути: irl-core LightBuffer.java (HEADER_BYTES=16, 12Б pad свободны), LightCollector.collect() seam (setUploadCap-паттерн), patches/complementaryreimagined.irlights (march L1325-1514, steps уже runtime-bound L1387).»

Связь: [[shader-volumetric]] (текущий VL), [[shader-settings]] (Iris-опции = recompile-стена), [[addon-light-buffer-ssbo]] (binding 7), [[plan-perf-fix-cluster-phase3]] (binding 6 — реюз в CP4).

---

# Финальный отчёт: zero-recompile / качественная / почти-бесплатная волюметрика для IRLite

## 1. TL;DR

Все три требования достижимы одновременно, и «тот проект» почти наверняка сделал это единственным способом, который вообще существует в экосистеме Iris: он **демотировал compile-time настройки в per-frame GPU-данные** (uniform / UBO / SSBO), потому что любая Iris shader-option — это `#define`/`const`, и её изменение всегда вызывает полный `Iris.reload()` (подтверждено кодом: `OptionAnnotatedSource.apply()` переписывает текст шейдера, `ShaderPackScreen.applyChanges()` → `Iris.reload()` → `destroyEverything()`). Хорошая новость: этот механизм у нас **уже работает** для per-light `vlParams` (HG g, density, beamStrength) через SSBO binding 7 с нулевой перекомпиляцией — осталось перенести туда же **глобальные** `IRLITE_VL_*`. Рекомендуемая победившая связка — **runtime-globals-spine (CP1) + STBN blue-noise dither (CP2) + cluster-cull VL-петли (CP4)** как доказуемо-надёжный хребет, судьями оценённый strong/viable, плюс дешёвые march-оптимизации (stratified jitter, Hi-Z spot-skip, bilateral upsample) как усилители. «No banding» половина требования качества закрывается уверенно и почти бесплатно; **«rich texture / a tier above» половина — открытый риск**, поскольку единственный прямой ответ (3D curl-noise, CP3) получил strong/weak-раскол по стоимости, а высший потолок качества (temporal supersample) заблокирован именно нашим сценарием — движущимися лампами. Ключевой недооценённый рычаг, найденный критиком: **мод владеет позициями и скоростями всех ламп и уже пишет их в SSBO каждый кадр** — это ровно тот motion-vector, отсутствием которого судьи убивали froxel/temporal; скормив его в reprojection, можно разблокировать temporal supersample для движущихся ламп, чего чисто-шейдерные пайплайны не могут. Стратегия: сначала внедрить хребет CP1+CP2+CP4 с двумя go/no-go экспериментами (end-to-end проверка одного глобала на recompile и occupancy A/B), а richness лечить прототипом light-velocity temporal перед тем как объявлять «tier above» достигнутым.

## 2. Как возможно «всё сразу»

**Zero-recompile (req1).** Механика однозначна и подтверждена исходниками Iris 1.20.1. Iris знает ровно два типа опций — `BooleanOption` (`#define`/`#ifdef`) и `StringOption`/const (`OptionType.java`); применение значения **буквально переписывает исходник** (`OptionAnnotatedSource.apply(OptionValues)` → `editDefine`/`editConst`), после чего пак рекомпилируется через `Iris.reload()` (`Iris.java:532`, `destroyEverything()`+`loadShaderpack()`+`preparePipeline()`). Слайдер vs кнопка (`sliders` директива) — чисто UI над тем же compile-time списком; профили BSL/Complementary — просто именованные пачки `#define`. Значит, **ни один тип Iris-опции не применяется без recompile** — это стена за болью F3+R. Единственный выход — доставлять значение как runtime-данные, читаемые GLSL каждый кадр. У нас этот канал уже есть и доказан: `LightBuffer.java` (binding 7, `GL_DYNAMIC_DRAW`) пишется каждый кадр, а per-light `vlParams` (`LightBuffer.java:20/93/110`) тюнятся из UI без перекомпиляции. Заголовок буфера — `HEADER_BYTES=16` = `uint count` + 12 байт pad (`irlite_pad0/1/2`, GLSL L104-107), **никогда не записываемых** (`memAlloc` не зануляет, GLSL их не читает) — подтверждённое свободное место. Плюс уже существует per-frame config→core seam: `LightCollector.collect()` (L116/L120) каждый кадр толкает `IrliteConfig.maxShaderLights()`/`shaderLightClustering()` в core через `setUploadCap`/`ClusterGridBuffer.setEnabled` — идентичный паттерн для будущего `setVlGlobals(...)`.

**Качество (req2).** Разбивается на две ортогональные оси. «Нет бандинга» — это проблема dither/temporal, не количества шагов: void-and-cluster blue-noise (Ulichney 1993) + blue-noise dithered sampling (Georgiev/Fajardo, NVIDIA STBN Wolfe 2021-22) выталкивают квантование в высокие частоты, которые глаз/TAA отбрасывают; текущий dither — белесый `.b`-хэш из noisetex, активный только под `#ifdef TAA` (CR deferred2 L1589-1592). «Rich texture» — это уже структура плотности: текущий 2-октавный fBm реконструируется из 2D-слоёв noisetex → axis-aligned артефакты; истинный 3D-шум с curl-domain-warp даёт изотропную вихревую структуру. Высший 2D-потолок — temporal supersample (эффективный NX), но он завязан на motion-vectors.

**Почти-бесплатно (req3).** Ключевой факт из W5: **драйвер НЕ back-специализирует uniform в константу** — теряется только unroll/DCE/const-fold. Но VL-петля уже НЕ разворачивается: `int steps = clamp(ceil(segLen*STEPS/24), min(16,STEPS), STEPS)` (CR L1387) — рантайм-граница, `for(int s=0;s<steps;s++)` (L1438). Значит перевод `IRLITE_VL_STEPS` в uniform int **структурно ничего не ломает**. Uniform-скаляр в FMA стоит как immediate (scalarization в SGPR, AMD), ветка на uniform-значении warp-когерентна (off-путь реально пропускается — GPU Gems Ch.34). Единственная реальная цена — register pressure/occupancy uber-шейдера, когда shadow/noise/cookie-код резидентен даже выключенным. Асимптотический рычаг стоимости — froxel (работа фиксирована сеткой, 1080p==4K), но это R&D. Практические рычаги: cluster-cull (снять O(all lights) с VL-петли — сейчас VL петляет ВСЕ лампы линейно, binding-6 маска гейтится только `IRLITE_SURFACE_PASS`), reduced-res (RESOLUTION сайзит colortex10 — прямой множитель числа marched-пикселей), и tap-скиппинг.

## 3. Идеи, ранжированные

### Хребет (обязательная подложка req1)

**CP1 — runtime-globals-spine** (verdicts: **strong / strong**; tun 9/9, qual 5/4, cost 8/7, integ 8/8). *Суть:* перенести все числовые `IRLITE_VL_*` + toggles/strides/steps из `#define` в per-frame глобальный блок (3 варианта доставки: (a) расширить header binding-7 на N×vec4 перед `irlite_lights[]`; (b) отдельный std140 UBO; (c) mixin `CommonUniforms.addNonDynamicUniforms` по образцу `screenBrightness=gamma`). *По требованиям:* req1 — ЭТО и есть механизм zero-recompile, все скаляры/toggles/strides/steps мгновенны; req3 — ~0 (один header-write/кадр, scalarized reads, warp-когерентные ветки); req2 — нейтрально сам по себе, его ценность в том, что позволяет тюнить look-knobs других идей вживую. *Риски/блокеры (все watch-items, не стены):* (1) overclaim «byte-identical → 7 либ + один byte-proof» ЛОЖЕН — марши CR/Photon разошлись (adaptive vs fixed steps, quadtree vs 2D-atlas shadow decode, rec709→rec2020 в Photon), это 7 неидентичных self-owned правок с 7 byte-proof; (2) **correctness-hazard**: рост HEADER_BYTES меняет std430-layout binding-7, который читает и surface-pass — все translation units должны нарастить header идентично, иначе surface-lighting corrupt (вариант-b UBO нейтрализует полностью); (3) req1 на самом деле 11/13 knobs — `IRLITE_VOLUMETRIC` (program.enabled) и `IRLITE_VL_RESOLUTION` (size.buffer) остаются load-time by nature; (4) **occupancy** — де-пермутация `#ifdef SHADOWS/NOISE/COOKIE` в runtime `if()` держит код резидентным → VGPR pressure; обязательный in-game A/B, при просадке оставить VL-shadow toggle как `#define`-пермутацию; (5) micro: `tipGlow exp()` (L1468) теряет DCE при tip=0 — обернуть в `if(uTipBoost>0)`.

### Качество — «нет бандинга» (уверенно, дёшево)

**CP2 — STBN blue-noise dither** (**viable / strong**; tun 5/6, qual 6/7, cost 9/9, integ 8/8). *Суть:* заменить белесый noisetex march-start jitter на mod-owned blue-noise текстуру через доказанный `IrlSamplers.register(...)` канал (128² R8, GL_NEAREST/REPEAT), ротируемую per-frame; pure-ALU IGN fallback (Jimenez). *По требованиям:* req2 — spatial blue-noise это строгое Pareto-улучшение над текущим статичным white-hash (TAA-off) с ~0 стоимостью; req3 — net zero-to-negative (один nearest texelFetch вместо mipmapped tap; IGN ~5 ALU, 0 tap); req1 — сила/селектор ездят на CP1. *Механизм landing доказан:* `IrlSamplersBind.tryRebind` уже несёт 10 динамических сэмплеров, `addDynamicSampler` no-op для программ без uniform → 11-й лэндит pack-agnostic без shaders.properties. *Риски:* (1) **temporal oversell** — per-frame STBN-ротация с движущимися лампами БЕЗ TAA даёт boiling/shimmer в рендере видео; честная первичная победа — spatial slice, temporal rotation по умолчанию OFF для TAA-off рендеров; (2) не байт-идентично — 7 разных per-pack dither-source свопов; (3) Photon уже использует temporal blue noise для этого jitter → там ~no-op, реальные бенефициары IterationRP (white hash) + noisetex-паки; (4) «select IGN when sampler doesn't resolve» НЕ реализуемо в GLSL (unbound sampler2D читает unit 0) — селектор только через header-enum (CP1); (5) закрывает только «no banding», не richness.

**CP5 — stratified per-step jitter + tent** (**viable / viable**; tun 7/6, qual 7/5, cost 8/6, integ 8/7). *Суть:* джиттерить КАЖДЫЙ шаг в своей страте (golden-ratio ротация), не только старт; tent-реконструкция. *Риски (существенные, урезают заявку):* (1) **мисчитан baseline** — накопление НЕ «flat rectangle», а энергосохраняющий Beer-Lambert (`oneMinusAbsorption = 1-exp(-ext·Δ)`, running transmittance, early-out<0.02); tent над рекурсивным transmittance с early-break не drop-in, риск negative weights/non-conservation → сдвиг яркости в 7 per-pack-тюненных сетапах; (2) «12 шагов как 48» без quadrature-обоснования; high-freq термы (noise, shadow taps, cone edge) от tent не выигрывают; (3) STRIDE-кэширование нуллифицирует стратификацию для этих термов; (4) на движущихся лампах per-step страты, меняющиеся per-frame, мерцают под TAA. **Рекомендация судей:** отгрузить stratified jitter ОДИН (byte-identical anchors, ~2 ALU, реальный fix), tent-половину прототипировать отдельно.

### Стоимость (bounded, но реальные)

**CP4 — cluster-cull VL-петли** (**viable / viable**; tun 5/5, qual 5/5, cost 7/7, integ 7/6). *Суть:* объявить binding-6 маску и в VL-TU, скипать лампы с нулевым битом тайла. Pure-GLSL, zero new data (`FramePipeline.onGbufferMatricesCaptured` строит/биндит сетку каждый кадр). *По требованиям:* req3 — снимает O(lightCount) с внешней петли (пустой тайл неба: ~30 ламп × ~12 ALU ray-sphere → 0-write early-out); req2 — **точно нейтрально** (conservative cull, бит-идентичный вывод, temporally SAFE — маска перестраивается каждый кадр, нет ghosting). *Риски:* (1) **MASK_LIGHTS=64 cap** — «independent of lamp count» ЛОЖНО выше 64; (2) gl_FragCoord/viewWidth gotcha в reduced-res VL → ОБЯЗАТЕЛЬНО `texCoord*gridX`, не shared fetch; (3) блок binding-6 существует только в CR — 6 пакам нужен byte-exact мирроринг struct+fetch vs `ClusterGridBuffer`; (4) выигрыш **неизмерен** — измерить lights-per-tile в реальной сцене; (5) Photon VL в composite0 (не `IRLITE_VL_PASS`) — проверить, что сетка там забиндена.

**reduced-res + bilateral upsample** (**viable / viable**; tun 4/6, qual 7/5, cost 8/9, integ 6/6). *Суть:* RESOLUTION — крупнейший рычаг стоимости; опустить (CR 0.5→0.25 = 1/16-res) + depth-aware 4-tap bilateral upsample вместо наивного bilinear. *Риски:* (1) Photon MISS — нет RESOLUTION-опции, VL в нативном quarter-res `fog_scattering`; (2) bilateral чисто spatial, не стабилизирует god-ray edges на движущихся лампах, при 1/16 регрессирует; (3) degenerate weights при 1/16 над sub-tap геометрией (заборы/листва); (4) per-pack линеаризатор глубины разный. **Рекомендация судей: декуплировать** — bilateral на текущем 0.5 как безусловный cost-neutral quality-win; 0.25 opt-in per-shot.

**march-loop-cost-refinements (Hi-Z + adaptive + tap budget)** (**viable / viable**; tun 8/6, qual 5/3, cost 8/5, integ 8/6). *Суть:* (a) Hi-Z shadow-tap skip через min/max пирамиду (fully-lit/occluded → скип всех per-step taps); (b) adaptive solid-angle steps + per-pixel tap budget. *Риски:* (1) **point Hi-Z небезопасен** — VL-сегменты пересекают cube-грани часто, radial depth немонотонен → light-leak; (2) tap-budget опирается на несуществующий VL cluster-cull; (3) continuous adaptive steps нарушает anti-divergence дисциплину (`IRLITE_SHADOW_ADAPTIVE` квантован в 3 бакета) → temporal pulsing; (4) «1 coarse tap» = на деле 4 texelFetch RG32F. **Рекомендация:** отгрузить только spot fully-LIT skip (exact, low-risk), point/budget/adaptive дропнуть или де-рискнуть.

### Качество — «rich texture» (спорно)

**CP3 — 3D curl-fBm density texture** (**strong / weak** — раскол!; tun 6/5, qual 8/5, cost 8/4, integ 9/6). *Суть:* mod-owned tileable 64³/96³ RGBA16F volume (R=value-noise, GBA=precomputed curl) через `IrlSamplers`, hardware trilinear + REPEAT, curl domain-warp; убирает seamless-wind hack. *Раскол судей:* strong-судья verified весь binding-механизм (`tryRebind` уже несёт non-2D targets, `CookieArrayBase` — прецедент glTexImage3D; version-split: 5 clean sampler3D #130+ включая ОБА флагмана / 2 на 2D-atlas fallback). Weak-судья РЕФУТИРОВАЛ стоимость: заменяемый Noise3D = 2 texture2DLod-LOD0 из резидентной 128²/64KB noisetex, а предложенные «2 tap» — DEPENDENT цепочка 3D-трилинейных из 2-7MB volume, спиллящего L1 → neutral на big-L2, negative на слабых; плюс **латентный баг** — `texture()` (implicit LOD) в дивергентной march-петле = UB, надо `textureLod`; 64³ < 128² источника → тайлит на широких вистах, реальный tier нужен ~128³=16MB. *Вердикт:* качественная победа реальна, cost-claim под сомнением — нужен GPU-timer proof.

### Отсеянные (weak оба или почти)

**CP8 — temporal reprojection / TAA-feed** (**weak / weak**). (1) headline-механизм НЕВАЛИДЕН — у Iris нет per-frame main/alt ping-pong для self-read (`clear=false` single-writer читает frame-0 garbage); (2) ценность уже захвачена — оба флагмана инжектят VL UPSTREAM своего TAA и уже джиттерят march per-frame; (3) **самодисквалификация под наш workload** — движущиеся лампы + быстрые камеры = disocclusion worst-case, ghost trails, 2D-reprojection не может reproject не-surface-attached движущийся свет.

**CP9 — analytic airlight hybrid** (**viable / weak**). (1) Sun-2005 база изотропна+гомогенна → noise-tap поверх это post-hoc wobble, НЕ гетерогенное рассеяние → регресс «puffs»; (2) spot+gobo не имеет дешёвой closed-form → губит crisp cone edge + gobo; (3) «10×» против STRIDE=1 strawman, реально ~3-5×; (4) K=3-4 tap мерцают на движущихся casters.

**froxel-vbuffer-rewrite** (**viable / weak**). Высший асимптотический потолок и лучшая tunability, но weeks-not-days R&D: (1) 160×90×64 trilinear = low-pass → крупные god-rays размыты; (2) temporal-потолок опирается на отсутствующие mod-side motion-vectors (но см. §6a!); (3) hook `onGbufferMatricesCaptured` фаейрит ДО opaque-depth; (4) ZERO существующего compute у мода. Содержит преферред Route I4 (mod-owned compute, sampler3D auto-bind), уникально достающую 3 piggyback-пака. R&D pilot-CR-only, далёкая перспектива.

## 4. Рекомендуемая целевая архитектура

**Победившая связка (shippable spine): CP1 + CP2 + CP4 + spot-Hi-Z + bilateral@0.5**, с CP3 как контролируемым richness-экспериментом и light-velocity-temporal (§6a) как R&D-разблокировкой потолка.

**Data flow:**
```
IrliteConfig (addon) / LightConfig (editor)
      │  каждый кадр (существующий seam)
      ▼
LightCollector.collect() ──► LightBuffer.setVlGlobals(...)   [НОВЫЙ сеттер рядом с setUploadCap]
      │
      ▼
LightBuffer.upload()  ──glBufferSubData──►  SSBO binding 7
   ├─ header: uint count + GlobalVL блок (intensity, maxDist, tipBoost/radius,
   │           noiseAmount/scale/speed, stepMax, shadowStride, noiseStride,
   │           frameIndex, flags-bitfield)
   └─ irlite_lights[]  (96B stride НЕ тронут)
      │
      ▼
GLSL (7 либ): GlobalVL как runtime-значения вместо #define;
   #ifdef NOISE/SHADOWS/COOKIE → warp-coherent if(flags & bit);
   dither → texelFetch(irl_blueNoise, (fragCoord+frameJitter)&127)   [CP2]
   VL-петля → skip lights с нулевым битом irlite_clusterMask[texCoord]  [CP4]
```

**Вариант доставки глобалов — решение по риску:** по умолчанию **вариант (b) std140 UBO** (изоляция от 164KB light-SSBO, меньший byte-proof, нейтрализует surface-pass hazard) ЕСЛИ survey свободных UBO binding-индексов по 7 пакам+Iris+MC пройдёт; иначе (a) header-grow с обязательным re-byte-proof surface-pass. Вариант (c) CommonUniforms-mixin — lightweight standalone для ≤3-5 глобалов (§6b), Iris-version-fragile.

**Tiering по пакам:**
- **Tier A (полный набор): Complementary, Photon** — CP1 header, CP2 blue-noise, CP4 cluster-cull (Photon через composite0/`uv`), CP3 sampler3D clean (оба #130+/#400).
- **Tier B (new-pass паки, RESOLUTION-exposed): BSL, RethinkingVoxels, Solas** — + reduced-res+bilateral. BSL/Bliss #120 → CP3 2D-atlas fallback; RVX colortex15.
- **Tier C (piggyback): Bliss, IterationRP** — CP1+CP2 базово; cluster-cull если VL-TU имеет texCoord; froxel только через Route I4 в далёкой перспективе.

**Какие #define остаются структурными:** `IRLITE_VOLUMETRIC` (гейтит program.enabled — pass existence) и `IRLITE_VL_RESOLUTION` (гейтит size.buffer — allocation). Опционально VL-shadow toggle как permutation, если Э2 покажет просадку occupancy.

## 5. Поэтапный план

**Фаза 0 — Go/No-Go эксперименты (~1-2 дня, до любого тиража):**
- **Э1 (гейтит весь CP1):** пропатчить ОДИН глобал (`IRLITE_VL_INTENSITY`) в header Complementary, подтвердить zero-F3+R apply + FPS-дельта. (~1 ч)
- **Э2 (решает uber vs permutation):** де-пермутировать `#ifdef SHADOWS/NOISE/COOKIE`→runtime `if()` на CR, frametime everything-ON. Просадка → VL-shadow остаётся `#define`.
- **Э7 (header byte-proof):** surface-pass корректно читает выросший header binding-7; при сомнении → pivot std140 UBO.

**Фаза 1 — Хребет CP1:** `setVlGlobals` в core (main + 3 порт-ветки, republish mavenLocal между линиями), header/UBO, 7 либ (7 отдельных byte-proof!), dual-write из IrliteConfig И LightConfig. Гейт: byte-proof PASS + in-game все look-knobs instant.

**Фаза 2 — Anti-banding CP2 (параллельно):** STBN asset (CC0/build-time) в irl-core, `IrlSamplers.register("irl_blueNoise")`, 7 dither-свопов + IGN fallback. **Э4 (двусторонняя валидация):** статик TAA-OFF (banding ушёл?) И video движущейся лампы (boiling?) → ship temporal-rotation on/off. Гейт: TAA-off статик чистый.

**Фаза 3 — Cost CP4 + spot-Hi-Z + bilateral:** **Э3:** замер lights-per-tile в stage-сцене + scan-bound проверка. Если >~единиц ламп/тайл — cluster-mask в 7 VL-TU (texCoord fetch!). Bilateral@0.5 безусловно; 0.25 opt-in. Spot fully-LIT Hi-Z skip (point дропнуть). Stratified jitter (без tent).

**Фаза 4 — Richness (R&D, открытый риск):** **Э5:** Iris компилирует ИНЖЕКТИРОВАННЫЙ `uniform sampler3D` на CR+Photon (binding proven, transformer acceptance НЕ проверен). Прототип CP3 curl-3D + GPU-timer proof. **Прототип light-velocity temporal (главная находка):** prev-frame позиции ламп → per-light velocity field → VL reprojection для ДВИЖУЩИХСЯ ламп. Пилот CR only. Гейт: A/B hero-shot (движущаяся лампа + dolly) без ghost trails.

## 6. Открытые вопросы и риски

**(a) Главный недооценённый рычаг — light-velocity motion vectors.** Каждый temporal/froxel вердикт падал на «нет per-lamp motion vectors mod-side». Но мод владеет позицией каждой лампы и уже пишет их в SSBO-7 каждый кадр. Prev-frame light transforms → VL reprojection velocity field = отсутствующий вход, делающий froxel/history temporal жизнеспособным для движущихся ламп. Самая ценная незакрытая дверь к «tier above»; прототипировать ДО объявления richness достигнутой.

**(b) CommonUniforms bridge как first-class lightweight req1-путь.** Mixin `CommonUniforms.addNonDynamicUniforms` (PER_FRAME named uniforms по образцу `screenBrightness=gamma`): для горстки глобалов — ноль GLSL byte-proof, одна mixin-строка + один `uniform float` на knob. Заслуживает cost/benefit vs Route (a). Риск: Iris-version-fragile по 4 source-линиям.

**(c) Checkerboard / interleaved VL** (¼ пикселей/кадр + reproject) — никем не предложенный middle-ground между reduced-res и froxel; естественный партнёр (a).

**Прочие:**
- req2-richness — наименее покрытое требование для hero-shots (banding решён; richness = спорный CP3; потолок = temporal, заблокирован до (a)).
- req3 покрыт в агрегате, но НЕИЗМЕРЕН (64-light cluster cap; VL уже reduced-res; Hi-Z straddle-rate неизвестен). Обязательны Э3 + frametime A/B до заявлений.
- Occupancy uber-шейдера — единственная реальная цена CP1; Э2 решает.
- 7-pack divergence — марши разошлись (adaptive vs fixed steps, quadtree vs 2D-atlas decode, rec709→rec2020, stride-идиомы) → каждый своп отдельный self-owned edit + byte-proof; labor ~7× наивной оценки.
- Cross-branch parity — CP1 Java на core main + 3 порт-ветки; GLSL-миррор 7 либ per MC-линия.
