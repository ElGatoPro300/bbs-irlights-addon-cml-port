---
name: feedback-visual-test-image-prompts
description: Для любой визуальной рантайм-проверки автоматически выдавать image-gen промпт (EN) в каноне ожидание/регрессия вместо SVG-визуализаций.
metadata: 
  node_type: memory
  type: feedback
  originSessionId: a102893e-2771-4573-937c-afa176631989
---

Директива юзера 2026-07-02: каждый раз, когда нужна визуальная проверка в рантайме (ин-гейм тест шейдера/света/теней и т.п.), АВТОМАТИЧЕСКИ писать промпт для image-gen ИИ в установленном каноне — без просьбы.

**Why:** визуальная пара «ожидание/регрессия» даёт юзеру 100% понимание, какой фидбек давать по скринам; генерация у внешней ИИ экономит контекст сессии (SVG-визуализации его забивают).

**How to apply:**
- Канон промпта (EN, генераторы плохо понимают кириллицу и мелкий текст): общий стилевой префикс «Flat technical infographic for game shader debugging, clean minimal style, dark navy background, no photorealism, large readable English labels only, teal pill badge "EXPECTED" and red pill badge "REGRESSION" above panels, 16:9» + тело сцены.
- Панели: EXPECTED vs REGRESSION side-by-side; если уместен убитый старый баг для сравнения — третья панель с серым бейджем "BEFORE".
- Артефакт описывать словами максимально буквально (где пятно, какая форма, чем помечено — red dashed circle/ellipse и т.п.); полезно добавлять мини-график (brightness profile) где дискриминатор — градиент.
- Один тест = один промпт-блок в code fence; в конце заметка про Midjourney-суффикс `--ar 16:9 --style raw` при необходимости.
- Свои SVG-виджеты для таких проверок по умолчанию НЕ делать; только если нужна схемная точность (координаты/профили) и юзер явно попросил.

Связь: [[plan-shadow-filtering-refactor]] (первое применение — тесты Ф2: bleeding/шов Softness/рёбра куба).
