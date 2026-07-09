---
name: project-imgui-axiom-collision
description: "Редактор крашил MC (NoSuchMethodError на imgui init) при открытии оверлея рядом с модом с UNRELOCATED imgui-java (Axiom, 1.20.1). Фикс 2026-06-21: ImGuiRuntime не крашит (try/catch+disabled+forceClose). 2026-07-09 PR #2 влит (main+4 порта): init через рефлексию invokeBackend, резолв по имени+параметрам, игнор return-type drift init()Z vs init()V. Юзер подтвердил бой на 1.20.1."
metadata:
  node_type: memory
  mod_scope: redactor-only
  type: project
  originSessionId: 618840a1-1900-45ed-942d-bf892fb8621f
---

Краш редактора при коллизии imgui-java с Axiom (фикс 2026-06-21).

Симптом: открытие ImGui-редактора (клавиша J) роняло Minecraft:
```
java.lang.NoSuchMethodError: 'boolean imgui.gl3.ImGuiImplGl3.init(java.lang.String)'
  at org.qualet.irlredactor.imgui.ImGuiRuntime.ensureInit(ImGuiRuntime.java:62)
```
Краш из БОЕВОГО инстанса (PrismLauncher, MC 1.20.1) с Axiom 5.3.0 + BBS 2.3-1.20.1 + BiomesOPlenty/TerraBlender/Caxton и т.д.

Корень (проверено): javap нашей бандл-imgui-java-lwjgl3 1.89.0 -> ImGuiImplGl3 ИМЕЕТ обе init() и init(String). Раз в рантайме NoSuchMethodError — значит загружается ЧУЖОЙ imgui.gl3.ImGuiImplGl3. Axiom (Moulberry) тащит imgui-java UNRELOCATED (тот же пакет imgui.gl3.*); на общем classpath Fabric класс резолвится в копию Axiom'а без init(String). На прошлых тестах (1.20.4/1.21.11) Axiom'а рядом не было -> не всплывало. imgui-java НЕЛЬЗЯ безопасно релокейтить (натив-JNI завязан на имя пакета) -> коллизию с любым unrelocated-imgui модом со своей стороны не убрать совсем, только пережить.

Фикс (коммиты: main 175123d / port/1.20.1 ce458c7 / 1.21.1 27187cf / 1.21.4 6e56ce2 / 1.21.11 7454d79 — все запушены в quaIett/irl-editor). 2 версионно-нейтральных файла (ImGuiRuntime.java идентичен на всех ветках; LightEditorScreen.java — на 1.21.11 ручной мерж из-за applyBlur/renderDarkening + лишнего коммента в renderActiveOverlay):
- ImGuiRuntime: ensureInit обёрнут в try/catch(Throwable) -> при провале ставит disabled=true + лог (warn), НЕ пробрасывает. Init с fallback: init("#version 150") -> при NoSuchMethodError -> init() (есть во всех версиях). frame() гардит disabled и ловит LinkageError (наши UI-баги = RuntimeException, всплывают как обычно). +аксессор isDisabled().
- LightEditorScreen.renderActiveOverlay: при isDisabled() зовёт новый forceClose(mc) (сброс editorVisible + setScreen(null)) — иначе курсор/ввод заперты за пустым оверлеем.
Гарантия по построению: редактор НИКОГДА не роняет клиент (worst-case — тихо отключается). Best-effort: если imgui Axiom'а в остальном совместим, откроется.

Проверка / каветы:
- ЮЗЕР ПОДТВЕРДИЛ В БОЮ (2026-06-21): «всё работает» — редактор открывается рядом с Axiom на 1.20.1, best-effort сработал (не просто «не крашит», а реально функционирует). Локально агентом не верифицировалось (того инстанса нет), подтверждение = юзер.
- Глубже: ImGui-контекст в нативе глобален; наш createContext()/destroyContext() теоретически может мешать собственному ImGui Axiom'а (отдельная проблема сосуществования, вне «не крашить»).

Обновление 2026-07-09 (PR #2 в quaIett/irl-editor, влито approve+merge как quaIett). Механизм init в ImGuiRuntime.ensureInit переведён с прямого вызова + catch(NoSuchMethodError) на рефлексию. Новый invokeBackend(backend, name, paramTypes, args): getMethod по имени+типам параметров -> invoke, игнорирует return type; true если метод есть и вызван, false если overload отсутствует (тогда fallback на no-arg init); реальную ошибку существующего метода пробрасывает (RuntimeException/Error как есть), не глотает. GLFW-init (implGlfw.init) тоже через invokeBackend. Уточнение диагноза vs 2026-06-21: init(String) в билде Axiom не отсутствует, а дрейфует return type — boolean в нашей imgui-java-lwjgl3 1.89.0 vs void в старом билде Axiom; прямой invokevirtual компилируется в init()Z и падает NoSuchMethodError против загруженного init()V, рефлексия резолвит по имени+параметрам и линкуется против того imgui, что победил на classpath. Внешняя обёртка try/catch(Throwable)->disabled+forceClose из фикса 2026-06-21 сохранена. Влито: main (merge dff7784) и как imgui-часть порт-PR #4 port/1.20.1 / #5 port/1.21.1 / #6 port/1.21.4 / #7 port/1.21.11 (шейдерная camera-relative часть тех же порт-PR — отдельно, PR #3 в main).

Связь: [[reference-edit-routing-by-area]] (UI=только redactor), [[project-irlite-base-ported]], [[project-port-1201]] (где всплыло), [[project-bbs-version-drift-compat]] (та же волна PR 2026-07-09).
