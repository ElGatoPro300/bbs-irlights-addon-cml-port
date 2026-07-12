---
name: plan-perf-fix-cr-phase1
description: "План перф-фиксов Phase 1 по аудиту 2026-07-10: ТОЛЬКО аддон (master) + ТОЛЬКО ComplementaryReimagined; F1 VL-стек, F2 point blocker-search, F4 мелочь РЕАЛИЗОВАНЫ 2026-07-10 (патч регенерирован, run-пак синкнут, НЕ закоммичено); F3 outline = открытый вопрос юзеру (ON сидит и в патче); ин-гейм проверка + FPS-замер PENDING."
metadata:
  node_type: memory
  type: project
---

План следующей сессии — лечение перфа по аудиту [[project-perf-audit-irlite-2026-07-10]]. Статус: КОД ВЫПОЛНЕН 2026-07-10 (см. блок СТАТУС в конце), ин-гейм проверка/замер/коммит PENDING.

СКОУП (директива юзера 2026-07-10): менять ТОЛЬКО bbs-irlights-addon на master и ТОЛЬКО ComplementaryReimagined (Shadres/Modification/ComplementaryReimagined + patches/complementaryreimagined.irlights). irl-core НЕ трогать. Остальные 5 паков НЕ трогать — CR = пилот, тираж строго по прямой команде позже.

РЕЖИМ: ultracode (Workflow-оркестрация). Оркестратор = Fable 5: план, раздача, синтез, финальные решения. Суб-агенты НЕ выше Opus 4.8: реализация/математика = 'opus', механика/сверка/реген = 'sonnet'. Каждый фикс проходит adversarial-ревью (opus-скептик: корректность математики, не сломаны ли контракт SSBO/дефайны/якоря патча) ДО передачи юзеру. Коммиты только в чекпоинты по подтверждению юзера ([[commit-checkpoints]]); веток под сессию не создавать ([[feedback-no-per-session-branch]]).

ПОЧЕМУ ПРИОРИТЕТЫ СДВИНУТЫ ОТНОСИТЕЛЬНО АУДИТА: в аддоне авто-света нет => N обычно единицы-десятки, O(N)-цикл (P0 аудита) здесь не доминирует, а его правильное лечение (cap/приоритизация на flush) живёт в irl-core => отложено. В скоупе аддон+CR главные выигрыши на кадр = VL-стек и point blocker-search (оба CONFIRMED P1, чисто шейдерные).

ФИКСЫ (порядок):
- F0 recon (sonnet): диф Modification vs patches/complementaryreimagined.irlights — главное, IRLITE_OUTLINE ON в Modification (irlite_lights.glsl:57) против канона default OFF: дрейф рабочей копии или патча? Патч — источник истины. Заодно подтвердить актуальные строки из аудита.
- F1 VL-стек (irlite_lights.glsl): поднять дефолты IRLITE_VL_NOISE_STRIDE 1->2..3 (:51) и IRLITE_VL_SHADOW_STRIDE 1->2 (:40); шаги сделать адаптивными к длине сегмента: steps = clamp(f(segLen), min, IRLITE_VL_STEPS) вместо фиксированных 48 (:1158/:1210), либо общий бюджет шагов на фрагмент. Критерий: клубы/лучи визуально не деградируют (A/B), FPS растёт на сцене с 5-10 VL-светами.
- F2 point blocker-search (:673-730): заменить 10-тап Vogel-поиск оценкой blockerDist из уже читаемой min/max-пирамиды (:616-660) или 1-2 тапами; итог по-прежнему кормит lodP одного MSM-fetch (:720-722). Критерий: пенумбра point-теней без артефактов, тапы на пенумбра-фрагмент падают ~вдвое.
- F3 outline-дефолт: если F0 покажет дрейф копии — вернуть канон default OFF ([[project-photon-outline-switch-to-old]]); если ON сидит и в патче — решение юзера (ON стоит ~5 depth-тапов + обратные проекции почти на весь экран в composite1).
- F4 опционально: cookie fY напрямую из cone.x в surface-пути (:126, в VL уже так :1240); ранний return при irlite_lightCount==0 до матриц (:920).

ПРОЦЕДУРА НА КАЖДЫЙ ФИКС: (1) правка в Modification; (2) adversarial-ревью; (3) реген патча + PatchHarness byte-валидация (apply == Modification); (4) ин-гейм: runClient '-Pmc=1.20.4', лог run/runclient-console.log, в фоне ([[feedback-addon-runclient-command]]); (5) замер: фиксированная сцена, FPS до/после, спайки по Alt+F3-графику frametime; (6) для визуальной проверки дать image-gen промпт EXPECTED/REGRESSION ([[feedback-visual-test-image-prompts]]); (7) чекпоинт-коммит по подтверждению.

ЗАМЕРЫ: полный профайлер НЕ делаем (решение 2026-07-10) — FPS-счётчик + Alt+F3; временная nanoTime-строка точечно, только если результат мутный, и сразу убрать.

ОТЛОЖЕНО (вне скоупа, строго по прямой команде): irl-core — cap+приоритизация N на flush (наследник «Рычага 1»), троттлинг mustBake первых бейков (ShadowBaker:843, лечит cold-start спайк), hashmap-дедуп в LightRegistry.slot(), гамма-предрасчёт цвета (core+шейдер синхронно); тираж F1/F2/F3 на остальные 5 паков; C10 per-face block-cull (P2).

