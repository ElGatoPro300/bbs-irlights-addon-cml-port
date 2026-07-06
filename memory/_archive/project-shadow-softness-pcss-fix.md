---
name: project-shadow-softness-pcss-fix
description: "⛔ ПОСТ-МОРТЕМ (2026-06-18, port/1.21.11): попытка починить «квадрат» теней при росте Shadow Softness. ВСЕ изменения сессии ОТКАЧЕНЫ к старту (git checkout миксина; шейдер Modification+run восстановлен из коммиченного photon.irlights). Баг НЕ исправлен. ИСКЛЮЧЕНО эмпирически: (1) PCSS-алгоритм шейдера — point сделан байт-в-байт = эталон IRLEngine, квадрат остался; (2) фильтр сэмплера — добавление LINEAR (bindSamplerToUnit 0) в cube-array миксин не дало визуального эффекта. ОСТАВШИЙСЯ подозреваемый: БЕЙК (содержимое cube-карт глубины). Эталон-оракул: C:\\Users\\Qualet\\Media\\Archive\\AI_Slop_Projects\\IRLEngine\\Shaders\\Patched\\photon_IRLights\\shaders\\include\\lighting\\ir_lights.glsl."
metadata:
  node_type: memory
  mod_scope: shader-inject
  type: project
  originSessionId: 8b96c0a2-5e0f-437d-9f4e-2bcda4ef1121
---

# Пост-мортем: «квадрат» теней при росте Shadow Softness (НЕ исправлено, всё откачено)

Баг (юзер, 2026-06-18, на port/1.21.11 редакторе, виден на ВСЕХ версиях IRL): при Shadow Softness = 0/0.10 тень нормальная, при увеличении (глоб. Iris-параметр `IRLITE_SHADOW_SIZE` ИЛИ per-light `cone.w`=bulbSize, оба → `lightSize`) вокруг тени появляется **зернистый квадратный ореол**; на **point хуже**, на spot слабее. Эталон-предшественник IRLEngine этой проблемы НЕ имеет.

## ⛔ Итог сессии: всё откачено, баг открыт
Три попытки фикса (см. ниже) НЕ помогли / ухудшили. По просьбе юзера **все изменения отёбачены к старту сессии**: миксин `SamplerBindingCubeArrayMixin.java` — `git checkout` (дерево чистое, HEAD=e925a07); шейдер `…/Modification/Photon/.../irlite_lights.glsl` и `irlights/run/shaderpacks/Photon/.../irlite_lights.glsl` — восстановлены из коммиченного `irlights/.../patches/photon.irlights` (+file блок между `<<<`/`>>>`, ~745 строк, CRLF). Pre-session подтверждён (есть texelWorld, нет maxFilter/feather/searchTan).

## ЧТО ЭМПИРИЧЕСКИ ИСКЛЮЧЕНО (не повторять!)
1. **Алгоритм PCSS в шейдере** — НЕ причина. `irlite_pointShadow` был сделан байт-в-байт = эталонный `IR_PointShadowSample` (убраны texelWorld/textureSize, texel-полы, texel-scaled normal-offset → плоский; penumbra без клампа; search без пола). **Квадрат остался.**
2. **Фильтр сэмплера (LINEAR vs NEAREST)** — НЕ причина. Гипотеза: cube-array миксин отменяет Iris `SamplerBinding.updateSampler` на HEAD и теряет `bindSamplerToUnit(unit, sampler)` → стейл sampler-object форсит NEAREST. Добавил `IrisRenderSystem.bindSamplerToUnit(this.textureUnit, 0)` → **визуально НИЧЕГО не изменилось** (значит куб и так читался LINEAR, либо фильтр не при чём).
3. **Два воркфлоу-диагноза НЕВЕРНЫ:** «out-of-domain PCF тапы = lit» и «binary blocker gate `blockerCount==0→1.0`» — оба механизма ЕСТЬ И В ЭТАЛОНЕ, который работает. Мои фиксы на их основе (round-1 капы maxFilterNdc/maxFilterTan+drop-and-divide+угловой футпринт; round-2 feather по blockerFrac) дали жёсткий квадрат на 0.6, а feather протащил его на 0.0. ОТКАЧЕНЫ.
4. **Евклидов face-agnostic компэр** (storedEuclid=distFromDepth01*len/maxaxis vs refDist) — ОТКЛОНЁН (затапливает плоский пол самозатенением, ~42680 флипов; вернёт «6-лучевую звезду»). Доминантно-осевой refDepth НЕ трогать.

## ОСТАВШИЙСЯ подозреваемый: БЕЙК (Java-генерация cube-карт глубины)
Раз шейдер = эталон и сэмплинг LINEAR, а результат ≠ эталон → отличие в самих картах глубины. Зацепки (НЕ проверено в игре):
- Бейк рендерит **light-facing (ближнюю) грань, cull OFF** (`ShadowRenderer.java` ~стр.330 и ~497: «depth test keeps the nearest light-facing surface»). Плоский пол при этом сам себя пишет в глубину → при росте PCF-ядра (softness) грейзинг-тапы на БОКОВЫХ гранях куба читают пол под скользящим углом (плохая точность перспект. глубины) → самозатенение-acne на ШВЕ грани −Y/боковая (на полу это квадрат) → растёт с softness, на point хуже (у spot одна грань сверху, грейзинга нет). Это гипотеза, требует проверки.
- Проверять: рендерит ли бейк world-блоки (пол) в point-куб (`BlockShadowCollector`/`renderBlocksDepth`); сравнить с тем, что бейкал IRLEngine (вероятно НЕ world-террейн); культинг/біас/polygon-offset; формат/фильтр куба (`PointShadowArray.createArray`: DEPTH32F, LINEAR, seamless, COMPARE NONE — выглядит корректно).
- **Лучший следующий шаг — ВИЗУАЛЬНОЕ заземление**: чёткий скрин квадрата на макс. softness (где он — вокруг тени куба ИЛИ квадрат под лампой?), либо запустить runClient и осмотреть/покрутить softness самому (computer-use). Хватит чинить вслепую — три попытки на гипотезах провалились.

## Эталон-оракул (рабочий, без бага)
`C:\Users\Qualet\Media\Archive\AI_Slop_Projects\IRLEngine\Shaders\Patched\photon_IRLights\shaders\include\lighting\ir_lights.glsl` → `IR_PointShadowSample` (стр.~1060) / `IR_SpotShadowSample` (стр.~907). Маппинг имён: SL/IL.pos→posRadius.xyz, SL.outer→degrees(acos(cone.x)*2), bulbSize→cone.w, IRL_SHADOW_NEAR→0.05, IR_*→irlite_*. Iris-исходники для чтения: `…\Media\Archive\AI_Slop_Projects\IRLEngine\Source-libary\Iris-1.20.1` (память [[iris-source-library]] — путь устарел, реальный в архиве).

Связь: [[shader-shadow-sampling]], [[addon-shadows]] (бейк-сторона), [[project-shadow-1211-real-models]] (raw-GL бейк 1.21.11), [[reference-edit-routing-by-area]].
