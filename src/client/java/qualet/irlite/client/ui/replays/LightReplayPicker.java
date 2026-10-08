package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import io.netty.util.collection.IntObjectMap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import qualet.irlite.client.light.ReplaySelection;

/** Adapts Orbit/Tracker's actor picker to a light's set of replay IDs; owns no UI. */
public final class LightReplayPicker
{
    private LightReplayPicker()
    {}

    public static void open(UIContext context, UIFilmPanel panel, ReplaySelection.Selection current, Consumer<String> apply)
    {
        Film film = panel.getData();
        Map<String, IEntity> entities = new LinkedHashMap<>();
        IntObjectMap<IEntity> actors = panel.getController().getEntities();
        Set<String> selected = new LinkedHashSet<>();

        for (Replay replay : film.replays.getList())
        {
            if (!ReplaySelection.isSelectable(replay.form.get()))
            {
                continue;
            }

            String id = replay.getId();
            IEntity entity = actors.get(film.replays.getList().indexOf(replay));

            if (entity != null)
            {
                entities.put(id, entity);
            }

            /* Keep saved selections even when their entities aren't currently instantiated. */
            if (current.belongsTo(film) && current.replays().contains(id))
            {
                selected.add(id);
            }
        }

        context.replaceContextMenu(menu ->
        {
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, () ->
            {
                selected.clear();
                apply.accept(ReplaySelection.encode(film.getId(), selected));
            });

            for (String id : entities.keySet())
            {
                Replay replay = film.replays.getList().stream().filter(candidate -> candidate.getId().equals(id)).findFirst().orElseThrow();

                menu.action(Icons.CLOSE, IKey.constant(replay.getName()), () ->
                {
                    if (!selected.remove(id))
                    {
                        selected.add(id);
                    }

                    apply.accept(ReplaySelection.encode(film.getId(), selected));
                });
            }
        });

        if (context.contextMenu instanceof UISimpleContextMenu menu)
        {
            List<ContextAction> actions = menu.actions.getList();
            Map<ContextAction, String> rows = new LinkedHashMap<>();
            int index = 1;

            /* displayActors adds None, then instantiated actors in film order. */
            for (String id : entities.keySet())
            {
                rows.put(actions.get(index++), id);
            }

            Runnable refresh = () -> rows.forEach((action, id) -> action.icon = selected.contains(id) ? Icons.CHECKMARK : Icons.CLOSE);

            refresh.run();

            /* Use the native list's callback so picking more actors doesn't dismiss its menu. */
            menu.actions.callback = (picked) ->
            {
                if (!picked.isEmpty() && picked.get(0).runnable != null)
                {
                    picked.get(0).runnable.run();
                    refresh.run();
                }
            };
        }
    }
}
