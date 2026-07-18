---
name: plan-shadow-bake-track
description: "Bake-трек: Ф0 (профайлер-разбивка бейка) РЕАЛИЗОВАНА+ПРОВЕРЕНА 2026-07-18, НЕ ЗАКОММИЧЕНА — ждёт таблицы юзера из реальной сцены; дальше по данным: C10 per-face block-cull, per-face гранулярность Point Pyramid/EVSM, BBS-probe статики. Имплем-детали Ф0 и рекон-якоря внутри."
metadata: 
  node_type: memory
  type: project
  originSessionId: 1a47d7b9-f46d-49f3-a5ce-79b7f33d8d56
---

# Bake-трек: разбивка и оптимизация бейка теней

## СТАТУС 2026-07-18 (сессия 4): Ф0 РЕАЛИЗОВАНА + e2e PASS + ЗАКОММИЧЕНА (core 4e4e490 / addon 06d7de9)
- **Core (irl-core 4e4e490)**: NEW ShadowBakeProbe (интерфейс section/counter); ShadowEngine.installBakeProbe/bakeProbe (volatile, null=off); ShadowBaker: probeSection/probeCount + секции по швам bakeInner (bake-spot → bake-spot-filter → bake-point → bake-point-filter → bake-tail) + счётчики (sp/pt.bake.t0-2 per-tier через tierForIndex, sp.copy, sp.dyn, sp.clear, pt.copy.f, pt.dyn.f, pt.clear.f); 4 фильтр-класса: counter pyr.sp/evsm.sp/pyr.pt/evsm.pt = bitCount(mask) в flushDirty после early-out'ов.
- **Addon (06d7de9)**: VlProfiler.switchPass (endPass+beginPass — сиблинг-переключение, нестинг GL_TIME_ELAPSED невозможен) + counters-окно (лог-строка "[irlite] bake: k v | … | N frames" + HUD-ряды по 4) + derived "bake X ms" (Σ sumNs bake-* / max samples, печатается при ≥2 сегментах); PASS_BAKE="shadow-bake"→"bake-head" (голова = collect/prioritize/beginBake); IrliteClient ставит probe только при VlProfiler.ENABLED. Редактор probe НЕ ставит → core no-op.
- Ревью-воркфлоу 13 агентов: 1 подтверждённая (off-by-one кадров первого окна — починена переносом windowFrames++ после flush), 3 рефьюта (в т.ч. «копии неотделимы от дро по GPU-времени внутри bake-spot/point» — рефьют: счётчики дают атрибуцию).
- Сборки зелёные (core publish → пурж loom → addon → editor). E2E quickplay Testing PASS: все 6 сегментов в "[irlite] gpu:", bake 0.79 ≈ Σ сегментов, счётчики консистентны (sp.copy=sp.dyn=pyr.sp=evsm.sp=frames — оверлей-цепочка), первое окно точное (17=17), stuck-query/GL-ошибок нет. Уже на спавн-сцене: bake-spot-filter 0.69 ms vs дро 0.09 ms — фильтры доминируют.

## NEXT
1. **Гейт Ф0 = прогон юзера в реальной сцене** (та же, где bake 3.4-4.0 ms): -Dirlite.profileVl=true, снять "[irlite] gpu:"+"[irlite] bake:" → таблица какой из [spot-filter | point-filter | копии | дро] доминирует steady → выбор Ф1/Ф2.
2. Дальше фазы ниже по данным Ф0.

## КАРТА ВИНОВНИКОВ (рекон 2026-07-18, актуальный код ПОСЛЕ atlas-merge; аудит-якоря частично устарели)
**Steady 3.4–4ms — цепочка оверлея, вне таксономии аудита:** все BBS-кастеры динамические (энтити/реплеи by design; модел-блоки ЖЁСТКО isStatic=false — INVARIANT 2 в IRLiteBbsCasterSource ~L424-430, «анимированный модел-блок с isStatic=true заморозил бы тень»; modelBlockHash = мёртвый код с TODO Ф3 Open Q1). Любой актёр в радиусе лампы → overlay КАЖДЫЙ кадр: copyStaticToLive + dyn-дро + **markDirty на ВЕСЬ тайл/блок → Pyramid+EVSM flushDirty каждый бейк**. Цена фильтров: spot-тайл ≈ 30+ compute-дисп. (convert + blur H/V + mip-цепочка ~10-11 lod с ре-блюром, батч по lod); point — гранулярность «весь блок»: PointShadowPyramid.markDirty(block), ВСЕ диспатчи z=6 (все 6 граней), PointShadowEvsm layer=localBlock*6+face тоже z=6 → **одна динамическая грань = полный 6-гранный ребилд MSM+пирамиды каждый кадр**. Бюджеты НЕ покрывают оверлеи/копии/фильтры вообще (ShadowBaker L242-43 «Dynamic overlays and static->live copies are NOT counted»).
**Спайки ~320ms — C10 (единственный открытый пункт аудита):** статик-бейк рисует весь per-light блок-VBO ×6 граней БЕЗ пограневого отсечения блоков (renderBlocksDepth; куллятся только кастеры через renderInRangeFace/shortFaceMask).

