---
name: fix-shadow-depthstate-repin
description: "Фикс (irl-core d21320c, на main): тень model-block (bbmodel) ПРОПАДАЕТ, когда игрок берёт предмет в руку от 1-го лица, и ВОЗВРАЩАЕТСЯ при отлёте. Причина — shared per-pass depth-state, испорченный self-drawing кастером; решение — ре-пин depth/blend перед каждым emit (depth-аналог MAJOR-A). Подтверждено на ране GL-логом."
metadata:
  node_type: memory
  mod_scope: irl-core-shared
  type: reference
  originSessionId: e9007a62-e5da-4d8e-84e8-176830474140
---

«Тень model-block пропадает при предмете в руке» — диагноз + фикс.

Симптом (BBS-аддон): тень от model-block с bbmodel исчезает, когда игрок берёт в руку любой предмет от 1-го лица (меняет вьюмодел рука->айтем); при отлёте на дистанцию света тень возвращается.

Корневая причина (доказана на ране):
- Локальный игрок ВСЕГДА окклюдер — IRLiteBbsCasterSource.collect arm 1 берёт все LivingEntity в COLLECT_DIST=72 от камеры, БЕЗ исключения камерного игрока (ты на дистанции ~0).
- Предмет в руке рисуется через BBS FormRenderer, который делает RenderSystem.disableDepthTest() (и может оставить blend) ВНУТРИ emitOccluder игрока (как MorphRenderer bbs-fs строки 46/60).
- ShadowRenderer.beginCasterBatch пиннит depthMask/depthTest/blend ОДИН раз на пасс. Следующий кастер в том же CASTERS_DYNAMIC-пассе — model-block (BBS ModelForm self-draw через glDrawArrays) — наследует depthTest=false -> не пишет в depth-map -> тень молча исчезает (no GL error; тот же режим, что обходит renderBlocksDepth, см. ShadowRenderer:429).
- Игрок и model-block в одном пассе, игрок эмитится ПЕРВЫМ (порядок коллекта entity->model-block). Дистанция: отлёт за lightRange + радиус убирает тебя из окклюдеров этого света (scanInRange) -> порчи нет -> тень возвращается.
- Триггерит даже ВАНИЛЬНЫЙ блок-айтем (white_concrete), не только BBS-айтемы -> фикс нужен общий.

Фикс (irl-core, ShadowRenderer.emitCaster, commit d21320c на ветке main): ре-пин depth-стейта перед КАЖДЫМ emit — depth-аналог MAJOR-A (который уже ре-устанавливает матрицы):
```java
RenderSystem.depthMask(true);
RenderSystem.enableDepthTest();
RenderSystem.disableBlend();
```
Shared-ядро -> оба мода подхватят после publishToMavenLocal + пересборки. Риск минимальный (идемпотентные сеты; бейк и так хочет depth-test ON).

Подтверждение на ране: GL-лог в emitCaster показал MODEL_BLOCK ENTER 315/315 с depthTest=true ПОСЛЕ фикса (было 30 испорченных depthTest=false blend=true), при том что SELF EXIT с предметом по-прежнему depthTest=false — т.е. лечит именно ре-пин, а не «предмет перестал портить».

Диагностическая техника (переиспользовать): при «тень молча пропала, no GL error» — логировать GL{depthMask/depthTest/blend/cull/currentProgram} на входе/выходе emit каждого кастера через GL11.glGetBoolean/glIsEnabled + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM). Ловит порчу shared per-pass GL-состояния, которую матричный MAJOR-A не покрывает.

Этот файл = MAJOR-A (ре-пин depth/матриц): тень model-block пропадает, когда предмет в руке self-draw'ит c disableDepthTest от 1-го лица -> ре-пин depth/blend/матриц перед КАЖДЫМ emit.
Компаньон MAJOR-B (другой баг того же шва): feet-pivot AABB НЕДООЦЕНИВАЕТ габарит при повороте энтити -> эмитить bounding-СФЕРУ на feet-center через sink.emit (НЕ сырой AABB). Оба = «тень молча врёт/пропадает», лечатся на стороне эмита кастера.

Открытый хвост: port/1.21.11 редактора — отдельный raw-GL ShadowRenderer (не из core); проверить, нужен ли там аналогичный ре-пин (вероятно нет model-block/BBS-форм-каста, но depth-стейт стоит сверить). Деплой фикса в PrismLauncher-инстанс = заменить standalone irl-core-1.1.jar (+ пересобрать аддон, т.к. он JiJ-бандлит core).

Связано: [[addon-shadows]] (оркестрация бейка, MAJOR-A), [[reference-edit-routing-by-area]] (тени правятся в core).
