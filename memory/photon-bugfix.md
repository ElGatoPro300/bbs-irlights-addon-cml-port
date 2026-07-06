---
name: photon-bugfix
description: "IRLite->Photon post-port BUG TRACKER — known issues after the port phases landed; per-bug symptom/repro/suspect/status + the bugfix workflow (edit Modification only, sync-loop test, one focused fix per bug, commit per confirmation, regen photon.irlpatch after)."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: project
  originSessionId: photon-bugfix
---

Bug tracker for the IRLite->Photon inject AFTER the port phases landed. Parent: [[MEMORY]]. Port roadmap + where things hook = [[photon-pipeline]]; inject internals = [[shader-irlite-glsl]] / [[shader-shadow-sampling]] / [[shader-volumetric]] / [[shader-settings]]; integration reference = [[ref-irlengine-photon-patch]].

WORKFLOW (same as the port):
- Edit ONLY Shadres/Modification/Photon — never Shadres/Original/Photon (pristine diff baseline; folders refactored 2026-06-11, see [[sync-workflow]]). Test via the sync loop: copy changed files Modification/Photon -> run/shaderpacks/Photon_IRLite, then runClient or hot shader-reload.
- One bug = one focused fix = one commit. Commit ONLY on user confirmation ([[commit-checkpoints]]); repo style = short Russian subject; watch the gitignore gotcha (new shader file needs git add -f).
- If a fix changes the inject, RE-SYNC the affected shader doc AND remember patches/photon.irlpatch must be regenerated/updated from the final Modification.
- A bug in the SHARED inject logic (not Photon-specific glue) likely affects the IterationRP patch too — note it and fix both / port the fix.

HOW TO LOG A BUG: one entry per bug — ID, SYMPTOM, REPRO, SUSPECT (cause + files/lines), STATUS (open / investigating / fixed-unverified / VERIFIED). Once VERIFIED and committed, the git history records it — trim the entry (keep the list = live open work, not an archive).

OPEN BUGS:

- B-OUTLINE-TRANSLUCENT (CLOSED 2026-06-28) — был про НОВЫЙ Blender-style outline (не инкал BBS-модели: они в translucent-проходе, а silhouette-маска читалась в d4 пре-translucent из combined_depth_tex/colortex1). Закрыт УДАЛЕНИЕМ фичи: новый outline убран из Photon, вместо него вернули СТАРЫЙ IRLEngine LocalLightOutline (Fresnel backlight-rim). См. [[project-photon-outline-switch-to-old]]. WATCH: старый rim тоже считается в d4 (combined_depth_tex), т.е. видит только solid-силуэты — translucent-модели могут не давать кромку; если всплывёт при улучшении старого outline — лечить как тут (post-translucent проход) или принять solids-only.

- WATCH (из закрытой цепочки B1) — bob-flicker block self-shadow acne НЕЗАГАРЖЕН. B1's GL_FRONT second-depth workaround откатан в disableCull, так что terrain self-shadow acne может мерцать с view-bobbing on. Если всплывёт — фиксить маленьким КОНСТАНТНЫМ depth bias на block-пассе, НЕ FRONT-cull (peter-pan) и НЕ slope-scaled glPolygonOffset (взрывался на grazing-углах на cube-гранях point-света, толкая block depth за far plane = нет теней). См. [[addon-shadows]] (его FRONT-cull повествование теперь устарело). Контекст: вся сага «block-тени пропадают с ванильным ModelBlock» ЗАКРЫТА — opaque фикс 23ac3f4 (матрицы), cutout фикс 6e5c3fd (выставить view/proj света ДО cutout-цикла, т.к. cutout-блоки >1 RenderLayer делят Immediate fallback BufferBuilder и getBuffer(newLayer) авто-флашит прошлый слой посреди цикла с тогда-испорченными матрицами). bbmodel не ломался = прямой VAO, без RenderLayer. Оба коммита = pack-agnostic Java (Photon + IterationRP); без shader/patch-правок.

TRIAGE / WATCH:

- LESSON (shadow read-path acne, FIXED 2026-06-09, commit 2eef284): «staircase»/acne на ребристых полах = инжект УРОНИЛ receiver normal-offset IRLEngine. Фикс = irlite_spotShadow/irlite_pointShadow теперь берут vec3 normal и сдвигают receiver fragWorld += (N + L) * IRLITE_SHADOW_NORMAL_OFFSET (0.05) перед сэмплом — зеркалит IRLEngine ir_lights.glsl:1253. НЕ slope-scale depth bias вместо этого (пробовали -> только меняет acne на peter-panning). SHARED инжект -> ВСЁ ЕЩЁ ПОРТИРОВАТЬ в iterationrp.irlpatch И в photon.irlpatch при его регене. Детали: [[shader-shadow-sampling]].

- T1 — «с Diffuse OFF у источника остаётся слабый свет». ЛИКЕЛИ НЕ БАГ: IRLITE_DIFFUSE / IRLITE_SPECULAR / IRLITE_VOLUMETRIC = независимые каналы, все ON по умолчанию (irlite_lights.glsl:42-44); оба d4 call-site корректно гейтятся (#ifdef IRLITE_DIFFUSE :589, #ifdef IRLITE_SPECULAR :605). Остаток = VL tip-glow у лампы (IRLITE_VL_TIP_BOOST 1.5) и/или specular. Чтобы погасить свет полностью — выключить ещё Specular+Volumetric или intensity 0. STATUS: triage — баг только если дизайн подразумевает «Diffuse off => нет IRLite-света вообще»; подтвердить с юзером, иначе закрыть.

- KNOWN-LIMIT (из [[photon-pipeline]] Phase 1): Photon get_specular_highlight зануляет IRLite specular в new-moon midnight overworld (sunAngle>0.5 && moonPhase==4) и размеряет highlight по угловому радиусу солнца/луны — IRLite spec наследует оба, т.к. реюзит GGX-highlight Photon. STATUS: open — пересмотреть только если укусит (фикс = выделенный IRLite specular или явный light size вместо celestial sizing).

Связь: shader-inject (IRLite-side авторинг GLSL-инжекта; per-pack баг-трекер Photon, синк в redactor через copy-patches.ps1). Дополняет [[project-port-1211]], [[plan-irl-core-shadow-extraction]], [[reference-edit-routing-by-area]]. Источник: память IRLite.
