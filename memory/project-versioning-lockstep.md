---
name: project-versioning-lockstep
description: "Версионирование трилогии: semver MAJOR.MINOR.PATCH, lockstep (3 компонента под одним номером), единый источник = BbsRoot/VERSION, build-trilogy штампует во все MC-линии. Старт 1.1.1 (2026-07-09)."
metadata:
  node_type: memory
  type: project
  originSessionId: 1ff3aed7-6038-4f81-9dfb-a8ae1f9e0624
---

Схема версий трилогии (введена 2026-07-09, до этого все компоненты жили на плоском 1.1 в per-branch gradle.properties -> дрейф). Причина: юзер часто делает багфиксы, нужна нумерация, где юзер и он сам не путаются.

SEMVER MAJOR.MINOR.PATCH, LOCKSTEP (все три компонента — irl-core, IRLite addon, IRL-redactor — всегда ОДИН номер):
- MAJOR — ломается совместимость (формат .irlights, SSBO-layout, контракт core<->мод).
- MINOR — новая фича, старое работает (новый тип света, gobo, lens flare).
- PATCH — багфикс, поведение то же. Сюда идёт большинство релизов.

ЕДИНЫЙ ИСТОЧНИК ПРАВДЫ = файл BbsRoot/VERSION (одна строка, напр. 1.1.1). Продуктовая версия НЕ хранится по веткам — сборочный скрипт штампует её в каждый одноразовый worktree перед gradle (helper Set-WorktreeVersion, см. [[tool-build-trilogy-script]]): mod_version у addon+editor, irl_core_version у core, координата org.qualet:irl-core:<v> в build.gradle обоих консьюмеров (обе строки modClientImplementation+include). Закоммиченные gradle.properties веток НЕ трогаются (штамповка живёт только в throwaway-worktree). Покрыты ОБА пути: последовательный build-trilogy.ps1 и параллельный build-trilogy-parallel.ps1 (штампует в фазе SETUP + кладёт Version в LineJson -> build-line.ps1 читает её для core-jar проверок; UTF-8 без BOM, .NET WriteAllText). Переопределение разово: -Version X.Y.Z (иначе читается VERSION).

Раскладка на числа: продукт видит юзер в jar-именах — irlite-<v>+mc<MC>.jar, irl-redactor-<v>+mc<MC>.jar. core юзеру НЕ виден (JiJ внутри модов), его jar плоский irl-core-<v>.jar без +mc. Ось MC (+mc1.20.4) независима от продукт-версии — это цель сборки (semver build-metadata после +, игнорируется при сравнении старшинства), НЕ прогресс. Один VERSION -> N jar под разные MC = один релиз.

CHANGELOG.md в BbsRoot (Keep a Changelog стиль); секция на версию.

Цикл релиза (совмещён с [[commit-checkpoints]] + тайминг [[feedback-bump-version-on-commit]]): фикс+коммит по веткам -> (после команды коммитить) предложить бамп VERSION + строку CHANGELOG -> build-trilogy.ps1 (все MC-линии выходят синхронно под новым номером) -> опц. git tag vX.Y.Z. Дрейф между MC-линиями структурно невозможен: одно число красит всё.

Верифицировано прогоном линии 1.20.4 (2026-07-09, exit 0): core irl-core-1.1.1.jar (mavenLocal) + editor irl-redactor-1.1.1+mc1.20.4.jar + addon irlite-1.1.1+mc1.20.4.jar, JiJ-проверка (irl-core-1.1.1.jar внутри модов) прошла.

Связь: механизм сборки/штамповки = [[tool-build-trilogy-script]]; тайминг предложения = [[feedback-bump-version-on-commit]]; коммит-чекпоинты = [[commit-checkpoints]]; per-MC ветки трилогии = [[project-irl-sync-strategy]].
