package qualet.irlite.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.qualet.irl.light.FramePipeline;
import org.qualet.irl.light.iris.IrisShadersState;
import qualet.irlite.client.diag.VlProfiler;
import qualet.irlite.client.light.LightCollector;

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
        // Dev VL profiler (-Dirlite.profileVl=true): the shadow bake below runs
        // strictly before the Iris pass sequence, so its GL_TIME_ELAPSED bracket
        // never nests with the per-pass brackets. collect/prioritize inside
        // frame() issue no GL, so the bracket measures bake GPU work only. The
        // core-side ShadowBakeProbe (installed in IrliteClient) switches this
        // bracket to bake-* siblings at the bakeInner seams; endPass closes
        // whichever segment is open.
        VlProfiler.frameTick();
        VlProfiler.beginPass(VlProfiler.PASS_BAKE);
        long pipelineT0 = System.nanoTime();
        FramePipeline.frame(
            tickDelta,
            IrisShadersState::shadersDisabled,
            LightCollector::collect,
            qualet.irlite.client.compat.IrliteCalCompat::resetCalAutoShadowRamp
        );
        VlProfiler.cpuSample("pipeline", System.nanoTime() - pipelineT0);
        VlProfiler.endPass();
    }

    /**
     * Deferred SSBO upload, injected just AFTER this frame's Camera.update (offset ~187
     * in renderWorld, still well before WorldRenderer.render / Iris activation): the origin
     * the light SSBO is made relative to must be the post-update, current-frame eye that the
     * shaderpack reconstructs fragments against, not the stale HEAD camera. The Camera.update
     * descriptor is unchanged on 1.21.1; only renderWorld's own params differ (RenderTickCounter).
     */
    @Inject(method = "renderWorld",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
                     shift = At.Shift.AFTER,
                     ordinal = 0),
            require = 1)
    private void irlite$uploadLights(RenderTickCounter tickCounter, CallbackInfo ci)
    {
        long uploadT0 = System.nanoTime();
        FramePipeline.uploadIfPending();
        VlProfiler.cpuSample("upload", System.nanoTime() - uploadT0);
    }
}
