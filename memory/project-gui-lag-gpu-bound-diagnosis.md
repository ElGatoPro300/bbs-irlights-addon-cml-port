---
name: project-gui-lag-gpu-bound-diagnosis
description: "ИЗМЕРЕНО (2026-06-22): лаг GUI редактора при многих источниках = GPU-bound (шейдер цикл по свету/пиксель), НЕ потоковая контенция. Рычаг 2 (collect-cache в nearest()) done; рычаг 1 (GPU-cull/LOD/дефолт autoLightMax) открыт. Инструмент FrameProfiler."
metadata:
  node_type: memory
  type: project
  mod_scope: redactor-only
  originSessionId: 3b765630-5e10-4a26-90f8-b52c7f9871ac
---

Лаг GUI редактора при многих источниках — измеренный диагноз (2026-06-22). Жалоба пользователя: «GUI отстаёт/лагает когда источников слишком много, при этом FPS может быть высоким; ощущение, что GUI на том же треде, что и свет». Структурно верно (ImGui-оверлей и пайплайн света оба на render-треде, последовательно в одном кадре: GameRendererLightMixin renderWorld HEAD -> collect/bake/flush; MinecraftClientImGuiMixin render(Z) после блита -> ImGui), но фикс — НЕ потоки.

Как мерили: временная инструментация org.qualet.irlredactor.diag.FrameProfiler (за флагом -Dirl.profileFrame=true, опц. -Dirl.profileGpu = timed glFinish) — раз/сек пишет [irl] frame: total avg/max | collect | bake | flush | imgui | cpuSum. Врезана в 3 точки (collect/bake/flush в GameRendererLightMixin, imgui в LightEditorScreen.renderActiveOverlay, frameMark в MinecraftClientImGuiMixin). Флаг прокинут в dev runClient через tasks.matching{runClient}.jvmArgs в build.gradle. Прогон: main (1.20.4), Iris+пропатченный пак, авто-свет, счётчик 1->200->2000. НЕ закоммичено (диагностика; убрать вместе с diag-кодом).

Результат (редактор открыт):
| Источников | total (FPS) | collect | bake | flush | imgui | cpuSum | GPU=total−cpuSum |
|---|---|---|---|---|---|---|---|
| 1 | 7.2ms (~139) | 0.00 | 0.02 | 0.01 | 0.47 | 0.5ms | ~6.7ms |
| ~200 | ~50ms (~20) | 12.5 | ~2 | 0.03 | 0.5 | ~16ms | ~34ms |
| ~2000 | 287ms (~3.5) | 12.5 | 1.4 | 0.05 | 0.5 | ~14ms | ~273ms |

Вывод: при 2000 источников весь наш CPU = 14мс (5% кадра), GPU = 273мс (95%). Render-тред не загружен нами — он ЖДЁТ GPU на present. VSync исключён (273мс >> интервала развёртки = реальная работа GPU). Разрыв 14<->287 сам по себе доказывает GPU-bound, glFinish-прогон не понадобился.

Два рычага (в порядке важности):
1. GPU (главный, ~273мс) — пропатченный шейдерпак гоняет цикл по ВСЕМ загруженным источникам на КАЖДЫЙ пиксель (MAX_LIGHTS=2048 в irl-core, слайдер autoLightMax до 2000). Лечить: frustum+distance cull перед upload / LOD по дальности / куда меньший дефолт+потолок autoLightMax. Слайдер max режет именно GPU. Контракт буфера = [[addon-light-buffer-ssbo]].
2. collect() (~12.5мс CPU) — AutoLightManager.nearest() ПОЛНОСТЬЮ сортирует весь набор эмиссивных блоков в радиусе скана КАЖДЫЙ render-кадр (feed.sort O(n·log n)), обрезая до max уже ПОСЛЕ сортировки. Поэтому collect ~ const (12.5мс) и при 200, и при 2000 — стоимость зависит от autoLightRadius (сколько найдено), не от max. Эти 12мс режут потолок до ~80fps даже при бесплатном GPU. Лечить: не сортировать каждый кадр (набор меняется ~раз/сек по скан-проходу + движению камеры) -> кэш ближайших / bounded-selection O(n). См. [[project-auto-block-lights]].

Что НЕ виновато: bake 1.4мс (кэш/бюджет/cull теней из [[project-shadow-bake-perf-audit]] работают), flush 0.05мс, сам ImGui 0.5мс (панель дёшева — sourceList перебирает только ручной LightScene, не 2000 авто). Вынос UI в отдельный поток НЕ поможет — render-тред ждёт GPU, отдельный поток делит GL-контекст и упрётся в тот же present.

Рычаг 2 РЕАЛИЗОВАН + ИЗМЕРЕН + ЗАКОММИЧЕН + ПОРТИРОВАН (2026-06-22): правка в AutoLightManager (redactor-only, не lockstep) — feed-кэш в nearest(): пересортировка ТОЛЬКО при смене набора (setGeneration++ на add/evict/clear) / росте cap / сдвиге камеры >2 блоков (FEED_RESORT_DIST2=4.0), иначе возврат кэшированного feed. Корректно: auto-light ИНСТАНСЫ стабильны (поля живые через скан), кэшируется только порядок-ближайших. Замер до/после: collect стоя 12.5мс -> 0.02мс (~600×); медленно идёшь ~1.4мс; быстрый облёт = полный сорт ~раз/кадр (~32мс в плотной сцене — НЕ регресс, старый код платил всегда). Остаток для облёта = bounded-selection O(n) вместо O(n·log n) (НЕ сделано — открытый фоллоу-ап). Закоммичен на ВСЕ 5 веток (cherry-pick чистый, файл идентичен): main f5ff760, port/1.20.1 c314595, port/1.21.1 b6df14a, port/1.21.4 0db1fbe, port/1.21.11 eda28d3. Компиляция main+1.21.11 OK. Чистый jar задеплоен в Prism (mods/irl-redactor-1.20.4.jar). НЕ запушено (origin = quaIett/irl-editor). Диагностика (diag/FrameProfiler + инструментация 3 файлов + build.gradle runClient-флаг) ОТКАЧЕНА — код профайлера сохранён в этой памяти выше, вернуть в 1 шаг для пункта 1.

Рычаг 1 (GPU-cull/LOD/дефолт autoLightMax) НЕ начат — корень лага.
