---
name: plan-shadow-bake-perf
description: "📋 ПЛАН оптимизаций бейкинга теней. ✅ Все T реализованы+проверены на main(1.20.4): A=T1.1/1.2/1.3 da9c980, B=T2.5/2.4/2.3 57d1a4c, C=T2.2 2cb1292. ❌ T2.1 УДАЛЁН. ⚠️ ФОРВАРД-ПОРТ НА 1.21.11 ≠ cherry-pick (ревью 2026-06-17, 72 агента): T2.2 и T2.3 ПРОПУСТИТЬ (порт ShadowRenderer = raw-GL переписка; renderCaster заглушка, cutout-пути нет, render-API удалены 1.21.5). Портируются hunk-by-hunk: T2.5(чище всего)/T1.1/T1.2/T1.3/T2.4-budget-half. Главный реальный выигрыш = НОВАЯ per-face block-cull (спайк ~320ms). См. секцию ФОРВАРД-ПОРТ. Находки=[[project-shadow-bake-perf-audit]]"
metadata: 
  node_type: memory
  type: project
  originSessionId: 1a39cd4f-717e-4606-91ea-702c4aef9934
---

# План оптимизации бейкинга теней (Tier 1 + Tier 2)

Источник находок и вердиктов: [[project-shadow-bake-perf-audit]]. Все строки сверены с кодом 2026-06-16 на ветке `claude/elated-wing-8537ad` (worktree), но движок света общий с port/1.21.11 и main — сверить актуальные номера строк перед правкой (`Read` целиком). Пакет: `src/client/java/org/qualet/irlredactor/light/shadow/`.

## ✅ СТАТУС (2026-06-17)
**Сессия A ЗАВЕРШЕНА.** `T1.1 + T1.2 + T1.3` написаны, собраны (`./gradlew build` зелёный), проверены in-world (1.20.4 + Iris: точечная тень трекает ходячего моба без залипших граней, cutout цел, без исключений) и закоммичены: **`da9c980`** на **`main`** (1.20.4). ⚠️ Форвард-порт на `port/1.21.11` НЕ сделан — движок там структурно тот же, но это отдельный коммит/cherry-pick.

**✅ Сессия B ЗАВЕРШЕНА (2026-06-17): in-world проверена пользователем (всё ок) + закоммичена `57d1a4c` на `main` (1.20.4), claude-ветки удалены.** Реализованы все три: **T2.5** (sphere-exact инвалидация), **T2.4** (бюджет бейков), **T2.3** (cutout-VBO кэш). Затронуты 4 файла: `LightConfig.java`, `BlockShadowCache.java`, `ShadowBaker.java`, `ShadowRenderer.java`. ⚠️ Форвард-порт на `port/1.21.11` НЕ сделан (как и для A — отдельный cherry-pick). Ключевые решения:
- **T2.5** (`BlockShadowCache`): на `CacheEntry` добавлены снапнутые `cx/cy/cz/cr` (ставятся в `getOrCompute` при сборе); `invalidateAt` перед пометкой dirty отбраковывает правку тестом `distSq(blockCenter=pos+0.5, center) > cr²` — ТОЧНО зеркалит keep-тест коллектора (центр блока vs снапнутый радиус), секционный индекс остался грубым пре-фильтром. Мутатор-миксин игнорит return, его doc УЖЕ описывал sphere-точность.
- **T2.4** (`ShadowBaker` + `LightConfig.shadowBakeBudget`, default 4, `<=0`=безлимит=`Integer.MAX_VALUE`): per-frame `staticBakeBudget`, хелпер `allowStaticBake(mustBake)` — деком на 1 если бейк идёт, mustBake-бейки идут всегда (могут увести бюджет в минус). 4 сайта полного СТАТИК-бейка загейчены (2 spot, 2 point). Отложенный = НЕ рисуем + НЕ обновляем dirty-стейт ⇒ staleness сама ретраит. **mustBake (нельзя откладывать)**: pure-static = first/tile-moved; overlay = `!dyn ИЛИ first/tile-moved` (кадр УХОДА субъекта обязан печь — иначе `rememberLive` пометит чистым и pure-static больше не заметит). copy/overlay-динамика НЕ бюджетируются. **ВАЖНО**: в point overlay решение copyAll6-vs-copyByMask переключено со `staticStale` на новый `bakedStatic` (реально ли пёкся в этом кадре) — при отложенном бейке статик-куб не менялся ⇒ copyByMask верен.
- **T2.3** (`ShadowRenderer`): per-light кэш `CutoutVbos{cutout, cutoutMipped}` (по слою CUTOUT/CUTOUT_MIPPED), ключ = id + инстанс block-list. `renderBlocksDepthCutout(id, blocks)`: на miss тесселит `brm.renderBlock` в `BufferBuilder.begin(layer.getDrawMode(), layer.getVertexFormat())` → `endNullable()` (null если слой пуст/всё скуллено) → VBO STATIC; на hit/после билда `drawCutoutVbo`: `layer.startDrawing()` (cutout-шейдер+атлас на Sampler0+blend off+cull on) → `vb.draw(currentView, currentProj, RenderSystem.getShader())` → `endDrawing()` в finally. Матрицы передаются ЯВНО ⇒ старый баг «второй cutout-слой гасит тени» (порча RenderSystem-modelview мобом-кастером + авто-flush слоёв immediate) устранён конструктивно. Эвикт обоих VBO-кэшей в `retainBlockVbos` (убран ранний `return`, чтобы cutout-only лампы тоже чистились). API сверены `javap` по 1.20.4: startDrawing/endDrawing public, endNullable есть, VertexBuffer.draw(Matrix4f,Matrix4f,ShaderProgram). ⚠️ известный edge (вне скоупа): захваченные UV атласа протухнут при resource-reload с репаком атласа без правки блока.

