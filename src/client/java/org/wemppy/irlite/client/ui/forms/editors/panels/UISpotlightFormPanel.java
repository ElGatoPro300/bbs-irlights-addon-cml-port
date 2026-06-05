package org.wemppy.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import org.wemppy.irlite.forms.SpotlightForm;

public class UISpotlightFormPanel extends UIFormPanel<SpotlightForm>
{
    public UIColor color;
    public UITrackpad intensity;
    public UITrackpad radius;
    public UITrackpad angle;
    public UITrackpad softness;

    public UISpotlightFormPanel(UIForm editor)
    {
        super(editor);

        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.intensity = new UITrackpad((v) -> this.form.intensity.set(v.floatValue())).limit(0, 20);
        this.radius = new UITrackpad((v) -> this.form.radius.set(v.floatValue())).limit(0.1, 128);
        this.angle = new UITrackpad((v) -> this.form.angle.set(v.floatValue())).limit(1, 179);
        this.softness = new UITrackpad((v) -> this.form.softness.set(v.floatValue())).limit(0, 1);

        this.options.add(UI.label(IKey.constant("Color")), this.color);
        this.options.add(UI.label(IKey.constant("Intensity")), this.intensity);
        this.options.add(UI.label(IKey.constant("Radius")), this.radius);
        this.options.add(UI.label(IKey.constant("Angle")), this.angle);
        this.options.add(UI.label(IKey.constant("Softness")), this.softness);
    }

    @Override
    public void startEdit(SpotlightForm form)
    {
        super.startEdit(form);

        this.color.setColor(form.color.get().getARGBColor());
        this.intensity.setValue(form.intensity.get());
        this.radius.setValue(form.radius.get());
        this.angle.setValue(form.angle.get());
        this.softness.setValue(form.softness.get());
    }
}
