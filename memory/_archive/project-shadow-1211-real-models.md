---
name: project-shadow-1211-real-models
description: "✅ КОД ГОТОВ + ОТРЕВЬЮЕН + BUILD/REMAP ЗЕЛЁНЫЙ (2026-06-18; in-world визуал за юзером, НЕ закоммичено): port/1.21.11 тени — AABB-бокс на энтити + hand-rolled cutout заменены на РЕАЛЬНУЮ модель/MC-тесселяцию (паритет main/1.21.1). 1.21.11 энтити-рендер = deferred OrderedRenderCommandQueue (interface) → НОВЫЙ OccluderGeometryCapturer (capturing-queue: submitModel→setAngles+render; submitModelPart→render; submitItem→capture.quad; submitBlock/BlockStateModel→BlockModelRenderer.render) + position/UV-capturing VertexConsumer → реал-силуэт через raw-GL depth. Cutout→brm.renderBlock(cull). ShadowRenderer.renderCaster кэширует по entity.getId() (clear в beginBake), flushCasters рисует. Адверс. ревью (wf_f2d263f7, 21 агент, 5 findings) пофикшено. Решения юзера: энтити=геометрия-онли; cutout=реал-MC-тесселяция."
metadata: 
  node_type: memory
  type: project
  originSessionId: 271a88e8-273f-42e2-a920-3a209e71d7ad
---

# port/1.21.11 тени: реальные модели вместо box/hand-rolled (паритет)

Задача (юзер, 2026-06-18): «догнать реализацию теней как в других версиях». На `port/1.21.11` энтити = AABB-бокс (`ShadowRenderer.renderCaster`→`emitBox`, «категорически неправильно»), cutout-блоки = hand-rolled BakedQuad+атлас («cut кривые»). На `main`/`port/1.21.1` = реальная модель через `dispatcher.render(...,immediate,FULL_LIGHT)` (`RedactorEntityCasterSource`) + cutout через `BlockRenderManager.renderBlock`→immediate.

## Почему сломалось на 1.21.11 (grounded по декомпилу C:\Windows\Temp\mc12111-src)
- Immediate-путь рендера убран (1.21.5 RenderPipeline + 1.21.9 EntityRenderState). Энтити-рендер теперь **сабмитит deferred-команды** в `OrderedRenderCommandQueue`, не пишет вершины. Прошлая сессия не достала геометрию → fallback на box.

## Несущие API-факты 1.21.11 (проверено)
- `EntityRenderManager` (rename of dispatcher; `mc.getEntityRenderDispatcher()` kept): `getAndUpdateRenderState(entity,tickProgress)→EntityRenderState`; `render(S state, CameraRenderState, double ox,oy,oz, MatrixStack, OrderedRenderCommandQueue)`.
- `OrderedRenderCommandQueue extends RenderCommandQueue extends FabricRenderCommandQueue` — **interface** → можно реализовать свой capturing-queue. Executor (`ModelCommandRenderer`): `model.setAngles(state); model.render(matrices, vc, light, overlay, color)`.
- `RenderCommandQueue` методы (зеркаль `OrderedRenderCommandQueueImpl` сигнатуры дословно): submitModel(`<S>`,Model<? super S>,S,MatrixStack,RenderLayer,int,int,int,Sprite,int,CrumblingOverlayCommand), submitModelPart, submitItem(...,List<BakedQuad>,...), submitBlock/MovingBlock/BlockStateModel/Custom×2/ShadowPieces/Label/Text/Fire/Leash, getBatchingQueue(int)→this. Fabric 2 метода (submitBlock 7-арг, submitBlockStateModel 11-арг) = **default** (Impl их не реализует) → можно НЕ реализовывать.
- `Model.render(MatrixStack,VertexConsumer,int,int,int)` (final, рендерит root part); `ModelPart.render(...)`; `ModelPart.Cuboid.renderCuboid` пишет в **bulk** `VertexConsumer.vertex(x,y,z,color,u,v,overlay,light,nx,ny,nz)` с **уже-трансформированными** позициями (matrix4f.transformPosition). Default `VertexConsumer.quad(MatrixStack.Entry,BakedQuad,...)` тоже декомпозит в bulk vertex (трансформит + декодит UV через `Vector2f.getX/getY`). ⇒ capture-consumer реализует только примитивы (`vertex(x,y,z)`+`texture(u,v)`+no-op остальное), один на энтити И блоки.
- `BlockRenderManager.renderBlock(state,pos,world,matrices,vc,boolean cull,List<BlockModelPart> parts)` — реальная тесселяция; `BlockModelRenderer.render` translate'ит только `getModelOffset(pos)`, НЕ позицию блока ⇒ caller pre-translate matrix на абс. world pos (как hand-rolled +ox/oy/oz).
- Безопасность: `getAndUpdateRenderState` гардит `if(dispatcher.camera!=null)` (нет NPE); `GameRendererLightMixin` зовёт `configure(camera,cameraEntity)` РОВНО перед `ShadowBaker.bake()` ⇒ camera выставлена. Capture = чистый CPU (consumer не делает GL), GL-draw отдельно во flush. `Vector2f.getX/getY` decode корректен (не баг).

