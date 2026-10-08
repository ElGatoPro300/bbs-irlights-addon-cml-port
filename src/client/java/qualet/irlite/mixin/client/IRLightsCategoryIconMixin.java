package qualet.irlite.mixin.client;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Addon icons remain scoped to their settings owner, even when category ids overlap. */
@Mixin(targets = "mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel$UICategoryButton", remap = false)
public abstract class IRLightsCategoryIconMixin
{
    @Shadow @Final private ValueGroup group;

    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0)
    private Icon irlite$categoryIcon(Icon original)
    {
        if (this.group.getParent() == null || !"irlights".equals(this.group.getParent().getId()))
        {
            return original;
        }

        return switch (this.group.getId())
        {
            case "presets" -> Icons.PROPERTIES;
            case "lighting" -> Icons.LIGHT;
            case "volumetric" -> Icons.FADING;
            case "shadows" -> Icons.OUTLINE_SPHERE;
            case "outline" -> Icons.OUTLINE;
            case "patcher" -> Icons.WRENCH;
            default -> original;
        };
    }
}
