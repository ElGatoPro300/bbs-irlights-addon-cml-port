---
name: shader-settings
description: "IRLite Iris settings-screen injection — shaders.properties (SSBO feature flag, screen.IRLITE tree, sliders, main-menu entry) and lang/en_us.lang color-coded labels; in-game path."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: phase3-shader
---

How IRLite registers its options into IterationRP's Iris settings UI. Parent: [[MEMORY]]. The #define options themselves live in irlite_lights.glsl ([[shader-irlite-glsl]] / [[shader-shadow-sampling]] / [[shader-volumetric]]). Patch ops in patches/iterationrp.irlpatch ([[patcher]]). Two files edited: shaders/shaders.properties and shaders/lang/en_us.lang.

IN-GAME PATH: Iris -> Shader Pack Settings. IRLite appears (a) as a top-level group [IRLITE] in the main options screen (next to [SHORTCUT]) and (b) the same group is reachable under Lighting. Sub-screens: Specular, Volumetric, Shadows, Toon, Outline.

shaders.properties EDITS:
1. replace `iris.features.required=CUSTOM_IMAGES` -> add ` SSBO` (= "CUSTOM_IMAGES SSBO"). REQUIRED — without the SSBO feature flag Iris will not honor the std430 binding-7 buffer the addon writes ([[addon-light-buffer-ssbo]]). This is the single most important properties edit.
2. after `screen.LIGHTING=[LIGHT_SOURCE] [HELDLIGHT] [SHADOW]` -> define the IRLITE screen tree (2-column layouts):
     screen.IRLITE = IRLITE_INTENSITY IRLITE_DIFFUSE [IRLITE_SPECULAR_SCR] [IRLITE_VOLUMETRIC_SCR] [IRLITE_SHADOWS_SCR] [IRLITE_TOON_SCR] [IRLITE_OUTLINE_SCR]
     sub-screens: IRLITE_SHADOWS_SCR (IRLITE_SHADOWS, IRLITE_SPECULAR_HARD, IRLITE_SHADOW_QUALITY, IRLITE_SHADOW_SIZE, IRLITE_SHADOW_BIAS); IRLITE_SPECULAR_SCR (IRLITE_SPECULAR, IRLITE_SPECULAR_INTENSITY); IRLITE_VOLUMETRIC_SCR (IRLITE_VOLUMETRIC, IRLITE_VL_INTENSITY, IRLITE_VL_STEPS, IRLITE_VL_MAX_DIST, IRLITE_VL_TIP_BOOST, IRLITE_VL_TIP_RADIUS); IRLITE_TOON_SCR (IRLITE_TOON, IRLITE_TOON_BANDS, IRLITE_TOON_SMOOTH); IRLITE_OUTLINE_SCR (IRLITE_OUTLINE, IRLITE_OUTLINE_PIXEL_SIZE, IRLITE_OUTLINE_FRESNEL_POWER, IRLITE_OUTLINE_STRENGTH).
3. replace `[SHORTCUT]` -> prepend `[IRLITE]            <empty>` so IRLite is a top-level group in the main screen.
4. after `sliders=PT_VOXEL_RESOLUTION \` -> append all numeric IRLITE_* options to the sliders= list so they render as sliders (not toggles): IRLITE_INTENSITY, IRLITE_SPECULAR_INTENSITY, IRLITE_SHADOW_QUALITY, IRLITE_SHADOW_SIZE, IRLITE_SHADOW_BIAS, IRLITE_VL_INTENSITY, IRLITE_VL_STEPS, IRLITE_VL_TIP_BOOST, IRLITE_VL_TIP_RADIUS, IRLITE_VL_MAX_DIST, IRLITE_TOON_BANDS, IRLITE_TOON_SMOOTH, IRLITE_OUTLINE_PIXEL_SIZE, IRLITE_OUTLINE_FRESNEL_POWER, IRLITE_OUTLINE_STRENGTH. (Toggles IRLITE_DIFFUSE/SPECULAR/VOLUMETRIC/SHADOWS/SPECULAR_HARD/TOON/OUTLINE are NOT in this list -> rendered as on/off.)

lang/en_us.lang EDIT (after `option.iterationRP_VERSION=iterationRP by Tahnass`): labels for every screen.* header and option.* / suffix.*. Color-coded with Minecraft section codes per feature group: IRLights header §6§l (gold); Specular §b (aqua); Volumetric §d (pink); Shadows §3 (dark aqua); Toon §a (green); Outline §e (yellow). Suffixes: IRLITE_VL_TIP_RADIUS/IRLITE_VL_MAX_DIST = "m", IRLITE_OUTLINE_PIXEL_SIZE = "px".

EDIT WORKFLOW REMINDER ([[patcher]]): all of the above are anchor-based ops on stable IterationRP strings (iris.features.required, screen.LIGHTING, [SHORTCUT], sliders=PT_VOXEL_RESOLUTION). If IterationRP renames any of these upstream the anchors must be re-captured. New screen names must also be added to lang or Iris shows the raw key.

IRIS OPTION-REGISTRATION RULE (cross-pack gotcha, cost a lost-buttons bug in the Complementary port 2026-06-12): Iris recognizes a boolean #define X / //#define X as a CONFIGURABLE toggle ONLY if X also appears somewhere in the include graph as a BARE #ifdef X or #ifndef X directive with NOTHING after the name (OptionAnnotatedSource.parseLine/parseIfdef in Iris source; #if defined X && ... and #elif references are deliberately NOT counted, and #ifdef X // comment does not count either because parseIfdef requires end-of-line after the name). Numeric options need no reference — the // [list] comment registers them. Trailing comments ON THE DEFINE LINE are fine for both kinds. CONSEQUENCE for every inject: each IRLITE_* toggle must keep at least one bare #ifdef IRLITE_X line; when a gate needs A && B, write nested #ifdef A / #ifdef B instead of #if defined A && defined B (this is why the Complementary lib nests DEFERRED1/IRLITE_OUTLINE, COMPOSITE1/IRLITE_VOLUMETRIC, IRLITE_VL_SHADOWS/IRLITE_COMPILE_SHADOWS).

Связь: shader-inject (содержимое .irlights, инъекция в shaders.properties/lang; владелец IRLite, синк в redactor через copy-patches.ps1). Дополняет [[reference-edit-routing-by-area]] и [[project-irl-sync-strategy]] деталями того, ЧТО инъектится в Iris-настройки. Источник: память IRLite.
