---
name: plan-lens-flare
description: "Lens flare для IRL-источников — PLAN-only (обсуждение 2026-07-02, кода нет); каркас BSL + инфраструктура старого IRL, единый блоб как VL."
metadata: 
  node_type: memory
  type: project
  originSessionId: 59dc683a-d78e-42fd-9f3c-e9285a6a328b
---

Статус: PLAN-only, код не начат. Обсуждение 2026-07-02.

Референсы (оба локальные):
- Старый IRLEngine: Shadres\IRL_Shaders\photon\photon_old\include\lighting\ir_lens_flare.glsl. Только гауссово ядро + гало (визуально = glow-спрайт, не flare — причина недовольства видом). Ценное: per-light flareIntensity (0=off), 3x3 depth-PCF окклюжн (мягкое гашение за геометрией), world-distance fade, cull за-камерных/внеэкранных (margin 1.25), хук до bloom/DoF/TAA (Photon: c1_blend_layers). Был на 16 uniform-лампах.
- BSL: Shadres\photonics\BSL_irl\shaders\lib\post\lensFlare.glsl. ~19 элементов: 6 BaseLens-призраков + 5 OverlapLens + 5 PointLens + 2 RingLens + AnamorphicLens + 2 RainbowLens. Призраки вдоль оси источник-центр экрана (texCoord + lightPos*dist - 0.5); fovmult от зума; falloffIn (гасит цепочку у центра) / falloffOut (гасит всё у края). Палитра захардкожена под солнце.

Принятые решения:
- Каркас = BSL-композит, но масштаб элементов меньше солнечного; палитра НЕ хардкод — тинт цветом лампы с лёгким hue-сдвигом по цепочке призраков.
- Из старого IRL забрать: per-light слайдер, 3x3 depth-окклюжн, world-distance fade.
- 2 пресета: тонкий (ядро+гало+пара призраков) и киношный (полная цепочка+анаморф).
- Default OFF (#define в Iris UI) + per-light flare=0 по умолчанию — паттерн outline.
- Математика в общий .irlights блоб (как VL/outline/cookie) — единая для всех 6 паков, правится в одном месте; per-pack только хук-сайт до bloom-input. depthtex0 стандартен во всех паках.
- Перф: uniform-branch (flare<=0 / offscreen) когерентен для варпа — выключенные лампы ~бесплатны; кап активных флейр-ламп (8-16) как страховка. Опция на потом: half-res проход как Complementary VL (флейр низкочастотнее VL).

Открытые вопросы:
- Слот в SSBO: контракт 6xvec4 забит (cookie.w = flags, занят bit0). Вариант A (рекомендован): 7-й vec4, 112б/лампа, bump CONTRACT_VERSION [[addon-light-buffer-ssbo]], +3 запасных слота. Вариант B (отклонён как хрупкий): упаковка в дробную часть cookie.w.
- Очередность паков; точки хука 5 паков кроме Photon.
- UI: поле в форме IRLite + ImGui-панель редактора + persist в .irlights-сцену; глобалки в shaders.properties (size, falloff distance, occlusion on/off, preset).

Связь: [[shader-irlite-glsl]], [[shader-volumetric]] (half-res образец), [[project-photon-outline-switch-to-old]] (паттерн default-OFF + reuse старого IRLEngine).
