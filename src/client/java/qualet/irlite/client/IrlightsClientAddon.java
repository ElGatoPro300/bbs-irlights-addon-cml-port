package qualet.irlite.client;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterFormEditorsEvent;
import mchorse.bbs_mod.api.client.events.RegisterFormRenderersEvent;
import mchorse.bbs_mod.api.client.events.RegisterKeyframeEditorsEvent;
import mchorse.bbs_mod.api.client.events.RegisterL10nEvent;
import mchorse.bbs_mod.resources.Link;

import qualet.irlite.client.forms.PointLightFormRenderer;
import qualet.irlite.client.forms.SpotlightFormRenderer;
import qualet.irlite.client.ui.forms.editors.forms.UIPointLightForm;
import qualet.irlite.client.ui.forms.editors.forms.UISpotlightForm;
import qualet.irlite.client.ui.replays.UIReplaySelectionKeyframeFactory;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;

public class IrlightsClientAddon implements BBSAddonMod
{
    @Override
    public int requiredApiVersion()
    {
        return 2;
    }

    @Subscribe
    public void registerLanguages(RegisterL10nEvent event)
    {
        event.l10n.registerOne(language -> new Link("irlite", "strings/" + language + ".json"));
    }

    @Subscribe
    public void registerKeyframeEditors(RegisterKeyframeEditorsEvent event)
    {
        event.registerProperty("outline_replays", UIReplaySelectionKeyframeFactory::new);
        event.registerProperty("light_replays", UIReplaySelectionKeyframeFactory::new);
    }

    @Subscribe
    public void registerRenderers(RegisterFormRenderersEvent event)
    {
        event.register(PointLightForm.class, PointLightFormRenderer::new);
        event.register(SpotlightForm.class, SpotlightFormRenderer::new);
    }

    @Subscribe
    public void registerEditors(RegisterFormEditorsEvent event)
    {
        event.register(PointLightForm.class, UIPointLightForm::new);
        event.register(SpotlightForm.class, UISpotlightForm::new);
    }
}
