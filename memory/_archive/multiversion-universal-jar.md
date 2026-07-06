---
name: multiversion-universal-jar
description: Making the master branch produce ONE jar that runs on both Minecraft 1.20.1 and 1.20.4 (Route A confirmed feasible)
metadata: 
  node_type: memory
  mod_scope: IRLite-only
  ported_from: IRLite
  consolidated: 2026-06-18
  type: project
  originSessionId: 4bc140b4-85eb-4b66-b552-0f12acca6141
---

Goal (started 2026-06-12): make `master` produce a SINGLE universal jar that loads on both Minecraft 1.20.1 and 1.20.4. Not two jars — one jar in either instance.

**Feasibility = CONFIRMED (Route A, single universal jar).** Phase 0 proved MC-API divergence between 1.20.1 and 1.20.4 for THIS code is essentially zero: the whole `qualet.irlite` source compiles clean against BOTH yarn mappings after fixing exactly ONE spot. Every MC member the mod touches is intermediary-stable, so the SAME bytecode (built against 1.20.1) runs on 1.20.4 too.

**The one and only source divergence found:** `net.minecraft.util.math.Box.getXLength()/getYLength()/getZLength()` exist in yarn 1.20.1 but were renamed (to getLengthX/Y/Z) by yarn 1.20.4+build.1. Fixed universally in `ShadowBaker.java` (~L1095, L1100) by using the stable public fields `box.maxX-box.minX` etc. (field names identical across versions). Same intermediary, so this was purely a source-name issue, not an API change.

**Why it's easy here (verified facts, see [addon-architecture]):**
- The 4 MC-targeting mixins (`GameRenderer.renderWorld` @HEAD with `(F J MatrixStack CI)` — MatrixStack STILL present in 1.20.4; `World.setBlockState(...II)Z`; World accessor) have byte-identical targets/descriptors in both versions. Confirmed against the working 1.20.4 port in `bbs-refreshed_20_4/src/.../org/wemppy/irlights`.
- Render Java (`RenderSystem.setProjectionMatrix(Matrix4f,VertexSorter)`, `EntityRenderDispatcher.render(...)` 9-arg, `BufferRenderer.drawWithGlobalProgram`, `Matrix4f`) — all stable; the 1.20.4 port uses identical calls.
- BBS dep: a 1.20.4 jar EXISTS (`C:\prismlauncher\instances\BBS\minecraft\mods\bbs-2.2-1.20.4.jar`, copied into `bbs-irlights-addon/libs/`). BBS classes aren't remapped; the `mchorse.bbs_mod.*` API the addon uses (BBSMod.onInitialize, BBSSettings.register, Films.controllers accessor) is identical in both BBS source trees.
- Iris dep (remap=false): hooks `ProgramSamplers.Builder.build()` @HEAD and `SamplerBinding.updateSampler()` @HEAD cancellable — both exist identically in Iris 1.7.2 for 1.20.1 AND 1.20.4. Jars: iris `1.7.2+1.20.1` / `1.7.2+1.20.4`, sodium `mc1.20.1-0.5.11` / `mc1.20.4-0.5.8`.
- Shader patches `patches/*.irlights` = pure GLSL, version-independent, untouched.

**Build system (implemented in build.gradle):** a `-Pmc=1.20.1|1.20.4` selector picks minecraft/yarn/fabricApi/bbsJar/sodium/iris coords + the LWJGL 3.3.1 pin (1.20.1 only; 1.20.4 ships 3.3.3, no pin). Default = 1.20.1. The PRODUCT jar is built against 1.20.1 (lower version, most stable intermediary "upward"); `-Pmc=1.20.4` is for dev-testing the 1.20.4 runtime path. PowerShell quirk: pass `-Pmc=...` via the `--%` stop-parsing token or batch splits it on dots (`mc=1`).
`gradle.properties` minecraft_version/yarn/fabric_version are now superseded by the V map (kept as harmless defaults). `fabric.mod.json` depends.minecraft widened from `${minecraft_version}` to `">=1.20.1 <1.20.5"`.

**Status (2026-06-12): COMMITTED + PUSHED (c84a2ae on master).**
- Phase 0 (compile both) — DONE, both green.
- Phase 1 (metadata range) — DONE.
- Phase 2 (-Pmc dual dev profile) — DONE.
- Phase 3 (build universal jar) — DONE; `irlite-0.0.1.jar` built against 1.20.1, manifest verified (depends `>=1.20.1 <1.20.5`), deployed into the 1.20.4 prism instance (replaced a stale earlier jar that had pinned depends `"1.20.1"` and wouldn't load on 1.20.4).
- Phase 4 (in-game) — user reported "всё норм" and OK'd commit/push; NOT independently observed by the agent. The full test matrix (mixin-apply + lights/shadows/VL + Iris samplers + patcher UI on a 1.20.1 instance AND 1.20.4 prism) was not run by the agent — treat in-game runtime behavior as user-attested, not agent-verified.

Commit c84a2ae touches exactly 3 tracked files (build.gradle, ShadowBaker.java, fabric.mod.json). BBS jars stay untracked (`.gitignore *.jar`) — provided out-of-band in `libs/` per existing policy; `-Pmc=1.20.4` needs `libs/bbs-2.2-1.20.4.jar` present.
Out of scope: the org.wemppy 1.20.4 port's extra features (Area Light, DOF) — different feature set, not merged.

---

## Связь с IRL-redactor (объединение памяти 2026-06-18)

- **Применимость:** IRLite-only — Применимо ТОЛЬКО к IRLite (BBS-аддон, пакет qualet.irlite, master-ветка + универсальный jar). Это первичная IRLite-сторонняя запись стратегии «один jar на 1.20.1↔1.20.4», которую [[project-irl-sync-strategy]] упоминает кратко в секции «ВЕРСИОННАЯ ОСЬ». Контрастирует с веточным портом redactor'а ([[project-port-1211]]: отдельные ветки main=1.20.4 / port/1.21.11). У standalone-redactor'а этого `-Pmc`-механизма НЕТ.
- **Связанные в redactor:** [[project-irl-sync-strategy]], [[project-port-1211]] (complements)
- Источник: портировано из памяти IRLite; оригинал оставлен как архив.
