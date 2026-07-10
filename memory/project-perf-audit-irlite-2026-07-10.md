---
name: project-perf-audit-irlite-2026-07-10
description: "Статический перф-аудит трилогии света (2026-07-10, ultracode 30 агентов, adversarial-verify, БЕЗ правок): P0 = per-fragment цикл по всем N в CR surface-пути; P1 = VL per-step стек (48 шагов, noise 4 тапа, shadow stride 1), point blocker-only-for-LOD, mustBake обходит бейк-бюджет, flush без cull (даунгрейд с P0); C10 даунгрейднут в P2; 9 DRIFT памяти."
metadata:
  node_type: memory
  type: project
---

Статический перф-аудит IRLite 2026-07-10 (workflow wf_12a5201d-ab5, 30 агентов: 9 областей + adversarial-verify P0/P1 на Opus). Скоуп: irl-core main@d5e40ad, аддон master@34bb996, шейдер = Shadres/Modification/ComplementaryReimagined (working copy, НЕ патч). Только диагностика, правок нет. 41 находка, 16 пережили verify.

ИТОГ ПО ПРИОРИТЕТАМ (finalPriority после adversarial):
- P0 (CONFIRMED 2 линзами): irlite_lights.glsl:925-926 — плоский per-fragment цикл по ВСЕМ irlite_lightCount в irlite_lightSurface, DoLighting в ~7 gbuffers-программах, без кластеров/break; даже вне радиуса свет стоит ~2 vec4 SSBO + ALU на фрагмент. Лечение: cap+приоритизация N перед upload и/или clustered-cull.
- P1 flush-без-cull (LightRegistry.java:236, ДАУНГРЕЙД с P0 обоими скептиками): факт верен (только сфера 256 бл в LightCollector:152/323; камера в момент flush известна — FramePipeline:151), НО frustum-cull срезал бы только дешёвый reject-член; доминирующий теневой член принадлежит источникам во фрустуме. Корневой рычаг = сокращение N/кластеризация, не чистый frustum.
- P1 mustBake обходит staticBakeBudget (ShadowBaker.java:843, CONFIRMED): все first-bake в один кадр на старте/тогле шейдеров/смене качества; потолок 16 spot + 16 point = worst ~112 depth-пассов + холодная тесселяция VBO. Это и есть наблюдаемый спайк (сильнее C10).
- P1 point blocker-search (irlite_lights.glsl:673, CONFIRMED): 10 cube-тапов только ради выбора LOD одного MSM-fetch (:722); PCF-fallback ниже мёртв на совпадающем билде (:730 return).
- P1 VL-стек per-step (все CONFIRMED): 48 фикс. шагов на пересечённый свет (:1210, break :1322 не срабатывает на тонкой дымке), noise 4 тапа/шаг stride 1 (:1314, крупнейший per-step множитель), shadow-тап каждый шаг stride 1 (:1295). Смягчение: deferred2 = 1/4 пикселей, одна программа, per-light ray-cone/sphere сегментация есть (:1188/:1198).
- P2 (даунгрейды): C10 renderBlocksDepth весь VBO x6 граней (ShadowRenderer:428/492, ShadowBaker:569/606/656) — PER-BAKE не per-frame, depth-only, гейты dirty+budget => P2, «~320мс» относится к mass-first-bake сценарию (см. mustBake); спот контакт-PCF 10-16 gather (:501); scanBlockEntities обходит ВСЕ blockEntityTickers мира + getBlockEntity per тикер в радиусе 256 (LightCollector:135/158); outline edge-тест 5 depth-тапов почти на весь экран в composite1 (:799, и OUTLINE ВКЛЮЧЁН по умолчанию в этой копии); профили POTATO..ULTRA не трогают IRLITE_*; worst-case слайдеры ~5.3x (не 10x — SHADOW_QUALITY НЕ влияет на VL-марш, там hard 1-tap :1125/:1143).
- P2 непроверенные: LightRegistry.slot() линейный дедуп O(N^2) (:132); pow(rgb,1/2.2) per light per fragment (:978 — предрасчитывается на CPU); cookie-базис acos/tan/cross per fragment (:126) и per VL-шаг (:1272); vogel sincos per tap (:241); MSM size-gate fallback в полный PCF при несовпадающем билде (:718); IRLITE_COMPILE_SHADOWS раздувает composite1 (occupancy, :102); cutout-VBO кэш per-light (перекрывающиеся лампы тесселируют дубли, ShadowRenderer:718); IRLiteBbsCasterSource.collect обходит все сущности+BE мира per-frame (:100); overlay MSM/EVSM prefilter всего куба при dirty (ShadowBaker:731); Matrix4f-аллокации в walk() (LightCollector:208+).
- P3 (в т.ч. показательные даунгрейды): outline fast=false (был P0 -> P3: гейт contourFactor :837 = только кромки); варп-дивергенция как отдельный налог (двойной учёт с тапами); 3x normalize dirType; fixed-cost при N=0; textureSize в цикле; upload без orphaning (умеренно, заливка до рендера мира).

