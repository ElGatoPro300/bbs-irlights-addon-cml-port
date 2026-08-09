---
name: plan-cluster-heatmap-debug
description: "IDEA/мини-план (2026-07-13, код НЕ начат): дебаг-heatmap для tile-based light culling (ClusterGridBuffer, irl-core, binding6) — цветовой градиент поверх кадра по числу ламп на тайл, по референсу юзера (стиль Unity/Unreal light-complexity view). ОТДЕЛЬНЫЙ трек от [[plan-octahedral-point-shadow]] — не смешивать."
metadata:
  node_type: memory
  type: project
  originSessionId: 8144b2eb-ba27-48fa-a231-960f3771d8c1
---

ИДЕЯ: юзер показал референс — полупрозрачный цветовой градиент (синий = мало / красный = много) поверх рендера, показывающий нагрузку по экранным тайлам. Хочет что-то похожее для нашей системы tile-based light culling.

ЧТО УЖЕ ЕСТЬ (irl-core, org.qualet.irl.light.ClusterGridBuffer.java, binding=6, из обсуждения в этой сессии — не перепроверялось отдельным ресёрчем):
- Сетка 32x18=576 тайлов на экран, GRID_X/GRID_Y константы.
- На тайл — битовая маска 64 лампы (MASK_LIGHTS=64, 2×uint32) в std430 SSBO, заполняется раз в кадр в buildAndUpload (проекция экранного охвата каждой лампы на сетку).
- GLSL читает маску через irlite_clusterMaskFetch() (в irlite_lights.glsl), делает masked-continue в per-fragment лупе.
- Число ламп на тайл = popcount двух uint32 масок — ничего нового считать не нужно, данные уже там.

ЧТО НУЖНО (черновой скоуп, НЕ детализировано, детализация — в самой next-session):
- Shader-side оверлей: читать ClusterGridBuffer (уже забинжен на 6), popcount маски своего тайла -> цвет по градиенту -> подмешать поверх финального кадра. Похожий паттерн инъекции уже есть для outline (IRLITE_OUTLINE в composite1) — можно взять как образец точки внедрения.
- Тумблер вкл/выкл — как остальные IRLITE_*-опции (define/Iris-настройка).
- Новой Java-стороны/бейка не требуется — чистая визуализация уже существующих данных.

НЕ РЕШЕНО (обсудить в начале next-session, до кода):
- Ветка: новая (feature/...) или прямо в master, т.к. это чисто аддитивный дебаг-тумблер без риска для прод-рендера — юзер ещё не выбрал.
- Шейдер-пак для прототипа — по умолчанию предложить CR (ComplementaryReimagined), как в остальных текущих экспериментах, но не зафиксировано.
- Тираж на другие паки — не обсуждалось, по умолчанию НЕ в скоупе первой итерации.

ГЕЙТЫ: НЕ путать с [[plan-octahedral-point-shadow]] (ветка optimization/octahedral-point-shadows, другая задача, другой скоуп) — эта идея работает НАД master/основной кластеризацией, не над point-shadow экспериментом.

PROMPT ДЛЯ NEXT SESSION (юзер может вставить как есть, чтобы начать):
```
Хочу debug-heatmap для tile-based light culling (ClusterGridBuffer, irl-core, binding=6).
Референс — как light-complexity view в Unity/Unreal: цветовой градиент (синий = мало ламп
на тайл, красный = много) полупрозрачным оверлеем поверх обычного рендера.

Прочитай память plan-cluster-heatmap-debug.md — там черновой скоуп и что уже есть в
ClusterGridBuffer (сетка 32x18, битовая маска 64 лампы на тайл, popcount = число ламп).

Это ОТДЕЛЬНЫЙ трек от plan-octahedral-point-shadow (ветка optimization/octahedral-point-shadows)
— не смешивать, не трогать ту ветку.

Сначала обсудим план (какая ветка, какой шейдер-пак прототипировать — по умолчанию предлагаю
CR), код пока не пиши.
```

Связь: [[plan-octahedral-point-shadow]] (отдельный параллельный трек, не смешивать), [[plan-perf-fix-cluster-phase3]] (откуда взялся ClusterGridBuffer).
