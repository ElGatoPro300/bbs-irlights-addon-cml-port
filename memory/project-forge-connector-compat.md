---
name: project-forge-connector-compat
description: "Запуск аддона/трилогии на Forge через Sinytra Connector (2026-06-21). Первый блокер найден+починен: Connector beta.48/1.20.1 эмулирует Fabric Loader 0.18.x, а аддон требовал >=0.19.3 (из loader_version) -> FML отклонял мод -> ClassCastException null на ForgeLoadingOverlay. Фикс = хардкод низкого пола fabricloader. Юзер подтвердил: грузится + свет работает."
metadata:
  node_type: memory
  type: project
  mod_scope: IRLite-only
  originSessionId: 3ce1368a-b181-44d8-ac40-04ca538f67af
---

Forge через Sinytra Connector — совместимость.

Направление (2026-06-21): гонять Fabric-моды трилогии (IRLite-аддон + BBS) на Forge 1.20.1 через Sinytra Connector beta.48. Инстанс: C:\prismlauncher\instances\1.20.1\ (Forge 47.4.10 + Connector-1.0.0-beta.48+1.20.1 + Forgified Fabric API 0.92.6 + bbs-2.3-1.20.1 + наши irlite-addon-1.20.x.jar и (избыточный) irl-core-1.0-obt.jar).

Блокер #1 (ПОЧИНЕН) — пол fabricloader:
- Симптом в краш-репорте: java.lang.ClassCastException: null на GameRenderer.m_109093_ при рендере ForgeLoadingOverlay. Это вторично.
- Реальная причина (logs/latest.log): ModLoadingException … mod IRLite (irlite) 1.0-obt requires version 0.19.3 or later of Fabric Loader, but only the wrong version is present: 0.18.x! -> мод в broken state -> рендерер не инициализирован -> ClassCast.
- Механизм: src/main/resources/fabric.mod.json имел "fabricloader": ">=${loader_version}", а gradle.properties -> loader_version=0.19.3. Connector эмулирует Fabric Loader 0.18.x, который ниже 0.19.3 -> отказ.
- Фикс: в fmj аддона захардкодить низкий пол -> "fabricloader": ">=0.15.0" (развязали runtime-требование от dev-loader; dev-зависимость остаётся 0.19.3 через gradle.properties). Пересобрать gradlew build -Pmc=1.20.1, jar build/libs/irlite-1.0-obt.jar -> скопировать в инстанс под именем irlite-addon-1.20.x.jar, и снести кэш mods/.connector/temp/irlite-addon* чтобы Connector перетрансформировал.
- ПОДТВЕРЖДЕНО ЮЗЕРОМ (2026-06-21): грузится + свет РАБОТАЕТ на Forge/Connector. Это единственная правка для запуска под Connector.

Что РАБОТАЕТ под Connector (проверено diagnostics + глазами):
- Все миксины аддона применяются и ремапятся (Connector->SRG): BBS-регистрация форм/настроек, GameRendererLightMixin (renderWorld->SRG, MatrixStack->PoseStack), и Iris-миксины в Oculus (iris.ProgramSamplersBuilderMixin->net.irisshaders.iris.gl.program.ProgramSamplers$Builder, iris.SamplerBindingCubeArrayMixin->...gl.sampler.SamplerBinding). Шейдер-мод на Forge = Oculus 1.8.0 (форж-порт Iris, тот же пакет net.irisshaders.iris.*, 2-арг addDynamicSampler совпал с таргетом мастера).
- IrisShadersState под Oculus детектит пайплайн верно (IrisRenderingPipeline, не Vanilla) -> гейт ACTIVE.
- Raw-GL SSBO binding 7 (LightBuffer) доходит до шейдера; пропатченный пак (ComplementaryReimagined_..._IRLights) грузится, Oculus: Found flag SSBO + iris.features.optional.
- Ложная тревога «нет света» оказалась ошибкой юзера в сцене (ModelBlock без включённой формы света: diag показывал modelBlocks=1, withForm=0), НЕ багом порта. После исправления сцены — свет рисуется.
- Пак-кварки Oculus (НЕ наши, не фатально): Unknown variable: BIOME_PALE_GARDEN/endFlashIntensity (Complementary r5.8.1 ссылается на 1.21-контент) — WARN, пайплайн строится.

Заметки:
- irl-core = plain-library jar (нет fabric.mod.json) -> Connector/FML его модом НЕ считает, метаданные чинить не надо; настоящий core берётся из JiJ META-INF/jars/irl-core-1.0-obt.jar внутри аддона. Отдельный irl-core-1.0-obt.jar в mods/ избыточен (молча игнорится).
- В PowerShell аргумент -Pmc=1.20.1 нужно кавычить: '-Pmc=1.20.1' (иначе ломается в -Pmc=1).
- Тот же одно-строчный фикс fmj применим к port/1.21.1, ЕСЛИ там Connector тоже отдаёт loader < 0.19.3 (не проверено; Connector для 1.21.1 — другой билд/версия loader).

Маршрут правок: [[reference-edit-routing-by-area]]. Репо: [[project-github-repos]] (bbs-irlights-addon @ master).
