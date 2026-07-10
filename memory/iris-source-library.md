---
name: iris-source-library
description: "Локальные исходники Iris для чтения внутренностей (ProgramSamplers, SamplerBinding, pipeline, shadows): предпочитать точную копию 1.20.1, fallback 1.7.2-1.20.4; пути + карта пакетов."
metadata:
  node_type: memory
  mod_scope: reference-shared
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 125c0218-fa90-479f-a94e-3212e180f766
---

Локальные исходники Iris для чтения при вопросах о внутренностях Iris (как Iris строит программы, биндит сэмплеры, гоняет shadow-пасс, кормит юниформы). Миксины IRLite таргетят эти классы remap=false — это авторитетный референс символов, которые хукает [[addon-iris-integration]].

Две копии, обе полные Gradle-проекты (build.gradle.kts, settings.gradle.kts, README.md, docs/, gradle/, DHApi.jar) под старым деревом IRLEngine (см. [[project-refactor-origin]]):
- PRIMARY (точное соответствие Fabric 1.20.1-рантайму IRLite): C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLEngine\Source-libary\Iris-1.20.1
- Fallback (Iris 1.7.2 для MC 1.20.4 — близко, не точно): C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLEngine\Source-libary\Iris-1.7.2-1.20.4

Предпочитать копию 1.20.1 для всего, что касается работающей игры; копию 1.20.4 смотреть только чтобы сверить более новую форму API. Раскладка идентична:
- Корень Java-исходников: src\main\java\net\irisshaders\iris\
- Source sets под src\: main, headers, vendored, sodiumCompatibility, desktop, disabledTest.

Карта пакетов под net\irisshaders\iris\:
- gl — GL-обёртки; классы, в которые миксится IRLite: gl.program.ProgramSamplers(.Builder), gl.sampler.SamplerBinding. Начинать тут для вопросов sampler/program/texture-binding.
- samplers — сборка sampler-set на программу.
- shaderpack — парсинг shaders.properties / пака (релевантно [[patcher]] и [[shader-settings]]).
- shadows — собственный shadow-пасс Iris / shadow render targets.
- pipeline — драйвер deferred/composite пайплайна.
- uniforms — поставщики встроенных юниформов.
- прочее: api, apiimpl, compat, compliance, config, fantastic, features, gui, helpers, layer, mixin, mixinterface, parsing, pathways, targets, texture, vertices.

Связь: внешний reference для ОБОИХ модов (Iris-миксины remap=false есть в обоих). Дополняет [[project-port-1211]] (конкретные формы API Iris 1.10.7 для 1.21.11). Источник: портировано из памяти IRLite.
