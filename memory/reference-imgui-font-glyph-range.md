---
name: reference-imgui-font-glyph-range
description: "Атлас шрифта ImGui-редактора = getGlyphRangesCyrillic() (Basic Latin + Latin-1 + кириллица). Галочка/крест/предупр/стрелка/многоточие/em-dash не входят -> тофу. Статус давать цветом, не значком."
metadata:
  node_type: memory
  mod_scope: IRL-redactor-only
  type: reference
  originSessionId: b84aba53-c9b9-4911-a8b6-01210acd8b20
---

ImGuiRuntime.loadFonts строит атлас как fonts.addFontFromMemoryTTF(minecraft.ttf, 16, cfg, glyphRanges), где glyphRanges = fonts.getGlyphRangesCyrillic(). Диапазоны: 0x0020–0x00FF (Basic Latin + Latin-1 Supplement), 0x0400–0x052F (кириллица), 0x2DE0–0x2DFF, 0xA640–0xA69F. General Punctuation / Arrows / Dingbats НЕ входят.

Поэтому в строках UI редактора (lang irl-redactor.*, мета/статус патчера, виджеты) НЕ использовать: U+2713 (галочка), U+2717 (крест), U+26A0 (предупреждение), U+2192 (стрелка), U+2026 (многоточие), U+2014 (em-dash). Они не растеризуются, рисуются fallback-глифом ('?') / квадратом. (До правки 2026-06-21 мета-строка патчера содержала стрелку — почти наверняка показывалась как тофу.)

Можно: ASCII, «» (U+00AB/BB) и · (U+00B7) — они в Latin-1, рисуются нормально.
Передавать смысл ЦВЕТОМ, не значком: Widgets.textColored* даёт зелёный/красный/янтарный — этого достаточно для OK/ошибки/предупреждения без значков.

Длинный текст: Widgets.emitText (custom shadowText -> addText) НЕ переносит по словам, обрежется в окне фикс. ширины. Для длинных фраз использовать Widgets.textColoredWrapped / textDisabledWrapped (добавлены 2026-06-21, greedy word-wrap по getContentRegionAvail().x).

Связь: [[project-irlite-base-ported]] (локализация редактора через editor/Lang/I18n); патчер-UI = redactor-only ([[reference-edit-routing-by-area]]).
