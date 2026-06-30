---
name: plan-shadow-1211-fwdport-c10
description: "📋 ПЛАН: ✅ Сессия D (форвард-порт shadow-perf на port/1.21.11) СДЕЛАНА 2026-06-17 — 3 коммита: T2.5+T1.1=e56e42c, T1.2+T2.4=d0dc60c, T1.3=3a6d470. ✅ ЭНТИТИ+CUTOUT ТЕНИ ДОДЕЛАНЫ 2026-06-17 (e035600): box-окклюдер энтити + textured cutout-блоки; build зелёный, ревью=SHIP; in-world больше не гейт. ⏳ ОТКРЫТО: Сессия E (C10 per-face блок-куллинг world-блоков, спайк ~320ms) — НЕ начата (единственная реальная незавершёнка по тек. теням). НЕ cherry-pick (порт ShadowRenderer = raw-GL переписка). Вердикты = ревью 72 агента в [[plan-shadow-bake-perf]] секция ФОРВАРД-ПОРТ"
metadata: 
  node_type: memory
  type: project
  originSessionId: e65468af-2ba5-4363-8716-ed6d6f29bf55
---

# Дуга: форвард-порт shadow-perf на 1.21.11 (Сессия D) + C10 per-face block-cull (Сессия E)

Цель: довести оптимизации бейкинга теней до `port/1.21.11`, затем закрыть главный реальный спайк (~320ms). Все вердикты «что портируется / что нет» — из состязательного ревью 2026-06-17 (72 агента, javap по yarn 1.21.11+build.6 + декомп + эмпирический cherry-pick); детали и таблица API в [[plan-shadow-bake-perf]] (секция «⚠️ ФОРВАРД-ПОРТ НА port/1.21.11»). Аудит находок = [[project-shadow-bake-perf-audit]]. Контекст порта = [[project-port-1211]].

## 🔖 ВОЗВРАТ-ПОИНТ (зафиксировано 2026-06-17, по просьбе пользователя «вернёмся»)
**ГДЕ**: ветка `port/1.21.11`, HEAD = `e035600` («add entity + cutout block shadows on 1.21.11»), рабочее дерево ЧИСТОЕ, build зелёный (compile+jar+remap), состязательно проверено (вердикт SHIP). Git remote нет (локально).

**ЧТО СДЕЛАНО в этой сессии (последние Stage-1/2 заглушки теней)**:
- **Тени от энтити** = box-окклюдер (пользователь выбрал, НЕ силуэт по модели). `ShadowRenderer.renderCaster` рисует interp-bbox энтити через pos-only depth-программу, копит per-pass + флашит в `endPass` (`flushCasterBoxes`, общий VBO `casterScratchVbo`). Только энтити (model-block/replay на порте не собираются).
- **Тени от cutout-блоков** = текстурно-точные. `BlockShadowCollector` классифицирует через `net.minecraft.client.render.BlockRenderLayers.getBlockLayer(BlockState)` → enum `BlockRenderLayer{SOLID,CUTOUT,TRANSLUCENT,TRIPWIRE}` (API не удалён, а переименован+ретипнут из `RenderLayers`!); `CUTOUT` → запись `shape==null,cutout=true`; SOLID/TRANSLUCENT → старый shape-путь (как 1.20.4, стекло = сплошная тень). `ShadowRenderer.renderBlocksDepthCutout` печёт из `BakedModel` quad'ов (`MinecraftClient.getBlockRenderManager().getModel(state)`→`BlockStateModel.getParts(Random)`→`BlockModelPart.getQuads(@Nullable Direction)`→`BakedQuad` record: `getPosition(i)`=Vector3fc, `getTexcoords(i)`=packed long → `net.minecraft.client.util.math.Vector2f.getX/getY` (НЕ org.joml!)) через НОВУЮ textured depth-программу (атлас `((GlTexture)tex.getGlTexture()).getGlId()` на unit0, `discard` при alpha<0.5). Отдельный `cutoutVao`/`cutoutProgram`/per-light `cutoutVboId`. `retainBlockVbos` чистит оба кэша; гард ребилда теперь по инстансу списка (без `vbo==0`) — иначе cutout-only лампа в листве ре-тесселится каждый кадр.

