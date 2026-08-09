package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelFormRenderer.class, remap = false)
public class ModelFormRendererMixin
{
    @Shadow
    protected MatrixCache bones;

    @Inject(method = "renderBodyParts", at = @At("HEAD"))
    private void irlite$pushWorldHeader(FormRenderingContext context, CallbackInfo ci)
    {
        MatrixStack world = ((IFormRenderingContext) context).irlite$getWorld();
        if (world != null)
        {
            world.push();
        }
    }

    @Inject(method = "renderBodyParts", at = @At("RETURN"))
    private void irlite$popWorldHeader(FormRenderingContext context, CallbackInfo ci)
    {
        MatrixStack world = ((IFormRenderingContext) context).irlite$getWorld();
        if (world != null)
        {
            world.pop();
        }
    }

    @Inject(method = "renderBodyParts",
            at = @At(value = "INVOKE",
                     target = "Lmchorse/bbs_mod/forms/renderers/ModelFormRenderer;renderBodyPart(Lmchorse/bbs_mod/forms/forms/BodyPart;Lmchorse/bbs_mod/forms/renderers/FormRenderingContext;)V"))
    private void irlite$pushWorldBone(FormRenderingContext context, CallbackInfo ci)
    {
        MatrixStack world = ((IFormRenderingContext) context).irlite$getWorld();
        if (world != null)
        {
            world.push();
            // The local variable for part is not directly captured in head, but bones cache has the entry
            // In renderBodyParts, context is being iterated. To get the current bone matrix:
            // We apply the exact same transformation logic as stack:
        }
    }

    @Inject(method = "renderBodyPart", at = @At("HEAD"))
    private void irlite$applyBoneToWorld(BodyPart part, FormRenderingContext context, CallbackInfo ci)
    {
        MatrixStack world = ((IFormRenderingContext) context).irlite$getWorld();
        if (world != null)
        {
            MatrixCacheEntry entry = this.bones.get(part.bone.get());
            Matrix4f boneMatrix = entry != null ? entry.matrix() : null;
            if (boneMatrix != null)
            {
                MatrixStackUtils.multiply(world, boneMatrix);
            }
            else
            {
                world.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));
            }
        }
    }

    @Inject(method = "renderBodyPart", at = @At("RETURN"))
    private void irlite$popWorldBone(BodyPart part, FormRenderingContext context, CallbackInfo ci)
    {
        MatrixStack world = ((IFormRenderingContext) context).irlite$getWorld();
        if (world != null)
        {
            world.pop();
        }
    }
}
