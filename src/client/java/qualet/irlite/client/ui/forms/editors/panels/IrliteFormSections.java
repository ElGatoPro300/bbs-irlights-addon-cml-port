package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;

/**
 * Factory for BBS collapsible {@link UISection} groups.
 */
public final class IrliteFormSections
{
    /** Vertical gap between stacked sections; mirrors {@code UIConstants.SECTION_GAP}. */
    private static final int SECTION_GAP = 3;

    /** A collapsible section titled {@code title} containing {@code fields}. */
    public static UIElement section(IKey title, UIElement... fields)
    {
        UISection section = new UISection(title);
        section.fields.add(fields);
        return section;
    }

    /** Overload for String title */
    public static UIElement section(String title, UIElement... fields)
    {
        return section(IKey.constant(title), fields);
    }

    /** Like {@link #section} but with a top margin, for the 2nd+ section in a stack. */
    public static UIElement spaced(IKey title, UIElement... fields)
    {
        return section(title, fields).marginTop(SECTION_GAP);
    }

    /** Overload for String title */
    public static UIElement spaced(String title, UIElement... fields)
    {
        return section(title, fields).marginTop(SECTION_GAP);
    }

    private IrliteFormSections()
    {
    }
}
