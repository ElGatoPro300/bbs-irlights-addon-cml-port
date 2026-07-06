---
name: project-light-editor-gui-plan
description: Детальный план портирования HTML light-editor-prototype.html в Minecraft GUI (Fabric 1.20.4+)
metadata: 
  node_type: memory
  type: project
  originSessionId: 2c057351-c8b5-4fdb-8e2d-397b23ee0ef4
---

# Цель
Портировать `C:\Users\Qualet\Desktop\light-editor-prototype.html` в Minecraft как GUI-экран для редактирования источников света (IRLights). 

**Why:** Нужен полноценный in-game редактор с drag-слайдерами, тогглами, цветопикером, сворачиваемыми группами и пресетами.  
**How to apply:** При любой работе над GUI этого мода — опираться на этот план и bbs-fs как источник виджетов.

---

## Решение: кастомный GUI на ванильных вызовах + тонкая абстракция

Выбран вариант 2 (не ImGui). Вся логика рендера изолирована в `Batcher2D` из bbs-fs — при обновлении MC правится только он, виджеты не трогаются.

---

## Источник виджетов: bbs-fs

Путь: `C:\Users\Qualet\Documents\Project\Minecraft\BBS\bbs-fs`

Берём готовым из bbs-fs:
- `UITrackpad` / `UISliderTrackpad` — drag-слайдеры
- `UIToggle` — boolean toggle (track + knob)
- `UIButton` — кнопки
- `UITextbox` — текстовый ввод
- `UIColorPicker` — HSV/RGB пикер
- `UIScrollView` — скроллируемый контейнер
- `ColumnResizer`, `RowResizer` — layout
- `UIElement`, `UIContext`, `Area`, `Flex` — фреймворк
- `Batcher2D`, `UIRenderingContext` — рендер-абстракция

Пишем с нуля:
- `UICollapsibleGroup` — секция с треугольником, сворачивается по клику header
- `UISegmentedControl` — взаимоисключающие кнопки (Точечный / Прожектор)
- `UIColorSwatch` — цветной квадрат → открывает UIColorPicker
- `LightEditorPanel` — сборка всего дерева виджетов
- `LightEditorScreen extends Screen` — обёртка Minecraft

---

## Фазы

### Фаза 0 — Импорт фреймворка
Скопировать ядро UI из bbs-fs в irlights-мод. Проверить компиляцию на Fabric 1.20.4.  
**Выход:** пустой Screen открывается в игре.

### Фаза 1 — Smoke test виджетов
Тестовый экран: UITrackpad + UIToggle + UIButton. Проверить drag, события, рендер.  
**Выход:** три виджета работают в игре.

### Фаза 2 — Новые составные виджеты
- `UICollapsibleGroup`: header (треугольник + title + опциональный toggle) + скрываемое body
- `UISegmentedControl`: N кнопок с общим activeIndex, активная подсвечена accent-цветом
- `UIColorSwatch`: прямоугольник текущего цвета, клик → UIColorPicker оверлей  
**Выход:** три виджета работают изолированно.

### Фаза 3 — LightEditorPanel (дерево виджетов)
```
LightEditorPanel (ColumnResizer)
├── PanelHeader
│   ├── Title + ResetButton
│   ├── UITextbox (имя источника)
│   └── UISegmentedControl (Точечный / Прожектор)
├── UIScrollView (body)
│   ├── PresetsBlock (RowResizer wrap, UIButton×6: Факел/Лампа/Свеча/Неон/Студийный/Окно)
│   ├── UICollapsibleGroup "Основное" (открыта по умолчанию)
│   │   ├── UIColorSwatch
│   │   ├── UITrackpad: Температура (1500–10000 K)
│   │   ├── UITrackpad: Прозрачность (0–100%)
│   │   ├── UITrackpad: Яркость (0–20)
│   │   ├── [Point only] UITrackpad: Радиус (0.1–64)
│   │   └── [Spot only] UITrackpad×3: Дальность/Угол/Мягкость края
│   ├── UICollapsibleGroup "Объёмные лучи" (toggle в header)
│   │   ├── UITrackpad: Сила лучей (0–5)
│   │   ├── subhead "Тонкая настройка"
│   │   ├── UITrackpad: Плотность (0.005–0.5)
│   │   └── UITrackpad: Направленность (-0.95–0.95)
│   └── UICollapsibleGroup "Тени и поведение"
│       ├── UIToggle: Тени
│       ├── UITrackpad: Мягкость тени (0–2)
│       ├── UIToggle: Только на entity (взаимоисключает blocks)
│       └── UIToggle: Только на блоки (взаимоисключает entities)
└── PanelFooter (статус-текст)
```
Логика Point/Spot: при переключении сегмента — скрывать/показывать соответствующие блоки трекпадов.  
Логика пресетов: PRESETS = { torch, lamp, candle, neon, studio, sun } — программно выставляют значения.  
**Выход:** полный статический GUI без подключения к данным.

### Фаза 4 — LightEditorScreen
`extends Screen`, делегирует mouse/keyboard события в `LightEditorPanel`.  
**Выход:** экран открывается по биндингу, полностью интерактивен.

### Фаза 5 — Data model + интеграция
`LightSourceData` POJO ↔ `LightEditorPanel`. Открытие экрана при взаимодействии с источником света.  
**Выход:** GUI реально редактирует данные IRLights.

---

## Портируемость 1.20+

- `Batcher2D` — единственная точка контакта с `DrawContext`. При обновлении MC — правим только его.
- Виджеты зависят только от `UIContext` и `Batcher2D`, не от Minecraft API.
- `LightEditorScreen extends Screen` — единственная точка входа в MC.
