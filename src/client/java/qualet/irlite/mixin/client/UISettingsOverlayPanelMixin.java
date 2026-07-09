package qualet.irlite.mixin.client;

import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.ui.patcher.UIPatcherSection;

@Mixin(UISettingsOverlayPanel.class)
public abstract class UISettingsOverlayPanelMixin
{
    @Shadow public UIScrollView options;
    @Shadow public UITextbox search;
    @Shadow private String selectedCategoryId;

    @Shadow public abstract void refresh();

    @Inject(method = "refresh", at = @At("TAIL"))
    private void irlite$appendPatcher(CallbackInfo ci)
    {
        String query = this.search == null ? "" : this.search.getText().trim();

        if (!query.isEmpty() || this.selectedCategoryId == null)
        {
            return;
        }
        if (!"irlite_patcher".equals(this.selectedCategoryId))
        {
            return;
        }

        UIPatcherSection.append(this.options, this::refresh);
        this.options.resize();
    }
}
