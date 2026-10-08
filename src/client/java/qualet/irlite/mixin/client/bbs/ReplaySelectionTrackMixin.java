package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qualet.irlite.client.light.ReplaySelectionPlayback;

@Mixin(FormProperties.class)
public class ReplaySelectionTrackMixin
{
    @Inject(method = "applyProperty", at = @At("HEAD"), cancellable = true)
    private void irlite$selectionStartsAtKeyframe(float tick, Form form, KeyframeChannel channel, float blend, CallbackInfo callback)
    {
        if (ReplaySelectionPlayback.resetBeforeFirst(form, channel.getId(), channel, tick, blend))
        {
            callback.cancel();
        }
    }
}
