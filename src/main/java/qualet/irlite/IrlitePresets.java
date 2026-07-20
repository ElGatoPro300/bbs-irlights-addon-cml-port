package qualet.irlite;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/** Two independent preset axes over the live IRLights knobs: how much it costs
 *  (quality) and how the beams look (style). Neither axis is stored — the
 *  selected preset is derived from the member values every time it is drawn, so
 *  there is no second source of truth to keep in sync: editing any member by
 *  hand simply stops matching and the axis reads Custom.
 *
 *  Index {@code COUNT} on each axis is that Custom slot; applying it is a no-op
 *  (there are no values to restore, the current ones already are the custom
 *  set). The Balanced / Dusty entries are deliberately identical to the
 *  registered defaults in BBSSettingsMixin, so a fresh install already sits on a
 *  named preset instead of reading Custom. */
public final class IrlitePresets
{
    public static final String[] QUALITY_LABELS = {"Performance", "Balanced", "Quality", "Ultra", "Custom"};
    public static final String[] STYLE_LABELS = {"Clean", "Dusty", "Smoky", "Custom"};

    /** Cost axis. Two deliberate omissions: shadow quality never reaches ULTRA
     *  (3) — the 4096 point layers cost ~4.6 GiB of VRAM, the collapse this
     *  project already fixed once — and max_shader_lights is not a member at
     *  all, so the light cap stays wherever the user put it (default 0 = no
     *  cap) no matter which preset is picked. */
    private record Quality(int steps, float maxDist, int shadowStride, int noiseStride,
        boolean vlShadows, int shadowQuality, boolean blockShadows)
    {}

    private static final Quality[] QUALITY = {
        new Quality(16, 48F, 4, 4, false, 0, false),
        new Quality(48, 96F, 2, 2, true, 1, true),
        new Quality(56, 128F, 1, 2, true, 2, true),
        new Quality(64, 192F, 1, 1, true, 2, true),
    };

    /** Look axis. Noise morph is NOT a member: it is the priciest single VL knob
     *  (0.36 ms measured, more than the noise it modulates), so it stays at
     *  whatever the user set — presets never switch it on behind their back. */
    private record Style(boolean noise, float amount, float scale, float speed,
        float tipBoost, float tipRadius)
    {}

    private static final Style[] STYLE = {
        new Style(false, 0.6F, 2F, 0.25F, 1F, 1.5F),
        new Style(true, 0.6F, 2F, 0.25F, 1.5F, 1.5F),
        new Style(true, 1F, 0.5F, 3F, 2F, 2F),
    };

    private IrlitePresets()
    {}

    /** Index of the quality preset the current values spell out, or the Custom
     *  slot when they match none. */
    public static int quality()
    {
        for (int i = 0; i < QUALITY.length; i++)
        {
            Quality q = QUALITY[i];

            // Exact float compares are intentional: every value here was written
            // by applyQuality from these very literals, so a match is bit-exact.
            if (IrliteConfig.vlSteps() == q.steps()
                && IrliteConfig.vlMaxDist() == q.maxDist()
                && IrliteConfig.vlShadowStride() == q.shadowStride()
                && IrliteConfig.vlNoiseStride() == q.noiseStride()
                && IrliteConfig.vlShadowsLive() == q.vlShadows()
                && IrliteConfig.shadowQuality() == q.shadowQuality()
                && IrliteConfig.shadowBlocks() == q.blockShadows())
            {
                return i;
            }
        }

        return QUALITY.length;
    }

    /** Index of the style preset the current values spell out, or the Custom
     *  slot when they match none. With noise off the noise shape values are
     *  don't-cares, so Clean only checks what it actually pins. */
    public static int style()
    {
        for (int i = 0; i < STYLE.length; i++)
        {
            Style s = STYLE[i];

            if (IrliteConfig.vlNoiseLive() != s.noise()
                || IrliteConfig.vlTipBoost() != s.tipBoost()
                || IrliteConfig.vlTipRadius() != s.tipRadius())
            {
                continue;
            }

            if (!s.noise())
            {
                return i;
            }

            if (IrliteConfig.vlNoiseAmount() == s.amount()
                && IrliteConfig.vlNoiseScale() == s.scale()
                && IrliteConfig.vlNoiseSpeed() == s.speed())
            {
                return i;
            }
        }

        return STYLE.length;
    }

    public static void applyQuality(int index)
    {
        if (index < 0 || index >= QUALITY.length)
        {
            return;
        }

        Quality q = QUALITY[index];

        set(IrliteConfig.vlSteps, q.steps());
        set(IrliteConfig.vlMaxDist, q.maxDist());
        set(IrliteConfig.vlShadowStride, q.shadowStride());
        set(IrliteConfig.vlNoiseStride, q.noiseStride());
        set(IrliteConfig.vlShadowsLive, q.vlShadows());
        set(IrliteConfig.shadowQuality, q.shadowQuality());
        set(IrliteConfig.shadowBlocks, q.blockShadows());
    }

    public static void applyStyle(int index)
    {
        if (index < 0 || index >= STYLE.length)
        {
            return;
        }

        Style s = STYLE[index];

        set(IrliteConfig.vlNoiseLive, s.noise());
        set(IrliteConfig.vlNoiseAmount, s.amount());
        set(IrliteConfig.vlNoiseScale, s.scale());
        set(IrliteConfig.vlNoiseSpeed, s.speed());
        set(IrliteConfig.vlTipBoost, s.tipBoost());
        set(IrliteConfig.vlTipRadius, s.tipRadius());
    }

    private static void set(ValueInt value, int x)
    {
        if (value != null)
        {
            value.set(x);
        }
    }

    private static void set(ValueFloat value, float x)
    {
        if (value != null)
        {
            value.set(x);
        }
    }

    private static void set(ValueBoolean value, boolean x)
    {
        if (value != null)
        {
            value.set(x);
        }
    }
}