## План реализации (геометрия-онли энтити; cutout=реал-тесселяция)
1. НОВЫЙ `OccluderGeometryCapturer` (light/shadow): Capture(VertexConsumer, POS+UV, quad→2 tri по группам 4 верт), CaptureQueue(OrderedRenderCommandQueue: submitModel→setAngles+render, submitModelPart→render, submitItem→capture.quad, прочее no-op). `captureEntityTris(entity,tickDelta)→float[] xyz` (getAndUpdateRenderState + CameraRenderState из mgr.camera + render(state,cam,wx,wy,wz,new MatrixStack(),queue)). `captureCutoutBlockTris(world,brm,pos,state,random)→float[] xyz+uv` (random.setSeed(getRenderingSeed) + getParts + renderBlock cull=true + matrix translate abs pos).
2. `ShadowRenderer`: `renderCaster` энтити → cache `Int2Object<float[]>` по entity.getId() (clear в beginBake), append в per-pass `FloatArrayList casterAccum`; flush рисует position-программой (stride 12). Удалить emitBox/box-путь. Cutout `buildCutoutVbo` → per-block `captureCutoutBlockTris` вместо hand-rolled getQuads (та же cutout-программа атлас+discard, stride 20).
3. beginBake: clear entityGeomCache + casterAccum.

Триангуляция: только полные группы 4 верт (модели/блоки/items = QUADS). submitCustom no-op (избежать смешения 3/4-верт). Per-entity try/catch (manager.render кидает CrashException) — degrade-to-absent.

Контекст: [[plan-shadow-1211-fwdport-c10]], [[project-port-1211]] (API-карта 1.21.11), [[reference-edit-routing-by-area]] (port=raw-GL, отдельный мир от lockstep). Reference main-каста: `RedactorEntityCasterSource`.

## ✅ РЕАЛИЗОВАНО + АДВЕРС.РЕВЬЮ (2026-06-18, build+remap зелёный)
Файлы: НОВЫЙ `OccluderGeometryCapturer.java` + правки `ShadowRenderer.java` (renderCaster/flushCasters/beginBake/endPass/buildCutoutVbo, удалён emitBox-для-энтити + emitCutoutQuads/appendCutoutVert/DIRECTIONS) + `ShadowBaker.onShadersDisabled`→`ShadowRenderer.releaseScratch()`. **Подтверждено grounded: `LivingEntityRenderer.render`→`submitModel` (захват работает для ВСЕХ обычных мобов/игроков); тело + фичи (броня/предметы) идут через submitModel/submitModelPart/submitItem.** submitCustom-only рендереры (эндердракон, картины, XP-орбы) деградируют молча (нет тени) — приемлемо.

**Адверс.ревью wf_f2d263f7 (21 агент, 16 raw→5 confirmed, все ПОФИКШЕНЫ):**
1. (MAJOR) блок-фичи мобов (снеговик-тыква, эндермен-блок, жел.голем-цветок, мухомор-грибы, медный-голем-голова) терялись — `submitBlock`/`submitBlockStateModel` были no-op → реализованы через `BlockModelRenderer.render(matrices.peek(), capture, model, r,g,b, light, overlay)` (submitBlock резолвит модель через `brm.getModel(state)`, гард INVISIBLE). submitMovingBlock остаётся no-op (падающие блоки = не LivingEntity, не собираются).
2. (MAJOR-перф) flushCasters делал memAllocFloat+memFree КАЖДЫЙ динам-пасс → персистентный grow-only `casterScratch` FloatBuffer (clear, не free; освобождается в releaseScratch).
3. (MINOR) мёртвый `broken`-латч → заменён на `IntOpenHashSet failedEntities` (скип энтити, чей рендер кинул — иначе MC пере-строит CrashReport каждый кадр).
4. (MINOR) пост-стоянно-падающий модд-энтити → тот же failedEntities скип.
5. (MINOR) casterScratchVbo не освобождался на shaders-off → `releaseScratch()` (буфер+VBO) из onShadersDisabled.

Dismissed-11 = подтверждённые «не-баги» (координаты world-space ✅, триангуляция групп-по-4 ✅, отсутствие реентранси ✅, precision = pre-existing TODO). Программы/VAO не освобождаются на shaders-off — pre-existing, benign (GLFW-контекст живёт весь процесс), оставлено.

✅ ЗАКОММИЧЕНО: `port/1.21.11`@`e925a07` (3 файла; remote нет, не пушено).
⏳ Осталось: **in-world визуал** (runClient + шейдерпак + поставить свет + моб рядом → проверить силуэт) — глазами юзера ещё не проверено.
