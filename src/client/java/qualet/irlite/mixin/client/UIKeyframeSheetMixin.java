package qualet.irlite.mixin.client;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qualet.irlite.client.ui.replays.LightTrackLayout;

@Mixin(UIKeyframeSheet.class)
public abstract class UIKeyframeSheetMixin
{
    @Inject(method = "<init>(Ljava/lang/String;Lmchorse/bbs_mod/l10n/keys/IKey;IZLmchorse/bbs_mod/utils/keyframes/KeyframeChannel;Lmchorse/bbs_mod/settings/values/base/BaseValueBasic;Z)V", at = @At("TAIL"))
    private void irlite$decorate(String id, IKey title, int color, boolean separator,
        KeyframeChannel channel, BaseValueBasic property, boolean boneTrack, CallbackInfo callback)
    {
        LightTrackLayout.decorate((UIKeyframeSheet) (Object) this);
    }
}
