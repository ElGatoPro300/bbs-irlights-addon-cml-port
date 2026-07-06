# Аудит фильтрации солнечных теней в шейдерпаках (Shadres/Original)

Замечание по инструментам: `Shadres/` в gitignore, поэтому ripgrep-тул молча пропускал файлы; поиск выполнен через `rg --no-ignore`. Проаудированы все 8 паков: BSL, Bliss, ComplementaryReimagined, IterationRP, Kappa (бонусом, в задании не был), Photon, RethinkingVoxels, Solas.

## Сводная таблица

| Пак | Техника | Blocker search | PCF-тапы | Дизер | Temporal | SS-blur после сэмплинга |
|---|---|---|---|---|---|---|
| Photon | PCSS (SHADOW_VPS) | 3 тапа (Vogel disc); 12 при SSS | адаптивно 6–12 (MIN/MAX/SCALE), Vogel | blue noise (noisetex) | да, r1(frameCounter) -> TAA/TAAU | нет |
| ComplementaryReimagined | PCF фикс. радиуса, спираль | нет | 4–32 (SHADOW_SMOOTHING 1–4: 2/4/8/16 итераций x2 тапа), hardware shadow2D | IGN | да, +goldenRatio*frameCounter только при TAA | нет |
| RethinkingVoxels | тот же код, что CR (форк) + воксельный GI отдельно | нет | как CR | IGN | как CR | нет |
| BSL | PCF фикс. 9-тап кольцо (SHADOW_FILTER) | нет | 9, hardware shadow2D, фикс. offset ~0.0007 | нет в основном фильтре (IGN только для SSS) | нет | нет |
| Solas | PCF спираль, мини-бюджет | нет | 4 (2 итерации x2), ручное сравнение глубины (не hw-PCF) | blue noise | да, golden ratio * frameCounter при TAA | нет |
| Bliss | VPS: blocker в ОТДЕЛЬНОМ пассе | 4 (до 32) тапов в composite.fsh, результат пишется в буфер (g/b каналы) | 13 (SHADOW_FILTER_SAMPLE_COUNT), спираль CleanSample, читает пенумбру из буфера в composite1 | blue noise + R2 | да, seed от frameCounter | нет (только edge-aware upsample для half-res VL) |
| IterationRP | VPS + RTW-warped shadowmap + screen-space shadows | 4–16 (clamp(SHADOW_QUALITY,4,16)), golden-spiral | SHADOW_QUALITY*4 динамически (16–64 при quality 4), sqrt-радиус golden-spiral, свой бикубический SampleShadowBilinear | blue noise | да, BlueNoiseTemporal при TAA | нет |
| Kappa | VPS в два пасса | 36 (6x6 grid) в deferred2, sigma пишется в буфер vpsSigmaStore | 15 (6–30, shadowFilterIterations) R2-спираль в deferred3, bilinear-выборка + sharpenLerp | blue noise | да | нет |

Ключевые файлы:
- `Photon/shaders/include/lighting/shadows/pcss.glsl` (+`settings.glsl` строки 126–136)
- `ComplementaryReimagined/shaders/lib/lighting/shadowSampling.glsl`, `mainLighting.glsl` (строки 262–275: выбор числа тапов)
- `BSL/shaders/lib/lighting/shadows.glsl`
- `Solas/shaders/lib/lighting/shadows.glsl`
- `Bliss/shaders/dimensions/composite.fsh` (blocker, ~строки 470–540) и `composite1.fsh` (ComputeShadowMap, ~640–700); `lib/settings.glsl` 144–151
- `IterationRP/shaders/Lib/BasicFunctions/Sunlight_Shadow.glsl`; `Lib/Settings.glsl` 310–324
- `Kappa/shaders/world0/deferred2.fsh` (shadowVPSSigma) и `deferred3.fsh` (shadowFiltered)

## Детали, важные для выводов

