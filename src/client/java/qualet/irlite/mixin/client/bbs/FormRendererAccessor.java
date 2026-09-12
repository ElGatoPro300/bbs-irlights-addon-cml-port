package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.renderers.FormRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = FormRenderer.class, remap = false)
public interface FormRendererAccessor {
    @Invoker("applyTransforms")
    void irlite$applyTransforms(MatrixStack matrices, boolean ui, float transition);
}
