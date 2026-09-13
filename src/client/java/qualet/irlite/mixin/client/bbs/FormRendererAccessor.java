package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.renderers.FormRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

@Mixin(value = FormRenderer.class, remap = false)
public interface FormRendererAccessor {
    @Invoker("applyTransforms")
    void irlite$applyTransforms(PoseStack matrices, boolean ui, float transition);
}
