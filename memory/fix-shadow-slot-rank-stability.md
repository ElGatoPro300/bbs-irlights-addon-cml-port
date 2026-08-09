---
name: fix-shadow-slot-rank-stability
description: "Фикс «прыгающих» теней при спросе > пула слотов — rank-стабильность к повороту камеры + spare-режим; код в irl-core ShadowBaker, НЕ закоммичен, ретест юзера pending"
metadata: 
  node_type: memory
  type: project
  originSessionId: 75748b97-b06a-4172-9d58-dd77b603b151
---

# Fix: rank-стабильность теневых слотов + spare-режим (2026-07-17)

Баг (репорт юзера, подтверждён его рантайм-тестом): при >30 поинт-ламп тени пропадали/«прыгали» между источниками при повороте камеры; при 25 — стабильно. Диагноз (34-агентный workflow, все механизмы верифицированы построчно): (1) behind-camera cull стоял ДО потребления ранга → поворот пересдавал ранги всех ламп через границы тиров/пула; (2) лампа с рангом ≥30 мгновенно теряла тень (tile −1) при живом свете.

## Реализовано (всё в ShadowBaker.java irl-core, спот+поинт зеркально; ветка optimization/octahedral-point-shadows, НЕ закоммичено, поверх незакоммиченного atlas-merge)
1. **Rank-стабильность**: behind-cull больше не пропускает потребление ранга (`boolean behind`, ветка после rank++). Ранг = чистая функция ПОЗИЦИИ камеры. Behind-лампа: keep-alive стампа владеемого тайла только при `desired != TIER_NONE && desired <= lastTier` (не пинить тайл лучше заслуженного), без бейка/публикации.
2. **pastPool-баунд**: Schmitt-удержание за ВСЕМ пулом (raw=TIER_NONE, desired=lastTier через DEMOTE_MARGIN) тикает общий contentionHold (и behind); после cap → desired форсится в TIER_NONE. `contentionHold.remove` после acquire гейтится `!pastPool`.
3. **SPARE-режим** (ключевое, идея из адверсарного ревью): видимая лампа с desired==TIER_NONE → `acquireSpareTile`: owner-match / free (worst-tier-first) / реклейм age>STALE_FRAMES; НИКОГДА не крадёт живой слот; стамп `frameIndex-1` («вечно на грани протухания») → заслуживающая лампа (итерирует раньше по рангу) крадёт штатно в момент реального спроса. Тень умирает ТОЛЬКО при фактической краже слота. Живой spare-держатель наблюдается age ровно 2 (=крадаем in-pool, НЕ реклеймим spare) — математическая гарантия отсутствия spare-vs-spare пинг-понга.
4. **releaseOldTile = pool-scan**: освобождает ВСЕ owner[t]==id кроме myTile (лечит сироту через SHADOW_PENDING-без-lastTile → dual ownership → чужой steal пуржил живое состояние; дыра существовала и в закоммиченном 700b60c).
5. **holdCap(id)** = CONTENTION_HOLD_FRAMES + (id & 3): джиттер против синхронного истечения hold-ов пачки вытесненных ламп (фриз-хитч от пачки FORCED-бейков).
6. **Профайлер** `-Dirlite.profileShadows=true` (флаг СУЩЕСТВУЕТ, ShadowBaker:272): добавлено `demand: spot X/64 (behind Y), point X/30 (behind Y)` (max за окно).
7. collectBlocks пропускается для behind-ламп при entInRange>0 (rank-нейтрально).

## Верифицировано (3 workflow-раунда, 51 агент суммарно)
Foreign-w инвариант выдержал все атаки (публикация всегда со стампом ≥ fI−1; steal требует age≥2, spare-реклейм age>2; spare-претенденты строго после in-pool по итерации). Сходимость за O(1) кадров, ноль осцилляций; поворот на месте в сцене юзера меняет НИЧЕГО; новичок ранга 3 получает слот ≤12 кадров.

## Известные принятые лимиты (pre-existing, НЕ фиксились)
- Mid-tier Schmitt promote-starvation: при статичной камере новичок может застрять в худшем тире (маржа-держатели не отдают лучший тир) — качество (512 vs 1024), не пропадание.
- BAKE_FORCED обходит C2-бюджет в all-dyn сценах (cold-start 30×6 фейс-бейков одним кадром) — pre-existing дизайн overlay-пути.
- Слот исчезнувшей лампы в недогруженном пуле не освобождается (bounded leak ≤94, цена sticky-дизайна для bone-ламп).

## Статус
ЗАКРЫТ 2026-07-17: ин-гейм ретест юзера PASS («стало намного стабильнее», план закрыт им явно). Builds PASS (core publishToMavenLocal + addon -Pmc=1.20.4, JDK 21 = Eclipse Adoptium jdk-21.0.11.10). Тест шёл на живом atlas-merge коде (CR-пак) — I5 из [[plan-point-shadow-atlas-merge]] фактически закрыт тем же тестом. Коммит-чекпоинт: по подтверждению ([[commit-checkpoints]]); дерево содержит atlas-merge + этот фикс вперемешку (ShadowBaker трогали оба трека) — разделить нельзя, коммитить вместе. Связано: [[plan-shadow-lod-tiers]].
