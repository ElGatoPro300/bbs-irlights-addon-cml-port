---
name: plan-interactive-spot-guides
description: "Интерактивные гайды спотлайта в превью редактора форм (drag колец = radius/inner, диска торца = range) через реюз стенсил-пикинга BBS"
metadata: 
  node_type: memory
  type: project
  originSessionId: 7f172475-bb40-4100-a82e-64c096a5d6f7
---

Фича (2026-07-02): гайды спотлайта в превью редактора форм интерактивны — хват за внешнее кольцо тянет radius (угол), за внутреннее кольцо inner_radius, за диск в центре торца range. Реюз машинерии bbs-fs, point light отложен (юзер: только спот). Ф2 (in-world drag в film editor, UIFilmController) не начата.

Механика BBS (проверено по bbs-2.3.1 jar, 1:1 с исходниками bbs-fs):
- UIPickableFormRenderer.renderUserModel гоняет стенсил-пасс каждый кадр при курсоре во вьюпорте: StencilMap.setup (гизмо ID 1..STENCIL_MAX, формы дальше), FormUtilsClient.render с context.stencilMap, gizmo stencil поверх, pickGUI = glReadPixels 1x1 -> getPicked() = Pair<Form,String>. Ховер-подсветка + лейбл-карточка "Form - bone" бесплатны (PickerPreviewProgram).
- ГОЧА (словил 2026-07-02, первый заход был мимо — «ничего не изменилось»): в стенсил-FB два пайплайна записи ID. Модельный путь = PickerModelsProgram + Target-униформа через setupTarget (подмена vertex-consumer'ов, hijackVertexFormat). ПРОСТАЯ геометрия (гизмо, наши хэндлы) = ОБЫЧНЫЙ PositionColorProgram, ID кодируется ЦВЕТОМ вершин (r|g<<8|b<<16, alpha=1) — см. Gizmo.drawAxes stencil-ветку. PickerModels с POSITION_COLOR-буфером молча не пишет ничего. Индекс рисовать = context.getPickingIndex() ДО addPicking (addPicking присваивает и инкрементит после).
- Клик: subMouseClicked -> formEditor.clickViewport (gizmo -> pickFormFromRenderer). Кастомного хука нет -> наш миксин на HEAD.
- Undo бесплатно: UIFormEditor вешает form.preCallback(undoHandler::handlePreValues) на всё дерево значений, submitUndo каждый кадр рендера, мердж в окне 1с — любые .set() при драге undo-ятся как трекпады.
- Матрица стека превью = view * translate(-camPos) * chain (Gizmo.computeWorldOrigin); мировая точка = camPos + V^-1 * M * p. Луч мыши: CameraUtils.getMouseDirection(P, V, mx, my, area) + camera.position.

Реализация (все файлы аддона, BBS не патчится):
- SpotGuideDrag (client/forms): статический стейт; captureGuideMatrix(form, stack) из видимого прохода renderGuide (WeakHashMap per form); tryStart/mouseReleased/update; математика в guide-local (луч через inverse(V^-1 * G)): radius/inner = пересечение с плоскостью торца z=spotRingZ -> angle=2*atan(radial/range) clamp 1..179 (inner доп. <= outer); range = ближайшая точка луча к оси Z, clamp 0.1..128.
- LightGuideRenderer: spotRingZ(range) = единая точка правды знака торца (сейчас +range при конусе/оси в -range — подозрение на рассинхрон знака, проверить в игре, флипать в одном месте); renderSpotlightGrabRing/GrabCap(stack, ..., stencilIndex) (жирные хват-зоны, grabThickness=max(wireT*6, r*0.015), диск r*0.10) через renderStencilTriangles: PositionColorProgram, ID цветом (stencilColor), blend OFF, depth OFF.
- AbstractLightFormRenderer.render3D: в picking-ветке при context.modelRenderer && stencilMap.increment -> renderStencilHandles(context) (хук, дефолт пустой) ПЕРЕД renderPickBox.
- SpotlightFormRenderer: renderStencilHandles = 3x (draw с context.getPickingIndex() -> addPicking(form, имя)); имена = SpotGuideDrag.HANDLE_RADIUS/"radius", HANDLE_INNER/"inner_radius", HANDLE_RANGE/"range" (заодно красивый ховер-лейбл). Порядок отрисовки: outer, inner, cap — поздний выигрывает перекрытие; гизмо BBS рисуется после нас и всегда приоритетнее.
- UIPickableFormRendererMixin (mixin/client, зарегистрирован в irlite.client.mixins.json): subMouseClicked HEAD -> tryStart (съедает клик до clickViewport), subMouseReleased HEAD -> mouseReleased, renderUserModel TAIL -> update.
- UISpotlightFormPanel: bindPanel в startEdit + syncLightShape (live setValue трекпадов range/radius/innerRadius при драге).

Статус: in-game PASS (2026-07-02, юзер подтвердил «заработали»); дебаг-логи сняты, compile PASS. Ложный след при тесте: два захода «ничего не поменялось» = тест вне dev-клиента/не дошёл до превью (дебаг показал 0 вызовов render3D за сессию) — фичу тестировать ТОЛЬКО в runClient или свежем jar. Знак ringZ подтверждён практикой (хват-зоны совпали с видимыми кольцами). Открыто: тюнинг толщин хватов по желанию, Ф2 in-world (model block панель/film editor — оба GizmoViewport со своим стенсилом), point light (юзер не уверен что нужно), порт-ветки 1.21.x, коммит-чекпоинт.
