---
name: reference-edit-routing-by-area
description: "КАРТА что-где менять (читать ПЕРВОЙ при «поменяй X» / «добавь Y»). 3 репа (IRLite BBS-аддон + IRL-redactor standalone + irl-core common): патчер-движок и std430 light в irl-core; тени = оркестрация в irl-core за швом ShadowCasterSource + per-mod caster-impl; UI/гайды/gizmo только в redactor; .irlights = IRLite owner -> redactor sync. Глубже: [[project-irl-sync-strategy]] (shareability), [[plan-irl-core-shadow-extraction]] (шов теней)."
metadata:
  node_type: memory
  type: reference
  originSessionId: 16d38abd-7219-4f5d-90b9-26c73f8fdadf
---

Карта «что-где менять» (routing-by-area). Читай эту память ПЕРВОЙ, когда пользователь говорит «поменяй X / добавь Y» без указания проекта. Здесь — таблицы маршрутизации; за деталями архитектуры иди в [[project-irl-sync-strategy]], правила теней в [[plan-irl-core-shadow-extraction]].

Три репа:
| репо | язык | что внутри | как потребляется |
|---|---|---|---|
| irl-core | per-version Loom-модуль (MC-typed; был plain-Java до 2026-06-25/Ф2-shadow) | патчер-движок + light SSBO/Registry + оркестрация теней (light.shadow) | publishToMavenLocal (remapped intermediary) -> modClientImplementation + include (JiJ). Composite includeBuild СНЯТ. |
| bbs-irlights-addon | Fabric (Loom 1.9 master / 1.15 port-1.21.1) | BBS-аддон: свет от BBS Form/Film/Morph, тени с BBS-кастером | jar в mods/ |
| irlights | Fabric (Loom 1.9 main / 1.15 port-1.21.1 / 1.14 port-1.21.11) | standalone ImGui-редактор того же света + патчер-UI | jar в mods/ |

Версии × присутствие модов (актуально 2026-06-18):
| MC | irl-core | аддон (BBS) | редактор |
|---|---|---|---|
| 1.20.x (1.20.1/1.20.4) | да | да, master (универс. jar -Pmc) | да, main (1.20.4) + port/1.20.1 |
| 1.21.1 | да | да, port/1.21.1 — ФИНАЛ для аддона | да, port/1.21.1 |
| 1.21.11 | да | НЕТ, BBS не существует на этих версиях | да, port/1.21.11 (редактор-онли) |

Все три версии-линии потребляют общий irl-core (Ф2-миграция закрыта на ВСЕХ линиях redactor: main@348261b, port/1.21.1@77de503, port/1.21.11@1093f64). BBS-аддон остановлен на 1.21.1 — дальше (1.21.11+) только редактор.

Папки переименованы 2026-06-18: IRLite->bbs-irlights-addon, IRL-redactor->irlights (имена модов в прозе сохранены).

Патчер:
| Что меняешь | Куда | Файлы / якоря |
|---|---|---|
| Логика parse/validate/apply | irl-core | org.qualet.irl.patcher.{IrlPatchParser, IrlPatchApplier, PatchEngine, IrlPatch, PatchResult, PatchLibrary, Shaderpacks, Patcher} |
| Биндинг к окружению (gameDir / Iris API / openFolder) | per-mod host | IRLite -> BbsPatcherHost (UIUtils, BBS Iris-аксессоры) • redactor -> RedactorPatcherHost (Util.getOperatingSystem().open, MC Iris API) |
| Содержимое .irlights (ops в shaders.properties, новые паки) | только IRLite (источник правды) | bbs-irlights-addon/Shadres/Original\|Modification/<pack>/ + bbs-irlights-addon/tools/gen-<pack>-patch.ps1 -> артефакт bbs-irlights-addon/patches/<pack>.irlights. Затем синк в redactor: irlights/tools/copy-patches.ps1 (одно-направл.). |
| UI-панель патчера | per-mod | IRLite -> BBS UI • redactor -> org.qualet.irlredactor.editor.PatcherPanel (ImGui) |
| .gitattributes для .irlights | оба мода (* text eol=lf) — пресечь EOL-дрейф |

После правки в irl-core -> gradlew publishToMavenLocal в core -> пересобрать оба мода с --refresh-dependencies (composite includeBuild СНЯТ в Ф2-shadow: core теперь MC-typed Loom-модуль из mavenLocal; версия 1.0-obt не-semver => нужен refresh, иначе берётся кэш; clean обязателен после переезда файлов).