## РЕКОН-ЯКОРЯ (content-анкеры; core = irl-core/src/main/java/org/qualet/irl/light/shadow/)
- Поток: FramePipeline.frame() → ShadowBaker.bake (L127) → bakeInner (L331): collect (MAX_OCCLUDERS=128, SoA) → spot-петля (L406-798) → SpotShadowPyramid/Evsm.flushDirty (L801-02) → re-init бюджета (L811) → point-петля (L818-1177) → PointShadowPyramid/Evsm.flushDirty (L1180-81). Три ветки per light: legacy full / pure-static (бейк только при dirty) / overlay.
- Overlay-триггер: dynamicInRangeScratch из scanInRange (spot — после cone-cull; point — любой в сфере) + 1 хвостовой кадр wasDynamic. Spot overlay: copyStaticToLive(tile) + renderInRangeCone(CASTERS_DYNAMIC) + markDirty tile (L747-778). Point overlay: copyMask = dynNow|lastFaceDynamic → пограневые копии; dyn-дро с per-face skip (L1127-31 — ЕДИНСТВЕННАЯ петля со скипом граней); markDirty ВСЕГО блока (L1162-63).
- 6-гранные петли: L946 (no-cache), L985 (pure-static), L1072 (overlay static), L1127 (overlay dyn — со скипом), L1148 (no-static clear: «vacated face would keep a phantom shadow»). Статик всегда begin/clear/renderBlocksDepth все 6.
- sphereTouchesFace (L1879-93, k=r·√2) считается раз на окклюдер в scanInRange (point, L1743-53) → shortFaceMask[s], OR-агрегат только для динамики (dynFaceMaskScratch L1769). Для face-скипа статик-дро НЕТ агрегата по всем кастерам. Per-face изоляция стоимости есть: beginPointFace = FBO+viewport/scissor/clear на грань (ShadowRenderer L193-246), glGet-снапшот 1 раз на бейк (savePassState L915-949).
- Копии: DepthTileAtlas.copyStaticToLiveRect = 1 glCopyImageSubData (L255); point-блок = один прямоугольник 3f×2f.
- Классификация: oStatic[]/ostatichash[] (ShadowBaker L59-69); статик-контент = ТОЛЬКО мир-блоки (BlockShadowCache instance-identity). Память addon-shadows «static = CASTER_MODEL_BLOCK» — УСТАРЕЛА (ядро умеет, аддон хардкодит all-dynamic).
- Бюджеты: staticBakeBudget (deferrable) + mandatoryBakeBudget (max(1,budget), ре-арм per петля L811); BAKE_FORCED не троттлится (cold start 30×6 — принятый лимит из fix-shadow-slot-rank-stability).
- Профайлер: GameRendererLightMixin (renderWorld HEAD) брекетит ВЕСЬ FramePipeline.frame() как PASS_BAKE (collect/prioritize без GL — мерится только GL бейка). **Нестинг НЕВОЗМОЖЕН** (один GL_TIME_ELAPSED; beginPass дропает сэмпл при activeQuery!=-1) → суб-брекеты ТОЛЬКО сиблингами: bake-spot / bake-spot-filter / bake-point / bake-point-filter по швам bakeInner (петли и flushDirty-пары). VlProfiler живёт в аддоне, ShadowBaker в core → нужен core-side хук (статический begin/end-колбэк на ShadowEngine, install-паттерн как у ShadowConfig/ShadowCasterSource). F3/GlTimer-гарды уже центральные в beginPass (pre-check GL_CURRENT_QUERY, recycle, STUCK_FRAMES=120) — сиблинги наследуют бесплатно. Счётчики частично есть под -Dirlite.profileShadows (profSpot/PointBakes/Overlays, demand/behind, печать L1602-08); НЕТ: per-tier срезы (tileTier/tierForIndex), faces baked/copied, pyramid/EVSM dirty-counts. Каналы вывода: лог (окно 1с, top-12), чат (итог свипа), HUD (hudLines + HudRenderCallback при ENABLED); Stat держит только ns sum/max/samples — счётчикам нужна мини-доработка.

