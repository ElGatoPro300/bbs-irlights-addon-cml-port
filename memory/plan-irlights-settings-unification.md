---
name: plan-irlights-settings-unification
description: "Единый дизайн Iris-настроек для всех 7 паков + видимый ребрендинг IRLite -> IRLights; демо done, перенос done (харнесс-валидация чисто), open: коммит + ин-гейм прогон"
metadata: 
  node_type: memory
  type: project
  originSessionId: 8899d476-6e6a-428a-a2a7-e23bed6aa53c
---

Задача (старт 2026-07-02, подтверждённое переключение с фазы VL-noise): унифицировать категорию настроек в Iris UI во всех 7 паках + переименовать видимое «IRLite Lights» -> «IRLights». Демо: docs/irlights-settings-demo.html в bbs-irlights-addon (интерактивный мок Iris GUI + спека + таблица разнобоя; docs/ пока untracked).
Статус 2026-07-02: перенос ВЫПОЛНЕН во все 7 паков (Modification-деревья + реген патчей; Photon вручную). Каждый пак валидирован: PatchHarness2 apply на Shadres/Original + git diff --no-index --ignore-cr-at-eol против Modification = чисто. Photon получил новое: NORMAL_OFFSET и VL_SHADOWS выведены в UI, NORMAL_OFFSET добавлен в sliders, добавлен ru_RU-оп (якорь option.BOX_LINE_WIDTH). Юзер проверил и подтвердил; коммит 3b3d79a (2026-07-02, патчи+генераторы+демо, рабочее дерево чистое). Остаточный риск — полный ин-гейм прогон всех 7 паков при случае.
Комбо-синк 2026-07-02 (по команде юзера): канон + VL-noise перенесены в 7 IRL+DoF комбо-патчей bbs-dof-addon/patches/*-irl-dof.irlights скриптом scratchpad/sync-combo.ps1 (парсинг опов file+verb+anchor, своп тел, совпавших со старыми ревизиями main: 6545101/32c1be5/a74a906/27083f3; iterationrp old-ref = build/resources копия). 38 тел заменено + RU-оп вставлен в photon-комбо; 2 умышленных расхождения (BSL "#ifdef FSH", Bliss diffuse_lighting-include — комбинированные IRL+DoF тела, сегодня не менялись) не тронуты. Все 7 комбо применяются к Original чисто (PatchHarness2). Комбо отставали на 2 коммита (mirror был только до cookie) — VL-noise доехал этим же синком. Коммит dof-репо 5f89282, дерево чистое. Задача ЗАКРЫТА (обе линии: main 3b3d79a + комбо 5f89282); остаточное — ин-гейм прогон при случае.
Инфра-находки 2026-07-02: (1) tools/PatchHarness.java устарел (импортирует qualet.irlite.client.patcher — классы переехали в irl-core org.qualet.irl.patcher; рабочий вариант = копия харнесса с новыми импортами, javac -sourcepath irl-core/src/main/java). (2) Modification/Photon был несинхронен с патчем (патч = истина); дерево перезалито из applied-выхода 2026-07-02. (3) patches/iterationrp.irlights + gen-iterationrp-patch.ps1 НАМЕРЕННО в .gitignore (платный шейдер, локально до разрешения автора) — их правки в git-диффах не видны, это норма.

Канон дизайна (утверждать по демо):
- Ребрендинг только видимого: главная кнопка §6§lIRLights§r, подэкраны без префикса, порядок на главном экране Specular/Shadows/Toon/Volumetric/Outline (Toon ПЕРЕД Volumetric — правка юзера 2026-07-02). Дефайны IRLITE_*, irlite_* GLSL, struct IrliteLight НЕ трогать (ноль риска, Iris хранит выбор юзера по имени дефайна).
- Ключи экранов -> канон: screen.IRLIGHTS, IRLIGHTS_SPECULAR, IRLIGHTS_SHADOWS, IRLIGHTS_TOON, IRLIGHTS_VOLUMETRIC, IRLIGHTS_OUTLINE (невидимы игроку, живут только в инжектах).
- Единые дефолты (правка #define в options-блоке патчей, утверждено юзером): IRLITE_VL_STEPS 48 (было 14), IRLITE_VL_SHADOW_STRIDE 1 (было 2), IRLITE_OUTLINE вкл по умолчанию, IRLITE_OUTLINE_FRONT и IRLITE_OUTLINE_GLOW выкл.
- columns=2 везде (сейчас Bliss=1). Каждый подэкран: тумблер фичи первым, группы разделены пустой строкой <empty> <empty>.
- Цвета: §6 главный, §b Specular, §3 Shadows, §d VL, §a Toon, §e Outline.
- sliders= канон: все числовые; кнопки-циклы только тумблеры + IRLITE_VL_RESOLUTION (3 значения с лейблами Full/Half/Quarter).
- Лейблы/тултипы EN+RU единые (лучшие из текущих, зафиксированы в демо). RU сейчас только Solas/Bliss — добавить ru_RU-инжект остальным (где у пака есть ru_RU.lang).

Раскладка экранов (порядок опций) — источник истины = SCREENS в демо-файле.

Текущий разнобой (что чинит перенос): заголовок «IRLite Lights» у CR/RV/BSL/Solas/Bliss; ключи IRLITE_*_SETTINGS (CR/RV/BSL/Solas) vs *_SCREEN (Bliss) vs *_SCR (IterationRP) vs irlite_* lowercase (Photon). Дырки в UI: SPECULAR_SMOOTHNESS нет в CR/RV/Photon/IterationRP; SHADOW_NORMAL_OFFSET нет в UI Photon (дефайн есть); VL_RESOLUTION нет в Bliss/Photon/IterationRP (нет пасса — строку просто убирать); VL_SHADOWS+STRIDE нет в UI Photon (VL-тени там всегда вкл). Опции без физической поддержки в паке не показывать — единая структура допускает выпадение строки.

Порядок переноса: один пак = один проход по .irlights (ключи -> раскладка -> lang EN -> lang RU -> sliders=). Очередь: CR (эталон) -> RV -> BSL -> Solas -> Bliss -> Photon -> IterationRP; после каждого diff-проверка, в конце общий ин-гейм прогон. Связь: [shader-settings], [project-irl-sync-strategy].