Форвард-порт валидирован (build + ревью SHIP); in-world больше не гейт. Durable-кавеат для Сессии E: возможен десинк GL-кэша MC при бинде атласа в баке (opaque-дисциплина snapshot/restore доказана, но текстуры — новая территория); профайлер `-Dirlite.profileShadows=true`. Опц. follow-up если коробка моба грубовата: силуэт по модели (хрупко, внутренности рендера 1.21.9 — отклонено в этой сессии).

## ✅ СЕССИЯ D ЗАВЕРШЕНА (2026-06-17) — форвард-порт сделан на `port/1.21.11`
3 коммита (батчи как планировалось, build зелёный после каждого):
- **e56e42c** = T2.5 (sphere-exact `invalidateAt`: на `CacheEntry` снапнутые `cx/cy/cz/cr`, distance-reject зеркалит keep-тест коллектора) + T1.1 (shortlist `shortIdx`/`shortFaceMask`/`shortCount`/`dynFaceMaskScratch` в `scanInRange`; `renderInRangeCone/Face` итерируют шортлист; `faceHasDynamic` УДАЛЁН).
- **d0dc60c** = T1.2 (`PointShadowArray.copyStaticFaceToLive(slot,face)` + `lastFaceDynamic` map; point-overlay копирует только грани `dynNow|lastFaceDynamic`, full-copy если `bakedStatic` или нет истории; `lastFaceDynamic.remove` в no-static ветке + lifecycle purge/reset/retain) + T2.4 (`LightConfig.shadowBakeBudget`=4, `allowStaticBake(mustBake)`, 4 сайта загейчены; overlay copy-choice на `bakedStatic` НЕ `staticStale`).
- **3a6d470** = T1.3 (`pickStableUp`→статик `UP_Y`/`UP_Z`; spot/point proj кэш по (fov,far)/far в отдельные `spotProjCache`/`pointProjCache`, `currentProj.set(cache)` каждый pass — НЕ хранить кэш в общем currentProj).

⚠️ РЕАЛЬНОСТЬ ПОРТА (важно для Сессии E): на порте `occ[]` держит ТОЛЬКО энтити (`collect()` не кладёт model-block) ⇒ `staticInRangeScratch` всегда 0, `staticOccSigScratch`=0; статик-слой = ТОЛЬКО `renderBlocksDepth` (world-блоки в VBO). Значит T1.1 = снижение CPU-проходов + ФУНДАМЕНТ C10; T1.2 = реальная экономия per-frame VRAM-копий когда моб рядом с лампой. Поле `staticFaceMaskScratch` из T1.1 main НАМЕРЕННО пропущено (нет потребителя в этом скоупе). 🟢 ОБНОВЛЕНО 2026-06-17 (`e035600`): `renderCaster` БОЛЬШЕ НЕ заглушка — рисует interp-bbox энтити (box-окклюдер) через depth-программу, копит per-pass + флашит в `endPass`; T1.2 теперь даёт ВИДИМЫЙ выигрыш (динамика реально рисуется в overlay). Плюс добавлены cutout-тени блоков (textured BakedModel-quads + atlas + alpha-discard, отдельная программа). C10 (Сессия E, per-face блок-куллинг VBO) НЕ затронут — он про world-блоки, остаётся не начат.

❗ ОТКРЫТО: только Сессия E (C10 per-face блок-куллинг). Профайлер до/после обязателен (`-Dirlite.profileShadows=true`).

## ⚠️ ГЛАВНОЕ ПЕРЕД СТАРТОМ
- **НЕ cherry-pick.** `port/1.21.11` ShadowRenderer — НЕ структурный порт 1.20.4, а **raw-GL переписка** (свой `#version 150` depth-шейдер + VAO + `glDrawArrays`, ноль `net.minecraft` render-типов). Переносить **hunk-by-hunk / переписывая под raw-GL**, не `git cherry-pick`.
- **T2.2 и T2.3 — ПРОПУСТИТЬ полностью** (renderCaster заглушка → батчить нечего; cutout-пути нет → кэшировать нечего; нужные MC render-API удалены ~1.21.5; выигрыш 0). Не возвращаться без отдельной Stage-3 entity-occluder работы.
- Ветка: работать прямо на `port/1.21.11` (без per-session ветки — [[feedback-no-per-session-branch]]).
- Verify: `./gradlew runClient` на 1.21.11 + Iris-пак (свет виден ТОЛЬКО на пропатченном паке — патчер на порте есть, см. [[project-port-1211]]); профайлер `-Dirlite.profileShadows=true` (через `JAVA_TOOL_OPTIONS`, подхватывается форкнутым клиентом). Снимать до/после.
- Перед правкой: `Read` целиком `port/1.21.11` ShadowBaker.java + ShadowRenderer.java + BlockShadowCache.java + PointShadowArray.java + LightConfig.java — номера строк/структура иные, чем на main.

