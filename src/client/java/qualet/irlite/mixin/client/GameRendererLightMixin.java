package qualet.irlite.mixin.client;

import qualet.irlite.client.compat.IrliteCalCompat;
import qualet.irlite.client.diag.VlProfiler;
import qualet.irlite.client.light.LightCollector;

import org.qualet.irl.light.FramePipeline;
import org.qualet.irl.light.iris.IrisShadersState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererLightMixin
{
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void irlite$collectLights(DeltaTracker tickCounter, CallbackInfo ci)
    {
        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
        VlProfiler.frameTick();
        VlProfiler.beginPass(VlProfiler.PASS_BAKE);
        long pipelineT0 = System.nanoTime();
        FramePipeline.frame(
            tickDelta,
            IrisShadersState::shadersDisabled,
            LightCollector::collect,
            IrliteCalCompat::resetCalAutoShadowRamp
        );
        VlProfiler.cpuSample("pipeline", System.nanoTime() - pipelineT0);
        VlProfiler.endPass();

        long uploadT0 = System.nanoTime();
        FramePipeline.uploadIfPending();
        VlProfiler.cpuSample("upload", System.nanoTime() - uploadT0);
    }
}
