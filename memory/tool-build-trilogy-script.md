---
name: tool-build-trilogy-script
description: "Скрипт сборки+раздачи ВСЕЙ трилогии на все версии MC: BBS/build-trilogy.ps1 (вне репов, в родит. папке BBS). Чекаутит ветки, gradlew build -x test, раскладывает jar по папкам версий в C:\\Users\\Qualet\\Desktop\\IRLights. Создан 2026-06-20."
metadata:
  node_type: memory
  type: project
  originSessionId: d0fdc802-9bfd-472c-b6b0-df6d6236bcf9
---

build-trilogy.ps1 — сборка всей трилогии на все версии MC. Лежит в C:\Users\Qualet\Documents\Project\Minecraft\BBS\build-trilogy.ps1 (в РОДИТЕЛЬСКОЙ папке BBS, НЕ внутри какого-либо из 3 репов — оркестрирует все три). Запуск: & 'C:\Users\Qualet\Documents\Project\Minecraft\BBS\build-trilogy.ps1' (опц. -Dest, -BbsRoot, -Clean, -KeepGoing).

Что делает: для каждой (репо, ветка) -> git checkout -> удаляет stale jar из build/libs -> gradlew build -x test -> копирует свежий jar (переименованный с версией MC) в папку версии под -Dest (по умолч. C:\Users\Qualet\Desktop\IRLights). В конце пишет README.txt + сводку, восстанавливает исходные ветки обоих модов (try/finally). Перед стартом: аборт если рабочее дерево грязное (чекаут затёр бы правки).

Матрица 8 сборок (default-args, БЕЗ -Pmc — addon-default сам = 1.20.1/1.21.1, заодно обходит дробление -Pmc=1.20.1 по точкам в PowerShell):
- irl-core main -> универс. версионно-нейтральный jar (gradlew build -x test, ветку не трогаем) -> копируется в КОРЕНЬ Desktop\IRLights\ (по просьбе юзера 2026-06-20: «core универсальный -> в корень»).
- addon master -> универс. 1.20.1+1.20.4 (jar в обе папки) · addon port/1.21.1 -> 1.21.1
- redactor port/1.20.1->1.20.1 (добавлен 2026-06-21, см. [[project-port-1201]]) · main->1.20.4 · port/1.21.1 · port/1.21.4 · port/1.21.11

Раскладка Desktop\IRLights\: КОРЕНЬ = irl-core-1.0-obt.jar (универс.) + README.txt; папки версий: 1.20.1 (addon+editor) · 1.20.4 (addon+editor) · 1.21.1 (addon+editor) · 1.21.4 (editor) · 1.21.11 (editor). jar универсал аддона = irlite-addon-1.20.x.jar; редактор = irl-redactor-<MC>.jar.

Несущие факты среды (проверены 2026-06-20): JAVA_HOME=...\.jdks\ms-21.0.10 (Java 21) — gradlew его юзает, годен для всех Loom (addon master/port=1.15.5, redactor main=1.9.2, 1.21.4=1.15.5, 1.21.11=1.14.10); BBS-jar 1.20.1/1.20.4/1.21.1 лежат в bbs-irlights-addon/libs/. Полный прогон с тёплыми кэшами ~50s, 8/8 зелёные (2026-06-21).

ГОТЧА ЗАПУСКА (2026-06-21): при запуске через инструмент-обёртку Claude (powershell -ExecutionPolicy Bypass -File ...build-trilogy.ps1, в т.ч. в фоне) $PSScriptRoot приходит ПУСТЫМ -> -BbsRoot дефолтится в "" -> падает мгновенно: «Cannot bind argument to parameter 'Path' because it is an empty string». Фикс: всегда передавать -BbsRoot "C:\Users\Qualet\Documents\Project\Minecraft\BBS" явно. Полный прогон тёплым кэшом ~40-100s, 8/8 зелёные.

