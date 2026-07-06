---
name: project-shadow-seam-phase3-impl
description: "✅ Ф3 + fix-b + MAJOR-B СДЕЛАНЫ + ЗАКОММИЧЕНЫ (2026-06-17): IRLite на ОБЩУЮ (канон redactor-main) оркестрацию теней + BBS-шов IRLiteBbsCasterSource. Оркестрация ДОКАЗАНО байт-идентична канону; Ф4 (option C+) теперь это мехpiratically обеспечивает — см. [[project-shadow-seam-phase4-lockstep]] и `irl-core/tools/verify-shadow-lockstep.py`. Аудит SHIP/0 блокеров. IN-WORLD: runtime-smoke PASS (визуал больше не гейт). ЗАКОММИЧЕНО: IRLite master@7bc0a94 + redactor main@5c03818 + irl-core@f847e69. Ф4-обновления: redactor main@40b0f53 + IRLite master@fd506ad + irl-core@f0c182a."
metadata:
  node_type: memory
  type: project
  originSessionId: 93539f81-a906-4d87-b424-e0791b867626
---

# Ф3 выполнена в коде: IRLite на общей оркестрации теней + BBS-шов

Продолжение [[plan-shadow-seam-refactor]] / [[project-shadow-seam-phase1-frozen]] (Ф1 заморожена, Ф2 в redactor-main@522c1f0). Здесь — Ф3 (самая рискованная): IRLite получил канон-оркестрацию (6 перф-опт + 5 инвариантов) даром + сохранил каст по BBS-форме. **Статус: СДЕЛАНА + ЗАКОММИЧЕНА. Билд зелёный, аудит SHIP, in-world runtime-smoke PASS. Один подтверждённый дефект (MAJOR-A) отложен на след. сессию решением пользователя.**

## Коммиты
- **IRLite `master`@e301090** — Ф3 код (12 файлов: 6 новых seam + ShadowBaker/Renderer/BlockShadowCache/PointShadowArray/IrliteConfig/BBSSettingsMixin). База была 9953f31.
- **irl-core `main`@f847e69** — docs/ (shadow-caster-seam-spec.md + Ф3-эррата, shadow-phase3-port-plan.md, shadow-phase3-audit-verdict.md) + `/bin/` в .gitignore. База была 59d893a.
- redactor `main`@522c1f0 (канон, не трогали).
- НЕ закоммичены (пред-существующие, не наши): IRLite README.md, IRLite TEMP/.

## Что сделано (6 created + 6 modified в IRLite)
- **5 seam-файлов скопированы ВЕРБАТИМ** (только строка package): ShadowCasterSource, OccluderSink, OccluderBatch, ImmediateOccluderBatch, CasterType → `qualet.irlite.client.light.shadow`. RedactorEntityCasterSource НЕ копировался (заменён новым BBS-швом).
- **ShadowBaker.java + ShadowRenderer.java заменены каноном** (copy-and-adapt). **ДОКАЗАНО байт-идентичны redactor-main** после реверс-нормализации адаптаций (package; `org.qualet.irlredactor.light.LightRegistry`→`org.qualet.irl.light.LightRegistry` (irl-core); `LightConfig`→`qualet.irlite.IrliteConfig`; SOURCE=`new IRLiteBbsCasterSource()`; collect-shim). ⇒ оркестрация + 6 T-опт + INV-машинерия унаследованы без дрейфа от уже-закалённого канона.
- **IRLiteBbsCasterSource.java (НОВЫЙ, ~545 ln)** — единственный IRLite-специфичный файл. 3 collect-арма (entity→model-block→replay, порядок несущий для over-cap) + 3 draw-арма (switch по CasterType). drawEntity = MorphRenderer-first (renderPlayer/renderLivingEntity, vanilla dispatcher fallback) — причина существования варианта. drawModelBlock/drawReplay = BBS FormRenderer via FormRenderingContext. Все 7 старых BBS-методов + getActiveEditorController + FNV-helper + FULL_LIGHT перенесены сюда из старых ShadowBaker/Renderer.
- **BlockShadowCache** → канон T2.5 (CacheEntry cx/cy/cz/cr + точный center+radius reject в invalidateAt). **PointShadowArray** → добавлен copyStaticFaceToLive(slot,face) (T1.2, отсутствовал). **IrliteConfig** + `shadowBakeBudget()` (default 4). **BBSSettingsMixin** регистрирует `shadow_bake_budget` (getInt 4,0,16).
- GameRendererLightMixin.bake(...)/onShadersDisabled() + WorldBlockChangeMixin.invalidateAt — сигнатуры совпали, миксины НЕ трогали.