Сессия B verify пройден пользователем глазами под Iris (cutout-тени ажурные и не пропадают рядом с мобом; движущийся моб трекается; без спайка). Рантайм-смоук тоже чист (клиент грузится, iris-пайплайн собран, выход 0, без исключений из теневого кода).

**✅ Сессия C ЗАВЕРШЕНА (2026-06-17): T2.2 (батчинг draw кастеров) — in-world проверена + закоммичена `2cb1292` на `main` (1.20.4).** Затронуты 2 файла: `ShadowRenderer.java` (renderCaster заменён на `beginCasterBatch`/`bufferCaster`/`endCasterBatch`+`flushCasterBatch` — один immediate.draw на проход вместо одного на кастера, ≤1 флаш/грань/тайл) + `ShadowBaker.java` (renderInRangeCone/Face оборачивают цикл в begin/buffer*/end). Гарды: (1) частичную вершину упавшего кастера флашим в одиночку в bufferCaster (catch) — не сливается в garbage-quad со следующим; (2) перед единым флашем `flushCasterBatch` пере-выставляет currentProj+currentView на RenderSystem (BufferRenderer трансформирует по живым матрицам, которые моб-кастер мог испортить; без лишнего push — push/pop у applyMatrices/endPass). Профайлер (point + ~24-32 ходячих моба, 1 лампа): overlay-путь **avg 0.47-0.58 ms** (~100/сек), без `[irlite] failed`/стектрейсов/краша, exit 0. ⚠️ ЗАМЕЧЕНО (НЕ T2.2, вне скоупа): кадры со `static bakes: 1-2 point(x6)` дают max ~305-327 ms спайк — статик-набор кастеров тут пуст (окклюдеры=энтити); ревью 1.21.11 (ниже) подтвердил: спайк = renderBlocksDepth рисует ВЕСЬ VBO раз на грань (6×) БЕЗ per-face блок-куллинга (C10, Tier-3 кандидат). ⚠️ Форвард-порт на `port/1.21.11` НЕ сделан — и НЕ через cherry-pick, см. секцию ФОРВАРД-ПОРТ ниже.

