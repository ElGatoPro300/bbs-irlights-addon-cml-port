---
name: project-shadow-seam-phase1-frozen
description: "✅ Ф1 ЗАМОРОЖЕНА + ✅ Ф2 КОД ГОТОВ (2026-06-17) плана [[plan-shadow-seam-refactor]]. Ф1: контракт шва ShadowCasterSource заморожен (readyToFreeze=true), спека = BBS/irl-core/docs/shadow-caster-seam-spec.md; 11-агентный адверс. воркфлоу нашёл major-дыру в КАЖДОМ из 5 инвариантов (видны только in-world), все свёрнуты. Ф2: шов врезан в redactor main(1.20.4) — 6 новых файлов + ShadowBaker/ShadowRenderer, build зелёный, 6-агентный behavior-diff review = SHIP-WITH-NOTES (0 блокеров, всё инертно на main кроме консервативного INV-5 радиуса). НЕ закоммичено, in-world пользователем НЕ проверено. Ф3 (IRLite) не начата."
metadata: 
  node_type: memory
  type: project
  originSessionId: a6c0810c-4541-4123-858f-a6f38a7815ba
---

# Ф1 заморожена: контракт шва ShadowCasterSource

Продолжение [[plan-shadow-seam-refactor]] (Ф0 подтверждена пользователем «работает»). Ф1 = дизайн/спека, кода не трогали. Гейт плана «аудитор не нашёл блокеров» ПРОЙДЕН: адверсарии нашли дыры, ревайзер их закрыл, остаточных блокеров нет.

**Спека (источник правды):** `BBS/irl-core/docs/shadow-caster-seam-spec.md` — полные интерфейсы + 5 инвариантов + per-variant скетчи + переанкоренные якоря 3 вариантов + changelog аудита. Сырой вывод воркфлоу: `tasks/wwyzixvf0.output` (run `wf_df826ff8-9bd`).

**Базовые коммиты на момент заморозки:** irl-core@59d893a, redactor port/1.21.11@1093f64, redactor main(1.20.4 = КАНОН), IRLite master@9953f31.

## Замороженный контракт (сигнатуры)
- `ShadowCasterSource { collect(ClientWorld, Vec3d camPos, float td, OccluderSink); emitOccluder(Object caster, int type, float td, OccluderBatch) }` — **view/proj НЕ параметры** (мертвы на всех 3 вариантах; читаются из ambient currentView/currentProj).
- `OccluderSink { emitFromBox(caster,type,isStatic,interpXYZ,Box,scale,staticHash) [блессед, СЧИТАЕТ сферу]; emit(...,cx,cy,cz,radius,...) [сырой escape] }` — alloc-free запись в fixed-32 SoA.
- `OccluderBatch` — ОПАК + shared-хуки `mark()`/`terminateRun(view,proj)` для INV-4.
- `CasterType{ENTITY,MODEL_BLOCK,REPLAY}` — НЕЙТРАЛЬНЫЙ холдер (НЕ на ShadowRenderer, чтобы source не импортил GL).
- `ShadowOccluders.drawBlockOccluders(long lightId, List<BlockShadowEntry>)` — блок-каст (cutout+AABB) ВНЕ BBS-шва, общий; кэш по INSTANCE-identity листа.
- SoA: occ/occType/**oStatic(NEW)**/ox/oy/oz/orad/ostatichash, MAX=32, без аллокаций.

## Что нашёл адверсариальный проход (КАЖДЫЙ инвариант имел major-дыру, видную только in-world)
1. **INV-1 матрицы** → СДЕЛАН УСЛОВНЫМ: re-establish = ПОСЛЕДНЕЕ действие перед draw (re-establish на batch-open ЗАПРЕЩЁН как единственный); caster-flush ДО drawBlockOccluders для портящих вариантов; raw-GL порт EXEMPT (флашит на endPass после блоков — это легально, нечего портить).
2. **INV-2 static/dynamic** → РАЗВЯЗАН: `occType` = только draw-arm; `isStatic` boolean (oStatic[]) = темпоральная ось. Анимированный model-block с константным Transform ОБЯЗАН isStatic=false (раньше замерзал).
3. **INV-3 staticHash** → УКРЕПЛЁН: плоская СУММА не инъективна (компенсирующая парная правка/своп членства маскируется). Фолд = avalanche-mix (`^= mix64(h*0x9E3779B97F4A7C15)`) + фолд СЧЁТА статиков; хэш source = full-avalanche по ПОЛНОМУ силуэту вкл translate.
4. **INV-4 исключения** → УСИЛЕН с crash- до run-изоляции: на throw wrapper ТЕРМИНИРУЕТ прогон ДО следующего emit (drain Immediate / rewind raw-буфера к per-caster mark), иначе обломки квада сливаются. «flush ровно раз» = только success-путь.
5. **INV-5 сфера** → ИСПРАВЛЕН: half-edge UNDER-bound (Гаст 4-блока: реальный угол √3≈3.46 vs 2.5 → cull тихо роняет). Радиус = ОПИСАННЫЙ = `0.5*sqrt(dx²+dy²+dz²)*scale + OVERLAP_MARGIN` по post-scale/post-rotate AABB. emitFromBox СЧИТАЕТ структурно.

