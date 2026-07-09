---
name: project-camera-relative-light-migration
description: "Camera-relative миграция света ВЫПОЛНЕНА 2026-07-09 (ultracode-сессия): irl-core PR#1 применён локально, все 7 патчей аддона + Modification + 7 DOF-патчей мигрированы, редактор реконвергирован (workтree). НЕ ЗАКОММИЧЕНО. Открыто: core PR#1-4 на GitHub, editor merge origin/main, VL-noise стал camera-locked."
metadata:
  node_type: memory
  mod_scope: irl-core-shared
  type: project
---

Баг: свет ~X=100000 пропадал — абсолютная реконструкция fragWorld = playerPos + cameraPosition теряла float-точность против позиции света в SSBO. Фикс: light SSBO camera-relative (irl-core `LightRegistry.flush(originXYZ)` вычитает камеру в double; старый flush() = делегат flush(0,0,0), ABI цел) + все инжекты дропают `+ cameraPosition`. Эталон = editor commit 48de059 + irl-core PR#1 (zqicev).

ВЫПОЛНЕНО 2026-07-09 (всё в рабочих деревьях, БЕЗ коммитов — ждёт коммит-чекпоинта юзера):
- irl-core main: pr1.diff применён (FramePipeline/LightRegistry/LightBuffer), `publishToMavenLocal` перевыпустил irl-core:1.1 camera-relative. PR#1-4 (по MC-линиям, идентичны) ВСЁ ЕЩЁ OPEN на GitHub — при коммите либо merge PR, либо пуш локального эквивалента.
- Аддон: 7 patches/*.irlights мигрированы. bliss/bsl/complementaryreimagined/rethinkingvoxels = байт-идентичны editor origin/main (cmp=0); solas (v3.7)/photon/iterationrp — ручные правки. Photon-форма: `position_world`→`position_scene` (surface), VL `world_start_pos/world_end_pos`→`gbufferModelViewInverse[3].xyz`/`scene_pos`. IterationRP: `cameraPosition + worldPos`→`worldPos`, irliteVLStart дропает cameraPosition, bobbing MVI[3] сохранён. Shadres/Modification/<все 7> — те же правки; PatchHarness round-trip (патч на Original → diff vs Modification) пуст у 6/7. Java аддона не менялся (зовёт FramePipeline.frame ядра). Сборка ок, JiJ-ядро в jar несёт flush(DDD) (javap-проверено).
- bbs-dof-addon: все 7 комбо-патчей *-irl-dof.irlights мигрированы теми же формами (DOF-аддон читает тот же binding-7 SSBO, своего ядра нет). Адверсариальное ревью: 0 абсолютных мест во всей трилогии+DOF.
- Редактор (рабочее дерево local main): copy-patches.ps1 прогнан (7/7 MD5 match — гоча «copy-patches ОПАСЕН» СНЯТА), .gitignore расижнорил iterationrp.irlights (untracked new), README + Rethinking Voxels + IterationRP† (Tahnass, платный) строки.
- tools/PatchHarness.java: 4 импорта перенацелены qualet.irlite.client.patcher.* → org.qualet.irl.patcher.* (движок в ядре; старый файл не компилился).

ТЕНИ НЕ ЛОМАЮТСЯ (аудит): шейдер восстанавливает spot view-proj / point cube-dir per-fragment из SSBO как РАЗНОСТИ (toR/dir/Loff = fragWorld−lp) — инвариантны к общему сдвигу. Java-бейк остаётся АБСОЛЮТНЫМ: ShadowBaker читает LightRegistry.getX/Y/Z (сырые массивы, вычитание только в flush→SSBO). ВАЖНО для будущих рефакторов: getX/Y/Z обязаны оставаться абсолютными.

ОТКРЫТО/наследство:
- Editor local main ahead 1 / behind 4 origin/main — нужен merge (solas.irlights сконфликтует; наша рабочая копия v3.7+camrel = правильное разрешение).
- Найдено ревью: VL-шум `irlite_vlNoise(startWorld + pos, ...)` после миграции camera-locked (пуфы плывут с камерой; было world-anchored). ТАК ЖЕ в эталоне 48de059 — оставлено эталон-точно, фикс (`+ cameraPosition` в домен шума) = отдельная задача на 8+7 патчей + редактор.
- Pre-existing дрейфы (НЕ наши): (1) patches/photon.irlights vs Shadres/Modification/Photon — разные поколения IRLite (VL_NOISE/меню/lang) + застрявший irlite_patched.txt в Modification → photon round-trip не пуст; (2) iterationrp-irl-dof якорь без `, fogTransmittance` (main-патч актуализирован 7423a16, DOF-копия нет — патч вообще не применяется к текущему паку); (3) solas-irl-dof целится в до-v3.7 Solas (4 битых якоря).
- irl-core PR не бампает версию (остаётся 1.1) — mavenLocal 1.1 теперь camera-relative, а 1.1.1 (от verify-прогона lockstep) АБСОЛЮТНЫЙ. До ребилда через build-trilogy под новым номером не смешивать.
- Верификация in-game 2026-07-09 (сессия IterationRP): телепорт на X=100000 — свет СВЕТИТ, disappearing-баг ИСПРАВЛЕН. Остаточный second-order джиттер (позиция неточная + скачки при движении) ДИАГНОСТИРОВАН И ПОЧИНЕН тем же днём (migration-сессия, Java-only, патчи НЕ трогались; сборки ok, финальное ревью ok):
  - Гипотеза «GPU реконструирует относительно FLOAT cameraPosition uniform» ОПРОВЕРГНУТА источниками: GPU-фрагмент = точная eye-relative реконструкция (viewPos из глубины; Sodium держит камеру int+frac, террейн-offset точен ~1.5e-5 на любых коордах; gbufferModelView без трансляции камеры). Больших float-координат на GPU нет вообще.
  - Реальные дефекты (оба CPU): (M2, скачки) flush на renderWorld HEAD брал камеру ПРОШЛОГО кадра (Camera.update идёт позже, bytecode offset 176) → весь свет смещён на кадр движения (~0.09 блока при спринте@60fps), ноль в покое; (M1, неточность) позиции квантовались в float ДО вычитания — registerPoint/Spot(float), px[] float[], плюс LightCollector собирал ModelBlock-свет через float Matrix4f с АБСОЛЮТНЫМ translate(100000) (координато-зависимый джиттер анимированных форм).
  - Фикс: (A) flush отложен — frame() на HEAD только collect+bake+flushPending; новый FramePipeline.uploadIfPending() зовётся из ВТОРОГО @Inject обоих миксинов после Camera.update (origin == камера кадра N, та же что у Iris); guard на отменённый renderWorld. (B) double end-to-end: px/py/pz double[], double-оверлоады registerPoint/Spot (float-версии делегируют, ABI цел, 6 оверлоадов javap-проверены), LightDriver (редактор)/PointLightFormRenderer/SpotlightFormRenderer/LightCollector передают double; LightCollector держит 100000 ВНЕ float-матриц (double base через walk/emit; алгебра сверена — позиции те же). getX/Y/Z для ShadowBaker = (float)px[i], абсолютные, поведение бейка не изменилось.
  - ЛОКСТЕП-КРИТИЧНО: frame() больше НЕ грузит SSBO — старый jar аддона/редактора (без второго inject) с новым ядром = свет вообще не грузится. Шипить core+addon+editor только вместе. Порты 1.21.x: точку инжекта после Camera.update пере-выводить (сигнатура renderWorld другая).
  - Осталось: in-game проверка на X=100000 (спринт) + контроль спавна. Если СТАТИЧНЫЙ свет на 100k всё ещё скачет после фикса — искать вне этого Java (re-audit GPU).

Связь: [[patcher]] + [[addon-light-buffer-ssbo]] (SSBO-контракт; posRadius.xyz теперь camera-relative!) + [[project-github-repos]] + [[sync-workflow]] + [[project-versioning-lockstep]] (бамп при коммите).
