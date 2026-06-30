---
name: project-shadow-seam-phase4-lockstep
description: "✅ Ф4 ВЫПОЛНЕНА (2026-06-17, option C+): механически-проверяемый lockstep MC-типизированной оркестрации теней между IRLite и redactor-main без физ.экстракции. Верификатор irl-core/tools/verify-shadow-lockstep.py диффит 14 файлов после нормализации пакета/config-класса/source-impl и фейлит exit-1 при дрейфе. Поймал РЕАЛЬНЫЙ дрейф в ShadowCasterSource.java (per-mod javadoc-ссылки) — починено. Commits: irl-core@f0c182a + IRL-redactor@40b0f53 + IRLite@fd506ad. Опции A (Loom→Loom) и B (Stonecutter) отклонены тут — A блокирована pre-work'ом миграции redactor-main на org.qualet.irl.light.LightRegistry + Loom 1.9↔1.15.5 трением; B = по плану это Ф5. Доклад: irl-core/docs/shadow-orchestration-lockstep.md. Строится поверх [[project-shadow-seam-phase3-impl]] + [[plan-shadow-seam-refactor]] (Ф4)."
metadata: 
  node_type: memory
  type: project
  originSessionId: 16d38abd-7219-4f5d-90b9-26c73f8fdadf
---

# Ф4 — Lockstep verifier (option C+)

Завершает [[plan-shadow-seam-refactor]] Ф4: единый источник правды для MC-типизированной оркестрации теней БЕЗ физического шаринга. Опирается на [[project-shadow-seam-phase3-impl]] (Ф3 привёл оба мода к одинаковой оркестрации за швом).

## Решение: C+ (копия-в-синхроне с механическим верификатором)

Опция A (общий Loom-модуль `irl-shadow`) отклонена пользователем после grounded-замера дрейфа:
- Loom 1.9 (IRLite) ↔ Loom 1.15.5 (redactor) — composite includeBuild с трением.
- redactor-main НЕ на `org.qualet.irl.light.LightRegistry` (свой LightRegistry) — pre-work миграции бы потребовался.
- MC-типы → не лезут в plain-Java `irl-core`.

Опция B (Stonecutter) — это Ф5 (мульти-версия 1.20↔1.21), не Ф4.

Опция C+ выбрана: оркестрация **физически в каждом репо**, но lockstep-парность мехpiratically проверяется. Цена «единого источника правды» — запуск скрипта; цена A — недели работы по pre-work.

## Замер дрейфа (precondition Ф4, 2026-06-17)

После Ф3 14 файлов оркестрации байт-идентичны между IRLite↔redactor-main после нормализации пакетов:
- **12 файлов: 0 строк diff** (Renderer, SpotlightDepthAtlas, PointShadowArray, и т.д.)
- **ShadowBaker: ~10 строк** реального дрейфа, 3 источника: `LightConfig`↔`IrliteConfig` (8 сайтов), `LightRegistry` import-путь, `SOURCE` инстанс (`new RedactorEntityCasterSource()`↔`new IRLiteBbsCasterSource()`).
- **ShadowCasterSource: 8 строк** javadoc-дрейфа от Ф2/Ф3 (per-mod {@link} в одном комментарии) — ПОЙМАН верификатором, ПОЧИНЕН (унификация javadoc, оба файла теперь идентичны).

Все эти дрейфы — структурно ожидаемые (различающиеся per-mod идентификаторы) кроме javadoc, который был случайным регрессом.

## Артефакты

- `irl-core/tools/verify-shadow-lockstep.py` (Python 3.9+) — диффер с нормализатором; запуск из любого репо: `python <BBS>/irl-core/tools/verify-shadow-lockstep.py`; exit 0=PASS, 1=DRIFT, 2=setup-error; env vars `IRL_REDACTOR_DIR`/`IRLITE_DIR` для override.
- `irl-core/docs/shadow-orchestration-lockstep.md` — контракт: какие 14 файлов в lockstep, какие 2 per-mod (RedactorEntityCasterSource, IRLiteBbsCasterSource), список allowlist-подстановок с обоснованием, инструкция «что делать когда верификатор красный» (применить парный edit и повторить, НЕ ослаблять верификатор), как расширять lockstep-set.

