package qualet.irlite.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.qualet.irl.light.FramePipeline;
import org.qualet.irl.light.iris.IrisShadersState;
import qualet.irlite.client.light.IrliteLightPipeline;

@Mixin(GameRenderer.class)
public class GameRendererLightMixin
{
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void irlite$collectLights(RenderTickCounter tickCounter, CallbackInfo ci)
    {
        // 1.21.1: renderWorld(RenderTickCounter) — the old (tickDelta, limitTime,
        // MatrixStack) parameters are gone, so derive the partial tick here
        // (ignoreFreeze=true matches the previous always-advancing behaviour).
        // NB: 1.21.1 still names this getTickDelta(boolean); getTickProgress is later.
        float tickDelta = tickCounter.getTickDelta(true);
        FramePipeline.frame(
            tickDelta,
            IrisShadersState::shadersDisabled,
            IrliteLightPipeline::collect,
            IrliteLightPipeline::afterFrame
        );
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
    private void irlite$uploadLights(RenderTickCounter tickCounter, CallbackInfo ci)
    {
        FramePipeline.uploadIfPending();
    }
}