Пробел: gen-photon-patch.ps1 отсутствует (5/6 паков покрыты генератором, photon руками). Не пытаться авто-регенерить photon — он уникален.

Тени (Ф2-shadow 2026-06-25 — теперь в irl-core, см. [[plan-irl-core-shadow-extraction]]):
Оркестрация (14 файлов) переехала в irl-core пакет org.qualet.irl.light.shadow — правишь ОДИН раз в core, оба мода подхватят после core publishToMavenLocal + пересборки (--refresh-dependencies, clean обязателен). Per-mod caster живёт в ТОМ ЖЕ пакете в КАЖДОМ моде. Lockstep-верификатор RETIRED.
| Что меняешь | Куда | Файлы |
|---|---|---|
| Оркестрация бейка (планировщик, dirty-кэш, T1/T2 опт, cull, тайл-аллокация, batch lifecycle) | irl-core (ОДИН раз) | org.qualet.irl.light.shadow.{ShadowBaker, ShadowRenderer, SpotlightDepthAtlas, PointShadowArray, BlockShadowCache, BlockShadowCollector, BlockShadowEntry, ShadowBakeState, IRLShadowQuality} |
| Контракт шва (API каста) + швы Ф2 | irl-core (ОДИН раз) | org.qualet.irl.light.shadow.{ShadowCasterSource, OccluderSink, OccluderBatch, ImmediateOccluderBatch, CasterType, ShadowConfig, ShadowEngine} + irl-core/docs/shadow-caster-seam-spec.md (frozen, 5 инвариантов) |
| Что/как рисовать каст (новый тип, фикс силуэта BBS Form, fix box-blob) + config-делегат | только владелец (per-mod, в пакете org.qualet.irl.light.shadow КАЖДОГО мода; install в client-init) | IRLite -> IRLiteBbsCasterSource + qualet.irlite.client.IrliteShadowConfig • redactor -> RedactorEntityCasterSource + LightConfig.SHADOW |
| Per-light dirty / staticHash (форма-специфика) | только IRLite (IRLiteBbsCasterSource.modelBlockHash(...)) — у редактора всё dynamic, hash=0 |

Lockstep-протокол — RETIRED (Ф2-shadow 2026-06-25). Оркестрация теперь ОДНА копия в irl-core -> синхронить нечего. Правишь в irl-core/.../light/shadow/, gradlew publishToMavenLocal, пересобираешь моды (--refresh-dependencies, clean). verify-shadow-lockstep.py = retired-stub (печатает объяснение, exit 0).

Версионная вилка теней:
- main (1.20.4) + port/1.21.1: тени держат реал-модельный путь (EntityRenderDispatcher.render); 1.21.1 форкнут ОТ main ДО рерайта рендера 1.21.5 -> near-lockstep с main. Аддон-сторона теней = только эти две (BBS до 1.21.1 включительно).
- port/1.21.11 (редактор-онли): тени переписаны на raw-GL (render-API 1.21.5+); ShadowRenderer устроен иначе, энтити = box-окклюдер (позже апгрейд до реал-моделей через capture-queue, см. [[project-port-1211]]). Lockstep с main НЕ покрывает порт.
- Опт-форвард-порт != cherry-pick: T2.2/T2.3 на 1.21.11 пропускаются (raw-GL caster — нет смысла); T2.5/T1.1/T1.2/T1.3/T2.4 портируются hunk-by-hunk. См. [[project-shadow-bake-perf-audit]] (секция OPEN WORK C10; форвард-порт done-лог в _archive/).

Свет (SSBO / Registry / Buffer):
| Что меняешь | Куда |
|---|---|
| LightBuffer (std430 layout, 6×vec4 / 96 байт incl. cookie, binding 7, MAX_LIGHTS=2048) / LightRegistry API / IrisShadersState | irl-core (org.qualet.irl.light.*) — ВСЕ версии обоих модов используют core. Ф2-миграция закрыта на всех линиях redactor -> дубля org.qualet.irlredactor.light.LightRegistry больше НЕТ, правишь в одном месте (core). Контракт = [[addon-light-buffer-ssbo]]. |
| Адаптеры «откуда берём светильники в мире» | per-mod (НЕ shareable): IRLite -> LightCollector + IRLightPositionResolver (BBS Form-аксессоры) • redactor -> LightScene / PlacedLight / LightStore / LightDriver / LightConfig |