## Сессия D — форвард-порт (порядок ВАЖЕН)
Все пять — на `port/1.21.11`, каждый: build зелёный → in-world verify → коммит. 57d1a4c на main бандлит T2.3+T2.4+T2.5, поэтому брать покусочно.
1. **T2.5** sphere-exact `invalidateAt` — ЧИЩЕ ВСЕГО, делать первым как разогрев. `BlockShadowCache.java` на порте байт-идентичен pre-T2.5 main; `getOrCompute` уже считает cx/cy/cz/cr. Патч: float-поля центр+радиус на `CacheEntry`, проставить в getOrCompute, distance-reject в invalidateAt. Zero API exposure. low.
2. **T1.1** occluder shortlist (`shortIdx/shortFaceMask/shortCount/dynFaceMaskScratch`) — pure-CPU рефактор `scanInRange`/`renderInRangeCone`/`renderInRangeFace`. ⚠️ на порте `renderInRange*` иные сигнатуры (inline-walk по occCount, `(lx,ly,lz,reachBase,...,filter,tickDelta)`), нет shortlist — реконсилить регион. Фундамент для C10. low.
3. **T1.2** per-face `copyStaticFaceToLive`+`lastFaceDynamic`+`dynFaceMaskScratch` — портируется; на порте сейчас безусловный all-6 `copyStaticToLive` + `faceHasDynamic`-гейт. `copyStaticFaceToLive` = `GL43.glCopyImageSubData` (sub-slice). medium.
4. **T2.4** — budget-половина (`LightConfig.shadowBakeBudget` + `staticBakeBudget`/`allowStaticBake`) pure-CPU, portable. ⚠️ point-overlay copy-choice (`bakedStatic` vs copyByMask) ЗАВЯЗАН на T1.2 (нужны dynFaceMaskScratch/lastFaceDynamic) — делать ПОСЛЕ T1.2.
5. **T1.3** — **НЕ moot** (опровергнуто): `pickStableUp` аллоцирует `new Vector3f` per-call (`SR:534-537`), spot/point-проекция перестраивается КАЖДЫЙ pass без fov/far-кэша (`SR:115,149`). Только MatrixStack-треть moot (на порте её и нет). low, но реальный.

Может влезть в одну сессию; если разрастётся — резать по зелёному+verified коммиту (T2.5+T1.1 | T1.2+T2.4 | T1.3), НИКОГДА посреди T1.1↔T1.2.

## Сессия E — C10 per-face block-cull (highest-value, НОВАЯ работа)
Проблема (подтверждена профайлером вживую = спайк ~305-327ms): `renderBlocksDepth` рисует ВЕСЬ per-light VBO раз на каждую из 6 граней point-света БЕЗ per-face frustum-куллинга блоков (куллятся только entity-кастеры). ~6× лишних вершин+дро.
Идея: T1.1-style per-face подвыборка блоков — партиционировать per-light VBO по граням куба (или хранить per-face index-диапазоны / 6 под-VBO), рисовать на грань только касающиеся её блоки (тест как `sphereTouchesFace`/AABB-vs-face-frustum). Опирается на инфраструктуру T1.1 (перенесённую в Сессии D).
Заметки: спайк есть И на main (1.20.4) — можно бэкпортнуть после. Профайлер до/после обязателен. Открытый вопрос: `onShadersDisabled`-хука нет ни на одной ветке — eviction VBO только через `retainBlockVbos(liveIds)`; проверить, что зовётся при тогле шейдеров (иначе VBO-лик).

## Состязательно-проверенные предостережения (не наступить)
- НЕ тащить удалённые API: `RenderSystem.setProjectionMatrix(Matrix4f,VertexSorter)`, `getModelViewStack()→MatrixStack`, `applyModelViewMatrix()`, `VertexBuffer`, `RenderLayer.getCutout/startDrawing`, `RenderSystem.getShader()`, `GameRenderer.getPositionProgram`. На 1.21.11 стек = RenderPipeline+RenderPass через GpuDevice; `ShaderProgram`-класса нет.
- Порт уже использует raw-GL (GL11/15/20/30) и СВОЙ depth-шейдер — все новые правки рендера держать в этом идиоме.
