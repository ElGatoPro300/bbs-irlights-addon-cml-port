package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import qualet.irlite.client.forms.SpotGuideDrag;
import qualet.irlite.forms.SpotlightForm;

public class UISpotlightFormPanel extends UIFormPanel<SpotlightForm>
{
    public UIColor color;
    public UITrackpad intensity;
    public UITrackpad range;
    public UITrackpad radius;
    public UITrackpad innerRadius;
    public UITrackpad beamStrength;
    public UITrackpad anisotropy;
    public UITrackpad vlDensity;
    public UITrackpad bulbSize;
    public UIToggle entitiesOnly;
    public UIToggle blocksOnly;
    public UIToggle shadows;

    public UIButton cookiePick;
    public UITrackpad cookieRotation;
    public UITrackpad cookieScale;
    public UIToggle cookieInvert;

    public UISpotlightFormPanel(UIForm editor)
    {
        super(editor);

        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.intensity = IrliteTrackpads.create((v) -> this.form.intensity.set(v.floatValue())).limit(0, 20);
        this.range = IrliteTrackpads.create((v) -> this.form.range.set(v.floatValue())).limit(0.1, 128);
        this.radius = IrliteTrackpads.create((v) -> this.form.radius.set(v.floatValue())).limit(1, 179);
        this.innerRadius = IrliteTrackpads.create((v) -> this.form.innerRadius.set(v.floatValue())).limit(1, 179);
        this.beamStrength = IrliteTrackpads.create((v) -> this.form.beamStrength.set(v.floatValue())).limit(0, 50);
        this.anisotropy = IrliteTrackpads.create((v) -> this.form.anisotropy.set(v.floatValue())).limit(-0.95, 0.95);
        this.vlDensity = IrliteTrackpads.create((v) -> this.form.vlDensity.set(v.floatValue())).limit(0.005, 0.5);
        this.bulbSize = IrliteTrackpads.create((v) -> this.form.bulbSize.set(v.floatValue())).limit(0, 2);
        // "Entities only" and "Blocks only" are mutually exclusive (both on = light lights nothing).
        this.entitiesOnly = new UIToggle(L10n.lang("irlite.forms.entities_only"), (b) -> {
            this.form.entitiesOnly.set(b.getValue());
            if (b.getValue())
            {
                this.form.blocksOnly.set(false);
                this.blocksOnly.setValue(false);
            }
        });
        this.blocksOnly = new UIToggle(L10n.lang("irlite.forms.blocks_only"), (b) -> {
            this.form.blocksOnly.set(b.getValue());
            if (b.getValue())
            {
                this.form.entitiesOnly.set(false);
                this.entitiesOnly.setValue(false);
            }
        });
        this.shadows = new UIToggle(L10n.lang("irlite.forms.shadows"), (b) -> this.form.shadows.set(b.getValue()));

        // Gobo / cookie: a projected grayscale mask (white = pass, black = block).
        // OFF until a texture is picked. All four fields keyframe in the film editor.
        this.cookiePick = new UIButton(L10n.lang("irlite.forms.cookie_texture"), (b) ->
            UITexturePicker.open(this.getContext(), this.form.cookie.get(), (l) -> this.form.cookie.set(l)));
        this.cookieRotation = IrliteTrackpads.create((v) -> this.form.cookieRotation.set(v.floatValue())).limit(0, 360);
        this.cookieScale = IrliteTrackpads.create((v) -> this.form.cookieScale.set(v.floatValue())).limit(0.1, 4);
        this.cookieInvert = new UIToggle(L10n.lang("irlite.forms.cookie_invert"), (b) -> this.form.cookieInvert.set(b.getValue()));

        // 1.21.1: BBS 2.2.1-1.21.1 has no UISection (a BBS 2.3.1 addition), so the
        // controls are laid out flat rather than in collapsible sections.
        this.options.add(UI.label(L10n.lang("irlite.forms.color")), this.color);
        this.options.add(UI.label(L10n.lang("irlite.forms.intensity")), this.intensity);
        this.options.add(UI.label(L10n.lang("irlite.forms.range")), this.range);
        this.options.add(UI.label(L10n.lang("irlite.forms.radius")), this.radius);
        this.options.add(UI.label(L10n.lang("irlite.forms.inner_radius")), this.innerRadius);
        this.options.add(UI.label(L10n.lang("irlite.forms.beam_strength")), this.beamStrength);
        this.options.add(UI.label(L10n.lang("irlite.forms.anisotropy")), this.anisotropy);
        this.options.add(UI.label(L10n.lang("irlite.forms.vl_density")), this.vlDensity);
        this.options.add(UI.label(L10n.lang("irlite.forms.bulb_size")), this.bulbSize);
        this.options.add(this.entitiesOnly);
        this.options.add(this.blocksOnly);
        this.options.add(this.shadows);
        this.options.add(UI.label(L10n.lang("irlite.forms.cookie_section")), this.cookiePick);
        this.options.add(UI.label(L10n.lang("irlite.forms.cookie_rotation")), this.cookieRotation);
        this.options.add(UI.label(L10n.lang("irlite.forms.cookie_scale")), this.cookieScale);
        this.options.add(this.cookieInvert);
    }

    /** Live-sync of the shape trackpads while a guide handle is dragged in the viewport. */
    public void syncLightShape(SpotlightForm form)
    {
        if (this.form != form)
        {
            return;
        }

        this.range.setValue(form.range.get());
        this.radius.setValue(form.radius.get());
        this.innerRadius.setValue(form.innerRadius.get());
    }

    @Override
    public void startEdit(SpotlightForm form)
    {
        super.startEdit(form);

        SpotGuideDrag.bindPanel(this);

        this.color.setColor(form.color.get().getARGBColor());
        this.intensity.setValue(form.intensity.get());
        this.range.setValue(form.range.get());
        this.radius.setValue(form.radius.get());
        this.innerRadius.setValue(form.innerRadius.get());
        this.beamStrength.setValue(form.beamStrength.get());
        this.anisotropy.setValue(form.anisotropy.get());
        this.vlDensity.setValue(form.vlDensity.get());
        this.bulbSize.setValue(form.bulbSize.get());
        this.entitiesOnly.setValue(form.entitiesOnly.get());
        this.blocksOnly.setValue(form.blocksOnly.get());
        this.shadows.setValue(form.shadows.get());
        this.cookieRotation.setValue(form.cookieRotation.get());
        this.cookieScale.setValue(form.cookieScale.get());
        this.cookieInvert.setValue(form.cookieInvert.get());
    }
}