## ⚠️ ФОРВАРД-ПОРТ НА port/1.21.11 — РЕАЛЬНОСТЬ (состязательный ревью 2026-06-17, 72 агента; javap по yarn 1.21.11+build.6 + декомп + эмпирический git cherry-pick)
**T2.2 и T2.3 НЕ форвард-портятся — ни cherry-pick, ни по смыслу. ПРОПУСТИТЬ оба.** Причина: `port/1.21.11` ShadowRenderer — НЕ структурный порт 1.20.4, а **raw-GL переписка** (свой `#version 150` depth-программа + VAO + `glDrawArrays` по `shape`-AABB; импорты только lwjgl/joml/fastutil, НИ одного `net.minecraft` render-типа).
- **T2.2 (батчинг кастеров) = no-op + не применяется.** `renderCaster` на порте — ЗАГЛУШКА (`SR:180-187`, TODO Stage-3 entity-box occluder; entity-рендер ушёл в deferred `OrderedRenderCommandQueue`, `EntityRenderDispatcher`→`EntityRenderManager` без immediate-overload). Батчить нечего (0 immediate.draw). `git cherry-pick 2cb1292` → **2/2 hunk FAILED** (хард-конфликт в ShadowBaker И ShadowRenderer; baker `renderInRange*` иные сигнатуры — inline-walk по occCount, без shortlist). Даже вручную не скомпилится: `RenderSystem.setProjectionMatrix(Matrix4f,VertexSorter)` / `getModelViewStack()`→`org.joml.Matrix4fStack` / `applyModelViewMatrix()` — УДАЛЕНЫ ребилдом пайплайна ~1.21.5. Выигрыш на 1.21.11 сегодня = 0.
- **T2.3 (cutout VBO) = нет цели + не скомпилится.** На порте НЕТ cutout-пути: `BlockShadowEntry.cutout` ВСЕГДА false (`BlockShadowCollector:116`), поле write-only, классификатор брошен под TODO (block→layer API переехал). `net.minecraft.client.gl.VertexBuffer` УДАЛЁН (→GpuBuffer/RenderPipeline); `RenderLayer.getCutout/getCutoutMipped` удалены; `RenderLayers.getBlockLayer` переехал+ретипнут (`BlockRenderLayer` enum); `RenderSystem.getShader()` удалён (класса `ShaderProgram` на 1.21.11 НЕТ вообще — стек RenderPipeline+RenderPass через GpuDevice). Плюс: per-light VBO-кэш на порте УЖЕ есть (vboId/vboList, ребилд только при смене инстанса списка) ⇒ кэш-выигрыш T2.3 для рисуемой геометрии уже достигнут.

**Что РЕАЛЬНО портируется (hunk-by-hunk; 57d1a4c бандлит T2.3+T2.4+T2.5 — применять покусочно, НЕ commit cherry-pick):**
- **T2.5** sphere-exact `invalidateAt` — ЧИЩЕ ВСЕГО, standalone-патч (`BlockShadowCache` на порте байт-идентичен pre-T2.5 main; `getOrCompute` уже считает cx/cy/cz/cr). low.
- **T1.1** occluder shortlist — pure-CPU, портируется. low.
- **T1.2** per-face `copyStaticFaceToLive`+`lastFaceDynamic` — портируется (copy = `glCopyImageSubData`). medium.
- **T1.3** — **НЕ moot** (опровергнуто прежнее «moot»): `pickStableUp` аллоцирует `new Vector3f` per-call (`SR:534-537`), spot/point-проекция перестраивается КАЖДЫЙ pass без кэша (`SR:115,149`); moot только MatrixStack-треть. low.
- **T2.4** — budget-половина pure-CPU portable; НО point-overlay copy-choice (`bakedStatic`) ЗАВЯЗАН на T1.2 (нужны dynFaceMaskScratch/lastFaceDynamic) ⇒ не portable в изоляции.
- **НОВАЯ работа (ни в одном коммите, highest-value, C10)**: `renderBlocksDepth` рисует ВЕСЬ per-light VBO раз на грань для point (6×) БЕЗ per-face frustum-куллинга блоков = профайлерный спайк ~305-327ms. Нужна T1.1-style per-face подвыборка/партиция VBO.

**✅ ФОРВАРД-ПОРТ СДЕЛАН (Сессия D, 2026-06-17, на `port/1.21.11`):** T2.5+T1.1=`e56e42c`, T1.2+T2.4=`d0dc60c`, T1.3=`3a6d470`; build зелёный; T2.2/T2.3 пропущены как и планировалось; in-world visual verify пользователем ещё НЕ сделан. Детали — [[plan-shadow-1211-fwdport-c10]] секция «СЕССИЯ D ЗАВЕРШЕНА».

