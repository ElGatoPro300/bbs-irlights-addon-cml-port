---
name: project-imgui-axiom-collision
description: "Редактор крашил MC (NoSuchMethodError ImGuiImplGl3.init(String)) при открытии оверлея рядом с модом, тащащим UNRELOCATED imgui-java (наблюдалось с Axiom на 1.20.1). Фикс 2026-06-21: ImGuiRuntime не крашит (try/catch+disabled-флаг) + fallback init()+force-close оверлея. На ВСЕХ 5 ветках, запушено. Юзер подтвердил: работает рядом с Axiom."
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

Связь: [[reference-edit-routing-by-area]] (UI=только redactor), [[project-irlite-base-ported]], [[project-port-1201]] (где всплыло).
