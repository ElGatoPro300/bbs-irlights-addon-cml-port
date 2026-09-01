package qualet.irlite.client.ui.debug;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import qualet.irlite.client.diag.VlProfiler;

/** Debug controls rendered at the bottom of the IRLights presets section. */
public final class UIDebugSection
{
    /** Debug UI is hidden by default. Opt back in with {@code -Dirlite.debug=true}
     *  — the profiler itself and its {@code -Dirlite.profileVl} boot switch are
     *  untouched; this gate only decides whether the settings button is surfaced.
     *  Kept (not deleted) so the section returns with a single flag. */
    private static final boolean DEBUG_UI = Boolean.getBoolean("irlite.debug");

    private UIDebugSection()
    {}

    public static void append(UIScrollView options, Runnable rebuild)
    {
        if (!DEBUG_UI)
        {
            return;
        }

        UILabel header = UI.label(L10n.lang("irlite.ui.debug.title"));
        header.marginTop(6);
        options.add(header);

        boolean on = VlProfiler.isEnabledOrPending();
        IKey labelKey = on
            ? L10n.lang("irlite.ui.debug.hide_overlay")
            : L10n.lang("irlite.ui.debug.show_overlay");

        UIButton button = new UIButton(labelKey, (b) ->
        {
            VlProfiler.toggle();

            if (rebuild != null)
            {
                rebuild.run();
            }
        });

        button.tooltip(L10n.lang("irlite.ui.debug.tooltip"));
        options.add(button);
    }
}