ГОТЧА (уже починена в скрипте, не повторять): в PowerShell 5.1 git checkout ... 2>&1 | Out-Null под $ErrorActionPreference='Stop' падает — git пишет «Switched to branch» в stderr, а 2>&1 оборачивает это в терминирующий NativeCommandError -> ложный exit 1. Фикс: НЕ редиректить stderr git'а (& git checkout ... | Out-Null + try/catch). Имена jar версию MC НЕ несут (оба репа = фикс. имя из archives_base_name) -> переименование при копировании обязательно.

ГОТЧА ВЫЗОВА — НЕ оборачивать запуск скрипта в 2>&1 (2026-06-21): запуск & build-trilogy.ps1 ... 2>&1 | Select-String ... РЕ-ТРИГГЕРИТ ту же stderr-gotcha НА УРОВНЕ ВЫЗОВА — 2>&1 мержит stderr git checkout внутри скрипта в поток, и под $ErrorActionPreference='Stop' КАЖДАЯ цель «падает» с FAILED: Switched to branch '…'. Запускать скрипт ПЛОСКО, без 2>&1 и без пайпа в Select-String (вывод = Write-Host, всё равно не фильтруется пайпом). Длинный вывод терпим (~60 строк).

ГОТЧА ГРЯЗНОГО ДЕРЕВА — addon :TEMP (2026-06-21): pre-flight абортит «Working tree of 'bbs-irlights-addon' is dirty», если в аддоне есть untracked-папка :TEMP (имя с приватным BBS-глифом U+F03A; git status --porcelain -> ?? "\357\200\272TEMP/"). Это побочный продукт gradle-сборки аддона (пустая на первом прогоне -> git её не видит; после сборки в ней появляется файл -> видна). Память [[reference-edit-routing-by-area]]: TEMP/ не коммитить. Фикс НЕ-инвазивный: добавить *TEMP/ в bbs-irlights-addon/.git/info/exclude (локальный, не коммитится, не удаляет папку) -> git status --porcelain снова пуст.

ГОТЧА ПОВРЕЖДЕНИЯ ИНДЕКСА (2026-06-21): при быстром цикле checkout+cherry-pick+gradle на irlights ловился fatal: index file corrupt / bad signature 0x00000000 (гонка git-записи с Loom/AV-локом). HEAD+объекты+рефы целы (всё закоммичено) — повреждён ТОЛЬКО .git/index. Восстановление БЕЗ потерь: rm -f .git/index && git reset --hard HEAD (пересобирает индекс из HEAD; безопасно, т.к. правки закоммичены), затем git checkout <нужная-ветка>. После — git fsck --connectivity-only (dangling tree = норма).

ГОТЧА ПОСТ-Ф2 (проверено 2026-06-25, 8/8 зелёная ~65с): после выноса теней в irl-core (Ф2, [[plan-irl-core-shadow-extraction]]; коммиты core 1b96e74 / ред d8705f4 / аддон 3e5066c) линия 1.20.4 (redactor main + addon master) тянет irl-core из mavenLocal (новая вязка modClientImplementation; composite includeBuild снят ТОЛЬКО на этих ветках). Скрипт (от 21 июня, до-Ф2) НЕ публикует core -> перед запуском вручную: в irl-core gradlew publishToMavenLocal (иначе main/master не разрешат org.qualet:irl-core:1.0-obt или возьмут устаревший). Порт-ветки (redactor port/1.21.1·1.21.4·1.21.11·1.20.1 + addon port/1.21.1) Ф2 ещё НЕ получили — всё ещё composite includeBuild :irl-core против main-core (Loom-сабпроект собирается, безвредный варн (1.0-obt) is not valid semver). Т.е. матрица сейчас гибрид: 1.20.4=mavenLocal, остальное=includeBuild — но с ручным publish ВСЯ трилогия зелёная. (Дополнить скрипт publish-шагом — НЕ сделано.)

Контекст трилогии: [[reference-edit-routing-by-area]], [[project-github-repos]], [[project-irl-sync-strategy]].
