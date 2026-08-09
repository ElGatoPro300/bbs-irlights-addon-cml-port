---
name: project-trilogy-unify-11
description: "Унификация трилогии 2026-07-08 - все 5 линий редактора на per-version ядре (E3 1.21.1 + 1.20.1 done), версия 1.1 везде, jar-нейминг name-1.1+mc<ver>, новый build-trilogy.ps1, раскладка Desktop/IRLights/{Redactor,BBS Addon}; 7 коммитов."
metadata: 
  node_type: memory
  type: project
  originSessionId: a4cbd228-8622-4d93-9fb1-677218138c68
---

Статус 2026-07-08: ЗАКОММИЧЕНО по подтверждению юзера (7 коммитов). Выполнено двумя workflow (wf_2fd348be irl-unify-11: 9 агентов, все PASS 0 blocker/major; wf_e10318c4 irl-e3-1201: 2 агента, APPROVE).

КОММИТЫ:
- редактор irlights: main@63f0d3c (свип версии/нейминга), port/1.20.1@8b14f4d (merge main e147571 - НОВОЕ: линия на ядре), port/1.21.1@b00e496 (merge main e147571 - НОВОЕ: линия на ядре, E3 закрыт), port/1.21.4@00b7990 (свип), port/1.21.11@8f1e6f7 (свип). Push не делался.
- аддон bbs-irlights-addon: master@940b740 (свип, только build.gradle; локальное удаление юзера в run/ не тронуто), port/1.21.1@2e34625 (свип).
- ядро irl-core: БЕЗ изменений (координата org.qualet:irl-core:1.1 сохранена).

ИТОГ АРХИТЕКТУРЫ: вклеенных копий движка НЕ ОСТАЛОСЬ нигде - все 5 линий редактора (main 1.20.4, port/1.20.1, port/1.21.1, port/1.21.4, port/1.21.11) + обе линии аддона потребляют per-version ядро из mavenLocal с JiJ. E3 из плана редактора ЗАКРЫТ ПОЛНОСТЬЮ.

ВЕРСИИ/НЕЙМИНГ: mod_version=1.1 везде (редактор был 1.0-obt); в build.gradle version = "${mod_version}+mc${minecraft_version}" (у аддона суффикс от резолвнутого -Pmc) -> jar irl-redactor-1.1+mc<ver>.jar / irlite-1.1+mc<ver>.jar; fabric.mod.json version через expand = 1.1+mc<ver>. Loom semver-warning на "1.1" остался (косметика).

E3-детали:
- port/1.21.1 merge: дифф против main = 6 файлов (эталон 1.21.4 имел 8); кастер RedactorEntityCasterSource байт-идентичен main (yaw жив на 1.21.1); GameRendererLightMixin = main-делегат + renderWorld(RenderTickCounter)/getTickDelta(true); LightGuideRenderer = main-логика + 1.21.1 buffer-rework; iris-миксины = main (делегация в core IrlSamplersBind, 2-арг на Iris 1.8.8); replaymod dev-зависимость снята (как на 1.21.4). Iris 1.8.8+1.21.1, sodium 0.6.13, Loom 1.15.5.
- port/1.20.1 merge: НОЛЬ git-конфликтов (порт не имел java-правок); дифф против main = 3 сборочных файла (деп-матрица: LWJGL 3.3.1 пин, iris 1.7.2+1.20.1, sodium mc1.20.1-0.5.11, replaymod 1.20.1-2.6.19, yarn 1.20.1+build.10, fabric 0.92.9+1.20.1, Loom 1.9-SNAPSHOT). Ядро = MAIN (ветка core 1.20.1 НЕ нужна: поверхность API совпадает с 1.20.4, компиляция чистая; прецедент универсального jar подтверждён). Вложенный core сверен по sha256 с mavenLocal.

