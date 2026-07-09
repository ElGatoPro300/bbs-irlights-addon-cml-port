package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Factory for titled form sections (header + field column).
 *
 * <p>BBS CML Edition removed {@code UISection}; this mirrors the layout used by
 * {@code UIModelSection} and works across BBS builds.</p>
 */
public final class IrliteFormSections
{
    /** Vertical gap between stacked sections; mirrors {@code UIConstants.SECTION_GAP}. */
    private static final int SECTION_GAP = 3;

    /** A section titled {@code title} containing {@code fields}. */
    public static UIElement section(String title, UIElement... fields)
    {
        UIElement section = new UIElement();
        section.column().vertical().stretch();

        UILabel header = UI.label(IKey.constant(title)).background(() -> 0x88000000 | BBSSettings.primaryColor());
        UIElement body = new UIElement();
        body.column().stretch().vertical().height(20);
        body.add(fields);

        section.add(header, body);
        return section;
    }

    /** Like {@link #section} but with a top margin, for the 2nd+ section in a stack. */
    public static UIElement spaced(String title, UIElement... fields)
    {
        return section(title, fields).marginTop(SECTION_GAP);
    }

    private IrliteFormSections()
    {
    }
}
