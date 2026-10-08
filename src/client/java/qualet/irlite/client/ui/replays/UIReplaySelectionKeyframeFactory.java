package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.List;

import qualet.irlite.client.light.ReplaySelection;
import qualet.irlite.forms.ValueReplaySelection;

/** The selection and its activation travel together in the ordinary, undoable string keyframe. */
public final class UIReplaySelectionKeyframeFactory extends UIKeyframeFactory<String>
{
    private static final String USE_LIGHT_SETTINGS = ReplaySelection.encode("", List.of(), ReplaySelection.Mode.INHERIT);

    public UIReplaySelectionKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.interp.removeFromParent();

        UIButton choose = new UIButton(IKey.constant("Choose replays..."), (b) -> this.pick());

        this.scroll.add(choose);
    }

    private String getDisplayValue()
    {
        return this.keyframe.getValue();
    }

    private UIFilmPanel filmPanel()
    {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);

        if (panel == null && this.getContext() != null)
        {
            List<UIFilmPanel> panels = this.getContext().menu.main.getChildren(UIFilmPanel.class);

            panel = panels.isEmpty() ? null : panels.get(0);
        }

        return panel;
    }

    private ReplaySelection.Selection selection()
    {
        ReplaySelection.Selection selection = ReplaySelection.decode(this.getDisplayValue());

        if (selection.mode() == ReplaySelection.Mode.INHERIT)
        {
            UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);

            if (sheet != null && sheet.property instanceof ValueReplaySelection property)
            {
                return ReplaySelection.decode(property.getOriginalValue());
            }
        }

        return selection;
    }

    private void pick()
    {
        UIFilmPanel panel = this.filmPanel();

        if (panel == null || panel.getData() == null)
        {
            return;
        }

        LightReplayPicker.open(this.getContext(), panel, this.selection(), (value) ->
        {
            ReplaySelection.Selection selected = ReplaySelection.decode(value);

            this.setValue(ReplaySelection.encode(selected.film(), selected.replays(), ReplaySelection.Mode.SELECTED));
        });
    }
}