**Реальный порядок форвард-порта (выполнен):** сверить регион `renderInRange*`/bake-block раз → T2.5 (standalone) + T2.4 budget-half + T1.1 → T1.2 → T2.4 copy-half. T2.3 cutout и T2.2 — ПРОПУЩЕНЫ. Открытый вопрос: `onShadersDisabled`-хука НЕТ ни на одной ветке — eviction только через `retainBlockVbos(liveIds)`; проверить, что он зовётся при тогле шейдеров (иначе VBO-лик у будущего cutout-кэша).

**❌ T2.1 (motion-gate по интерполированной позе) УДАЛЁН** (2026-06-17, решение пользователя): pose-сигнатура завязывается на внутренности ванильного `LivingEntity` (interp x/y/z, `limbAnimator`, `getPose()`, vehicle, ItemStack…) → несовместимо с BBS. **ПРИНЦИП: правки движка теней обязаны оставаться полностью BBS-совместимыми.** Поэтому Сессия B теперь = T2.4/T2.3 (+опц T2.5), БЕЗ T2.1.

## Как пользоваться (новая сессия)
1. Прочитать [[project-shadow-bake-perf-audit]] (что/почему) + этот файл (как).
2. Перечитать ShadowBaker.java + ShadowRenderer.java целиком — это hot-path, номера строк могли сдвинуться.
3. Делать в порядке из секции «Порядок». T1.1 первым (его face-маску переиспользуют T1.2 и частично T2.1).
4. Сборка: `./gradlew build`. Проверка вживую: `./gradlew runClient`, активировать Iris-пак (Complementary), поставить лампу + моба, смотреть тень. Профайлер: JVM-флаг `-Dirlite.profileShadows=true` → лог раз в секунду (avg/max bake ms, static bakes, overlays) — снимать ДО и ПОСЛЕ каждого пункта.

## ⚠️ ИНВАРИАНТЫ (нарушение = визуальный баг теней). Проверять для КАЖДОЙ правки:
- **scan-set == render-set**: множество кастеров, отрисованных в bake, должно ТОЧНО равняться тому, что посчитал `scanInRange` и что гейтило бейк — иначе SSBO-тайл указывает на карту, испечённую из другого набора.
- **sticky-tile ownership**: свет держит свой тайл/слот между кадрами (даже куллнутыми), чтобы переиспользовать карту. Кража тайла → `purgeDirtyState` форсит first-bake.
- **matrix-corruption guard**: кастер (ваниль-моб через EntityRenderer) может оставить RenderSystem-матрицы испорченными; блоки рисуются напрямую через `currentView`/`currentProj`. Любой батчинг/перенос flush должен это учитывать (см. cutout-путь как образец).
- **two-layer copy**: чистая статик-база восстанавливается ДО отрисовки динамики поверх.
- **cone/face cull консервативность**: НИКОГДА не куллить свет/грань, которая может затенить ОСВЕЩЁННЫЙ фрагмент. Over-draw допустим (frustum отбракует), under-draw — нет.
- **cache identity-stability**: `BlockShadowCache.getOrCompute` возвращает ТОТ ЖЕ инстанс List ⇔ контент не менялся; VBO-кэш и dirty-тест на этом держатся.
- **resource lifetime**: всё освобождается на смене quality (`resetTileState`+delete) и при выключении шейдеров (`onShadersDisabled`).

---

# TIER 1 (безопасно, делать первым)

## T1.1 — shortlist + face-маска в scanInRange (убрать ~13-19 проходов по окклюдерам)
Файл: `ShadowBaker.java`. Сейчас: `scanInRange` (~862-896) обходит occ[], потом `renderInRangeCone` (~947-969) и `renderInRangeFace` (~975-996) обходят ещё раз с тем же тестом, а для point ещё `faceHasDynamic` (~921-941) — отдельный проход на каждую из 6 граней.

Шаги:
1. Добавить статик-скретч (рядом с `dynamicInRangeScratch`/`staticOccSigScratch`): `int[] shortIdx` (размер MAX_OCCLUDERS), `int shortCount`, `int[] shortFaceMask` (6-бит на окклюдер, только для point), `int dynFaceMaskScratch`, `int staticFaceMaskScratch`.
2. В `scanInRange`: для каждого окклюдера, прошедшего range(+cone для spot) тест — записать индекс в `shortIdx[shortCount++]`. Для point (`cone==false`) посчитать 6-бит маску: `for face 0..5: if sphereTouchesFace(face, vx,vy,vz, orad[k]*SQRT2) set bit`. Скопить `dynFaceMaskScratch |= mask` для динамических, `staticFaceMaskScratch |= mask` для CASTER_MODEL_BLOCK.
3. `renderInRangeCone`/`renderInRangeFace`: итерировать `shortIdx[0..shortCount)`, фильтр по типу кастера (`casterMatches`) оставить, но reach/cone/sphereTouchesFace НЕ пересчитывать — для point проверять бит `shortFaceMask[k] & (1<<face)`.
4. Overlay-цикл point (~562-571): заменить вызов `faceHasDynamic(...)` на проверку `(dynFaceMaskScratch & (1<<face)) != 0`. **Удалить `faceHasDynamic` целиком.**

