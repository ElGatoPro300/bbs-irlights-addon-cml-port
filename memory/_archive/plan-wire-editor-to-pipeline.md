---
name: plan-wire-editor-to-pipeline
description: "DONE (2026-06-16) phases A-E — ImGui editor drives + persists real lights via LightScene/PlacedLight; only the GLSL inject/patcher port remains for visible light"
metadata: 
  node_type: memory
  type: project
  originSessionId: dee44e0c-7ef9-4374-a401-d48d80072ef3
---

# Plan: wire the ImGui editor to the light pipeline

Builds on [[project-irlite-base-ported]] (the BBS-free engine + seam) and [[project-irl-redactor-imgui-standalone]]
(the ImGui editor). GOAL: the editor stops being a dead visual prototype and actually drives real lights.

## STATUS 2026-06-16 — phases A–D DONE (builds clean: `gradlew build` -> irl-redactor-1.0.0.jar)

Implemented this session:
- **A (bridge):** `LightState` gained `float[3] pos` + `float[3] dir` (default down); `reset()` now resets only
  the visual params (keeps name/type/pos/dir). New `editor/LightSync.java` = the ONE mapper: `pull(PlacedLight->LightState)`
  on selection-change, `push(LightState->PlacedLight)` every frame. Non-1:1 mappings handled exactly as planned
  (outer=angle, inner=clamp(angle-soft,1,outer); beamStrength=vol?beam:0 with beam kept across vol toggle; dir copied).
- **B (list+selection):** `PlacedLight` gained `String name` + static `copyOf(src)` (fresh id). `LightEditorPanel`
  now holds `state`+`selected`+`syncedId`; new `sourceList()` draws Добавить/Дублировать/Удалить + a `Widgets.selectable`
  row per light. Mutations run BEFORE list iteration; selection click captured and applied AFTER the loop (no CME).
  `syncSelection()` pulls once when `selected.id != syncedId`. Add places at player eye; Delete picks the neighbour.
- **C (placement):** `placementGroup()` (header "Размещение") = X/Y/Z via new unbounded `Widgets.dragValue`
  (relative drag, 0.05/px), "Переместить сюда" -> player eye, and for spot a dir readout + "Навести по взгляду"
  -> `player.getRotationVector()`.
- **D (engine settings):** `engineGroup()` (header "Настройки движка") bound to `LightConfig` statics — quality
  segmented LOW/MED/HIGH/ULTRA, toggles Кэш теней / Тени блоков / Показывать гайды, trackpad Радиус теней блоков 4..96.
