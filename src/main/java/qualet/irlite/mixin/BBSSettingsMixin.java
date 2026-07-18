package qualet.irlite.mixin;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.IrliteConfig;

@Mixin(BBSSettings.class)
public class BBSSettingsMixin
{
    @Inject(method = "register", at = @At("TAIL"))
    private static void irlite$addSection(SettingsBuilder builder, CallbackInfo ci)
    {
        builder.category("irlite", Icons.LIGHT);
        IrliteConfig.showGuides = builder.getBoolean("show_guides", false);
        IrliteConfig.shadowQuality = builder.getInt("shadow_quality", 1, 0, 3).modes(
            IKey.constant("LOW"),
            IKey.constant("MEDIUM"),
            IKey.constant("HIGH"),
            IKey.constant("ULTRA")
        );
        IrliteConfig.shadowCache = builder.getBoolean("shadow_cache", true);
        IrliteConfig.shadowBlocks = builder.getBoolean("shadow_blocks", true);
        IrliteConfig.shadowBlockRadius = builder.getInt("shadow_block_radius", 24, 4, 96);
        IrliteConfig.shadowBakeBudget = builder.getInt("shadow_bake_budget", 4, 0, 16);
        IrliteConfig.maxShaderLights = builder.getInt("max_shader_lights", 64, 0, 2048);
        IrliteConfig.shaderLightClustering = builder.getBoolean("shader_light_clustering", true);
        IrliteConfig.vlIntensity = builder.getFloat("vl_intensity", 1F, 0F, 5F);
        IrliteConfig.vlSteps = builder.getInt("vl_steps", 48, 8, 64);
        IrliteConfig.vlMaxDist = builder.getFloat("vl_max_dist", 96F, 32F, 256F);
        IrliteConfig.vlShadowsLive = builder.getBoolean("vl_shadows_live", true);
        IrliteConfig.vlShadowStride = builder.getInt("vl_shadow_stride", 2, 1, 4);
        IrliteConfig.vlTipBoost = builder.getFloat("vl_tip_boost", 1.5F, 0F, 4F);
        IrliteConfig.vlTipRadius = builder.getFloat("vl_tip_radius", 1.5F, 0.5F, 4F);
        IrliteConfig.vlNoiseLive = builder.getBoolean("vl_noise_live", true);
        IrliteConfig.vlNoiseAmount = builder.getFloat("vl_noise_amount", 0.6F, 0.2F, 1F);
        IrliteConfig.vlNoiseScale = builder.getFloat("vl_noise_scale", 2F, 0.5F, 6F);
        IrliteConfig.vlNoiseSpeed = builder.getFloat("vl_noise_speed", 0.25F, 0F, 3F);
        IrliteConfig.vlNoiseMorph = builder.getFloat("vl_noise_morph", 1F, 0F, 3F);
        IrliteConfig.vlNoiseStride = builder.getInt("vl_noise_stride", 2, 1, 4);
        IrliteConfig.vlBlueNoise = builder.getBoolean("vl_blue_noise", true);
        IrliteConfig.vlDitherTemporal = builder.getBoolean("vl_dither_temporal", true);
        IrliteConfig.vlClusterCull = builder.getBoolean("vl_cluster_cull", true);
        IrliteConfig.vlShadowHiz = builder.getBoolean("vl_shadow_hiz", true);

        // Separate section for the shader patcher (UI injected by
        // UISettingsOverlayPanelMixin). Empty category — buildSections still
        // shows it because it's visible.
        builder.category("irlite_patcher", Icons.WRENCH);
    }
}
