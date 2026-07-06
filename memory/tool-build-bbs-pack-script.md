---
name: tool-build-bbs-pack-script
description: "Команда «Собери ббс пак» -> BBS\\build-bbs-pack.ps1 (создан 2026-06-29). Собирает irl-core (publishToMavenLocal v1.1) + 4 универс-1.20.x BBS-аддона (irlite/dof/refreshedui/curvefixer) и кладёт jar ПЛОСКО в C:\\Users\\Qualet\\Desktop\\bbs_pack. Родств. [[tool-build-trilogy-script]]."
metadata:
  node_type: memory
  type: project
  originSessionId: 1452fd22-e5a5-4f56-9939-f8c0f74967d2
---

build-bbs-pack.ps1 — команда «Собери ббс пак». Когда юзер говорит «Собери ббс пак» -> запусти C:\Users\Qualet\Documents\Project\Minecraft\BBS\build-bbs-pack.ps1 (лежит в родит. папке BBS, рядом с build-trilogy.ps1). Это ОТДЕЛЬНЫЙ набор от трилогии: пак = общий движок + 4 BBS-аддона, все универсальные MC 1.20.1–1.20.4, в одну плоскую папку (юзер кидает все jar в один инстанс 1.20.x).

Запуск (как trilogy — та же готча): & 'C:\Users\Qualet\Documents\Project\Minecraft\BBS\build-bbs-pack.ps1' -BbsRoot 'C:\Users\Qualet\Documents\Project\Minecraft\BBS' -KeepGoing. Передавать -BbsRoot ЯВНО (под Claude-обёрткой $PSScriptRoot пуст -> дефолт упал бы; в скрипте есть fallback-хардкод, но явный аргумент надёжнее). Запускать ПЛОСКО, без 2>&1/пайпа. Опц.: -Dest, -Clean, -KeepGoing.

Состав пака — 5 проектов (маппинг разговорных имён юзера -> папки). Юзер называл их неточно; вот канон (он сам подтвердил пути 2026-06-29):
| юзер сказал | реальная папка | mod id / jar base | ветка | как универсал-собрать | зависит от irl-core |
|---|---|---|---|---|---|
| irl-core | irl-core | irl-core v1.1 | main | build + publishToMavenLocal | — |
| «bbs аддон» | bbs-irlights-addon | irlite | master | дефолт build (-Pmc=1.20.1=универс, depends >=1.20.1 <1.20.5) | ДА, org.qualet:irl-core:1.1 из mavenLocal + JiJ include |
| bbs-dof-addon | bbs-dof-addon | irl_dof | master | дефолт build (нет -Pmc матрицы; depends [1.20.1,1.20.4]) | нет |
| bbs-refreshed-addon | resfreshed-addon (опечатка в имени папки!) | refreshedui | master | build -Pmc=universal | нет |
| bbs-shaderfixer-addon | bbs-curvefixer-addon (юзер сказал shaderfixer, имел в виду curvefixer) | curvefixer | main | build -Pmc=universal | нет |

Выходные jar в Desktop\bbs_pack (плоско): irl-core-1.1.jar (нативное имя), irlite-1.20.x.jar, irl_dof-1.20.x.jar, refreshedui-1.20.x.jar, curvefixer-1.20.x.jar + README.txt. Аддоны переименовываются в <base>-1.20.x.jar; core — нативное версионное имя.

Что скрипт делает (отличия от build-trilogy):
- irl-core ПЕРВЫМ (publishToMavenLocal v1.1) — иначе irlite не разрешит org.qualet:irl-core:1.1. irlite собирается с --refresh-dependencies (тянет свежий core). Остальные 3 аддона независимы.
- Ветки НЕ трогает в норме: все 5 уже на дефолт-ветках (irl-core=main, irlights/dof/refreshed=master, curvefixer=main). Ensure-Branch делает checkout ТОЛЬКО если репо на ДРУГОЙ ветке (и тогда требует чистое дерево + restore в finally). На текущей ветке — собирает как есть, грязное дерево ОК (в отличие от build-trilogy, который абортит на грязи).
- Чистит stale jar из build/libs каждого + старые *.jar в bbs_pack перед раскладкой.

Прогон-факты (2026-06-29, первый запуск, 5/5 зелёные ~92s):
- irl-core 3s / irlite 81s (refresh-deps + remap, медленный) / dof 3s / refreshed 3s / curvefixer 2s. Тёплый кэш -> большинство UP-TO-DATE.
- Ф2-shadow готча НЕ сработала: irlite master с core-1.1 (1.20.4 intermediary) + дефолт -Pmc=1.20.1 собрался зелёным. Универсал-jar для аддона жив (вопреки тревоге в [[tool-build-trilogy-script]] про «универсал больше не работает» — то про per-MC core линии, тут ОК).
- Безвредные варны при irlite: (1.1) is not valid semver for dependency org.qualet:irl-core:1.1 (Loom JiJ, как старый 1.0-obt); Cannot remap getRendertypeBreezeWindShader/... (sodium/breeze аксессоры, не в 1.20.1 таргете) — НЕ ошибки, build successful.

ГОТЧА, выловленная при создании (уже починена — не повторять): PowerShell имена переменных регистронезависимы: локальная $dest = Join-Path $Dest ... КЛОББЕРИТ параметр $Dest (это ОДНА переменная) -> путь накапливался bbs_pack\irlite-1.20.x.jar\irl_dof-...\.... Фикс: локальную назвал $destPath. (build-trilogy избежал этого, назвав локаль $target.)

Контекст: [[tool-build-trilogy-script]] (родственный скрипт трилогии), [[reference-edit-routing-by-area]], [[project-github-repos]].
