---
name: project-shadow-bake-perf-audit
description: "Аудит перфа бейкинга теней (2026-06-16, многоагентный, состязательно проверен) — приоритизированный список оптимизаций. ЕДИНСТВЕННЫЙ живой док перфа бейка (канон-таксономия). Все Tier-1/2 done на всех линиях; открыт только C10 per-face block-cull (~320ms спайк)."
metadata:
  node_type: memory
  type: project
  originSessionId: 1a39cd4f-717e-4606-91ea-702c4aef9934
---

ЕДИНСТВЕННЫЙ живой документ по перфу бейка теней (КАНОН таксономии; supersedes архивные IRLite-эра аудиты shadow-bake-refactor-audit, perf-audit-shadow-light-vl). Завершённые план-файлы plan-shadow-bake-perf (все T-тиры на main/1.20.4) и plan-shadow-1211-fwdport-c10 (форвард-порт + энтити/cutout-тени 1.21.11) перенесены в _archive/ 2026-06-29. Все Tier-1/2 СДЕЛАНЫ на всех линиях; единственный открытый остаток = C10 per-face block-cull (~320ms спайк) — см. секцию OPEN WORK ниже.

Аудит производительности системы бейкинга теней (org.qualet.irlredactor.light.shadow.*), 2026-06-16. Многоагентный Workflow, ~93 агента, каждая находка проверена 2 линзами (real = реально ли на hot-path; fix = безопасен ли для инвариантов). GPU-измерение проверялось декомпиляцией реального jar Iris 1.10.7. Tier-1 РЕАЛИЗОВАН+закоммичен (Сессия A, da9c980 на main, 1.20.4). Tier-2 T2.5/T2.4/T2.3 РЕАЛИЗОВАНЫ+закоммичены (Сессия B, 57d1a4c на main, in-world проверено). T2.2 РЕАЛИЗОВАН+закоммичен (Сессия C, 2cb1292 на main, in-world проверено). ВСЕ Tier-1/2 готовы на main(1.20.4). ФОРВАРД-ПОРТ на port/1.21.11 НЕ cherry-pick (состязат. ревью 2026-06-17): T2.2+T2.3 ПРОПУСТИТЬ (порт ShadowRenderer = raw-GL переписка, renderCaster заглушка, cutout-пути нет, MC render-API удалены ~1.21.5, выигрыш=0); портируются hunk-by-hunk T2.5/T1.1/T1.2/T1.3/T2.4-budget-half; главный реальный выигрыш = НОВАЯ per-face block-cull (профайлерный спайк ~320ms = renderBlocksDepth рисует весь VBO ×6 граней без куллинга). Детали+порядок — _archive/plan-shadow-bake-perf.md секция ФОРВАРД-ПОРТ. Система УЖЕ хорошо оптимизирована (sticky-тайлы, two-layer bake, VBO-кэш блоков, per-light dirty-кэш, single GL-state snapshot, cone/face-куллинг) — ниже только НЕпокрытое.

Tier 1 (выигрыш + фикс безопасен, делать первым):
- point-sextuple-scan (ShadowBaker:450-572,921-996) — point-overlay обходит окклюдеры до ~13-19×/кадр; faceHasDynamic+renderInRangeFace дублируют sphereTouchesFace. Фикс: shortlist + 6-битная face-маска в scanInRange. fix безопасен.
- Матричные аллокации (ShadowRenderer:111,148,183,673) — переиспользовать Matrix4f/MatrixStack-скретчи, константы UP_Y/UP_Z. fix ок.
- gpu-1 per-face copyStaticToLive (PointShadowArray:89-104, ShadowBaker:555) — блитит все 6 граней каждый overlay-кадр (до 96МиБ/свет/кадр ULTRA), нужна 1-2. Фикс: копировать грани по маске (faceHasDynamic_now | lastFaceDynamic). fix ок. ДЕЛИТ face-маску с point-sextuple-scan => одна задача.