## НЕ ПЕРЕОТКРЫВАТЬ (вердикты аудита 2026-06-16/17, подтверждены юзером)
- point-static-bake-all-6-faces — РЕФЬЮТ: мир-блоки достают во все грани; скип граней статик-бейка валиден ТОЛЬКО для caster-only ламп (blocks.isEmpty(), с clear-каветом первого бейка/освободившейся грани) или ПОСЛЕ C10-партиционирования блоков по граням.
- motion-gate-overlay — УДАЛЁН по решению юзера: поза через внутренности ванильного LivingEntity несовместима с BBS. Любой freeze-сигнал — ТОЛЬКО BBS-совместимый probe (Ф3 Open Q1, modelBlockHash готов как каркас) и ТОЛЬКО с гейтом юзера.
- Tier-1/2 аудита ВСЕ сделаны (face-маска оверлея, батч-флаш кастеров, cutout-VBO, бюджет, sphere-exact инвалидация) — не дублировать.

## ФАЗЫ
**Ф0 — разбивка бейка профайлером (гейтит всё).** Core: пара статических колбэков на ShadowEngine (install из аддона, no-op по умолчанию). Addon: 4 сиблинг-брекета по швам bakeInner + счётчики окна (tiles/faces baked/copied per type+tier, pyramid/EVSM dirty-tiles) в существующие каналы лог/HUD; активация тем же -Dirlite.profileVl=true (или отдельным -Dirlite.profileBake). Гейт: таблица юзера из реальной сцены (та же, где 3.4-4ms) → какой из [spot-filter | point-filter | копии | дро] доминирует steady.
**Ф1 — C10 per-face block-cull (известный, спайки).** Партиционировать per-light блок-VBO по граням (six index-ranges / 6 под-VBO; тест = AABB-vs-face-frustum как sphereTouchesFace). Открытый вопрос аудита: eviction VBO при тогле шейдеров (retainBlockVbos(liveIds) — проверить вызов). НЕ cherry-pick на 1.21.11 (raw-GL переписка). Профайлер до/после обязателен (спайк ~320ms).
**Ф2 — per-face гранулярность Point Pyramid/EVSM (кандидат №1 по steady, ПОСЛЕ данных Ф0).** markDirty(block) → markDirty(block, faceMask); диспатчи z=6 → по битам маски (или z=1×грань); EVSM layer-адресация уже per-face (localBlock*6+face) — менять только гранулярность dirty и dispatch. Инвариант: пропавшая/замёрзшая тень на любой грани = блокер; хвостовой кадр wasDynamic и clear-каветы сохранить.
**Ф3 — статика модел-блоков через BBS-probe (гейт юзера ОБЯЗАТЕЛЕН).** Open Q1: безопасный признак «форма не анимируется» из BBS-данных (НЕ ванильных) → isStatic=true + staticHash=modelBlockHash (каркас готов, dead code). Снимает overlay-триггер с неподвижных декораций → лампы без живых актёров выпадают из ежекадровой цепочки целиком. Риск: замороженная тень при пропущенной анимации — протокол ретеста с анимированными формами обязателен.
**Опционально после Ф0 (если данные покажут):** cadence оверлей-фильтров для дальних/мелких ламп (фильтры раз в N кадров при неизменном dyn-наборе), lazy-lod EVSM (глубокие lod реже).

## ПРОЦЕСС (готчи всех сессий 2026-07-17→18)
- JAVA_HOME = C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot; gradlew ТОЛЬКО из Git Bash (PowerShell режет -Pmc=1.20.4 до «-Pmc=1»).
- Core-правка → publishToMavenLocal → ПУРЖ loom-кэшей ОБОИХ модов: addon .gradle/loom-cache/remapped_mods/remapped/org/qualet/irl-core-a73a62ac/1.2; editor .gradle/loom-cache/remapped_mods/net_fabricmc_yarn_1_20_4_1_20_4_build_1_v2/org/qualet/irl-core/1.2. Сборки СТРОГО последовательно addon→editor (параллельные loom-билды одной MC = гонка ~/.gradle fabric-loom кэша). Лок «файл занят» = осиротевший демон → gradlew --stop в том репо.
- Рантайм: runClient -Pmc=1.20.4 фоном, лог run/runclient-console.log; автотест-сцена: -Pquickplay="Testing" -PclientJvmArgs="-Dirlite.profileVl=true". Grep из репо молчит в gitignored Shadres/ — абсолютные пути (тут не нужно: GLSL не трогаем).
- Коммиты чекпоинтами по подтверждению; память → repo memory/ → отдельный memory-коммит; MEMORY.md < ~17.5KB.
- Редактор разделяет ядро — после core-правок пересобрать и его; UI-ручек треку не нужно (профайлер-каналы уже есть).

Связь: [[project-shadow-bake-perf-audit]] (канон-таксономия, вердикты), [[plan-vl-profiler]] (инструмент+готчи GlTimer/F3), [[fix-shadow-slot-rank-stability]] (rank/spare-инварианты бейка — НЕ сломать), [[plan-point-shadow-atlas-merge]] (актуальная атлас-архитектура), [[addon-shadows]].