Критик добавил: дом occType→CasterType; trio batch/scratch/setBaking резолвнут вместе (wrapper владеет setBaking+scratch+try/catch+flush); collect() зовётся ОДИН раз до begin*(); кэш блок-листа по instance-identity.

## Решения (выбраны при заморозке)
- INV-2: boolean `isStatic` (НЕ 4-й тег CASTER_MODEL_BLOCK_DYNAMIC) — чище.
- view/proj выкинуты из emitOccluder (YAGNI; реальная реставрация моделей на порте = больший подъём: setBaking+textured immediate+scratch, вне заморозки).
- OccluderBatch оставлен ОПАК + хуки (не протекают оба бэкенда в шов).

## КАНОН и BBS-завязка
- Канон оркестрации = **redactor main(1.20.4)** (BBS-free, 6 T-опт, не raw-GL). Ф2 режет сюда первым.
- Вся BBS-завязка IRLite = **7 методов** (4 collect-side: collectModelBlocks/collectFilmReplays/getActiveEditorController/modelBlockHash + 3 draw-leaf: drawEntity/drawModelBlock/drawReplay) + диспетчер renderCaster. Остальное — variant-agnostic.

## Ф2 СТАТУС (2026-06-17): ✅ ЗАВЕРШЕНА — закоммичена redactor main@522c1f0, in-world подтверждена пользователем «идентично»
Build зелёный, adversarial review = SHIP-WITH-NOTES (0 блокеров), runClient прогон ~6мин без исключений/краша (3 света ставились+сохранялись). Врезано на ветке redactor `main` (поверх 2cb1292). 6 новых файлов в light/shadow/ (ShadowCasterSource, RedactorEntityCasterSource, OccluderSink, OccluderBatch, ImmediateOccluderBatch, CasterType) + правки ShadowBaker/ShadowRenderer. collect→SOURCE.collect(SINK); bufferCaster→общий враппер emitCaster (владеет scratch-reset + try/catch + terminateRun); drawEntity→источник; flushCasterBatch→flushCasterImmediate(immediate,view,proj); split по oStatic[] (не occType==MODEL_BLOCK); INV-3 avalanche(mix64=splitmix64)+count fold; INV-5 описанный радиус (диагональ). ВСЁ инертно на main (нет статиков) КРОМЕ INV-5 = строго консервативно (orad только растёт, в sig не входит → тень не пропадает, кэш не трогается; это фикс латентного бага). 6 агентов подтвердили behavior-preserving, 0 блокеров, 0 непреднамеренных изменений. ОТЛОЖЕНО (косметика, вне BBS-шва): ShadowOccluders rename (блок-путь = ShadowRenderer.renderBlocksDepth как есть, байты не тронуты).

## Осталось
- **Ф3** (НЕ начата, СЛЕДУЮЩАЯ, по плану = ОТДЕЛЬНАЯ СЕССИЯ — самая рискованная, тяжёлый контекст): IRLite на общую оркестрацию + BBS-impl каста (6 опт даром). Референс засеамленной оркестрации = redactor main@522c1f0 (6 новых файлов + ShadowBaker/ShadowRenderer). ТУТ INV-2/3/4/5 машинерия впервые НЕ инертна (IRLite имеет статик model-blocks), гейт по инвариантам обязателен (движущиеся формы не замёрзли, тени блоков на месте, совпадение с BBS-превью). Агенты плана R7(BBS-каст-impl)/R4(порт оркестрации)/R3+R6. BBS-завязка IRLite = 7 методов (collectModelBlocks/collectFilmReplays/getActiveEditorController/modelBlockHash + drawEntity/drawModelBlock/drawReplay) + диспетчер renderCaster.
- Остаточная latitude импл: точный mix64; реставрация моделей на порте — вне заморозки.
