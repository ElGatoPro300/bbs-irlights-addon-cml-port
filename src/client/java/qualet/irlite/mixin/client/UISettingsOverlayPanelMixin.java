package qualet.irlite.mixin.client;

import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
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
    @Shadow private ValueGroup category;
    @Shadow private String filter;
    @Shadow private Settings settings;

    @Shadow public abstract void refresh();

    @Inject(method = "refresh", at = @At("TAIL"))
    private void irlite$appendSections(CallbackInfo ci)
    {
        if (this.filter == null || !this.filter.isEmpty() || this.category == null)
        {
            return;
        }

        // Category ids are only unique within a module, so scope to ours —
        // otherwise a "presets" category in any other mod's settings would get
        // our widgets.
        if (this.settings == null || !"irlights".equals(this.settings.getId()))
        {
            return;
        }

        String id = this.category.getId();
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