## Решения по инвариантам
- **INV-2 = КОНСЕРВАТИВНО (locked, Open Q1):** `isStatic=false` для ВСЕХ касторов (entity/model-block/replay), staticHash=0L всегда. Гарантированно-корректно (анимированный model-block НЕ замёрзнет — отмена старого бага). НЕ регрессия (у старого IRLite static-слоя не было; model-block static-tile кэш лишь ОТЛОЖЕН, остальные 5 опт + world-block static работают). `modelBlockHash` дормантна (мёртвый код до включения probe; TODO в исходнике).
- **INV-1/3/4/5 машинерия** унаследована идентично от канона (на redactor инертна, на IRLite впервые активна через model-block-арм). INV-3 fold (avalanche+count) только в shared ShadowBaker; источник даёт CONTENT. INV-4 try/catch только в shared emitCaster + внутри collect-армов (degrade-to-absent); draw-армы НЕ ловят. INV-5 — over-bound rotation-expanded box (см. MAJOR-B).

## Верификация
- **Behavior-diff:** нормализованный diff ⇒ ShadowBaker/ShadowRenderer байт-идентичны канону. Малые дельты (cache/array/config) проверены вручную — корректны.
- **5-агентный адверсариальный аудит (run wf_5e641d8e-bb5):** вердикт **SHIP, 0 блокеров**. Полный вердикт = `irl-core/docs/shadow-phase3-audit-verdict.md`. План = `irl-core/docs/shadow-phase3-port-plan.md`.
- **2 MAJOR — ОБА пред-существующие, НЕ регрессии Ф3:**
  - **MAJOR-A (INV-1, self-drawing forms):** BBS-формы (ModelVAORenderer→glDrawArrays; MobFormRenderer.render3D оставляет live modelview=identity) рисуют СИНХРОННО в emitOccluder, читая live RenderSystem modelview ДО end-of-batch re-establish. В одном light-pass с >1 self-draw формой поздняя форма может рисоваться через испорченный modelview. Старый IRLite renderCaster имел ТУ ЖЕ дыру (re-establish раз на pass-begin). Спека INV-1 была написана под buffered-путь → **ИСПРАВЛЕНА эрратой** в shadow-caster-seam-spec.md. КОД не трогали.
  - **MAJOR-B (INV-5, feet-pivot):** rotationExpandedBox раскрывает AABB вокруг ЦЕНТРА, а applyTransform вращает вокруг НОГ (feet pivot) → недо-оценка сферы у высокого model-block при наклоне rotate.x/z ≳45-60° близко к границе cull (узкий угол). Пред-существующий класс (старый код вообще игнорил rotate). Лживый javadoc «guaranteed over-bound» **ИСПРАВЛЕН**; матем-фикс = fast-follow.

## IN-WORLD ГЕЙТ — РЕЗУЛЬТАТ (сессия 2026-06-17, runClient -Pmc=1.20.1, Photon_IRLights)
- **runtime-smoke PASS:** ~7.5 мин в мире, шейдеры активны, патчер enum'ит паки, дашборд открывается, чистый выход (exit 0). НИ ОДНОГО исключения из кода Ф3 (light.shadow.* / IRLiteBbsCasterSource). Ошибки в логе ВСЕ чужие (BBS TextureManager NPE при открытии дашборда; BBS film replay transform-property на server thread; vanilla worldgen noise; missing caxton/alex_bends ассеты dev-окружения).
- **Пользователь подтвердил: «остальные механизмы работают»** (тени появляются, силуэт/анимация ок).
- **❌ MAJOR-A ПОДТВЕРЖДЁН вживую:** смешанная сцена (моб + model-block под одним светом) — тень формы ПРОПАДАЕТ, но ВОЗВРАЩАЕТСЯ при отлёте/смене ракурса (смена ракурса меняет состав касторов в пассе через behind-cam/range-cull → убирает источник порчи modelview). Ровно прогноз аудита №1. **РЕШЕНИЕ ПОЛЬЗОВАТЕЛЯ: отложить на следующую сессию, закоммитить как есть.**
- НЕ проверены отдельно: MAJOR-B (наклонённый model-block у края конуса), перф на статик-тяжёлой сцене.

### Исходный чек-лист (для добивки в след. сессию)
1. MAJOR-A смешанная сцена (✅ воспроизведён, ждёт fix-b). 2. MAJOR-B наклон у края (не тестилось). 3. INV-2 анимация (ок). 4. model-block-only свет. 5. силуэт=превью (ок). 6. перф статик-тяжёлой сцены.

