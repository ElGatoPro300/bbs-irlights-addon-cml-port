---
name: addon-iris-integration
description: "How IRLite exposes shadow textures to Iris programs — ProgramSamplersBuilderMixin sampler names, SamplerBindingCubeArrayMixin cube-array rebind workaround."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 7caee3ab-d073-4ed1-bfcf-8daae6a18007
---

IRLite <-> Iris integration (binding shadow textures into shader programs). Parent: [[addon-architecture]]. Textures from [[addon-shadows]]. The SSBO (binding 7) is bound directly by [[addon-light-buffer-ssbo]], not through Iris. Mixins are in mixin/client/iris/, remap=false (Iris is not yarn-mapped).

ProgramSamplersBuilderMixin -> net.irisshaders.iris.gl.program.ProgramSamplers.Builder.build() @HEAD
- Iris calls builder(...).build() once per compiled program (gbuffers, deferred, composite, final, shadow, ...), so HEAD covers all programs.
- Adds two dynamic samplers (IntSupplier of GL texture id, by name):
    "irl_spotShadowAtlas"  -> SpotlightDepthAtlas::getGlTextureId
    "irl_pointShadowArray" -> PointShadowArray::getGlTextureId
- addDynamicSampler is a no-op (returns false) for programs that don't declare the uniform, so no texture unit is wasted.
- These are the exact uniform names the injected shader GLSL must declare to read IRLite shadows.

SamplerBindingCubeArrayMixin -> net.irisshaders.iris.gl.sampler.SamplerBinding.updateSampler() @HEAD cancellable
- Problem: Iris's TextureType enum has no CUBE_MAP_ARRAY, so irl_pointShadowArray gets registered as TEXTURE_2D and Iris would bind it as 2D.
- Fix: when the binding's texture id equals PointShadowArray.getGlTextureId() (and !=0), rebind it as GL_TEXTURE_CUBE_MAP_ARRAY on the same texture unit (IrisRenderSystem.bindTextureToUnit) and ci.cancel() the default 2D bind.
- Shadowed fields read: textureUnit, texture (IntSupplier).

NET RESULT: every Iris program can sample a 2D depth atlas (spotlights) and a depth cube-map-array (point lights) by the two
fixed uniform names, plus read the std430 SSBO at binding 7. That is the complete surface the shaderpack patch builds on.

Связь: IRLite-источник правды Iris-интеграции; в redactor миксины = per-mod/версионные копии (ProgramSamplersBuilderMixin никогда не шарится: Iris 1.7.2=2-арг, 1.10.7=4-арг). Дополняет [[project-irlite-base-ported]], [[project-irl-sync-strategy]] §3, [[reference-edit-routing-by-area]]. Источник: память IRLite.
