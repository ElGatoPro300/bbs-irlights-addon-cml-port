---
name: reference-bbs-fs-not-refreshed
description: "Для IRL-redactor смотреть исходники оригинального bbs-fs, НЕ форк bbs-refreshed_20_4."
metadata:
  node_type: memory
  type: feedback
  originSessionId: 82810e39-3ef2-4f2f-ae87-b989ed983dd3
---

При работе над IRL-redactor смотреть исходники оригинального BBS в C:\Users\Qualet\Documents\Project\Minecraft\BBS\bbs-fs (пакет mchorse.bbs_mod), а НЕ форк bbs-refreshed_20_4 — несмотря на то, что много additional working dirs указывают внутрь refreshed.

Why: refreshed — сильно разошедшийся приватный форк (UI-редизайн, IRLights-шум, version drift 2.1 vs 2.2-dev1). Канон/апстрим для нового аддона = bbs-fs.
How: при поиске API/паттернов BBS (формы, панели, события bbs-addon, Iris-утилиты) грепать и читать из bbs-fs. Память форка bbs-refreshed_20_4 (отдельная memory-база другого проекта) использовать только как контекст идей, не как источник кода.