## ✅ FIX-B + MAJOR-B СДЕЛАНЫ + ВЕРИФИЦИРОВАНЫ ЗЕЛЁНО (2026-06-17, нов. сессия)
Решение пользователя: **«фиксы lockstep сейчас, физическая Ф4-экстракция ОТДЕЛЬНО»** (после grounding-находки: redactor-main НЕ на irl-core — свой `org.qualet.irlredactor.light.LightRegistry`; loom 1.9 vs IRLite 1.15.5 → composite Loom→Loom «с трением»; тени MC-типизированы, в plain-Java irl-core не лезут → полная экстракция = большой лифт с предусловием миграции redactor→irl-core; отложена).
- **fix-b (INV-1/MAJOR-A):** в обоих ShadowRenderer.java вынесен матрич-пролог в `establishLightMatrices(view,proj)`; `emitCaster` зовёт его ПЕРЕД каждым `source.emitOccluder` (re-establish light view/proj). Применено БАЙТ-ИДЕНТИЧНО к канону (redactor-main) и IRLite (lockstep сохранён, доказано diff'ом). Идемпотентно для buffered redactor-пути.
- **MAJOR-B (INV-5):** в IRLiteBbsCasterSource `rotationExpandedBox`+`emitFromBox` заменены на `emitModelBlock`, сфера через raw `sink.emit`: center = `pivot + R·(0,hy,0)`, pivot = `feet + t.translate` (= block-center + **2×translate**, т.к. drawModelBlock складывает translate в feet, а applyTransform translate'ит ЕЩЁ РАЗ — закрыт и feet-pivot, и разрыв 1×-vs-2×-translate). radius = `|(ehx,ehy,ehz)| + OVERLAP_MARGIN(0.5)`. t==null и upright-untranslated кейсы БАЙТ-поведенчески = старым. MAJOR-B = только IRLite (у redactor нет model-block арма).
- **Билд:** redactor compileClientJava(1.20.4) ✅, IRLite(1.20.1)✅ +(1.20.4)✅.
- **Адверс. верификация (3 агента, run wf_0d4306f9-02f):** ВСЕ 3 PASS, 0 blocker/major/minor. fix-b: дизассемблирован MC+BBS байткод → MatrixStack.loadIdentity/multiplyPositionMatrix in-place (нет дисбаланса стека), scratch ≠ global modelview, ModelVAORenderer.setupUniforms = `GLOBAL modelview × scratch` ⇒ fix РЕАЛЬНО работает для self-draw (не no-op), INV-4 двойной drain безопасен. MAJOR-B: 2 млн-кейсный фузз → НОЛЬ under-bound; worked ex. (c) rotX=90 даёт точную мин. сферу. 3 нита (все намеренные/документированные): избыточная матрич-работа на buffered-пути (держим unconditional ради unified/byte-identical), safe yaw over-bound, OVERLAP_MARGIN-зеркало + мёртвый modelBlockHash под INV-2-локом. Действий не требуется.
- **Спека:** эрратум INV-1 «Hardening (scheduled)» в shadow-caster-seam-spec.md теперь РЕАЛИЗОВАН; ложный javadoc rotationExpandedBox удалён вместе с методом.
- ✅ **ЗАКОММИЧЕНО (2026-06-17):** redactor `main`@5c03818 (fix-b, 1 файл) + IRLite `master`@7bc0a94 (fix-b+MAJOR-B, 2 файла). На текущих ветках (no per-session branch). Pre-existing IRLite README.md+TEMP/ НЕ трогали.
- ✅ **IN-WORLD RUNTIME-SMOKE PASS (этой сессии, runClient -Pmc=1.20.1, Photon_IRLights):** ~8 мин, чистый exit 0, НИ ОДНОГО исключения из light.shadow/IRLiteBbsCasterSource. Бейк model-блоков ОТРАБОТАЛ (стек bake→renderInRangeFace→emitCaster→emitOccluder→drawModelBlock исполнился), INV-4 выдержала. В логе только ИЗВЕСТНЫЙ benign dev-env шум: missing-ассет `alex_bends.png` (BBS TextureManager ловит+печатает+фолбэк, наши кадры лишь в цепочке вызова) + BBS UITexturePicker NPE при открытии дашборда.
- 🔴 **ВИЗУАЛЬНЫЙ ГЕЙТ ещё за пользователем** (лог не показывает «вид тени»; инварианты молчат на компиляции): (1) fix-b — смеш. сцена моб+model-block под одним светом (тень формы НЕ должна пропадать/возвращаться при смене ракурса); (2) MAJOR-B — высокий model-block (hbH≈4) с rotate.x/z≈60-90° у края конуса/грани куба (тень не должна выпадать). Пользователь запустил клиент, визуальный вердикт по сценам 1/2 пока НЕ дан.

## Fast-follows (исторические; #1 #2 ВЫПОЛНЕНЫ выше)
1. ✅ **MAJOR-B матем-фикс** — СДЕЛАНО (см. выше): сфера на feet-pivot AABB + разрыв 1×-vs-2×-translate закрыт.
2. ✅ **INV-1 hardening fix-b (MAJOR-A)** — СДЕЛАНО (см. выше), применено к ОБОИМ вариантам lockstep.
3. Решить судьбу мёртвого `modelBlockHash` (оставить как scaffolding под probe / удалить).
4. **НИЗКАЯ УВЕРЕННОСТЬ:** drawModelBlock возможно дважды применяет t.translate (feet включает +translate И applyTransform делает stack.translate(translate)). ВЕРБАТИМ из старого IRLite (пред-существующее) — проверить против BBS ModelBlockEntityRenderer ПРЕЖДЕ чем трогать; обычно translate=0 ⇒ незаметно.
5. **Ф4 разблокирована:** оба варианта теперь засеамлены за идентичной оркестрацией → можно слить в общий модуль (план §Фаза4); fix-b/MAJOR-B логично делать в общем коде.