ДОСТАВКА Desktop/IRLights (структура юзера, внутренняя папка IRLights переименована в Redactor): Redactor/{1.20.1,1.20.4,1.21.1,1.21.4,1.21.11}/irl-redactor-1.1+mc<ver>.jar + "BBS Addon"/{1.20.x,1.21.1}/irlite-1.1+mc<ver>.jar. Jar ядра юзеру НЕ кладутся (JiJ). В "BBS Addon"/1.20.x сосуществуют +mc1.20.4 и +mc1.20.1.

BUILD-TRILOGY.PS1 (BBS-корень, вне git) ПЕРЕПИСАН: линии строго последовательно (mavenLocal общий), каждая = publishToMavenLocal ветки core -> сборка потребителей --refresh-dependencies -> Assert имени jar + JiJ -> копия в Dest. Detached temp-worktree (.build-wt/), уборка gradlew --stop -> Remove-Item -> prune. Параметры: -Dest, -Lines, -JavaHome (дефолт Temurin 21), -KeepWorktrees, -BbsRoot. Линии: 1.20.4 (core main -> редактор main + аддон -Pmc=1.20.4), 1.21.1, 1.21.4, 1.21.11, 1.20.1 (core main -> редактор port/1.20.1 + аддон -Pmc=1.20.1; порядок последним). Пост-ревью фикс: зачистка в Dest затирает ТОЛЬКО одноимённый same-MC jar и старые имена без +mc (сиблинги в общей 1.20.x живут). Бэкап старого скрипта в scratchpad сессии.

ФАКТЫ ОКРУЖЕНИЯ: JDK 21 = C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot (старый путь C:/Users/Qualet/.jdks/ms-21.0.11 МЁРТВ; дефолтный JAVA_HOME системы = JVM 8 - gradlew без явного JAVA_HOME падает). Worktree аддона требует провижининга libs/ (gitignored bbs-jar копировать из основного дерева - script/агенты уже знают).

ФИНАЛ 2026-07-08: (1) build-trilogy.ps1 ПОДТВЕРЖДЁН боевым прогоном - 13/13 таргетов OK за 2063s, включая ПЕРВУЮ сборку irlite-1.1+mc1.20.1.jar (аддон 1.20.1 на core main - работает); до зелёного прогона скрипт получил 3 закалки: (а) PS 5.1 stderr-ловушка - все нативные вызовы обёрнуты EAP='Continue'+2>&1 stringify, гейт по $LASTEXITCODE (без этого git «Preparing worktree» в stderr под редиректом = FATAL); (б) Copy-LocalLibs - провижининг gitignored libs/*.jar из основного чекаута аддона в worktree (иначе Loom NoSuchFileException на bbs-jar); (в) per-target логи .build-wt/logs/<line>-<comp>.log + Show-LogTail при падении. (2) ВСЯ трилогия ЗАПУШЕНА на GitHub (quaIett): core main+1.21.1+1.21.4+1.21.11 (ветки новые на remote) + тег v1.1; редактор все 5 веток; аддон master+port/1.21.1. (3) Все worktree убраны (8 сессионных _wt-* + 8 хвостов .build-wt/ - teardown скрипта не всегда добивает каталоги из-за локов демона, прибирать руками: gradlew --stop -> rm -rf -> prune). (4) mavenLocal = core main (последняя линия прогона 1.20.1). (5) Память обновлена: tool-build-trilogy-script переписан, reference-edit-routing-by-area (таблица версий, toolchain, JDK-путь).

ОТКРЫТО: (1) ин-гейм проверки юзером: редактор 1.20.1 и 1.21.1 (новые merge-линии; core main на рантайме 1.20.1 in-game не проверялся), аддон 1.20.1; (2) nit: RedactorPatcherHost.BUNDLED несёт iterationrp.irlights при 6 патчах на диске (пре-существующее, gitignored-локальный пак).

Связь: [[project-port-12111-refresh]] (вчерашний порт 1.21.11), [[plan-port-1211-workflow]] (порт 1.21.1 аддона), [[reference-edit-routing-by-area]], [[tool-build-trilogy-script]], [[commit-checkpoints]].