Tier 2 (выигрыш высокий, фикс требует аккуратности):
- per-caster-immediate-draw-flush РЕАЛИЗОВАН (Сессия C = T2.2, 2cb1292 на main, 1.20.4, in-world проверено): до 6N GPU-флашей/point-свет -> 1/проход. beginCasterBatch/bufferCaster/endCasterBatch+flushCasterBatch; гарды — изоляция упавшего кастера (флаш в одиночку) + пере-выставление currentView/currentProj перед единым флашем (BufferRenderer читает живые матрицы, моб мог испортить). Замечен предсуществующий спайк ~320ms на static-bake-кадрах (НЕ T2.2 — renderBlocksDepth/VBO-аплоады, кандидат Tier-3).
- cutout-not-vbo-cached РЕАЛИЗОВАН (Сессия B = T2.3, 57d1a4c на main): cutout-геометрия в VBO на слой (CUTOUT/CUTOUT_MIPPED), редрав под cutout-шейдером с явными матрицами; заодно убит старый баг «второй cutout-слой гасит тени».
- no-bake-budget РЕАЛИЗОВАН (Сессия B = T2.4): per-frame бюджет полных статик-бейков (LightConfig.shadowBakeBudget=4), first/leave-бейки не откладываются.
- bcc-2 sphere-exact инвалидация РЕАЛИЗОВАН (Сессия B = T2.5): distance-тест центр-блока vs снапнутый радиус в invalidateAt.

Tier 3 (verified low): gpu-2 удвоение VRAM статик-слоем (ULTRA~5ГиБ, не задок.); gpu-3 очистка 96 слоёв по одному (только смена quality); no-distance-lod; bcc-1 ре-walk сферы (только интерактивный драг — лампы статичны); bcc-4 аллокации на miss; frompolar-alloc; registry O(N²); occluder-cap первые-32; skip entity-walk для block-only (fix риск HIGH).

ОТКЛОНЕНО: motion-gate-overlay (УДАЛЁН 2026-06-17 по решению пользователя — pose-сигнатура завязана на внутренности ванильного LivingEntity -> несовместимо с BBS; правки движка теней обязаны оставаться BBS-совместимыми); point-static-bake-all-6-faces (refuted — world-блоки во все грани, пропуск нельзя); configure-per-frame (refuted — дёшев/нужен).

OPEN WORK — C10 per-face block-cull (Сессия E, НЕ начата) — единственный незавершённый перф-остаток теней:
Проблема (подтверждена профайлером -Dirlite.profileShadows=true, спайк ~305-327ms, есть на main(1.20.4) И port/1.21.11): renderBlocksDepth рисует ВЕСЬ per-light VBO мира раз на КАЖДУЮ из 6 граней point-света БЕЗ per-face frustum-куллинга блоков (куллятся только entity-кастеры) -> ~6× лишних вершин+дро. Идея: T1.1-style per-face подвыборка блоков (партиционировать per-light VBO по граням куба / per-face index-диапазоны / 6 под-VBO), рисовать на грань только касающиеся её блоки (тест как sphereTouchesFace/AABB-vs-face-frustum). Опирается на инфраструктуру T1.1 (shortlist/face-mask), уже перенесённую в Сессии D. Профайлер до/после обязателен. Открытый вопрос: onShadersDisabled-хука нет — eviction VBO только через retainBlockVbos(liveIds); проверить что зовётся при тогле шейдеров (иначе VBO-лик). НЕ cherry-pick на 1.21.11 (там raw-GL переписка ShadowRenderer). Полный done-лог форвард-порта T-тиров + энтити/cutout-теней 1.21.11 = _archive/plan-shadow-1211-fwdport-c10.md; done-лог Tier-1/2 на main = _archive/plan-shadow-bake-perf.md.

Связано: [[project-port-1211]], [[project-irlite-base-ported]], [[plan-irl-core-shadow-extraction]], [[addon-shadows]].
