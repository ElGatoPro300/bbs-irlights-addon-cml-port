package qualet.irlite.mixin.client;

import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.forms.SpotGuideDrag;

/**
 * Routes clicks on IRLite's spotlight guide handles (extra stencil-pick entries
 * registered by SpotlightFormRenderer) into {@link SpotGuideDrag} before BBS's
 * own gizmo/bone picking runs, and drives the drag once per frame.
 */
@Mixin(UIPickableFormRenderer.class)
public abstract class UIPickableFormRendererMixin
{
    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true)
    private void irlite$grabGuideHandle(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        UIPickableFormRenderer self = (UIPickableFormRenderer) (Object) this;

        if (SpotGuideDrag.tryStart(self, context))
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "subMouseReleased", at = @At("HEAD"), cancellable = true)
    private void irlite$releaseGuideHandle(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (SpotGuideDrag.mouseReleased())
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderUserModel", at = @At("TAIL"))
    private void irlite$updateGuideDrag(UIContext context, CallbackInfo ci)
    {
        UIPickableFormRenderer self = (UIPickableFormRenderer) (Object) this;

        SpotGuideDrag.update(self, context);
    }
}