ГАРДЫ (из fix-верификатора, вердикт confirmed/low):
- Скретч переиспользуется: spot-цикл полностью предшествует point-циклу, и `scanInRange` зовётся per-light прямо перед его рендер-проходами → ни один проход не пересекает два `scanInRange`. НЕ кэшировать между светами.
- Маску грани строить ТОЛЬКО на point-скане (cone=false); cone-фильтр — только на spot-скане (общий скретч).
- Радиус для маски — строго `orad[k]*SQRT2` (как в `sphereTouchesFace`), не `orad[k]`.
- Динамику рисовать по ПЕР-окклюдерному биту (`shortFaceMask[k]`), не по агрегату — иначе окклюдер нарисуется в грань, которой не касается (агрегат = лишь over-draw, безопасно, но точнее по биту).
- Инвариант scan==render усиливается (один источник истины вместо трёх копий предиката).

## T1.2 — per-face copyStaticToLive (не блитить все 6 граней куба) — ДЕЛИТ маску с T1.1
Файлы: `PointShadowArray.java`, `ShadowBaker.java`. Сейчас `copyStaticToLive(slot)` (PointShadowArray:89-104) блитит depth=6 (все грани) каждый overlay-кадр (ShadowBaker:555), хотя динамика обычно на 1-2 гранях. До 96 МиБ/свет/кадр на ULTRA.

Шаги:
1. `PointShadowArray`: добавить `copyStaticFaceToLive(int slot, int face)` — `glCopyImageSubData(..., srcZ=slot*6+face, dstZ=slot*6+face, FACE_SIZE, FACE_SIZE, 1)`.
2. `ShadowBaker`: per-light `Long2IntOpenHashMap lastFaceDynamic` (6-бит маска граней, в которые в ПРОШЛЫЙ кадр рисовалась динамика).
3. В overlay-ветке point (после `staticStale`-блока, вместо безусловного `copyStaticToLive(myLayer)` на :555):
   - `int dynNow = dynFaceMaskScratch` (из T1.1).
   - `int copyMask = dynNow | lastFaceDynamic.get(id)` (absent → 0).
   - **ЕСЛИ статик-база только что переиспеклась (`staticStale==true`) ИЛИ это первый overlay/после кражи тайла → копировать ВСЕ 6 граней** (полный `copyStaticToLive`), иначе копировать только грани из `copyMask` по одной.
   - Динамику рисовать в грани `dynNow` (по биту, из T1.1).
   - `lastFaceDynamic.put(id, dynNow)`.
4. Чистка: `lastFaceDynamic` добавить в `purgeDirtyState`, `retainDirtyState`, `resetTileState` (lifecycle как у lastSig).

ГАРД (из fix-верификатора, confirmed): грань, бывшая динамической в ПРОШЛЫЙ кадр но не в этот, обязана восстановиться из статики (иначе на ней застрянет старая тень) — за это отвечает `| lastFaceDynamic`. При краже тайла новый владелец делает full static bake (`purgeDirtyState`→staticStale) → копия всех 6, безопасно.

