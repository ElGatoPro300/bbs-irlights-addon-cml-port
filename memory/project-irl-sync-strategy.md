---
name: project-irl-sync-strategy
description: "Карта дрейфа аддон<->редактор: что SHAREABLE (свет/патчер/тени-оркестрация -> общий irl-core) vs форк (касты/UI/Iris-миксины per-mod). Ф0+Ф2 done, Ф1 отменён, Stonecutter не внедряется. Единственный недовынесенный шов = CookieArray. Версии = per-MC ветки (универс-jar отменён)."
metadata:
  node_type: memory
  type: project
  originSessionId: d3074ea8-540a-4196-9536-e6e3a3edfc26
---

ЗАДАЧА (решена): 2 sibling-мода с дублированным кодом (движок света/теней + патчер + 6 .irlights) × неск. версий MC -> больно разносить правки по «4 углам» матрицы {IRLite,redactor}×{версии} вручную. Итог: общее ядро irl-core (свет + патчер + оркестрация теней), моды = тонкие хосты. Карта правок = [[reference-edit-routing-by-area]]; тени = [[plan-irl-core-shadow-extraction]]; контракт света = [[addon-light-buffer-ssbo]].

ИЗМЕРЕННЫЙ ДРЕЙФ (durable reference — что shareable vs форк). Сверено состязательно 2026-06-17 (IRLite master 1.20.x qualet.irlite.client.* vs redactor org.qualet.irlredactor.* main/port):
1. ЯДРО СВЕТА (LightBuffer/LightRegistry/IrisShadersState) — share HIGH: байт-идентичны после нормализации package, 0 версионного дрейфа (нет MC-типов, только LWJGL/Iris/java.nio), BBS-free. -> в irl-core (org.qualet.irl.light.*). Шов адаптеров = LightRegistry.registerPoint/registerSpot. Per-mod адаптеры (НЕ shareable): IRLite=LightCollector+IRLightPositionResolver (BBS-аксессоры); redactor=LightScene/PlacedLight/LightStore/LightDriver/LightConfig.
2. ПАТЧЕР (7 файлов) — share HIGH: 5/7 чистая java.nio (PatchEngine/PatchResult байт-идентичны). -> в irl-core за интерфейсом PatcherHost{gameDir;shaderpacksDir;listShaderpacks;openFolder;bundledPatch}. Хосты: IRLite=BbsPatcherHost (UIUtils+Iris), redactor=RedactorPatcherHost (Util.open+Iris). Семантика = [[patcher]].
3. IRIS-МИКСИНЫ (2): SamplerBindingCubeArrayMixin = rename-only (де-факто шарится, но держим per-mod копию — Loom AW требует миксин в репо мода); ProgramSamplersBuilderMixin = ВЕРСИОННЫЙ (Iris 1.7.2/1.8.8 = 2-арг addDynamicSampler; 1.10.7 = 4-арг) -> НИКОГДА не шарить, per-mod И per-version.
4. ТЕНИ (light/shadow/, ~14 файлов) — оркестрация ВЫНЕСЕНА в irl-core (Ф2-shadow, [[plan-irl-core-shadow-extraction]]); за швом ShadowCasterSource per-mod остаётся ТОЛЬКО каст (BBS Form/Film/Morph vs vanilla-модель vs box). main(1.20.4)/port-1.21.1 = реал-модельные тени; port/1.21.11 = raw-GL переписка (отдельный мир, lockstep не покрывает).
5. .irlights (6 паков) — контент идентичен (md5 после LF-норм; разница только EOL). IRLite ВЛАДЕЕТ генерацией (Shadres/Original|Modification + gen-*.ps1; gen-photon НЕТ — photon руками); redactor = чистый потребитель -> одно-направл. copy-patches.ps1 + .gitattributes *.irlights text eol=lf в обоих. Подробно = [[sync-workflow]].

СТАТУС ФАЗ (итог):
- Ф0 (data: .gitattributes + copy-patches.ps1 + ре-норм EOL) — done на обоих.
- Ф2 (создать irl-core; перенести патчер за PatcherHost + LightBuffer/LightRegistry; includeBuild+include; удалить дубли; IRLite на embed-патчи) — done на ВСЕХ линиях. Оба мода объявляют "irl-core": "*" в depends (fail-fast если JiJ-core вырезан). Позже Ф2-shadow ([[plan-irl-core-shadow-extraction]]) превратил core в per-version Loom-модуль (mavenLocal-publish вместо composite) и вынес туда оркестрацию теней.
- Ф1 (бэкпорт 6 перф-опт redactor->IRLite) — ОТМЕНЁН: общая оркестрация теней даёт аддону перф даром.
- Ф3 / Stonecutter — НЕ внедряются (версии-линии живут отдельными ветками; per-version убил Loom-трение, ради которого Stonecutter рассматривался).

ОСТАВШИЙСЯ ШОВ (durable): единственная реально невынесенная экстракция = CookieArray (~150 строк GL, байт-идентичный дубль addon<->redactor, GPU-only, ROI > риск) — кандидат в core. НЕ выносить: .irlights-дедуп (противоречит IRLite-владению генерацией, п.5); IrisShadersState за интерфейс (добавило бы зависимость core->Iris). Тот факт, что addon-jar (306KB) > core (69KB) — СТРУКТУРНО (JiJ + контент), не недоделка.

ВЕРСИОНИРОВАНИЕ (итог): единый универс-jar (Route A -Pmc=) СНЯТ — заменён per-MC ветками после Ф2-shadow: MC-типизированный core => per-MC (на каждую MC свой core через publishToMavenLocal; intermediary версионно-специфичны). Карта версий×модов = [[reference-edit-routing-by-area]]; сборка = [[tool-build-trilogy-script]].

Историческая детализация (Gradle composite-рецепт, PoC Loom-развязки, PatcherHost-перенос, исполнение коммитов Ф0/Ф2, Stonecutter-анализ) свёрнута 2026-06-29 — поднять из git-истории / [[plan-irl-core-shadow-extraction]] при необходимости.
