---
name: project-refactor-origin
description: "IRLite = полный рефактор старого IRLEngine — uniform -> std430 SSBO binding 7, добавлен патчер; функциональность портируется на новую архитектуру."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  ported_from: IRLite
  consolidated: 2026-06-18
  type: project
  originSessionId: phase4-sync
---

IRLite (this repo, C:\Users\Qualet\Documents\Project\Minecraft\BBS\bbs-irlights-addon) is a FULL REFACTOR of an older project IRLEngine at C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLEngine. Parent: [[MEMORY]].

Key architectural differences (old IRLEngine -> current IRLite):
- Light data transport: OLD = GLSL uniforms (per-light uniform arrays). CURRENT = std430 SSBO at binding 7 ([[addon-light-buffer-ssbo]]). This is the central change driving the rewrite.
- Shaderpack integration: OLD = no patcher (shader hand-wired / not distributable as a clean inject). CURRENT = the anchor-based .irlpatch patcher ([[patcher]]) injects IRLite GLSL into a pack.

STATUS: in-progress port. Functionality from IRLEngine is being adapted feature-by-feature onto the new SSBO + patcher architecture (lighting, shadows, volumetrics, toon, outline already ported — see the shader-*.md and addon-*.md branches). When porting a feature, IRLEngine is the reference for intended behavior; the shader functions in [[shader-irlite-glsl]]/[[shader-volumetric]] note "ported from the original IRLights/IRLEngine" where they mirror old code.

Why: the SSBO path removes the uniform-count ceiling and lets the same buffer feed every Iris program, and the patcher makes the shader side distributable/maintainable instead of a hand-edited pack. Use IRLEngine only as a behavioral reference, not as the live codebase.

Связь: IRLite-сторона (история происхождения аддона из IRLEngine; у редактора такой родословной нет). Объясняет «почему SSBO/почему патчер»; редактор-канон ([[project-irl-sync-strategy]], [[project-irlite-base-ported]]) фиксирует это уже как текущее состояние irl-core. Источник: память IRLite.