## T1.3 — переиспользование матричных/векторных скретчей (ShadowRenderer)
Файл: `ShadowRenderer.java`. Все вердикты confirmed/none.
1. `pickStableUp` (671-674): завести `private static final Vector3f UP_Y=new Vector3f(0,1,0), UP_Z=new Vector3f(0,0,1)`; возвращать нужный (единственный читатель, `lookAt` не мутирует). Или передавать 3 компоненты прямо в `lookAt`.
2. `beginPointFace` (148): проекция 90°/aspect1/NEAR/far=radius ОДИНАКОВА для всех 6 граней. Считать один статик `Matrix4f pointProj`, перестраивать только при смене radius; не аллоцировать `new Matrix4f()` на грань. (Меняется только `currentView` per face.)
3. `beginSpot` (111): кэшировать последние `(outerDeg,range)→proj` в статик `Matrix4f`, перестраивать при изменении; писать в переиспользуемый объект.
4. `renderCaster` (183) + `renderBlocksDepthCutout` (464): один `private static final MatrixStack scratch`; в начале `renderCaster` сбрасывать в identity (loadIdentity на верхней Entry) вместо `new MatrixStack()`. Рендер-тред однопоточный → общий скретч безопасен.
ГАРД: `currentProj.set(proj)` в `applyMatrices` должен по-прежнему вызываться (проекция реально применяется), просто из переиспользуемого объекта.

---

# TIER 2 (выигрыш высокий, фикс требует аккуратности)

## T2.1 — ❌ УДАЛЕНО (motion-gate overlay по интерполированной позе)
Снято 2026-06-17 по решению пользователя: pose-сигнатура завязана на внутренности ванильного `LivingEntity` → несовместимо с BBS (движок теней обязан оставаться BBS-совместимым). Детали в блоке «СТАТУС» вверху и в [[project-shadow-bake-perf-audit]] (раздел ОТКЛОНЕНО). Сюда не возвращаться без BBS-нейтральной альтернативы.

## T2.2 — ✅ СДЕЛАНО (Сессия C, 2cb1292 на main, 1.20.4, in-world проверено) — батчинг immediate.draw() (ShadowRenderer)
Было: `renderCaster` делал `immediate.draw()` после КАЖДОГО кастера → до 6N флашей/point-свет. Стало: 1 флаш на проход (детали реализации в блоке «СТАТУС» вверху). Ниже — исходный план (для истории/форвард-порта).
Шаги: вынести pin состояния (`depthMask/enableDepthTest/disableBlend/setShader`) ОДИН раз перед циклом кастеров; `renderCaster` только буферизует (`dispatcher.render` в общий Immediate, per-caster try/catch вокруг только `dispatcher.render`); вызывающий (`renderInRangeCone/Face`) делает `immediate.draw()` один раз после цикла.
⚠️ ГАРДЫ (почему HIGH):
- Кастер, бросивший исключение посреди билда, может оставить ЧАСТИЧНУЮ вершину в общем BufferBuilder → единый flush нарисует мусор или упадёт, потеряв ВСЕ кастеры прохода. Нужно гарантировать, что упавший кастер не оставляет недостроенный quad.
- matrix-corruption: единый flush в конце читает RenderSystem-матрицы, которые кастер-моб мог испортить. Перед flush выставить `currentView`/`currentProj` на RenderSystem (как делает cutout-путь, ShadowRenderer:457-462). 
Обязательна проверка вживую (тени мобов под point-светом).

## T2.3 — VBO-кэш для cutout-блоков (ShadowRenderer) — fix риск low, нетривиально
Сейчас cutout (двери/листва/стекло/решётки) ре-тесселлируется через `brm.renderBlock` (499) на каждом статик-бейке; opaque уже в VBO. Закэшировать по аналогии: per-light VertexBuffer(ы) на RenderLayer (CUTOUT + CUTOUT_MIPPED), ключ = id + инстанс block-list, эвикт через `retainBlockVbos`. На redraw: выставить cutout-шейдер + забиндить block-атлас на Sampler0 (для alpha-discard), нарисовать VBO с `currentView/currentProj`. ГАРД: сохранить alpha-discard (текстурный cutout-шейдер), не сломать порядок авто-flush слоёв.

## T2.4 — бюджет бейков на кадр (ShadowBaker) — fix риск low
Сейчас все грязные лампы пекутся в одном кадре → спайк при массовой инвалидации (правка блока в общей секции, смена quality, поворот камеры на ряд ламп). Добавить per-frame бюджет ПОЛНЫХ СТАТИК-бейков (малый N, в LightConfig). Если бюджет исчерпан — оставить существующую карту (sticky-тайл гарантирует валидность до смены контента), пометить pending, доделать позже.
ГАРД: динамические overlay + copyStaticToLive НЕ бюджетировать (нужны каждый кадр). Свет, который НИКОГДА не пёкся (нет lastTile) и сейчас сэмплится, нельзя откладывать (сэмплит пустой тайл) → first-bake'и всегда пропускать вне бюджета (или приоритетом первыми).

