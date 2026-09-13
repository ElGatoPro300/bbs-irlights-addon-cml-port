package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.utils.pose.Pose;

import net.minecraft.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MobFormRenderer.class, remap = false)
public interface MobFormRendererAccessor {
    @Accessor("currentPose")
    static void irlite$setCurrentPose(Pose pose) {
        throw new AssertionError();
    }

    @Accessor("currentPoseOverlay")
    static void irlite$setCurrentPoseOverlay(Pose pose) {
        throw new AssertionError();
    }

    @Invoker("prepareMorphRenderState")
    boolean irlite$prepareMorphRenderState(LivingEntity living, IEntity stub);

    @Invoker("applyLivingAnimationState")
    void irlite$applyLivingAnimationState(LivingEntity living, IEntity stub);

    @Invoker("copyLimbAnimator")
    static void irlite$copyLimbAnimator(LivingEntity living, IEntity stub) {
        throw new AssertionError();
    }

    @Invoker("zeroLimbAnimator")
    static void irlite$zeroLimbAnimator(LivingEntity living) {
        throw new AssertionError();
    }
}
