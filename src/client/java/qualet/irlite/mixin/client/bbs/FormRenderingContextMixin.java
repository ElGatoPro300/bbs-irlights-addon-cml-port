package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FormRenderingContext.class, remap = false)
public class FormRenderingContextMixin implements IFormRenderingContext
{
    @Unique
    private MatrixStack irlite$worldStack;

    @Override
    public MatrixStack irlite$getWorld()
    {
        return this.irlite$worldStack;
    }

    @Override
    public void irlite$setWorld(MatrixStack world)
    {
        this.irlite$worldStack = world;
    }

    @Inject(method = "set", at = @At("RETURN"))
    private void irlite$onSet(FormRenderType type, IEntity entity, MatrixStack stack, int light, int overlay, float transition, CallbackInfoReturnable<FormRenderingContext> cir)
    {
        if (type == FormRenderType.ENTITY && entity != null)
        {
            MatrixStack world = new MatrixStack();
            float bodyYaw = Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition);
            world.translate(
                Lerps.lerp(entity.getPrevX(), entity.getX(), transition),
                Lerps.lerp(entity.getPrevY(), entity.getY(), transition),
                Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition)
            );
            world.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
            this.irlite$worldStack = world;
        }
        else
        {
            this.irlite$worldStack = null;
        }
    }
}
