package qualet.irlite;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterSettingsEvent;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;

/** Registers IRLights as its own settings module: an own icon in the overlay's
 *  module strip and an own config/bbs/settings/irlights.json, instead of two
 *  categories bolted onto the end of BBS's own settings list.
 *
 *  The subscriber method must stay public and take exactly one parameter —
 *  BBS's EventBus reflects over getDeclaredMethods() and invokes without
 *  setAccessible, and it dispatches by exact event class. */
public class IrlightsAddon implements BBSAddonMod
{
    private static final String MODULE = "irlights";

    @Subscribe
    public void registerSettings(RegisterSettingsEvent event)
    {
        event.register(Icons.LIGHT, MODULE, IrlightsAddon::build);
    }

    private static void build(SettingsBuilder builder)
    {
        MapType old = legacyDefaults();

        // What most people ever touch: the two preset axes (drawn by
        // UIPresetSection, not registered values) and the few knobs that make
        // sense on their own.
        builder.category("presets", Icons.GEAR);
        IrliteConfig.vlIntensity = builder.getFloat("vl_intensity", old.getFloat("vl_intensity", 1F), 0F, 5F);
        IrliteConfig.maxShaderLights = builder.getInt("max_shader_lights", old.getInt("max_shader_lights", 0), 0, 2048);
        IrliteConfig.showGuides = builder.getBoolean("show_guides", old.getBool("show_guides", false));

        builder.category("volumetric", Icons.SUN);
        IrliteConfig.vlSteps = builder.getInt("vl_steps", old.getInt("vl_steps", 48), 8, 64);
        IrliteConfig.vlMaxDist = builder.getFloat("vl_max_dist", old.getFloat("vl_max_dist", 96F), 32F, 256F);
        IrliteConfig.vlShadowsLive = builder.getBoolean("vl_shadows_live", old.getBool("vl_shadows_live", true));
        IrliteConfig.vlShadowStride = builder.getInt("vl_shadow_stride", old.getInt("vl_shadow_stride", 2), 1, 4);
        IrliteConfig.vlTipBoost = builder.getFloat("vl_tip_boost", old.getFloat("vl_tip_boost", 1.5F), 0F, 4F);
        IrliteConfig.vlTipRadius = builder.getFloat("vl_tip_radius", old.getFloat("vl_tip_radius", 1.5F), 0.5F, 4F);
        IrliteConfig.vlNoiseLive = builder.getBoolean("vl_noise_live", old.getBool("vl_noise_live", true));
        IrliteConfig.vlNoiseAmount = builder.getFloat("vl_noise_amount", old.getFloat("vl_noise_amount", 0.6F), 0.2F, 1F);
        IrliteConfig.vlNoiseScale = builder.getFloat("vl_noise_scale", old.getFloat("vl_noise_scale", 2F), 0.5F, 6F);
        IrliteConfig.vlNoiseSpeed = builder.getFloat("vl_noise_speed", old.getFloat("vl_noise_speed", 0.25F), 0F, 3F);
        IrliteConfig.vlNoiseMorph = builder.getFloat("vl_noise_morph", old.getFloat("vl_noise_morph", 0F), 0F, 3F);
        IrliteConfig.vlNoiseStride = builder.getInt("vl_noise_stride", old.getInt("vl_noise_stride", 2), 1, 4);
        IrliteConfig.vlDitherTemporal = builder.getBoolean("vl_dither_temporal", old.getBool("vl_dither_temporal", true));

        builder.category("shadows", Icons.SPHERE);
        IrliteConfig.shadowQuality = builder.getInt("shadow_quality", old.getInt("shadow_quality", 1), 0, 3).modes(
            IKey.constant("LOW"),
            IKey.constant("MEDIUM"),
            IKey.constant("HIGH"),
            IKey.constant("ULTRA")
        );
        IrliteConfig.shadowBlocks = builder.getBoolean("shadow_blocks", old.getBool("shadow_blocks", true));

        // Empty category — its body is injected at runtime by
        // UISettingsOverlayPanelMixin. buildSections still lists it.
        builder.category("patcher", Icons.WRENCH);
    }

    /** The settings used to live as an "irlite" category inside BBS's own
     *  bbs.json. BBS has no cross-module migration, and once this module writes
     *  its file that orphaned block is dropped on the next bbs.json save — so
     *  read it once and use it as the defaults the builder registers.
     *
     *  One-shot: the guard is our own file already carrying this layout, not
     *  merely existing — a leftover irlights.json from an older experiment has
     *  the name but not the categories, and must not be mistaken for a
     *  completed migration. Settings.toData writes every registered category,
     *  so "volumetric" is present in any file this build ever saved. */
    private static MapType legacyDefaults()
    {
        if (alreadyMigrated())
        {
            return new MapType();
        }

        File bbs = new File(BBSMod.getSettingsFolder(), "bbs.json");

        if (!bbs.exists())
        {
            return new MapType();
        }

        try
        {
            BaseType data = DataToString.read(bbs);

            if (data != null && data.isMap() && data.asMap().has("irlite"))
            {
                return data.asMap().getMap("irlite");
            }
        }
        catch (Exception e)
        {
            // Unreadable or malformed bbs.json — fall through to plain defaults.
        }

        return new MapType();
    }

    private static boolean alreadyMigrated()
    {
        File own = new File(BBSMod.getSettingsFolder(), MODULE + ".json");

        if (!own.exists())
        {
            return false;
        }

        try
        {
            BaseType data = DataToString.read(own);

            return data != null && data.isMap() && data.asMap().has("volumetric");
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
