package org.wemppy.irlite.mixin;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.wemppy.irlite.IrliteConfig;

@Mixin(BBSSettings.class)
public class BBSSettingsMixin
{
    @Inject(method = "register", at = @At("TAIL"))
    private static void irlite$addSection(SettingsBuilder builder, CallbackInfo ci)
    {
        builder.category("irlite", Icons.LIGHT);
        IrliteConfig.showGuides = builder.getBoolean("show_guides", false);
    }
}
