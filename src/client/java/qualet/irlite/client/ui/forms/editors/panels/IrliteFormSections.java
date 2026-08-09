package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Factory for grouped form sections.
 *
 * <p>On BBS builds that ship {@code UISection} (newer 2.3.x), these methods
 * are never called — the panel code checks {@link IrliteBbsCompat#SECTIONS}
 * first. On CML (and older BBS builds where {@code UISection} is absent),
 * these methods still need to <em>compile</em>; they fall back to a plain
 * {@link UIElement} column that groups its children without a collapsible
 * header. If the caller does reach here it still works, it just won't be
 * collapsible.</p>
 *
 * <p>All method signatures use {@link UIElement} / {@link String} only — types that
 * exist in every BBS build — so a caller's bytecode never names {@code UISection}.</p>
 */
public final class IrliteFormSections
{
    /** Vertical gap between stacked sections; mirrors {@code UIConstants.SECTION_GAP}. */
    private static final int SECTION_GAP = 3;

    /** A grouped container titled {@code title} containing {@code fields}. */
    public static UIElement section(String title, UIElement... fields)
    {
        UIElement section = new UIElement();
        section.column(4).vertical().stretch();
        for (UIElement field : fields)
        {
            section.add(field);
        }
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
