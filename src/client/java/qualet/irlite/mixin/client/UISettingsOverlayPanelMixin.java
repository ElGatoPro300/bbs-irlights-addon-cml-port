package qualet.irlite.mixin.client;

import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.ui.debug.UIDebugSection;
import qualet.irlite.client.ui.patcher.UIPatcherSection;
import qualet.irlite.client.ui.presets.UIPresetSection;

@Mixin(UISettingsOverlayPanel.class)
public abstract class UISettingsOverlayPanelMixin
{
    @Shadow public UIScrollView options;
    @Shadow public UITextbox search;
    @Shadow private String selectedCategoryId;
    @Shadow private Settings settings;

    @Shadow public abstract void refresh();

    @Inject(method = "refresh", at = @At("TAIL"))
    private void irlite$appendSections(CallbackInfo ci)
    {
        String query = this.search == null ? "" : this.search.getText().trim();

        if (!query.isEmpty() || this.selectedCategoryId == null)
        {
            return;
        }

        if (this.settings == null)
        {
            return;
        }

        String id = this.selectedCategoryId;
        if ("presets".equals(id))
        {
            // Presets go directly under the section header, above the knobs they
            // drive; everything else appends to the bottom as usual.
            IUIElement anchor = this.options.getChildren().isEmpty()
                ? null
                : this.options.getChildren().get(0);

            for (UIElement element : UIPresetSection.build(this::refresh))
            {
                if (anchor == null)
                {
                    this.options.prepend(element);
                }
                else
                {
                    this.options.addAfter(anchor, element);
                }

                anchor = element;
            }

            UIDebugSection.append(this.options, this::refresh);
            this.options.resize();
        }
        else if ("patcher".equals(id))
        {
            UIPatcherSection.append(this.options, this::refresh);
            this.options.resize();
        }
    }
}
