---
name: project-port-1201
description: "Порт редактора (irlights) на MC 1.20.1 — ветка port/1.20.1 от main, ЗЕЛЁНЫЙ (2026-06-21). Самый маленький порт линейки: ТОЛЬКО деп-матрица + LWJGL-пин, НОЛЬ правок исходников (1.20.1<->1.20.4 API-расхождение ~ ноль, единственный кейс Box.getXLength уже обезврежен в ShadowBaker). runClient PASS."
metadata:
  node_type: memory
  mod_scope: redactor-only
  type: project
  originSessionId: 618840a1-1900-45ed-942d-bf892fb8621f
---

Порт IRL-redactor на MC 1.20.1 (собран зелёным 2026-06-21). Ветка port/1.20.1 форк от main (1.20.4). Закоммичено 9ccc599 (3 файла, +19/−8), запушено в origin quaIett/irl-editor (2026-06-21). Добавлено в build-trilogy.ps1 (теперь 8 целей; 1.20.1-папка = addon+editor) — см. [[tool-build-trilogy-script]]; вся трилогия пересобрана 8/8 зелёным, jar роздан в Desktop\IRLights\1.20.1\irl-redactor-1.20.1.jar.

Несущий инсайт: НОЛЬ правок исходников. 1.20.1 ближе всего к 1.20.4 (одна 1.20.x-эра, ДО всех рерайтов рендера 1.21.x). По [[project-irl-sync-strategy]] API-расхождение 1.20.1<->1.20.4 для этого кода ~ ноль; единственный source-кейс — Box.getXLength()/getYLength()/getZLength() (1.20.1) -> getLengthX/Y/Z (1.20.4+) — уже обезврежен на main через стабильные поля (box.maxX-box.minX) в ShadowBaker.java (~L1212, есть комментарий прямо про этот рейминг). Поэтому compileClientJava прошёл против yarn 1.20.1 без единой правки .java. Компилятор = ground-truth, других расхождений нет.

Дельта = 3 файла (только деп-матрица + пин):
- gradle.properties: minecraft_version=1.20.1, yarn_mappings=1.20.1+build.10, fabric_version=0.92.9+1.20.1 (loader 0.16.14 БЕЗ изменений).
- build.gradle: iris 1.7.2+1.20.1, sodium mc1.20.1-0.5.11, replaymod 1.20.1-2.6.19 (test-harness, зарезолвился штатно), + блок LWJGL 3.3.1 resolution-pin (configurations.all { resolutionStrategy.eachDependency { if group==org.lwjgl -> useVersion "3.3.1" }}). Причина пина: 1.20.1 везёт LWJGL 3.3.1, транзитив бампит до 3.3.2 -> Sodium отказывается стартовать. loom 1.9-SNAPSHOT / Java 17 — БЕЗ изменений (та же 1.20.x-эра, что main).
- fabric.mod.json: minecraft: ~1.20.4 -> ~1.20.1.
Деп-координаты 1.20.1 = ground-truth из отлаженного -Pmc=1.20.1 профиля аддона ([[project-irl-sync-strategy]], bbs-irlights-addon/build.gradle).

Статус:
- gradlew build ЗЕЛЁНЫЙ (44s). Продакшн-jar irl-redactor-1.0-obt.jar (7.6 МБ) вшивает через JiJ META-INF/jars/irl-core-1.0-obt.jar + все imgui-java (binding+lwjgl3+натив win/linux/macos). Структура идентична остальным веткам.
- Mixin-warning'и на remap (Cannot resolve class org/lwjgl/..., getRendertypeBreezeWindShader, sodium$moveToNextVertex) — из ЧУЖИХ модов (Sodium/Iris), версионно-нейтральный шум, не наш конфиг.
- runClient BOOT-CHECK PASS (2026-06-21, ветка port/1.20.1): мод грузится ((irl-redactor) IRL Redactor stub loaded), Iris собрал пайплайн overworld БЕЗ ошибок шейдеров (Creating pipeline ... minecraft:overworld ×2), Sound engine started (меню), вошёл в мир -> Loaded 1 lights for world 'sp-_' (персист через irl-core жив), чистый выход exit 0 (BUILD SUCCESSFUL). 0 краш / 0 Mixin-fail нашего конфига / 0 исключений в нашем коде. Шум только benign-dev (Iris compat-миксин ClassNotFound ShadersRender/HandRenderer, LWJGL JNI-warn от imgui natives, Sodium NVIDIA-workaround, ванильный Failed to load random sequence). ВИЗУАЛ (горит свет/тень глазами) юзером отдельно НЕ подтверждался в этой сессии. irl-core НЕ трогался (общее ядро).

Карта версий: [[reference-edit-routing-by-area]] (таблица версий×модов). Линейка портов: [[project-port-1211]] (1.21.1/1.21.4/1.21.11).
