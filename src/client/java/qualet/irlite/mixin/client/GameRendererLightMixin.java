package qualet.irlite.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.qualet.irl.light.FramePipeline;
import org.qualet.irl.light.iris.IrisShadersState;
import qualet.irlite.client.light.LightCollector;

@Mixin(GameRenderer.class)
public class GameRendererLightMixin
{
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void irlite$collectLights(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci)
    {
        FramePipeline.frame(tickDelta, IrisShadersState::shadersDisabled, LightCollector::collect, () -> {});
    }

    /**
     * Deferred SSBO upload, injected just AFTER this frame's Camera.update (offset ~180
     * in renderWorld, still well before WorldRenderer.render / Iris activation at ~562):
     * the origin the light SSBO is made relative to must be the post-update, current-frame
     * eye that the shaderpack reconstructs fragments against, not the stale HEAD camera.
     */
    @Inject(method = "renderWorld",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
                     shift = At.Shift.AFTER,
                     ordinal = 0),
            require = 1)
    private void irlite$uploadLights(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci)
    {
        FramePipeline.uploadIfPending();
    }
}
