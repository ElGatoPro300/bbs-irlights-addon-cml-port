package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FormRenderer.class, remap = false)
public class FormRendererWorldMixin
{
    @Inject(method = "renderBodyPart", at = @At("HEAD"))
    private void irlite$pushBodyPartTransform(BodyPart part, FormRenderingContext context, CallbackInfo ci)
    {
        MatrixStack world = ((IFormRenderingContext) context).irlite$getWorld();
        if (world != null && part.getForm() != null)
        {
            world.push();
            MatrixStackUtils.applyTransform(world, part.transform.get());
        }
    }

    @Inject(method = "renderBodyPart", at = @At("RETURN"))
    private void irlite$popBodyPartTransform(BodyPart part, FormRenderingContext context, CallbackInfo ci)
    {
        MatrixStack world = ((IFormRenderingContext) context).irlite$getWorld();
        if (world != null && part.getForm() != null)
        {
            world.pop();
        }
    }
}
