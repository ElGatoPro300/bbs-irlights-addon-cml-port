package qualet.irlite.client.ui.presets;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.ArrayList;
import java.util.List;

import qualet.irlite.IrlitePresets;
import qualet.irlite.client.ui.IRLightsUIKeys;

/** The two preset rows drawn at the top of the IRLite settings section, above
 *  the individual knobs they drive.
 *
 *  These are not registered BBS values — the selected index is derived from the
 *  knobs themselves ({@link IrlitePresets#quality()}), so the buttons always
 *  show the truth and nothing has to be persisted or kept in sync. Picking a
 *  preset writes its members and rebuilds the panel, because the widgets below
 *  read their value only when they are built. */
public final class UIPresetSection
{
    private UIPresetSection()
    {}

    public static List<UIElement> build(Runnable rebuild)
    {
        List<UIElement> elements = new ArrayList<>();

        UILabel header = UI.label(IRLightsUIKeys.PRESETS);
        header.marginTop(6);
        elements.add(header);

        UICirculate quality = new UICirculate((b) ->
        {
            IrlitePresets.applyQuality(b.getValue());

            if (rebuild != null)
            {
                rebuild.run();
            }
        });

        for (IKey label : IRLightsUIKeys.QUALITY_LABELS)
        {
            quality.addLabel(label);
        }

        quality.setValue(IrlitePresets.quality());
        quality.w(90);

        elements.add(row(IRLightsUIKeys.QUALITY, quality, IRLightsUIKeys.QUALITY_HELP));

        UICirculate style = new UICirculate((b) ->
        {
            IrlitePresets.applyStyle(b.getValue());

            if (rebuild != null)
            {
                rebuild.run();
            }
        });

        for (IKey label : IRLightsUIKeys.STYLE_LABELS)
        {
            style.addLabel(label);
        }

        style.setValue(IrlitePresets.style());
        style.w(90);

        elements.add(row(IRLightsUIKeys.BEAM_STYLE, style, IRLightsUIKeys.BEAM_HELP));

        return elements;
    }

    /** Same shape UIValueFactory.column builds for registered values, but with a
     *  plain label instead of one resolved from a settings key. */
    private static UIElement row(IKey label, UIElement control, IKey tooltip)
    {
        UIElement element = new UIElement();

        control.removeTooltip();
        element.row(0).preferred(0).height(20);
        element.add(UI.label(label, 0).labelAnchor(0, 0.5F), control);
        element.tooltip(tooltip);

        return element;
    }
}
