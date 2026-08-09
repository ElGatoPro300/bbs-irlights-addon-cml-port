---
name: project-vram-collapse-investigation
description: "АКТИВНОЕ РАССЛЕДОВАНИЕ 2026-07-19: VRAM-коллапс в редакторе — java-процесс детерминированно съедает ~9.6 GB (двумя ступенями +7.3/+2.3) через ~20 с после джойна мира → free 10.2 GB → 0.5 GB → вечный PCIe-пейджинг → 15 FPS при невинных GL-таймерах. Юзер подозревает тени. Инструментарий готов (профайлер редактора 4d5b93c, alloc-телеметрия core db731ac, GPU-семплер). NEXT: репро с alloc-строками → поимённая атрибуция."
metadata: 
  node_type: memory
  type: project
  originSessionId: fc514b3c-22b5-4879-b601-d21f5bbad7a6
---

# VRAM-коллапс в редакторе (15 FPS, «даже без теней»)

## СИМПТОМ И УСТАНОВЛЕННЫЕ ФАКТЫ (2026-07-19, две сессии репро)
- Мир юзера (деревня, 0 размещённых ламп — весь point-свет = АВТО-СВЕТ от блоков; autoLights у юзера ВКЛ). CR_IRLights пак, RTX 3060 12 GB.
- Через ~20-40 с после джойна: free VRAM 10.2 GB → ~0.5 GB. Репро 1: одной секундой (14:35:21→22). Репро 2: двумя ступенями за ~7 с (java dedicated 657 MB → 7 976 → 10 295 MB; +7.3 GB, потом +2.3 GB). ОБЪЁМ ДЕТЕРМИНИРОВАН ~9.6 GB.
- Атрибуция по процессам (перф-счётчики Windows `\GPU Process Memory(*)\Dedicated Usage`, семплер 1 Гц): скачок ЦЕЛИКОМ в java-процессе клиента; firefox/dwm неподвижны. Значит in-process: наш GL / sodium / iris / ванила.
- Дальше вся сессия живёт на ~500 MB free → драйвер пейджит текстуры через PCIe → 15-20 FPS, при этом ЗАМЕРЕННЫЙ GPU (bake+Iris-пассы) ≈ 5-10 ms — узкое место ВНЕ GL-таймеров. Отключение теней (hold) НЕ лечит FPS после коллапса — голод уже наступил.
- Запаркованная аномалия «линейная деградация → F11 сброс» (см. [[plan-shadow-bake-track]]) почти наверняка тот же корень: F11 = mode change → драйвер выселяет мусор.
- Юзер: «кажется, это происходит из-за теней» (наблюдение без alloc-строк ещё).
- Оценки по javadoc IRLShadowQuality НЕ объясняют объём: весь shadow-стек MEDIUM < 1 GB, ULTRA ≈ 5-6 GB (point F=4096 клампится до 2048 по GL_MAX_TEXTURE_SIZE) — а съедено 9.6.

## ИНСТРУМЕНТАРИЙ (готов, ЗАКОММИЧЕН)
- Редактор-профайлер (irlights 4d5b93c): порт VlProfiler (без VlSweep), runtime-тумблер в ImGui-секции «perf» + HUD; строки `[irl-redactor] gpu:/bake:/vram:`. Плюс hold-bake: «Отложить бейк теней» (гейт castsShadows в LightDriver.emitPoint/emitSpot; лампы рендерятся без тени, tile −1, кэш не создаётся), «Откладывать при входе» (деф. ВКЛ, JOIN-хук), кнопка «Запустить бейк сейчас» → штатный cold-start путь.
- Alloc-телеметрия core (db731ac): `[irl-core] alloc: <тег> <размеры> = <MB> MiB | vram free <до>` на ВСЕХ текстурных аллокациях shadow-стека (депт-атласы live+static, point-EVSM тиры+temp, point-pyr тиры, spot-EVSM+scratch×2, spot-pyr) + `[irl-core] quality: <пресет> (эффективные размеры после клампа)` в IRLShadowQuality.apply. Хелпер ShadowAllocLog (mipChainBytes).
- GPU-семплер: PowerShell фон, `Get-Counter '\GPU Process Memory(*)\Dedicated Usage'` 1 Гц → scratchpad gpu-mem.csv (процессы >150 MB, pid→имя).

## NEXT SESSION
1. Репро с alloc-телеметрией: тот же мир, ~30 с. Вотчер на `vram: free [0-9]{1,3}/` в irlights/run/runclient-console.log. Сумма alloc-строк ≈ 9.6 GB → виновник поимённо; сумма скромная → sodium/iris/ванила (тогда per-allocation трейс глубже: apitrace или буферы).
2. Спросить/увидеть в логе фактический пресет качества юзера (quality-строка).
3. Кандидаты-подозреваемые при подтверждении теней: static-слой атласов (лениво удваивает депт), point-EVSM тиры при большом tierBlockCount, повторные delete+realloc при флипе качества/шейдеров без освобождения (утечка на ре-инициализации: delete() всех классов вызывается? проверить пары init/delete).
4. Рантайм редактора: runClient из irlights, JAVA_HOME=JDK21, лог run/runclient-console.log, фон. Профайлер = ImGui-тумблер (флаг -Dirlredactor.profileVl только пред-взводит).

Связь: [[plan-shadow-bake-track]] (родитель перф-трека, там же запаркованная F11-аномалия), [[plan-partial-tile-filter]] (закрытый трек сессии 8), [[addon-shadows]].
