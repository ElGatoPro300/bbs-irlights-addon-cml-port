---
name: project-irl-redactor-imgui-standalone
description: "IRL-redactor = автономный ImGui-мод, визуальный прототип light-editor без BBS/IRLite"
metadata: 
  node_type: memory
  type: project
  originSessionId: 9291e2c4-1771-47c9-9dc7-25ae94911fc2
---

# IRL-redactor: финальная архитектура (2026-06-15)

> ⚠ УСТАРЕВШИЙ ПУНКТ: граница «НЕ интегрируется с IRLite» больше НЕ действует — движок света теперь перенесён в редактор (см. [[project-irlite-base-ported]] + [[plan-wire-editor-to-pipeline]]). Файл остаётся как origin-story автономного ImGui-мода.

Решения, принятые с пользователем, **отменяют** старый план project-light-editor-gui-plan (archived)
(тот предполагал «не ImGui», копирование UI-фреймворка BBS и интеграцию с IRLights).

**ОБНОВЛЕНИЕ 2026-06-16:** «Границы» ниже частично устарели — в мод перенесён BBS-free движок света
IRLite (см. [[project-irlite-base-ported]]). Пункт «НЕ зависит от BBS» по-прежнему верен (зависимости =
Iris+Sodium, не BBS); но «НЕ интегрируется с IRLite / НЕ рендерит свет/тени» более не действует — движок
collect→bake→SSBO→Iris присутствует. Редактор ещё НЕ связан с движком (шов = LightScene/PlacedLight/LightDriver).

**Цель:** автономный Fabric-мод `IRL-redactor` (пакет `org.qualet.irlredactor`,
`C:\Users\Qualet\Documents\Project\Minecraft\BBS\irlights`, MC 1.20.4), который на
**ImGui (imgui-java, SpaiR)** визуально воспроизводит `C:\Users\Qualet\Desktop\light-editor-prototype.html`.

**Границы (важно):**
- НЕ зависит от BBS (jar) и НЕ копирует его фреймворк.
- НЕ интегрируется с IRLite, НЕ рендерит свет/тени.
- Пока **только визуальный прототип** GUI со своим in-memory состоянием (POJO).

**Why:** пользователь хочет полностью отделиться от BBS; ImGui даёт богатые виджеты
(DragFloat=трекпад, CollapsingHeader=группы, ColorEdit=свотч, RadioButton=сегмент) из
коробки, точный флэт-вид прототипа через ImGuiStyle, и лучшую переносимость между версиями MC
(нужен только GLFW window handle + GL-контекст).

**Цена ImGui:** нативные JNI-либы (.dll) бандлятся в мод; разовый GL/input-клей
(рендер в конце кадра с сохранением GL-стейта + перенаправление GLFW-ввода).

**How to apply:** референс вида = prototype.html + визуальное сходство с BBS-UI (маджента-акцент
`#e62e8b`, дефолтный шрифт Minecraft, флэт без сглаживания). Любой плоский элемент рисуем через
`ImGui.getWindowDrawList()` (как трекпад/тоггл в `editor/Widgets.java`).

**Сделано (Фаза 1 + BBS-скин, 2026-06-15):** мод компилируется и РАБОТАЕТ в `runClient` (подтверждено
скриншотом). Нативы imgui грузятся, рендер через хост-`Screen` (клавиша L в игре). Кадр:
`gl3.newFrame()`→`glfw.newFrame()`→`ImGui.newFrame()` (пропуск gl3.newFrame даёт ассерт "Font Atlas not built").
Шрифт MC вшит: `src/client/resources/assets/irl-redactor/fonts/minecraft.ttf` (источник —
`C:\Users\Qualet\Desktop\minecraft.ttf`) — **кириллица в нём есть, рендерится норм**.
BBS-скин: маджента-акцент `#e62e8b`, сглаживание фигур off, кастомный тоггл (`editor/Widgets.toggleRow`),
заливка трекпада = сплошной акцент `#e62e8b`. Футер = "IRLights · Все права защищены".
Пользователь УДАЛИЛ из панели: пресеты, Температуру, Прозрачность (и `ColorTemp.java`).
Экстра-правка: `FrameBorderSize=0` + убраны `addRect`-обводки у виджетов (флэт без контура).
Текст с ванильной MC-тенью (offset +1,+1 затемнённым): ВСЁ рисуется через `Widgets` (`shadowText`),
поэтому кнопки/заголовки секций/подписи — кастомные через `DrawList` (ImGui-нативный текст тень не умеет).
inputText (имя) остался ImGui-нативным (без тени — как поля ввода в MC).

**Осталось (на будущее):** v1-полировка (body-скролл вместо скролла всей панели, double-click-ввод
числа в трекпаде, свотч 46×22, подсветка заголовков секций розовым фоном как в BBS-UI);
bundling imgui-java + нативов в jar (shadow/JiJ) для запуска в prism-инстансе рядом с BBS.
