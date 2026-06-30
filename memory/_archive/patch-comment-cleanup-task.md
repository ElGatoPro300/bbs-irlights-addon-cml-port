---
name: patch-comment-cleanup-task
description: "DONE 2026-06-12 (79f9d31): comments in patches/*.irlights stripped to <=1-line, all lineage removed. Shadres/ not touched (untracked). lang option.*.comment= left as-is (user-facing content)."
metadata: 
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: project
  originSessionId: 7619b5e7-6587-4b0f-9472-77d8e594e475
---

TASK DONE 2026-06-12 commit 79f9d31 (13 files, -1972/+427 lines).
tools/assert-comments-only.ps1 + tools/grep-lineage.ps1 now tracked.
NOTE: Shadres/ was NOT touched (user preference); patches edited directly for remaining hits.
NOTE: option.IRLITE_VL_RESOLUTION.comment= in BSL+Complementary lang still mentions "Photon" — left as user-facing tooltip (DO NOT TOUCH rule).

Original task spec below for reference:

TASK (user-ordered 2026-06-12, prep done, main work NOT started): clean comments in patches/*.irlights
(6 packs). Keep only essential comments, EACH <=1 line. Remove ALL port-lineage mentions (BSL, Solas,
Photon, Complementary, IRLEngine, "ported from", "the BSL rule", "kept across ports", phase/history
notes) — user: VL is now OUR OWN system. Exception: the target pack's own name inside ITS OWN patch
stays where required (@name/@target, user-facing option tooltips, pack-credit headers in +file wrapper
bodies — e.g. "BSL Shaders v10 Series by Capt Tatsu" lines in bsl.irlights wrappers).

PIPELINE CONSTRAINT (why you can't just edit the patch files): patch bodies are spliced VERBATIM from
Shadres\Modification\<Pack> by tools/gen-<pack>-patch.ps1, and the validation contract is: apply patch
to Shadres\Original\<Pack> -> output must byte-diff clean vs Modification. So the cleanup goes:
(a) Modification GLSL — the irlite lib file + comments INSIDE the inject blocks of host files ONLY
    (never the pack's own comments elsewhere in host files);
(b) generator 'Emit '#...'' header block -> ONE line (keep the '# --- section ---' emits and all
    @directive emits unchanged); then REGENERATE the patch and VALIDATE.
PHOTON HAS NO GENERATOR: mirror identical comment edits by hand in BOTH Shadres\Modification\Photon
and patches/photon.irlights; gate = the same apply+diff (patch bodies are LF, Modification may be CRLF
— the diff ignores CR).

PREP DONE 2026-06-12:
- Baseline apply+diff of ALL 6 packs = CLEAN (Modification == patch everywhere, incl. Photon).
- tools/assert-comments-only.ps1 EXISTS (untracked yet) — gate proving a GLSL edit changed only
  comments: strips /**/ and // and blank lines -> stripped text must be identical; every line
  containing '// [' (Iris option-value lists) must survive byte-identical. Run per edited file vs a
  pre-edit backup. Backups go OUTSIDE the pack tree ($env:TEMP) — a stray file inside the pack breaks
  the diff gate (applier copies the whole pack).
- Harness compile (tools\build may need recompiling in a fresh session):
  javac -encoding UTF-8 -d tools\build tools\PatchHarness.java src\client\java\qualet\irlite\client\patcher\*.java
- Per-pack validation recipe:
  java -cp tools\build PatchHarness patches\<p>.irlights Shadres\Original\<Dir> $env:TEMP\<out>
  then DELETE $env:TEMP\<out>\irlite_patched.txt (applier always writes the marker), then
  git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol $env:TEMP\<out> Shadres\Modification\<Dir>
  must be EMPTY. Dirs: Bliss, BSL, ComplementaryReimagined, IterationRP, Photon, Solas (patch names =
  lowercase same).
- Comment volume baseline (lines: total / '#' / '//'): bliss 1549/158/297, bsl 1689/200/306,
  complementary 1539/187/294, iterationrp 1033/110/165, photon 1296/131/219, solas 1686/187/324.

EDIT RULES:
- Comments ONLY — no code/blank-structure changes beyond removing whole comment lines. Use the Edit
  tool (preserves per-file EOL; packs are CRLF/mixed-EOL). Never whole-file rewrites via PowerShell.
- '#define X v // [list]' option lines byte-exact (functional Iris sliders).
- Big /* */ lib header -> ONE line. '// ==== section ====' multi-line banners -> ONE line. KEEP:
  '#endif // NAME' tails, SSBO struct field comments, the 1-line "bare #ifdef registers the Iris
  toggle" notes (real Iris trap), the irl_ sampler exact-name note.
- Never leave '#ifdef'/'#define' text or '*/' inside a comment (Iris option scanner / JCPP traps).
- shaders.properties + lang files: DO NOT TOUCH ('######## IRLITE' banners are asserted by gen
  scripts; option.*.comment= lines are user-facing content, not comments).
- If a gen script asserts a specific comment line (IndexOfLine / StartsWith), keep that line exact.
- Java mod code is NOT involved in this task.

PLAN: 6 parallel agents (one per pack). Each: read its gen script (learn spliced files/regions and
assertions; for Photon read the patch @file/+file ops instead) -> backup target files to $env:TEMP ->
clean lib + inject-block comments -> assert-comments-only.ps1 per edited file -> rerun gen (except
Photon) -> apply+diff validate -> report before/after comment counts. Main session afterwards: grep
all patches for lineage words (BSL|Solas|Photon|Complementary|IRLEngine outside their own pack),
spot-check, propose ONE commit (patches + gen scripts + tools/assert-comments-only.ps1; Shadres is
untracked). Per [[commit-checkpoints]]: NEVER commit without user confirmation.

---

## Связь с IRL-redactor (объединение памяти 2026-06-18)

- **Применимость:** shader-inject — Применимо к IRLite-side авторингу .irlights (источник правды; синкается в redactor через copy-patches.ps1) — это shader-inject контент. Дополняет reference-edit-routing-by-area (кто владеет .irlights) и project-irl-sync-strategy (контент 6 паков идентичен, IRLite владеет генерацией): здесь — конкретная история чистки комментов и точные edit-правила пайплайна Shadres→gen→apply+diff.
- **Связанные в redactor:** [[reference-edit-routing-by-area]], [[project-irl-sync-strategy]] (complements)
- Источник: портировано из памяти IRLite; оригинал оставлен как архив.