СТАТУС 2026-07-10 (сессия выполнения; workflow wf_b37f8014-1a4 recon + wf_d8a10327-d22 impl, 9 агентов, все ревью PASS r1):
- F0: дрейфа НЕТ — Modification == патч байт-в-байт (только штамп irlite_patched.txt + хвостовой \n в en_US.lang); run-пак ComplementaryReimagined_IRLights подтверждён загружаемым (iris.properties). IRLITE_OUTLINE ON сидит И В ПАТЧЕ (:66) — не дрейф копии.
- F1 DONE (v2 после ин-гейм регрессии; workflow wf_019f35b1-abf): IRLITE_VL_SHADOW_STRIDE 1->2, IRLITE_VL_NOISE_STRIDE 1->2 (слайдер-аннотации целы); шаги per-light: steps = clamp(ceil(segLen*STEPS/24), min(16,STEPS), STEPS). v1 (якорь 32, пол 8) дал ПОДТВЕРЖДЁННЫЙ юзером кольцевой бандинг на мелких сферах дымки — пол 8 не разрешал tip-glow ~1.5 бл; v2 = якорь 24 + пол 16. ГОЧА: голый пол 16 без min() = clamp(x,16,8) UB при слайдере STEPS=8.
- F2 DONE (v2 = ПОЛНЫЙ ОТКАТ v1-оценщика после ин-гейм регрессий): v1 (оценка blockerDist из pyrMin пирамиды + центр-тап) дал ПОДТВЕРЖДЁННЫЕ юзером (а) размытый корень тени (min по футпринту ловит off-axis ближний блокер -> завышенная пенумбра) и (б) шов ширины блюра по границам cube-face (смена оценщика пирамида<->Vogel на границе). v2: рестракт откачен до legacy-структуры байт-в-байт, вместо него IRLITE_BLOCKER_TAPS_POINT = TAPS/2 под PREFILTER (Q2: 10->5; тиры 3/5/7/10), ТОЛЬКО point-путь (spot держит полный счёт — его поиск кормит реальную PCSS-пенумбру + EVSM-порог). Один оценщик везде = шва нет, weighted mean сохраняет contact hardening. Тапы пенумбра-фрагмента ~15 -> ~10. Урок: pyramid-min НЕ годится как blocker-оценка для lod (даже log2-потребителя) — футпринт-min систематически завышает пенумбру у корня.
- F4 DONE: irlite_cookie fY = min(c*inversesqrt(max(1-c^2,1e-12)), 114.58865) (паритет с VL shFY, минус 2 трансцендентные на тап); irlite_lightSurface ранний return при count==0 ДО mat3; irlite_outlineInk ранний return при count==0 ДО 5-тап edge-детектора.
- Реген (после v2): gen-complementary-patch.ps1 -> 21 ops (без дельты), PatchHarness round-trip ПУСТОЙ (--ignore-cr-at-eol, ex-штамп), run-пак синкнут (md5 0b6f1989...). git: изменён ТОЛЬКО patches/complementaryreimagined.irlights (+ Modification вне git).
- ЗАМЕР v1 (юзер, стресс-карта, дефолты обоих паков): +~4 FPS во всех тестах + обе регрессии (VL-бандинг, пенумбра) — они и привели к v2. Остальной налог кадра = P0 surface-цикл (core-фаза, отложено) + outline ON. Ре-замер v2 PENDING.
- A/B-стенд: run/shaderpacks/ComplementaryReimagined_IRLights_OLD = копия пака с baseline-шейдером (md5 27db944c, досессионный) — переключение в Iris UI; настроечных .txt у CR-пака нет, оба на дефолтах. НЕ синкать в _OLD новые правки.
- Бэкапы: scratchpad/irlite_lights.{baseline,pre-F1,pre-F2,pre-F4,pre-F1v2,pre-F2v2}.glsl (сессия c79d3ba9).
- Ре-тест v2 юзером: PASS — «регресс ушёл» (2026-07-10).
- F3 РЕШЕНО юзером 2026-07-10: тоггл Outline перф-разницы не показал => outline остаётся ON; единственная правка = дефолт IRLITE_OUTLINE_TARGET 0->1 (Both -> Entities Only, «и не более»). Применено в CR (:58), патч регенерирован (21 ops, round-trip чист), run-пак синкнут (md5 20a56392). Решение CR-специфично (пилот); канон-док project-photon-outline-switch-to-old НЕ переписывать до тиража.
- ЧЕКПОИНТ-КОММИТ по команде юзера 2026-07-10: master a43d46b (patches/complementaryreimagined.irlights). ФАЗА ЗАКРЫТА.
- СЛЕД. СЕССИЯ: выбор сделан 2026-07-10 = (а) core-фаза, реализована в [[plan-perf-fix-core-phase2]]. Исходная развилка была: (а) core-фаза — P0 cap+приоритизация N на flush / кластеризация (главный рычаг; per-fragment цикл по всем N в ~7 gbuffers-программах) + троттлинг mustBake (ShadowBaker:843, cold-start спайк) + hashmap-дедуп LightRegistry.slot() + гамма-предрасчёт цвета (core+шейдер синхронно); либо (б) тираж F1/F2/F3 на остальные 5 паков (shadow-body байт-идентичен CR). Список «ОТЛОЖЕНО» выше остаётся в силе.
- Гочи: перф-дельта F2 видна только при совпадающем irl-core билде (MSM/pyramid size-gate); runClient из PowerShell падает на JVM 8 — запускать через Git Bash c JAVA_HOME=Temurin-21.

Связь: [[project-perf-audit-irlite-2026-07-10]] (источник приоритетов), [[sync-workflow]] (Modification/patches/run цикл), [[complementary-pipeline]] (якоря CR).