1. Бюджеты скромнее, чем кажется. «Художественно приемлемый» уровень: blocker search 3–6 тапов (Photon вообще 3!), PCF 9–16 тапов. Никто не жжёт 64 тапа статически — IterationRP доходит до 64 только динамически и только где пенумбра широкая.
2. Все VPS-паки маскируют малое число тапов дизером + TAA. Паттерн одинаков: Vogel disc / golden-ratio спираль с sqrt-распределением радиуса, повёрнутая на угол из blue noise / IGN, с добавкой frameCounter*goldenRatio — шум меняется каждый кадр и интегрируется TAA. Complementary и Solas явно отключают временную компоненту без TAA (`#ifdef TAA`).
3. Ни один пак не делает screen-space blur теней после сэмплинга. Весь блюр — в shadow-space внутри одного фрагмента; шум разрешает TAA. (Bliss блюрит только half-res волюметрику edge-aware upsample'ом — это другое.)
4. Hardware PCF бесплатен. BSL/Complementary/Bliss сэмплят через `sampler2DShadow`/`shadow2D` — каждый тап это аппаратное 2x2 bilinear-сравнение, т.е. фактически x4 сглаживание бесплатно. Solas и IterationRP делают сравнение вручную (Iteration компенсирует своим бикубическим SampleShadowBilinear).
5. Early-out у Photon: первые 4 тапа, и если shadow > 4-eps (полностью освещено) или < eps (полностью в тени) — выход без остальных тапов. Для пенумбры это ~ничего, для 90% экрана — экономия в разы.
6. Адаптивное число тапов: Photon `step_count = MIN + SCALE*filter_scale, clamp MAX` (растёт с размером пенумбры); IterationRP `SHADOW_DYNAMIC_QUALITY` — steps = mix(Q, Q*4, avgDiff/maxDist).
7. Двухпассовый VPS: Bliss (composite -> буфер g/b -> composite1) и Kappa (deferred2 -> vpsSigmaStore -> deferred3) выносят blocker search в отдельный full-screen пасс и передают пенумбру через буфер — blocker и PCF не конкурируют за регистры/occupancy в одном шейдере.
8. Анти-алиас узкой пенумбры вместо доп. тапов: Photon — min_filter_radius = 2 texels + шарпенинг `linear_step(threshold, 1-threshold, shadow)` когда penumbra < min radius; Kappa — то же через sharpenLerp у bilinear-выборки. Дешёвый способ получить чистый жёсткий край без зерна.
9. Разные мелочи: клампы `penumbra_size <= blocker_search_radius` (Photon); слоуп-скейл z-offset вдоль нормали внутри PCF-петли (IterationRP `zOffestBase`); edge_factor против light leaking (Complementary, переиспользован Photon'ом); расширение пенумбры под облачной тенью для overcast-вида (Photon x5).

## Что переиспользовать для локальных источников IRLite

Применимо напрямую (per-pixel, дёшево):
- Дизер спирали blue noise/IGN + временная ротация под TAA. Все 7 хост-паков имеют TAA — можно добавить `frameCounter`-ротацию Vogel-паттерна за `#define IRLITE_TEMPORAL_DITHER` (гейтить как Complementary: без TAA — статичный шум). Это главный приём, которым паки покупают качество за 9–13 тапов.
- Early-out по первым 4 тапам (Photon). Для десятков источников на GPU-bound сценах это лучший usable-выигрыш: большинство пикселей относительно каждого света либо целиком в свету, либо целиком в тени.
- Hardware PCF для spot-атласа: `sampler2DShadow` + compare mode = бесплатное 2x2 на тап; для point cube-array есть `samplerCubeArrayShadow` (GL4 доступен во всех паках). Может позволить срезать тапы вдвое на LOW/MEDIUM.
- Адаптивный step_count от оценённой пенумбры (Photon MIN/MAX/SCALE) — ложится прямо на наши пресеты LOW–ULTRA.
- Шарпенинг суб-тексельной пенумбры (Photon/Kappa) — прямой кандидат против «зернистого квадрата» при высокой Softness: вместо наращивания радиуса/тапов клампить фильтр к 2 texel и шарпить результат.
- Бюджеты выровнять по нативным: blocker 3–4 тапа, PCF 6–16 — если наш PCSS сейчас выше, это можно резать без потери «паритета» с художественным уровнем хост-паков.

Наш уникальный рычаг, которого нет ни у одного пака: их sun-shadowmap перерисовывается каждый кадр, поэтому префильтровать её нельзя — а наши карты бейкаются и кэшируются. На амортизированном этапе бейка можно: (а) построить mip-цепочку min/avg depth и оценивать blocker depth одним mip-тапом вместо 3–6 per-pixel тапов; (б) вообще уйти в префильтруемые представления (VSM/EVSM/moment shadow maps) — тогда per-pixel остаётся 1 отфильтрованный тап, а «софтность» получается бесплатно из mip-уровня; это же снимает вопрос дальних дистанций (мелкий свет вдалеке = высокий mip). Риск — light bleeding VSM, лечится EVSM/клампом.

Двухпассовый трюк Bliss/Kappa (blocker в отдельный буфер) в наши инжект-точки ложится плохо (мы гость в чужом пайплайне), но mip/moment-бейк даёт тот же эффект лучше.

Для волюметрики: паки в ray-march сэмплят shadowmap по 1 неотфильтрованному тапу на шаг с дизером шага — наш VL-контракт менять не надо; при переходе на VSM/ESM волюметрика даже выигрывает (1 мягкий тап без compare).

Чего НЕ делать: screen-space blur пасс после сэмплинга — ни один из 8 паков так не делает; RTW-warping IterationRP не нужен (наши spot/point карты перспективные, без ортографической растяжки).