---
name: project-bbs-version-drift-compat
description: "Два класса version-drift: (1) BBS раздаётся разными билдами под одним version-string — детектить опциональные BBS-классы в рантайме; (2) Minecraft-маппинги дрейфуют между версиями MC — setBlockState 4-арг (...II)Z <=1.21.1 vs 3-арг (...I)Z 1.21.2+, WorldBlockChangeMixin, OPEN PR #1."
metadata:
  type: project
---

BBS распространяется РАЗНЫМИ билдами под ОДНИМ и тем же version-string (напр. `2.3.1-1.20.1`): dev-билд у разработчика (в `libs/`, есть свежие классы) vs публичный билд у юзера (тех же классов нет). Номер версии их не различает, поэтому `depends` в fabric.mod.json бесполезен.

Симптом: compile OK против `libs/bbs-*.jar`, а у юзера рантайм-краш при открытии редактора/инициализации:
- `NoClassDefFoundError`/`ClassNotFoundException` на новом UI-классе (реально: `mchorse.bbs_mod.ui.framework.elements.UISection`, добавлен в свежих 2.3.x, нет в публичном 2.3 и 2.3.1);
- `NoSuchMethodError` на новой сигнатуре (реально: `SettingsBuilder.category(String, Icon)` — нет в BBS 1.7).

**Why:** обновление версии юзером не помогает, если публичного билда с нужным символом не существует — чинить надо на стороне аддона.

**How to apply:** изолировать опциональный BBS-символ в отдельный класс-фабрику (пример: `IrliteFormSections` — единственный, кто `new UISection`), детектить наличие через `Class.forName(name, false, loader)` в классе БЕЗ прямых ссылок на тип (`IrliteBbsCompat.SECTIONS`), звать фабрику только при `true`, иначе фолбэк (плоский список). Проверка корректности: `javap -v` конструктора-потребителя не должен содержать class-ref на опциональный тип (только строку в детекторе) — иначе верификатор на старом BBS всё равно грузит класс и падает. Классы: `src/client/java/qualet/irlite/client/ui/forms/editors/panels/IrliteBbsCompat.java`, `IrliteFormSections.java` (2026-07-09). Область: только master (1.20.1/1.20.4, BBS 2.3.1 с UISection). Ветка `port/1.21.1` собирается против `bbs-2.2.1-1.21.1` где UISection НЕТ вообще — панели там плоские by design, детект/фикс неприменим и переносить не нужно. См. [[addon-ui-config]], [[project-forge-connector-compat]], [[reference-bbs-fs-not-refreshed]].

Второй класс дрейфа (2026-07-09): сигнатуры Minecraft-маппингов между версиями MC (не билды BBS). Кейс: net.minecraft.world.World.setBlockState. 4-арг (BlockPos, BlockState, int flags, int maxUpdateDepth) = дескриптор (Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z на MC <=1.21.1 (вкл. 1.20.1/1.20.4/1.21.1); Mojang убрал maxUpdateDepth примерно в 1.21.2 -> 3-арг (...;I)Z на 1.21.2+ (1.21.4, 1.21.11). WorldBlockChangeMixin @Inject (irlite$invalidateBlockShadows) таргетит эту сигнатуру. На 2026-07-09 на ВСЕХ ветках стоит 4-арг (...II)Z — для 1.21.4/1.21.11 неверно: целевой метод не резолвится, mixin не применяется (инвалидация block-shadows не работает). Статус OPEN: PR #1 (TotallyNotTimXD, quaIett/irl-editor) даёт 3-арг, но нацелен в main (=1.20.4, где 4-арг правильна) — в main НЕ мержить (регрессия: 3-арг перегрузка делегирует 4-арг, HEAD-инжект сработает реже). Действие: перенести 3-арг в port/1.21.4 и port/1.21.11, оставить 4-арг в port/1.20.1 и port/1.21.1, затем закрыть PR #1. Файл: src/client/java/org/qualet/irlredactor/mixin/client/WorldBlockChangeMixin.java. Точную границу версии (1.21.2 vs .3 vs .4) сборкой не верифицировал; надёжный признак — main/1.20.x/1.21.1 сейчас компилируются с 4-арг. Связь: [[project-imgui-axiom-collision]] (та же волна PR в irl-editor 2026-07-09).
