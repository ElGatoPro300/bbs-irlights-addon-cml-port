package qualet.irlite.mixin.client;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import qualet.irlite.client.light.ReplayOutlineContext;

@Mixin(FormUtilsClient.class)
public class FormUtilsClientMixin
{
    /**
     * Every form draw goes through here; wrapping the renderer call tags the draw with
     * the replay it belongs to (see ReplayOutlineContext) and flushes the entity batch
     * at tag boundaries.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/forms/renderers/FormRenderer;render(Lmchorse/bbs_mod/forms/renderers/FormRenderingContext;)V"))
    private static void irlite$renderTagged(FormRenderer<?> renderer, FormRenderingContext context)
    {
        ReplayOutlineContext.render(renderer, context);
    }

}
