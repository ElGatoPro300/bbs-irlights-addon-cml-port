---
name: addon-architecture
description: "IRLite addon high-level design — mixin-driven, registration order, per-frame render pipeline, full mixin inventory."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 7caee3ab-d073-4ed1-bfcf-8daae6a18007
---

IRLite addon architecture. Parent: [[MEMORY]]. Siblings: [[addon-forms]] [[addon-light-collection]] [[addon-light-buffer-ssbo]] [[addon-shadows]] [[addon-iris-integration]] [[addon-ui-config]].

BUILD / ENV
- Fabric mod, id "irlite", MC 1.20.1, Java 17, fabric-loom 1.15.5. Authors wemppy, qualet. License CC-BY-ND-4.0 (was MIT until 2026-06-12; full legal code in LICENSE.txt, notice in README.md).
- split env source sets: src/main (common) + src/client. Mixin configs: irlite.mixins.json (common: BBSModMixin, BBSSettingsMixin), irlite.client.mixins.json (client: the rest). compatibilityLevel JAVA_17, overwrites.requireAnnotations true.
- depends: fabricloader, fabric-api, bbs (libs/bbs-2.2-dev3-1.20.1.jar, local file dep), minecraft.
- dev runtime: sodium mc1.20.1-0.5.11, iris 1.7.2+1.20.1, plus iris runtime deps jcpp 1.4.14 + io.github.douira:glsl-transformer 2.0.0-pre13 (maven.gegy.dev). All org.lwjgl pinned to 3.3.1 (Sodium refuses 3.3.2).

DESIGN: ENTRYPOINTS ARE EMPTY. Irlite.onInitialize and IrliteClient.onInitializeClient do nothing.
IrliteDataGenerator just createPack(). ALL behavior is installed via mixins that hook BBS/MC/Iris init
and render. This is the key mental model: to find a feature, find its mixin.

REGISTRATION (install-time, via mixins)
- BBSModMixin -> BBSMod.onInitialize TAIL: register PointLightForm/SpotlightForm form types into BBSMod.getForms() under Link irlite:point_light / irlite:spotlight.
- ExtraFormSectionMixin -> ExtraFormSection.initiate TAIL: add the two forms to the "extra" form category (so they appear in the form picker).
- FormUtilsClientMixin -> FormUtilsClient.<clinit> TAIL: register client form renderers (PointLightFormRenderer, SpotlightFormRenderer).
- UIFormEditorMixin -> UIFormEditor.<clinit> TAIL: register editor UIs (UIPointLightForm, UISpotlightForm).
- BBSSettingsMixin -> BBSSettings.register TAIL: add settings categories "irlite" (show_guides, shadow_quality, shadow_cache) and empty "irlite_patcher".
- L10nMixin -> L10n.<init> TAIL: register English UI strings for the above.
- UISettingsOverlayPanelMixin -> UISettingsOverlayPanel.refresh TAIL: inject the patcher UI into the "irlite_patcher" settings category.

PER-FRAME PIPELINE (the heart of the runtime)
Single hook: GameRendererLightMixin -> GameRenderer.renderWorld @HEAD, runs irlite$collectLights every frame
BEFORE Iris activates and before world geometry is drawn. Order inside the hook:
  0. SHADERS-OFF GATE (2026-06-12, fixed "shadows bake with shaders disabled"): IrisShadersState.shadersDisabled()
     = Iris.getPipelineManager().getPipelineNullable() instanceof VanillaRenderingPipeline. When true: LightRegistry.clear()
     (render-path renderers keep registering regardless of Iris) + ONCE on the transition (mixin static irlite$dormant)
     LightBuffer.uploadEmpty() (zero SSBO count so a re-enabled pack can't read stale lights; never allocates) and
     ShadowBaker.onShadersDisabled() (resetTileState + drain BlockShadowCache/block VBOs + SpotlightDepthAtlas.delete()
     + PointShadowArray.delete() — VRAM freed, all lazily re-alloc + first-bake on wake), then early-return: no collect,
     no bake, no upload. FAIL-OPEN: null pipeline (first frame of session / pack reload / the toggle frame — Iris builds
     the pipeline later in the frame, inside renderLevel) counts as NOT disabled, so a frame that renders with shaders
     never reads an unbound SSBO (and the shaders-ON toggle frame gets fresh data with zero gap); any Iris API Throwable
     sets a sticky broken flag = permanently fail open (old always-on behavior).
  1. LightCollector.collect(world, cameraPos): scanner path. Walks loaded BBS ModelBlockEntity form-trees in
     world coords and registers their PointLight/Spotlight forms into LightRegistry. (Live actors / film replays
     are NOT collected here — they register from the render path during the PREVIOUS frame and are already in the
     registry; this is the 1-frame-stale path, see [[addon-light-collection]].)
  2. mc.getEntityRenderDispatcher().configure(world, camera, cameraEntity): needed so the shadow baker can use the
     vanilla entity dispatcher.
  3. ShadowBaker.bake(world, cameraPos, tickDelta): collects occluders, renders depth maps per spot (atlas tile) /
     point (cube faces) into IRLite's own FBOs, and writes each light's shadow tile/slot index back into LightRegistry.
     MUST run before Iris activates (vanilla entity rendering would otherwise scribble into our depth FBO) and before
     the SSBO upload. See [[addon-shadows]].
  4. LightRegistry.flush(): packs the accumulated lights (now with shadow tile indices) into LightBuffer and uploads
     the SSBO to binding 7, then clears the registry for next frame. See [[addon-light-buffer-ssbo]].

Then the actual world render runs (Iris active). During it, light form renderers on the render path
(live actors/replays) register lights for the NEXT frame, and Iris programs read the SSBO + shadow samplers.

PACKAGE MAP (src/client/java/qualet/irlite)
- forms/ (in src/main) — PointLightForm, SpotlightForm (data model, common env).
- client/forms/ — form renderers + LightGuideRenderer (in-world gizmos).
- client/light/ — LightCollector, LightRegistry, LightBuffer, IRLightPositionResolver.
- client/light/shadow/ — ShadowBaker, ShadowRenderer, SpotlightDepthAtlas, PointShadowArray, IRLShadowQuality, ShadowBakeState.
- client/patcher/ + client/ui/patcher/ — patcher (Phase 2).
- client/ui/forms/editors/ — editor UIs.
- mixin/ + mixin/client/ (incl. bbs/ accessors and iris/) — all hooks.

Связь: IRLite-only (redactor при порте выбросил пустые энтрипоинты, BBS-формы/настройки и mchorse-миксины). Дополняет [[project-irlite-base-ported]], [[project-irl-sync-strategy]]; общий per-frame шов LightRegistry.flush->SSBO binding 7 теперь в irl-core. Источник: память IRLite.
