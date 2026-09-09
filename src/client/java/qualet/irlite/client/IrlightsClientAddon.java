package qualet.irlite.client;

import mchorse.bbs_mod.addons.BBSClientAddon;
import mchorse.bbs_mod.events.register.RegisterFormCategoriesEvent;
import mchorse.bbs_mod.events.register.RegisterFormsRenderersEvent;
import mchorse.bbs_mod.events.register.RegisterL10nEvent;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.utils.colors.Colors;
import qualet.irlite.client.forms.PointLightFormRenderer;
import qualet.irlite.client.forms.SpotlightFormRenderer;
import qualet.irlite.client.ui.forms.editors.forms.UIPointLightForm;
import qualet.irlite.client.ui.forms.editors.forms.UISpotlightForm;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;

public class IrlightsClientAddon extends BBSClientAddon
{
    public IrlightsClientAddon()
    {
        super();
        registerReplayColors();
    }

    @Override
    protected void registerL10n(RegisterL10nEvent event)
    {
        event.l10n.registerOne((lang) -> new Link("irlite", "strings/" + lang + ".json"));
        event.l10n.reload();
    }

    @Override
    protected void registerFormsRenderers(RegisterFormsRenderersEvent event)
    {
        event.registerRenderer(PointLightForm.class, PointLightFormRenderer::new);
        event.registerRenderer(SpotlightForm.class, SpotlightFormRenderer::new);

        event.registerPanel(PointLightForm.class, UIPointLightForm::new);
        event.registerPanel(SpotlightForm.class, UISpotlightForm::new);
    }

    @Override
    protected void registerFormCategories(RegisterFormCategoriesEvent event)
    {
        FormCategory extra = event.getCategories().getExtraForms().getExtraCategory();
        extra.addForm(new PointLightForm());
        extra.addForm(new SpotlightForm());
    }

    private void registerReplayColors()
    {
        /* brightness */
        UIReplaysEditor.registerColor("intensity", Colors.ORANGE);
        /* volumetric group */
        UIReplaysEditor.registerColor("beam_strength", Colors.CYAN);
        UIReplaysEditor.registerColor("vl_density", 0x33ccaa);        // teal
        UIReplaysEditor.registerColor("anisotropy", 0x66ccff);        // light blue
        /* shape / reach */
        UIReplaysEditor.registerColor("bulb_size", Colors.PINK);      // penumbra / softness
        UIReplaysEditor.registerColor("radius", Colors.GREEN);        // point reach / spot outer angle
        UIReplaysEditor.registerColor("range", 0x33ff88);             // spotlight reach
        UIReplaysEditor.registerColor("inner_radius", 0x88ff44);      // lime, spot inner angle
        /* light mask */
        UIReplaysEditor.registerColor("entities_only", Colors.DEEP_PINK);
        UIReplaysEditor.registerColor("blocks_only", 0xffaa33);       // amber
        /* shadows */
        UIReplaysEditor.registerColor("shadows", 0x9b6dff);           // purple
    }
}