Iris-миксины:
| Файл | Где, как |
|---|---|
| SamplerBindingCubeArrayMixin | rename-only различия — по факту шарится; держи per-mod копию (Loom AW требует, чтоб миксин был в репо мода) |
| ProgramSamplersBuilderMixin | версионный — Iris 1.7.2 = 2-arg addDynamicSampler; Iris 1.10.7 (порт) = 4-arg (TextureType, supplier, ()->null, name). Per-mod И per-version, НИКОГДА не шарить. |

Редактор / UI / ImGui:
| Что меняешь | Куда |
|---|---|
| Любой UI редактора, gizmo, popup, локализация ru_ru/en_us | только redactor (org.qualet.irlredactor.editor.*, client.IRLRedactorClient) |
| Replay Mod интеграция (детач от Screen, ввод-миксины) | только redactor; main <-> port разные input-обёртки (1.20.4 raw events vs 1.21.11 record-обёртки MouseInput/KeyInput/CharInput) |
| prod-jar для пользователя | redactor требует include для imgui-java (binding+lwjgl3+natives win/linux/macos), иначе NoClassDefFoundError: imgui/ImColor в prod. См. build.gradle секцию imgui-java. |

Quick rules (запомнить дословно):
- Логика патчера / std430 света -> правишь в irl-core, оба мода подхватят после core+oba mod build.
- Оркестрация теней -> правишь ОДИН раз в irl-core (lockstep RETIRED), оба мода подхватят после publishToMavenLocal + пересборки.
- Что рисуется как тень (BBS-форма / vanilla-модель / box-blob) -> правишь только соответствующий *CasterSource (per-mod).
- Содержимое .irlights -> правишь в IRLite (Shadres + gen-*.ps1), затем irlights/tools/copy-patches.ps1.
- UI / гайды / gizmo / ImGui -> только redactor.
- Iris-миксины -> per-mod, версионные; не пытайся шарить.
- port/1.21.11 для теней — отдельный мир (raw-GL); не «прихватывай заодно» при правке main. port/1.21.1 — наоборот near-lockstep с main (реал-модельные тени).
- Java toolchain: irl-core теперь per-version Loom-модуль (Ф2-shadow): эталон 1.20.4 = Java17 / Loom 1.9 (наистарший Loom среди потребителей — артефакт принимают и Loom 1.15.5-потребители; Gradle 9.2.0). Аддон 1.20.x = Loom 1.15.5; редактор 1.20.x = Loom 1.9. 1.21.1 = Java 21 / Loom 1.15. 1.21.11 = Java 21 / Loom 1.14. (Прежний вердикт «общий shadow-модуль отклонён, тени через lockstep» ОТМЕНЁН — per-version убил Loom-трение; см. [[plan-irl-core-shadow-extraction]].)

Ключевые инструменты:
| Команда | Цель |
|---|---|
| <BBS>/irl-core/gradlew publishToMavenLocal | Опубликовать core (remapped) в mavenLocal — ОБЯЗАТЕЛЬНО перед сборкой модов (свет/патчер/тени в core). verify-shadow-lockstep.py RETIRED (Ф2). |
| pwsh <BBS>/irlights/tools/copy-patches.ps1 | Однонапр. синк .irlights из IRLite в redactor (после регенерации в IRLite) |
| <BBS>/irl-core/gradlew build | Компиляция core без публикации (для потребления модами нужен publishToMavenLocal, см. выше) |
| <BBS>/bbs-irlights-addon/gradlew build -Pmc=1.20.4 --refresh-dependencies | Сборка IRLite 1.20.4 (MC-версионный core => per-MC: -Pmc обязан совпасть с опубликованной core-версией; универсальный jar 1.20.1+1.20.4 БОЛЬШЕ НЕ работает на per-MC core линиях; нужен core-1.20.1 для 1.20.1) |
| <BBS>/irlights/gradlew build | Сборка redactor (текущая ветка = MC-версия: main=1.20.4, port/1.21.1=1.21.1, port/1.21.11=1.21.11) |

Что НЕ трогать без явной просьбы:
- bbs-fs/ — референс-копия BBS, не редактируем (см. [[reference-bbs-fs-not-refreshed]]).
- bbs-irlights-addon/Shadres/ — приватный source-of-truth шейдерпаков, в git игнорится (только patches/*.irlights коммитятся).
- Файлы bbs-irlights-addon/README.md и bbs-irlights-addon/TEMP/ — пред-существующие untracked; не коммитить.
- feedback-no-per-session-branch — не создавать git-ветку под сессию; работать в текущей.
