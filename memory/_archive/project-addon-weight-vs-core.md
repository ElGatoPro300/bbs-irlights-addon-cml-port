---
name: project-addon-weight-vs-core
description: "📊 Почему addon-jar (307KB) тяжелее irl-core (70KB) ПОСЛЕ Ф2-миграции: структурно, не след недоделки. Разбор по байтам + единственный реальный недотянутый шов = CookieArray (BBS-free GL, дубль байт-в-байт с редактором → кандидат в core). .irlights-дедуп и IrisShadersState — НЕ выносить (ROI). Дебаты 8 агентов 2026-06-28."
metadata:
  node_type: memory
  type: project
  originSessionId: e86abf29-7155-466a-abfa-248eda454225
---

# Вес addon-jar vs irl-core (разбор + остаток миграции)

Вопрос юзера 2026-06-28: почему `bbs-irlights-addon` весит больше `irl-core` после Ф2 ([[plan-irl-core-shadow-extraction]]), и что ещё не перенесено. Решено дебатами 8 суб-агентов (recon→openings→rebuttals→verdict).

## Измерено (собранные jar, 1.1)
`irlite-1.1.jar` (аддон) = **306 630 B сжато** / 626 895 распак. Состав сжато:
- `.irlights` ×6 шейдер-патчи: **87 459** (28.5%) — контент GLSL-в-DSL, дублируется с редактором
- `assets/irlite/icon.png`: **77 986** (25.4%) — бренд-иконка, немигрируема
- JiJ вложенный `irl-core`: **72 512** (23.6%) — весь core вшит ВНУТРЬ аддона
- свой `.class` аддона: **66 381** (21.6%, 37 файлов) — реальная логика
- json+manifest+license: ~2 300
`irl-core-1.1.jar` = **69 745 B сжато**: свой `.class` 66 537 (39 файлов).
Исходники строк: core **5025** (27 ф) > addon **3302** (37 ф) > redactor 5045 (33 ф).

## Вывод (победа тезиса A)
Разрыв **СТРУКТУРНЫЙ, не недоделка**: (1) аддон by construction несёт core внутри (JiJ) → не может быть легче; (2) ~54% jar = контент (.irlights+icon), ~24% = повторно посчитанный core, только ~22% = своя логика; (3) own-`.class` аддона (66 381) ≈ `.class` core (66 537) сжато, ХОТЯ строк у аддона МЕНЬШЕ — значит тяжёлая логика реально уехала в core. Плотность байткода аддона выше (миксины/BBS-UI).

## Почему остаток в аддоне (3 корзины)
- **(I) Неперемещаемо**: 26 файлов `import mchorse.bbs_mod.*` (forms-модель, рендереры, UI-панели, `IrliteConfig` Value*, `LightCollector` обходит BBS Form/Film/Morph). Шов уже на `LightRegistry.emit*` (в core) — редактор это доказывает BBS-free `LightDriver`. + 12 mixin/Loom-bound (refmap/remap привязаны к репо мода) + Iris/MC-version миксины (`ProgramSamplersBuilderMixin` version-specific addDynamicSampler).
- **(II) Контент по дизайну у аддона**: icon.png + 6 .irlights (движок `PatchLibrary`/`PatcherHost` уже в core, дубль = только payload). См. [[patcher]].
- **(III) Спорные** — см. ниже.

## ⏳ ЕДИНСТВЕННЫЙ реальный недотянутый шов: CookieArray
`CookieArray` (~150 строк GL: `GL_TEXTURE_2D_ARRAY`, R8 512², PBO-guard) **дублирован байт-в-байт** аддон↔редактор; GPU-часть импортит ТОЛЬКО `org.lwjgl.*`/`java.nio` — ноль `mchorse`. К BBS привязан ЛИШЬ адаптер загрузки байтов (`BBSMod.getProvider().getAsset` vs редактор `Files`). Это 2-й GPU-ресурс того же светового контракта (texId матчится в `SamplerBindingCubeArrayMixin`). **Рекоменд. вынос** (ROI высокий, риск низкий; прецедент = `PointShadowArray` оба мода тянут из core):
- core: `org.qualet.irl.light.cookie.CookieArray` = `int resolve(String key, Supplier<byte[]> loader)` + `getGlTextureId/reload/delete/RES/MAX_LAYERS`.
- per-mod адаптер байтов: addon `Link→byte[]` (BBSMod provider); redactor `name→byte[]` (Files) + `available()/dir()` UI-helper.
- после: поправить `SamplerBindingCubeArrayMixin` (`id == CookieArray.getGlTextureId()`).
- мотив: одна копия = один баг (PBO-guard однажды падал `EXCEPTION_ACCESS_VIOLATION`). См. [[project-auto-block-lights]] (cookie-фича) / [[project-spotlight-gobo-cookie-plan]].

## НЕ выносить (ROI отрицательный)
- **.irlights-дедуп через core-JiJ**: снимает ~87KB сжатого, НО инвертирует владение (core начнёт нести client-контент под своим namespace) + унификация CRLF/LF. Делать только при осознанной смене конвенции «контент=IRLite owner».
- **IrisShadersState**: diff = 1 строка `package`, BBS-free, дубль addon↔redactor (~48 строк), НО вынос тащит Iris `compileOnly` в core по всей деп-матрице → теряется свойство «core Iris-free» ради 30 строк. Если дубль раздражает — свести к host-SPI `shadersDisabled()`, не относить класс.