## Lockstep-set (14 файлов под shadow/)

`BlockShadowCache`, `BlockShadowCollector`, `BlockShadowEntry`, `CasterType`, `ImmediateOccluderBatch`, `IRLShadowQuality`, `OccluderBatch`, `OccluderSink`, `PointShadowArray`, `ShadowBakeState`, `ShadowBaker`, `ShadowCasterSource`, `ShadowRenderer`, `SpotlightDepthAtlas`.

Per-mod (НЕ в lockstep, верификатор проверяет «есть у владельца + НЕТ у другого»): `RedactorEntityCasterSource` (redactor), `IRLiteBbsCasterSource` (IRLite).

## Allowlist-подстановки нормализатора (порядок — длина prefix-а ↓)

| было | стало | почему |
|---|---|---|
| `org.qualet.irlredactor` | `MOD` | корневой пакет redactor |
| `qualet.irlite.client` | `MOD` | client-пакет IRLite |
| `org.qualet.irl` | `MOD` | irl-core (IRLite использует irl-core LightRegistry) |
| `qualet.irlite` | `MOD` | mixin/non-client пакеты IRLite (для javadoc-ссылок) |
| `LightConfig`/`IrliteConfig` | `Cfg` | per-mod статический config-холдер |
| `RedactorEntityCasterSource`/`IRLiteBbsCasterSource` | `SourceImpl` | per-mod seam impl, инстанцируемый в `SOURCE` |

Plus: `package` и `import` строки выкидываются. Всё прочее различие → DRIFT.

## Коммиты

- irl-core@f0c182a — добавлены `tools/verify-shadow-lockstep.py` + `docs/shadow-orchestration-lockstep.md`
- IRL-redactor@40b0f53 — унификация javadoc в `ShadowCasterSource.java` (drop `{@link RedactorEntityCasterSource}`)
- IRLite@fd506ad — унификация javadoc в `ShadowCasterSource.java` (drop `{@link IRLiteBbsCasterSource}`)

## Финальное состояние

`python irl-core/tools/verify-shadow-lockstep.py` → **PASS: all 14 orchestration files are in lockstep.** Exit 0.

Адверсариально подтверждено: подмена `MAX_OCCLUDERS=32→64` в одном моде → DRIFT с unified-diff и exit 1; ревёрт → PASS exit 0.

## Что осталось / не входит

- **Port branch (1.21.11) НЕ в lockstep-set.** redactor port/1.21.11 имеет raw-GL переписку ShadowRenderer (API 1.21.5+); унификация с main через Ф5 Stonecutter **СНЯТА** — порт живёт отдельной веткой, lockstep-верификатор покрывает реал-модельные линии (main + port/1.21.1). (Технически шов изолировал BBS-пути, так что Stonecutter был бы возможен — но в нём нет нужды.)
- **Pre-commit hook НЕ установлен.** Drift детектится, не предотвращается. Тривиально добавить `python ../irl-core/tools/verify-shadow-lockstep.py` в `.git/hooks/pre-commit` каждого мода, если ручной запуск окажется недостаточным.
- **Builds не запускались** на этом коммите — единственная code-правка (`ShadowCasterSource.java`) — чистый javadoc; `grep -n "doclint\|Werror"` в обоих `build.gradle` пусто, javac javadoc не процессит. Риск =0.

## Гейт для пользователя (опционально)

- Запустить `python irl-core/tools/verify-shadow-lockstep.py` своими руками — должен PASS.
- (Опционально) сделать тестовый правку оркестрации только в одном моде — увидеть как верификатор это репортит.
- (Опционально) добавить pre-commit hook в оба мода.
