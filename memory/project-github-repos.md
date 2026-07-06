---
name: project-github-repos
description: "GitHub-дом трилогии (создан 2026-06-20): 3 ПРИВАТНЫХ репо под owner quaIett (заглавная I) — irl-core / bbs-irlights-addon / irl-editor (бывш. irlights). gh CLI установлен, credential-helper настроен. Где origin у каждого и какие ветки запушены."
metadata:
  node_type: memory
  type: project
  originSessionId: 16e57e57-6e6f-4bc0-b9b7-f7fddfcb7982
---

GitHub-репозитории трилогии (созданы 2026-06-20). Трилогия (3 локальных репа из [[reference-edit-routing-by-area]]) опубликована на GitHub как ПРИВАТНЫЕ репозитории под аккаунтом quaIett (внимание: третий символ — ЗАГЛАВНАЯ I, не строчная l; git config user.name = qualet, это другое).

| Локальная папка | origin | default branch | запушенные ветки |
|---|---|---|---|
| irl-core | https://github.com/quaIett/irl-core.git | main | main |
| bbs-irlights-addon | https://github.com/quaIett/bbs-irlights-addon.git | master | master, port/1.21.1 |
| irlights (редактор) | https://github.com/quaIett/irl-editor.git | main | main, port/1.21.1, port/1.21.4, port/1.21.11 |

Все три — private. Тегов на момент создания не было.

Репо редактора ПЕРЕИМЕНОВАН 2026-06-20: irlights -> irl-editor (юзер: «redactor» — рунглиш-ошибка, по-английски правильно «editor»). Поменяны: имя GitHub-репо (старый URL редиректит) + локальный origin + брендинг в README всех 3 репов (IRL-redactor->IRL-editor, ссылки quaIett/irlights->quaIett/irl-editor). НЕ менялись (по просьбе «в коде не трогать»): локальная папка осталась irlights, пакет org.qualet.irlredactor, mod-id irl-redactor, archives_base_name=irl-redactor (-> jar всё ещё irl-redactor-*.jar, в README намеренно оставлено как фактическое имя).

Тулинг (важно для будущих push):
- gh CLI v2.95.0 установлен через choco -> C:\Program Files\GitHub CLI\gh.exe (PATH в новых шеллах подхватит; в старой сессии — полный путь).
- Авторизация: device-flow под quaIett, scopes repo, read:org, gist, токен в keyring.
- gh auth setup-git выполнен -> глобальный credential.helper = !gh auth git-credential -> git push по https работает без промптов.

README + лицензия (2026-06-20):
- У всех трёх репов есть README.md (стиль: центрированный заголовок, shields.io-бейджи, таблицы фич/версий) + LICENSE = MIT, на ВСЕХ ветках. Шаблон стиля = README аддона.
- Аддон перелицензирован: был CC BY-ND 4.0 (LICENSE.txt, NoDerivatives, © wemppy+qualet) -> теперь MIT (LICENSE.txt удалён, добавлен LICENSE).
- Копирайт во всех MIT-файлах = qualet (по решению юзера wemppy убран, хотя старая лицензия аддона его упоминала — учесть, если всплывёт вопрос соавторства).

IterationRP убран + ИСТОРИЯ ВЫЧИЩЕНА для публикации (2026-06-20): IterationRP — приватный/платный шейдерпак (Tahnass). По решению юзера интеграция убрана до разрешения автора; файлы оставлены локально (gitignored, НЕ удалены). НЕ возвращать в git без явной просьбы.
- Делистнут из всех 6 README; untracked + в .gitignore (маркер # IterationRP integration): аддон patches/iterationrp.irlights + tools/gen-iterationrp-patch.ps1; редактор src/client/resources/assets/irl-redactor/patches/iterationrp.irlights.
- НАХОДКА: в ИСТОРИИ аддона лежал весь Shadres/ (полные исходники IterationRP платный + Photon + Original + Modification) — когда-то закоммичен, потом gitignored (в HEAD не было, только в истории). Это блокировало публикацию.
- История ПЕРЕПИСАНА git filter-repo (вырезано из ВСЕХ коммитов/веток): аддон = Shadres/ + regex (?i)iterationrp (поймал и старое имя patches/iterationrp.irlpatch до ребрендинга .irlpatch->.irlights); редактор = regex (?i)iterationrp + \.log$ (5 dev-логов). irl-core НЕ трогали (история чиста).
- Репо bbs-irlights-addon и irlights УДАЛЕНЫ и ПЕРЕСОЗДАНЫ на GitHub (нужен был скоуп delete_repo — добавлен), залита чистая история; проверено свежим mirror-клоном: 0 совпадений shadres/iteration/.log. Дефолт-ветки восстановлены (master/main).
- SHA ВСЕХ коммитов аддона/редактора СМЕНИЛИСЬ (история переписана) -> ссылки на старые SHA в памяти/доках устарели (код тот же).
- Бэкап ДО переписи: BBS/_history-backup-20260620/{irl-core,bbs-irlights-addon,irlights}.bundle (addon.bundle 30M = со старым Shadres). Откат: git clone <bundle>.
- patches.zip (аддон) — локальный, не в git.
- Видимость на 2026-06-20 всё ещё private у всех трёх; юзер планирует сделать публичными (теперь безопасно).

Гочи:
- Старый remote аддона указывал на quaIett/IRLights.git — этот репо УДАЛЁН (404 даже после авторизации). origin аддона переуказан на новый bbs-irlights-addon.
- Owner легко опечатать: quaIett != qualett != qualet.
