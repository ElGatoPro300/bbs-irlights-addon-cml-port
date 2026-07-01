---
name: feedback-addon-runclient-command
description: "Рантайм-проверку аддона (bbs-irlights-addon) всегда гонять dev-клиентом командой gradlew runClient -Pmc=1.20.4 --console=plain с логом в run/runclient-console.log. Prism-деплой НЕ используется — только runClient@1.20.4."
metadata:
  node_type: memory
  mod_scope: addon
  type: feedback
  originSessionId: c719902a-cf62-4357-9ae6-8e21b7ce780a
---

Весь рантайм-тест BBS-аддона (bbs-irlights-addon) проводим ОДНОЙ командой из корня репо аддона (C:\Users\Qualet\Documents\Project\Minecraft\BBS\bbs-irlights-addon):

```
.\gradlew.bat runClient '-Pmc=1.20.4' --console=plain 2>&1 | Out-File -FilePath "run\runclient-console.log" -Encoding utf8
```

Why: единый воспроизводимый способ поднять dev-клиент для проверки шейдер-инжекта/света/теней. `2>&1 | Out-File run\runclient-console.log` кладёт весь вывод (stdout+stderr) в файл, чтобы Claude потом разобрал старт/краши/ошибки компиляции по логу. `--console=plain` убирает ANSI-прогрессбар (чистый парсимый лог). `-Pmc=1.20.4` обязателен: core теперь per-MC ([[reference-edit-routing-by-area]]) — версия должна совпасть с опубликованной core (нужен core-1.20.4 в mavenLocal).

How to apply: на запрос «запусти клиент / рантайм-проверка» по аддону — эта команда. Запускать в ФОНЕ (runClient висит, пока юзер не закроет клиент; сам не завершается). Дальше следить за run/runclient-console.log — ждать маркеры успешного старта (LWJGL/Sound engine/OpenGL) либо ошибки (BUILD FAILED / Exception / mixin apply failed). Если менялся Java/core/тени — сперва irl-core publishToMavenLocal + пересборка (--refresh-dependencies, clean). Шейдер-инжект (.irlights) в игре подхватывается через Apply Patch — гоча stale-working-copy в [[outline-target-entity-detection]]. Коммит-политика: [[commit-checkpoints]].

ПОДТВЕРЖДЕНО ЮЗЕРОМ 2026-07-01: рантайм ВСЕГДА проверяется dev-клиентом runClient против 1.20.4 — это единственный способ проверки в этом проекте. Prism-инстанс (C:\prismlauncher\instances\BBS) для проверки НЕ используется: НЕ собирать remapJar под Prism, НЕ копировать build/libs/*.jar в prismlauncher\...\mods\, НЕ трогать Prism working-copy патчей и НЕ полагаться на in-game Apply Patch в Prism. Забыть про Prism-деплой целиком. (Прежний «Цикл на каждый пак» в [[outline-target-entity-detection]] содержал шаг copy-jar-в-Prism — он ОТМЕНЁН этим правилом; вместо Prism-деплоя проверяем в runClient.)

КОРЕНЬ stale-working-copy ПОДТВЕРЖДЁН 2026-07-01 (уточняет теорию PatchLibrary.extracted-гонки из [[outline-target-entity-detection]]): реальный виновник — СТАЛЫЙ jar в run/mods/irlite-1.20.x.jar (ручной остаток прошлой сессии). runClient-Fabric ГРУЗИТ мод из run/mods/*.jar, если он там есть (процессит в run/.fabric/processedMods/irlite-1.1-<hash>.jar), а НЕ из свежего build/resources — прежнее утверждение «грузится из sourceSet» неверно, когда jar лежит в run/mods. Тогда openBundledPatch (getResourceAsStream /assets/irlite/patches/*) читает СТАЛУЮ встроенную копию из этого jar -> extractBundled перезаписывает run/irlite/patches свежую-ручную-копию сталью (лог «Refreshed bundled patch» ВВОДИТ В ЗАБЛУЖДЕНИЕ — рефреш из старого jar) -> in-game Apply Patch клепает сталый applied-пак (симптом: новой опции нет в Iris, screen-строка старая). Диагностика: `find run build -iname 'iterationrp.irlights'` + `find run -iname 'irlite*.jar' | unzip -p ... | grep -c <новый-токен>` — jar с 0 токенов = виновник. ФИКС (сработал, рабочая копия осталась 10 TARGET после релонча): (1) cp build/libs/irlite-1.1.jar -> run/mods/irlite-1.20.x.jar; (2) rm run/.fabric/processedMods/irlite-1.1-*.jar (форс ре-процесс); (3) cp patches/<pack>.irlights -> run/irlite/patches/; (4) прямой regen applied-пака PatchHarness patches/<pack>.irlights run/shaderpacks/<pack> run/shaderpacks/<pack>_IRLights. Т.е. цикл на пак теперь: правки Modification -> gen -> round-trip -> remapJar --rerun-tasks -> cp build/libs/irlite-1.1.jar в run/mods/irlite-1.20.x.jar + очистить processedMods -> runClient. Открыто (проверить в след. сессии): удалить run/mods/irlite-1.20.x.jar совсем и проверить, инжектит ли Loom dev-sourceSet сам (тогда ручной cp в run/mods не нужен вообще).

Гоча PowerShell (споткнулся 2026-07-01): аргумент -Pmc=1.20.4 БЕЗ кавычек ломается в powershell.exe — gradle получает -Pmc=1 (обрезка на точке) и падает «Unknown -Pmc=1; expected 1.20.1 or 1.20.4». Всегда квотить: '-Pmc=1.20.4' (одинарные), либо запускать gradlew через Bash-tool (Git Bash аргумент не корёжит).