## T2.5 — sphere-exact инвалидация (BlockShadowCache) — fix✅ safe, дешёвый
Сейчас `invalidateAt(pos)` (113-132) роняет всю сферу лампы при ЛЮБОй правке в одной из ~5×5×4 секций 16³, что ббокс сферы задевает — даже в дальнем углу секции, вне сферы. Фикс: хранить на `CacheEntry` снапнутый центр+радиус (уже считаются в `getOrCompute` :83-86); в `invalidateAt` перед пометкой dirty отбраковать правку, если `distSq(pos, center) > cr²` (пара float-операций). Секционный индекс оставить грубым пре-фильтром.

---

# Порядок
1. ✅ **T1.1 + T1.2 + T1.3** — СДЕЛАНО (Сессия A, da9c980 на main).
2. ✅ **T2.5** (bcc-2) — СДЕЛАНО (Сессия B, 57d1a4c на main).
3. ✅ **T2.4** (бюджет), **T2.3** (cutout VBO) — СДЕЛАНО (Сессия B, 57d1a4c на main, in-world проверено).
4. ✅ **T2.2** (батчинг draw) — СДЕЛАНО (Сессия C, 2cb1292 на main, in-world проверено).
(T2.1 motion-gate — удалён, см. СТАТУС.)

**ВСЕ T реализованы+проверены на main (1.20.4).** Форвард-порт на `port/1.21.11` — **НЕ cherry-pick** (движок там НЕ структурно тот же — raw-GL переписка ShadowRenderer): T2.2/T2.3 ПРОПУСТИТЬ, остальное hunk-by-hunk. Подробности+порядок — секция «⚠️ ФОРВАРД-ПОРТ НА port/1.21.11» выше (ревью 2026-06-17). См. [[project-port-1211]], [[project-shadow-bake-perf-audit]].

Каждый пункт: снять профайлер до/после, прогнать `runClient` с Iris-паком, глазами проверить тени (особенно T2.1/T2.2 — движущиеся мобы).

# Нарезка на сессии (почему ~3, а не 1 и не 7)
Дробить ПО СВЯЗНЫМ БАТЧАМ, не по одной T на сессию. Каждая новая сессия платит ~30-40k «налога на прогрев» (MEMORY.md + план + перечитать ShadowBaker.java 1088 строк + ShadowRenderer.java 675 для сверки строк) ДО первой строки кода — резать мельче = платить этот налог 7×. Но и всё в одну сессию нельзя: риск-код T2.1/T2.2 нельзя писать в деградировавшем хвосте (>100k) и под угрозой компактизации посреди правки. Середина — батчи по (общие файлы) + (тир риска) + (влезает под ~100k). Границы — ТОЛЬКО по зелёному build + закоммиченному verified-результату, НИКОГДА посреди атомарной правки (особенно не рвать T1.1↔T1.2).

- **Сессия A — ядро Tier 1** (файлы: ShadowBaker + ShadowRenderer): `T1.1 + T1.2` (атомарны, общая face-маска) + `T1.3` (скретчи матриц, тот же ShadowRenderer, тривиально). Одна когерентная правка «удешевить per-light bake-цикл»; один прочит двух файлов обслуживает все три; низкий/средний риск. Опц.: `T2.5` (BlockShadowCache, изолировано) как разогрев в начале. Старт свежей сессией — близко к 100k не подойдёт. Завершить: build + in-world verify + commit.
- ✅ **Сессия A — СДЕЛАНА** (T1.1+T1.2+T1.3, da9c980 на main).
- ✅ **Сессия B — СДЕЛАНА** (T2.5+T2.4+T2.3, 57d1a4c на main, in-world проверено, claude-ветки удалены). Детали реализации — в блоке «СТАТУС» выше.
- ✅ **Сессия C — СДЕЛАНА** (T2.2 батчинг draw, 2cb1292 на main, 1.20.4, in-world проверено: движущиеся мобы под point-светом, overlay avg ~0.5ms, без призраков/краша). Детали — в блоке «СТАТУС».

Зависимости через границы сессий: T1.1↔T1.2 (общая маска) уже сделаны в Сессии A. T2.5/T2.3/T2.4/T2.2 независимы по данным.
