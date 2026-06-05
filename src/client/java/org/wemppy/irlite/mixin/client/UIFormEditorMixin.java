package org.wemppy.irlite.mixin.client;

import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.wemppy.irlite.client.ui.forms.editors.forms.UIPointLightForm;
import org.wemppy.irlite.client.ui.forms.editors.forms.UISpotlightForm;
import org.wemppy.irlite.forms.PointLightForm;
import org.wemppy.irlite.forms.SpotlightForm;

@Mixin(UIFormEditor.class)
public class UIFormEditorMixin
{
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void irlite$registerUi(CallbackInfo ci)
    {
        UIFormEditor.register(PointLightForm.class, UIPointLightForm::new);
        UIFormEditor.register(SpotlightForm.class, UISpotlightForm::new);
    }
}