DRIFT ПАМЯТИ (9, поправить при консолидации):
1. project-photon-outline-switch-to-old «default OFF»: в CR Modification IRLITE_OUTLINE РАСКОММЕНТИРОВАН (irlite_lights.glsl:57) = ON. Патч .irlights не проверялся — сверить, это дрейф working-copy или патча.
2. shader-volumetric «VL-тени только Photon»: устарело — IRLITE_VL_SHADOWS есть и ВКЛ в CR (:39), stride 1 (:40).
3. «спот остался PCSS»: фактически гибрид PCSS-контакт + EVSM4-Chebyshev на широкой пенумбре (:466-480). Point = MSM4 (OK).
4. «~28 PCSS-тапов на shadowed-свет»: сырой максимум тира 2; с PYRAMID early-out большинство пикселей = 4 тапа; point ~15; спот-пенумбра 15-31.
5. addon-architecture PACKAGE MAP (строки 66-67): LightRegistry/LightBuffer/shadow-классы давно в irl-core; в аддоне client/light/ только LightCollector + IRLightPositionResolver + cookie/CookieArray.
6. addon-architecture «flush в @HEAD-хуке»: flush отложен во ВТОРОЙ инжект после Camera.update (FramePipeline.flushPending -> uploadIfPending, GameRendererLightMixin:28-37).
7. shader-irlite-glsl early-out «строки 141/258» -> теперь :317 (spot) / :534 (point).
8. LightBuffer.java:38 doc-коммент «164 KB» устарел (96Б/свет -> 192КБ).
9. CR irlite_lights.glsl = 1343 строки (1239 — клейм про Photon-эталон, не CR).
Плюс DRIFT постановки аудита (память права): авто-света в АДДОНЕ НЕТ (grep AutoLight* = 0) — AutoLightManager redactor-only. Нюанс: Java IRLShadowQuality = 4 пресета разрешения (0-3), GLSL IRLITE_SHADOW_QUALITY = 0-4 тап-тиров — раздельные ручки.

НЕ ВИНОВАТО (проверено): flush/upload дёшев (пакинг в предвыделенный scratch без аллокаций, glBufferSubData по used, 4 GL-вызова, суб-мс при 2048); байт-контракт Java<->GLSL цел поле-в-поле; collect аддона БЕЗ сортировки (12.5мс диагноз 2026-06-22 = редакторный nearest(), не аддон); все Tier-1/2 бейк-оптимизации доехали в core (shortlist+face-mask, batch-flush, cutout-VBO, budget 4, sphere-exact, state-snapshot 1x/bake, matrix-скретчи); EVSM/pyramid prefilter в покое no-op; GLSL перф-дефайны EARLY_OUT/LOD/ADAPTIVE/PYRAMID/PREFILTER все существуют, ON и реально ветвят; unshadowed early-out первой строкой (:317/:534); VL ray-сегментация + MAX_DIST работают, upsample 1 tap, VOLUMETRIC=OFF отключает пасс целиком; dormant-гейт обнуляет всё.

Обобщение на 6 паков: shadow-body 5 паков байт-идентичен CR (тираж 2026-07-04) => GLSL-находки P0/P1 (цикл, blocker-for-LOD, VL-стек) переносятся на все; per-pack отличия только в обвязке (VL-таргеты и т.п.).

Связь: [[project-gui-lag-gpu-bound-diagnosis]] (замер 273мс@2000 — механизм подтверждён этим аудитом), [[project-shadow-bake-perf-audit]] (C10 даунгрейднут, mustBake-спайк = новый главный подозреваемый бейка), [[addon-light-buffer-ssbo]], [[shader-irlite-glsl]].
