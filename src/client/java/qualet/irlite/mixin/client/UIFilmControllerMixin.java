package qualet.irlite.mixin.client;

import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.forms.SpotGuideDrag;

/**
 * Film-editor counterpart of {@link UIPickableFormRendererMixin}: routes clicks
 * on IRLite's in-world spotlight guide handles (stencil entries registered for
 * the selected replay by SpotlightFormRenderer) into {@link SpotGuideDrag}
 * before BBS's gizmo/replay picking, and drives the drag once per frame.
 *
 * <p>The drag update injects at the HEAD of {@code renderPickingPreview} (not
 * TAIL) on purpose: the method early-returns when nothing is picked under the
 * cursor, and mid-drag the cursor often leaves the handle's own pixels.</p>
 */
@Mixin(UIFilmController.class)
public abstract class UIFilmControllerMixin
{
    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true)
    private void irlite$grabGuideHandle(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        UIFilmController self = (UIFilmController) (Object) this;

        if (SpotGuideDrag.tryStartFilm(self, context))
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

    @Inject(method = "renderPickingPreview", at = @At("HEAD"))
    private void irlite$updateGuideDrag(UIContext context, Area area, CallbackInfo ci)
    {
        UIFilmController self = (UIFilmController) (Object) this;

        SpotGuideDrag.update(self, context);
    }
}
