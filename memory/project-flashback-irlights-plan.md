---
name: project-flashback-irlights-plan
description: "PLAN ONLY (2026-06-16, кода нет): отдельный аддон для интеграции IRLights в Moulberry Flashback replay mod. Решения LOCKED: цель MC 1.21.11 + Flashback 0.39.5; свет = Flashback keyframe-треки. Арх = two-mod (Yarn IRLights + Mojmap glue) + public KeyframeRegistry.register + наш SSBO render-шов без изменений. Есть verify-first kill-switch эксперименты."
metadata:
  node_type: memory
  type: project
  originSessionId: ea700912-15d6-4d2e-b482-61c02d9d533c
---

Plan: IRLights -> Flashback addon (только планирование, 2026-06-16).

Строит на [[project-irlite-base-ported]] / [[project-port-1211]] (BBS-free light engine, шов = LightScene/PlacedLight/LightDriver -> SSBO binding 7 -> Iris samplers) и prior art Replay Mod из [[project-editor-vs-replay-screen-conflict]]. Цель: ОТДЕЛЬНЫЙ аддон-мод, чтобы юзер размещал/анимировал НАШИ источники света внутри Moulberry Flashback (https://github.com/Moulberry/Flashback) и видел их в playback И в экспортированном видео. Исследовано 6-агентным ULTRACODE workflow + adversarial critique. КОДА НЕ НАПИСАНО — только план.

## Решения LOCKED с пользователем (2026-06-16)
- Версия-цель: MC 1.21.11 + Flashback 0.39.5 (ветка/релиз 1.21.11, НЕ master=26.1.2/Java25, который заставил бы делать полный ре-порт IRLights). Наш порт 1.21.11 уже бежит in-world с Iris 1.10.7.
- Модель авторинга: Flashback keyframe-ТРЕКИ на таймлайне (анимируется, сериализуется в .flashback), не статичный per-replay набор размещений.

## Рекомендованная архитектура (Option A)
- Регистрировать IRLightKeyframeType.INSTANCE через PUBLIC KeyframeRegistry.register(...) Flashback (единственная документированная не-mixin точка расширения). IRLightKeyframeChange.apply() мутирует привязанный к треку PlacedLight в LightScene; interpolate() блендит два keyframe. Стреляет каждый кадр (вкл. export) через EditorState.applyKeyframes(handler, tick).
- Render = наш существующий код БЕЗ ИЗМЕНЕНИЙ (GameRendererLightMixin collect->bake->flush->SSBO). Export Flashback переиспользует стандартный путь gameRenderer.render(timer,true)->LevelRenderer.renderLevel с активным Iris и читает post-Iris mainRenderTarget, так что освещённый кадр захватывается «бесплатно» — ЕСЛИ выполнены 3 предусловия (см. риски).
- MODULE LAYOUT (несущее, поправка критика): ДВА мода. IRLights остаётся Yarn-модом как есть; glue flashback-irlights = отдельный Mojmap-мод (Flashback = Mojmap), трогает только public API Flashback + LightScene через intermediary в рантайме. НЕЛЬЗЯ смешать Yarn-mixin и Mojmap-mixin в одном source set — «reuse GameRendererLightMixin unchanged in the addon» НЕ жизнеспособно как один мод.
- ImGui: рендерить в СОБСТВЕННЫЙ vendored ImGui Flashback (imgui.moulberry90, relocated ~1.90.0, владелец ReplayUI). НЕ поднимать свой ImGuiRuntime/Screen-detach/input-cancel миксины (эти Replay-Mod паттерны здесь инвалидированы — два контекста коллидят). MVP UI = KeyframeType.createPopup() + Keyframe.renderEditKeyframe(). Докнутая панель/gizmo требует ReplayUI-mixin (WindowType = закрытый enum) — поздняя фаза; ImGuizmo может даже не быть в форке Flashback.
- License: Flashback ARR, без внешних контрибуций. Только runtime-interop — реализовывать его public-интерфейсы, звать его public-registry, НИКОГДА не форкать/vendor его исходники.

## Keyframable-поля (из PlacedLight)
Чистый lerp/spline: x/y/z, r/g/b, intensity, radius, range, beamStrength, anisotropy, vlDensity, bulbSize, outerAngleDeg/innerAngleDeg (писать RAW, пропустить angle/soft remap у LightSync); dir = slerp+normalize. Step/hold (без блендинга): shadows/entitiesOnly/blocksOnly booleans; type POINT/SPOT = свойство ТРЕКА, не keyframe. НИКОГДА не keyframed/serialized: PlacedLight.id (минтуется свежим, ключует shadow-кэши) — один персистентный PlacedLight на трек, только мутируется. Global LightConfig-ручки (shadowQuality/shadowCache) = export-настройки аддона, не keyframes (вероятно форсить shadowCache=false при экспорте). Интерполяцию гнать от Flashback-тика (getCurrentTickDouble()), никогда не от wall-clock. UI обязан предупреждать у порога LightBuffer.MAX_LIGHTS.

## Топ-риски (ранжированы)
1. Yarn<->Mojmap mismatch — решён two-mod layout выше; структурный, не опциональный.
2. Свет виден ТОЛЬКО на IRLights-ПРОПАТЧЕННОМ Iris-паке — жёсткий продуктовый констрейнт; аддон обязан детектить пропатченность + громко предупреждать до экспорта, иначе сток-пак = молча ничего.
3. Стреляет ли наш render-хук под export Flashback (и существует ли вообще сигнатура renderWorld на целевом билде — наш порт 1.21.11 переписал её). Fallback = зеркалить FramePass Flashback MixinLevelRenderer (Option B render).
4. Выживание SSBO binding-7 под EXPORT (другой frame-loop; cubemap=6 проходов/кадр, SSAA -> re-upload на проход; чистит ли Iris binding 7 между программами). Успех in-world != доказательство для export. ЭТО ТЕХНИЧЕСКИЙ KILL-SWITCH.
5. Three-way Iris-пин: Flashback breaks iris <1.10.9, но наши Iris-миксины пришпилены к внутренностям 1.10.7 (ProgramSamplers.Builder, cube-array rebind) — сверить дельту 1.10.7->1.10.9.
6. Факты Registry/applyKeyframes/TypeAdapter/TimelineWindow прочитаны с master (26.1.2), НЕ с 0.39.5 — пере-верифицировать на точном jar 0.39.5 (нет Maven-публикации; компилить против локального remapped jar). Если TimelineWindow хардкодит список типов, путь «zero UI mixins» рано схлопывается в Option B.
7. Lifecycle PlacedLight на save/load .flashback + global static LightScene коллизия с JOIN-time load у LightStore.
8. Non-scalar interpolation contract (зовёт ли Flashback наш interpolate; терпит ли step/slerp) — не прочитан на цели.

## VERIFY FIRST (дёшево, БЕЗ кода аддона) — гейт всего плана
0. Взять известный IRLights-ПРОПАТЧЕННЫЙ пак; подтвердить IrisShadersState.shadersDisabled()==false на экспортированных кадрах (иначе чёрный результат неинтерпретируем).
1. KILL-SWITCH: немодифицированные IRLights+Flashback, пропатченный пак, лог из GameRendererLightMixin -> подтвердить, что миксин ПРИМЕНЯЕТСЯ на билде Flashback И стреляет во время export И свет в выходных кадрах (вкл. cubemap/SSAA face culling через cameraForward).
2. KeyframeRegistry.register round-trip на 0.39.5: тривиальный no-op тип появляется в add-меню таймлайна, размещается, переживает save->reopen .flashback; прочитать исходник KeyframeRegistry.TypeAdapter.
3. Iris-пин: диффнуть ProgramSamplers.Builder 1.10.7 vs >=1.10.9.

## Фазы
0 = самый дешёвый «увидеть свет» с ОБОИМИ модами немодифицированными (без аддона). 1 = MVP-аддон: детектить Flashback.isInReplay(), сидить статичный свет. 2 = export-захват (+shadowCache=false на isExporting()). 3 = кастомный KeyframeType, анимировать позицию. 4 = full-field keyframing + edit UI + round-trip .flashback. 5 = докнутая ReplayUI-панель + gizmo (тяжелейший coupling). 6 = персистентность/портируемость.

Public-поверхность Flashback (implement/call, никогда не форк): KeyframeType/Keyframe/KeyframeRegistry/KeyframeChange, EditorState.applyKeyframes, Flashback.{isInReplay,isExporting,EXPORT_JOB}, IrisApiWrapper, imgui.moulberry90.*, ReplayUI (phase-5 mixin), mixin/visuals/MixinLevelRenderer (паттерн для зеркалирования).

## Актуализация 2026-07-01 (при спасении из мёртвой pre-rename базы IRL-redactor в единый склад)
План не начат (кода 0), решения от 2026-06-16 в силе. Дрейф канона с тех пор: LightBuffer.MAX_LIGHTS теперь 2048, не 256 (см. [[project-auto-block-lights]]) — UI-warn-порог в разделе Keyframable обновить при старте; порт 1.21.11 = продакшн in-world PASS ([[project-port-1211]]). Оригинал жил только в orphan-дире ...BBS-IRL-redactor до её чистки 2026-07-01; см. [[reference-memory-junctions]].
