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

## ГЕЙТ Ф0 ПРОЙДЕН 2026-07-18: замер юзера, тестовая сцена, тени ULTRA, бейк вкл
Steady (окна 17-26 FPS, 114 окон): **bake total 34-42 ms** = кап фреймрейта; **bake-spot-filter 29.5-37.4 ms = ~86% бейка** (Pyramid+EVSM); bake-spot (копии+dyn-дро) 4.4-5.9 ms (~13%); point/point-filter = 0 (в сцене нет point-ламп); deferred2 (VL) 0.14-7.8 по ракурсу. Счётчики: sp.copy=sp.dyn=pyr.sp=evsm.sp=**25/кадр** — 25 спотов ВСЕ в overlay каждый кадр, static-ребейков НОЛЬ (sp.bake только на загрузке: t0=8, t1=17). Cold start: bake-spot max 318 ms (C10-класс спайк, виден инструментом), spot-filter окно 543/667 ms (первый флаш 25 ultra-тайлов / смена качества).
**ВЕРДИКТ: доминирует SPOT-FILTER (не point!)** — Ф2 (per-face гранулярность point Pyramid/EVSM) для этой сцены НЕ рычаг; 86% съедает ежекадровый полный re-filter 25 ultra-тайлов, контент которых меняется только силуэтом актёра.
Кандидаты-рычаги по данным (порядок = ожидаемый выигрыш/риск):
1. **Partial-tile filter**: convert/blur/mip только в bbox dyn-кастеров (+blur-margin) вместо всего тайла — бьёт в корень (у ultra стоимость ~квадратична от размера тайла).
2. **Half-res EVSM на ultra**: фильтр-цепочка на res/2 (EVSM и так пре-блюрен — потери качества минимальны) ≈ −70% фильтра.
3. **Cadence фильтров** (из «опционально»): раз в N кадров при неизменном dyn-наборе — просто, но лаг мягкой тени/пирамиды на анимации = визуальный риск.
4. lazy-lod mip-хвоста — мелочь на фоне blur lod0.
Ф2 (point per-face) остаётся в очереди для point-сцен; Ф1 C10 — спайк подтверждён (318 ms на загрузке), но steady не трогает.

## HALF-RES EVSM ПИЛОТ НА CR: DONE + ЗАКОММИЧЕН 2026-07-18 (core 01160ad / addon 6457312+c913299)
Рычаг после гейта Ф0. Итог той же ultra-сцены: **bake 34-42 → 17.8-18.4 ms, FPS-окна 17-26 → 29-35**; сплит фильтров дал атрибуцию: старые 29.6 = EVSM ~25.3 + pyramid ~4.3; после: **evsm 9.3-9.6 (×2.7)** + pyr 4.3 (не тронута) + дро/копии 4.2-4.5. VRAM −1 GiB, evictions +0, юзер визуально принял.
- **Core**: SpotlightDepthAtlas.evsmShift (1=atlas/2, 2=atlas/4 ТОЛЬКО ULTRA, ставится IRLShadowQuality.apply; setEvsmShift удаляет EVSM-текстуры при смене); SpotShadowEvsm параметризован shift'ом (convert NxN через uniform srcStep, levels=log2(tileSize)-shift+1, все сдвиги lod+shift); ShadowBaker: секция bake-spot-filter → bake-spot-pyr/bake-spot-evsm (+point-близнец).
- **GLSL (ТОЛЬКО CR)**: гейт `evsmRes*2==atlasSize.x` → ratio-aware (`evsmDiv==2||4`), minPenE=MIN_PEN*(div/2) в гейте И smoothstep-блэнде, lod=log2(pen/(2*div)) с капом findMSB(tileRes)-findMSB(div); div-2 бит-идентичен старому. Реген gen-complementary-patch.ps1 + byte-proof PASS; run-пак ComplementaryReimagined_IRLights засинкан вручную.
- **РЕВЬЮ**: 15 агентов, 4 minor (все закрыты), арифметика/лайфсайкл чистые. ГОТЧА byte-proof: апплаер пишет маркер irlite_patched — диффать надо только shaders/.
- **ОТКРЫТО (тираж, по команде)**: остальные 6 паков + бандл редактора (copy-patches.ps1) — на ultra их EVSM молча гаснет в PCF (не баг, деградация до тиража); Prism-инстанс = RE-PATCH (форк CR_IRLights+DOF старого поколения); пирамиду half-res НЕЛЬЗЯ без регена (GLSL texelFetch >>1 без гейта).

## АНОМАЛИЯ «ЛИНЕЙНАЯ ДЕГРАДАЦИЯ» (2026-07-18, ЗАПАРКОВАНА решением юзера)
В ultra-прогоне №1: spot-filter 29.6→430-510 ms за ~80 с при КОНСТАНТНОЙ работе (25 тайлов/кадр, счётчики не менялись), деградация стартует сама (юзер ничего не делал), **F11 (fullscreen→windowed, пересоздание свапчейна) мгновенно сбрасывает к 29.6**, затем цикл начинается снова. RTX 3060 12GB + браузер. Гипотеза №1 residency thrash (ultra спот-стек ~4 GiB: live 1 + static 1 + EVSM 1.4 + pyr 0.7). Добавлена VRAM-телеметрия в VlProfiler (строка "[irlite] vram:", GL_NVX_gpu_memory_info: free + evictions с дельтой; НЕ ЗАКОММИЧЕНА). Прогон №2 аномалию НЕ воспроизвёл (steady free 5.2 GiB, evictions +0). НЕ копать без команды; детектор встроен.

## NEXT
1. **Partial-tile filter** (юзер сказал «идём дальше» после half-res): фильтровать только bbox dyn-кастеров + blur-margin — Java-only (GLSL не видит диспатч-геометрию), режет все 3 компонента остатка (evsm 9.5 + pyr 4.3 + дро 4.4).
2. Тираж ratio-aware гейта на 6 паков + редактор — по команде.
3. Ф1 C10 / Ф2 point — по команде, когда дойдут руки до их сцен.

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