- **Cleanup:** removed the J/K debug keybinds + their lang keys (en/ru); only L (open editor) remains.
- **E (persistence) DONE:** `light/LightStore.java` saves/loads the scene as JSON via Gson (bundled with MC)
  under `config/irl-redactor/lights/<key>.json`. Key = `sp-<saveFolderName>` (SP, from `server.getSavePath(ROOT)`)
  or `mp-<address>` (MP, from `getCurrentServerEntry()`). `IRLRedactorClient` registers `ClientPlayConnectionEvents`:
  JOIN -> compute+store key, `LightStore.load` (clears scene first so lights don't bleed across worlds); DISCONNECT
  -> `saveCurrentWorld()` + clear. `LightEditorScreen.removed()` also saves so edits persist on closing the editor.
  DTO is decoupled from `PlacedLight` — id/static counter NOT persisted; a fresh id is minted on load (id stability
  doesn't matter across sessions; shadow caches are per-session). So lights are now world-bound across relogs.
- **UX polish (same session):** (a) source list is in a fixed-height (`6 rows`) scrollable `beginChild`, so a long
  list doesn't push the editor groups off-screen; (b) duplicate naming `duplicateName()` strips any stacked
  "копия [N]" tail then picks the first free "база копия [N]" — so duplicating never piles up "копия копия копия…";
  (c) `syncSelection()` drops a `selected` that's no longer in the scene (defends vs world reload / external clear).

- **Move GIZMO (ImGuizmo) DONE 2026-06-16:** selecting a light shows a 3D translate handle on it in the world that
  drags its position. imgui-java DOES bundle ImGuizmo: `imgui.extension.imguizmo.ImGuizmo` + `flag.Operation`/`flag.Mode`;
  the JNI symbols (`nManipulate`/`nBeginFrame`/`nSetRect`) are verified present in `imgui-java64.dll` (natives jar), so
  no extra dep. Impl is entirely in `LightEditorPanel.drawGizmo()` — NO new mixin, NO matrix capture. It **reconstructs**
  MC's world camera matrices each frame from `gameRenderer.getCamera()`: view = `Rx(pitch)·Ry(yaw+180)` (JOML, mirrors
  renderWorld's `RotationAxis` multiplies), proj = `new Matrix4f().perspective(toRad(options.getFov()), fbW/fbH, .05, 1000)`
  (near/far don't affect on-screen x/y, so the placeholder far is harmless), model = translate(`state.pos − camPos`) kept
  camera-relative for float safety. Flow: `ImGuizmo.beginFrame()` at top of `draw()`; after `ImGui.end()` (so it's not tied
  to the panel window) call `drawGizmo()` then `LightSync.push`. Draws on `ImGui.getBackgroundDrawList()` (over world, under
  the panel), `setRect(0,0,displayW,displayH)`, `manipulate(view,proj,Operation.TRANSLATE,Mode.WORLD,model)`; on `isUsing()`
  writes `state.pos = camPos + model[12..14]`. Consistent because view has no translation and the model is camera-relative,
  so ImGuizmo's camera sits at origin = real camera. NOTE: works in `runClient` (imgui is `clientImplementation`);
  bundling imgui-java + natives into the remapped jar for Prism is still the pending shadow/JiJ task (pre-existing, see
  [[project-irl-redactor-imgui-standalone]]).

- **ORIENTATION / rotate gizmo DONE 2026-06-16:** spotlights now get `Operation.TRANSLATE | Operation.ROTATE` (point lights
  stay TRANSLATE-only — no meaningful orientation). The engine model is UNCHANGED — `PlacedLight` still carries only a `dir`
  vector; orientation lives as editor scratch in the panel: `Matrix4f gizmoRot` (+ `Quaternionf gizmoQuat`, `boolean gizmoRotating`).
  Each frame for a spot: model = `T(pos−cam) · gizmoRot`; `gizmoRot` is rebuilt from `state.dir` via `Quaternionf.rotationTo(+Z, dir)`
  EXCEPT while actively rotating (`gizmoRotating`) — that keeps a drag continuous instead of snapping roll. After `manipulate`,
  when `isUsing()`: `state.dir` = normalized forward = the model's local +Z column `(m[8],m[9],m[10])`; `gizmoRot` is re-persisted
  from the manipulated model (`.set(gizmoModel).setTranslation(0,0,0)`). Roll around the beam is intentionally discarded (cone is
  radially symmetric → 2 DOF suffices). The "Навести по взгляду" button still works (sets `state.dir`; gizmo re-syncs next non-drag
  frame). Feedback during a rotate drag is the "Направление: x/y/z" readout (a beam-cone overlay was deferred — see design note below).

Per-frame flow in `LightEditorPanel.draw()`: `sourceList()` -> `syncSelection()` -> (if selected) header + groups
bound to `state` -> `LightSync.push(state, selected)` -> `engineGroup()`. Editor screen keeps the world rendering, so
`LightDriver` reads the scene each frame; push lands after the frame's collect -> 1-frame-stale, fine. **Two new
`Widgets`: `selectable(id,label,right,sel)` and `dragValue(id,label,v,idx,speed,fmt)`.**

REMAINING (superseded — see PATCHER ENGINE block below): the patcher engine port is now DONE on the 1.20.4 line,
so the editor + a self-patched pack now produce visible light. Optional later polish: raycast click-to-place (still TODO).

**PATCHER UI SHELL (visual-only) DONE 2026-06-16 — branch `claude/awesome-stonebraker-0fe265` off `main` (1.20.4, NOT
the port/1.21.11 line):** a "Патчер" button at the bottom of the engine-settings group (`engineGroup()`) opens a centred
ImGui modal `editor/PatcherPanel.java` that visually replicates the original BBS/IRLite patcher window (the screenshot):
"Шейдерпаки" + "Патчи" bordered scrollable lists (magenta scrollbar, plain-text rows), folder/refresh/× icons drawn via
DrawList, "Создавать новый пак каждый раз" toggle, magenta "Проверить"/"Патчить" primary buttons, status line. **No
functionality** — the patcher core is NOT ported (Stage 3 still pending); lists hold SAMPLE entries, selection + toggle
are live UI state, Validate/Patch are no-ops that set the status ("движок патчера ещё не перенесён"). Two new reusable
widgets added: `Widgets.primaryButton` (accent fill, dark text) + `Widgets.listItem` (transparent row, hover/selected
fill). `beginPopupModal` used for the centred + auto-dim modal; rendered at root after the panel's `ImGui.end()`.
Compiles clean. This is the UI half of Stage 3's "rewrite the patcher UI from BBS to ImGui" — the engine wiring (port the
java.nio patcher core + re-author the 6 `.irlights` patches) is still the remaining work.

**PATCHER ENGINE PORTED + WIRED + VERIFIED DONE 2026-06-16 — branch `claude/amazing-shtern-d5a76a` (off `main`, 1.20.4;
this branch was fast-forwarded over `claude/awesome-stonebraker-0fe265` to inherit the UI shell above). The patcher now
WORKS, not just looks real. Committed as `0b4e9ec`; **`main` was fast-forwarded onto it** (main now = 0b4e9ec, incl. the
two GUI-shell commits 5ec30bd/893147d). No git remote configured — local only.** Did:
- Ported the 7 IRLite patcher classes into NEW package `org.qualet.irlredactor.patcher`: the 5 pure `java.nio` core
  (`IrlPatch`, `IrlPatchParser`, `PatchEngine`, `IrlPatchApplier`, `PatchResult`) copied VERBATIM (only the package line +
  a few debranded comments/strings differ — `PatchEngine`/`PatchResult` byte-identical to IRLite), plus the 2 IO classes
  de-BBS'd: `PatchLibrary` + `Shaderpacks` now use **MC `net.minecraft.util.Util.getOperatingSystem().open(File)`** in
  place of `mchorse.bbs_mod.ui.utils.UIUtils.openFolder`. Iris API in `Shaderpacks` (`Iris.getShaderpacksDirectory()` /
  `getShaderpacksDirectoryManager().enumerate()`) UNCHANGED — IRL-redactor's iris (1.7.2+1.20.4) == IRLite's, so it ports free.
- Patches folder renamed `irlite/patches` -> **`<gameDir>/irl-redactor/patches`**. The 6 canonical `.irlights` are now
  BUNDLED as resources at `src/client/resources/assets/irl-redactor/patches/*.irlights` (verified present in the built jar)
  and `PatchLibrary.dir()` AUTO-EXTRACTS any missing one on first use (never overwrites a user edit; runs once/session) — so
  the patch list is populated out of the box.
- `editor/PatcherPanel.java` rewired from the visual prototype to the real engine: live `Shaderpacks.list()`/`PatchLibrary.list()`
  (reloaded on open + the refresh icon), folder icons call `openFolder()`, a cached-on-selection meta line (name → target
  [version] (N ops), amber mismatch warning via `packMatchesTarget`, auto-selects the single matching pack, red broken-patch
  parse error), Validate -> `IrlPatchApplier.validate` (dry-run), Patch -> `apply` producing `<pack>_IRLights` (+_2/_3 when
  "create new pack each time"), coloured status (green/red engine summary or neutral localized guard). Full op log -> `irl-redactor`
  logger. New `Widgets.textColored(s,0xRRGGBB)`. New lang keys `patcher.empty`/`patcher.meta.{ops,broken,mismatch}` (en+ru),
  removed the obsolete `status.{validateNotReady,patchNotReady}`.
- VERIFIED: `gradlew build` clean; offline harness (javac the 5 core + a default-pkg harness importing the new package) ran
  all 6 bundled patches against IRLite's pristine `Shadres/Original/<pack>` — ALL FIT with exact op counts (bliss 16, bsl 26,
  complementaryreimagined 21, iterationrp 12, photon 20, solas 19). Apply-vs-`Shadres/Modification` was diff-clean for
  Bliss/IterationRP/Photon; BSL/ComplementaryReimagined/Solas differ ONLY in expanded human comment blocks (e.g. BSL
  `deferred2.glsl` header) — cosmetic drift in the IRLite repo (patches not regenerated after a Modification comment edit),
  NOT a port bug (the original engine, being identical, would reproduce it). NOT yet exercised in-world.

**LOCALIZED to the game language DONE 2026-06-16 (commit 893147d, same branch):** the editor + patcher are no longer
hardcoded Russian — they follow Minecraft's selected language. ALL UI strings moved to `assets/irl-redactor/lang/en_us.json`
+ `ru_ru.json` (55 keys each, `irl-redactor.editor.*` / `irl-redactor.patcher.*`) and resolved via new
`editor/Lang.java` = thin wrapper over client `I18n.translate` (reads the current `Language` each call → re-localizes LIVE
on a language switch, no restart; en_us is the fallback for any non-ru/en language, rendered with the bundled font whose
atlas already carries Latin+Cyrillic). Default light names (`Источник %d`/`Source %d`) + the duplicate copy suffix
(`копия`/`copy`) follow the language too; the strip regex matches BOTH words (CASE_INSENSITIVE) so names don't pile up
across a switch. Patcher status stores a KEY not resolved text. Numeric direction readout pre-formats with Locale.ROOT
(`%s` template) to keep dot decimals. So the earlier "labels are Russian" note above is superseded.

**IN-WORLD GUIDES DONE 2026-06-16:** the "Показывать гайды" toggle (`LightConfig.showGuides`) now actually renders —
new `light/LightGuideRenderer.java` registers `WorldRenderEvents.LAST` (fabric-rendering-v1, in fabric-api 0.91.1).
**Iris fix:** it was first on `AFTER_TRANSLUCENT` and the guides were INVISIBLE with shaders on (the lines land inside the
shaderpack's gbuffer/translucent pass and the deferred composite discards them). Moved to `LAST` = after Iris composites the
frame into the main framebuffer, so they survive shaders (and still work without). Draws wireframe lines in CAMERA-RELATIVE
coords transformed by `ctx.matrixStack().peek().getPositionMatrix()` (same trick as the gizmo / vanilla debug renderers;
global RenderSystem modelview is identity during world render → no double-transform), with a reconstruct fallback
(`Rx(pitch)·Ry(yaw+180)`) if `matrixStack()` is null at LAST: spotlights get a cone along `dir` (centre axis line =
direction indicator + end ring + 4 spokes; radius = `len*tan(outerAngle/2)`, len=clamp(range,1,16)), point lights get a
3-axis position cross. Immediate Tessellator with `getPositionColorProgram`, `DEBUG_LINES`/`POSITION_COLOR`, depth test OFF
(x-ray, always visible), cull off, line width 2, colour = the light's own RGB floored to 0.25. Registered in
`IRLRedactorClient.onInitializeClient`. Shows for ALL lights whenever showGuides is on, even with the editor closed.
Gives the spot rotate-gizmo a visible beam too. NOTE: not yet re-verified in-world with shaders ON after the LAST switch.

**Gizmo look (2026-06-16):** user APPROVED the default ImGuizmo RGB axes (red=X/green=Y/blue=Z + white screen ring) from a
runClient screenshot — these are the binding defaults, we don't set them. Applied the available size levers:
`setGizmoSizeClipSpace(0.08f)` (const `GIZMO_SIZE`, default is 0.1 → a bit smaller) + `allowAxisFlip(false)`, called each
frame before `manipulate`. Composition kept (translate arrows + 3 rotate rings for spots). Further restyle DEFERRED below.

**DEFERRED — gizmo handle recolor/thickness (user "вернёмся позже" 2026-06-16):** ImGuizmo handle COLORS/thickness can't be
restyled via imgui-java 1.89.0 (the binding doesn't export `ImGuizmoStyle` — `javap` on ImGuizmo shows only `setGizmoSizeClipSpace`,
`allowAxisFlip`, `setRect`, ops; no style/getStyle). Levers we DO have: gizmo size (`setGizmoSizeClipSpace`), `allowAxisFlip(false)`,
which ops/axes to show, and drawing our OWN BBS-magenta overlay (`#e62e8b`) on the same `getBackgroundDrawList()` — marker dot,
radius/range circle, spot beam-cone/line along `dir`, name label. Full handle recolor would need a newer/custom binding or a
hand-rolled gizmo (big). The beam-cone overlay is also what would give the rotate gizmo a visible beam-direction indicator.

---
> Original pre-implementation plan dropped to save space — see git history.
