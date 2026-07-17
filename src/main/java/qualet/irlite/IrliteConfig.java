package qualet.irlite;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

public final class IrliteConfig
{
    public static ValueBoolean showGuides;
    public static ValueInt shadowQuality;
    public static ValueBoolean shadowCache;
    public static ValueBoolean shadowBlocks;
    public static ValueInt shadowBlockRadius;
    public static ValueInt shadowBakeBudget;
    public static ValueInt maxShaderLights;
    public static ValueBoolean shaderLightClustering;
    public static ValueFloat vlIntensity;
    public static ValueInt vlSteps;
    public static ValueFloat vlMaxDist;
    public static ValueBoolean vlShadowsLive;
    public static ValueInt vlShadowStride;
    public static ValueFloat vlTipBoost;
    public static ValueFloat vlTipRadius;
    public static ValueBoolean vlNoiseLive;
    public static ValueFloat vlNoiseAmount;
    public static ValueFloat vlNoiseScale;
    public static ValueFloat vlNoiseSpeed;
    public static ValueInt vlNoiseStride;
    public static ValueBoolean vlBlueNoise;
    public static ValueBoolean vlDitherTemporal;

    private IrliteConfig()
    {}

    public static boolean showGuides()
    {
        return showGuides != null && showGuides.get();
    }

    /** When on, shadow maps are only re-baked when the scene changes (default on). */
    public static boolean shadowCache()
    {
        return shadowCache == null || shadowCache.get();
    }

    /** When on, world blocks cast shadows by their real shape, and cutout
     *  blocks skip transparent texels (default on). */
    public static boolean shadowBlocks()
    {
        return shadowBlocks == null || shadowBlocks.get();
    }

    /** Shadow resolution preset ordinal (0 LOW .. 3 ULTRA), default 1 (MEDIUM). */
    public static int shadowQuality()
    {
        return shadowQuality != null ? shadowQuality.get() : 1;
    }

    /** Block-shadow collection radius in blocks (default 24). World blocks farther
     *  than this from a light cast no shadow even when the light's range is larger
     *  — it bounds the per-light bbox walk. Higher = bigger lights shadow correctly
     *  but each re-collection (light move / nearby block edit) costs more. */
    public static int shadowBlockRadius()
    {
        return shadowBlockRadius != null ? shadowBlockRadius.get() : 24;
    }

    /** Max full static shadow bakes started per frame before the rest are
     *  deferred to a later frame (default 4). Spreads a mass invalidation (a
     *  block edit near a cluster of lamps) across frames instead of one spike;
     *  the deferred lamps keep their existing (slightly stale) map until baked.
     *  &lt;= 0 disables throttling (bake everything every frame). First bakes and
     *  tile-reassign bakes are never deferred (they would sample a blank or
     *  foreign map); dynamic overlays and static-&gt;live copies are never
     *  budgeted (they must run every frame). */
    public static int shadowBakeBudget()
    {
        return shadowBakeBudget != null ? shadowBakeBudget.get() : 4;
    }

    /** Max lights uploaded to the shader SSBO per frame; the injected shader loops
     *  over every uploaded light per fragment, so fewer = cheaper. When more lights
     *  are in range than this, the nearest (highest-priority) ones win; the rest are
     *  skipped for lighting but stay registered and keep casting/receiving shadows.
     *  0 disables the cap (upload everything). Default 64. */
    public static int maxShaderLights()
    {
        return maxShaderLights != null ? maxShaderLights.get() : 64;
    }

    /** When on, the shader consults a per-frame screen-tile light grid so each pixel
     *  only loops the lights whose on-screen bounds cover its tile — the image is
     *  identical, the per-pixel light cost drops. Off falls back to the plain full
     *  loop over every uploaded light. Default on. */
    public static boolean shaderLightClustering()
    {
        return shaderLightClustering == null || shaderLightClustering.get();
    }

    /** Global multiplier on IRLite volumetric light brightness, applied live
     *  through the SSBO header each frame — no shader reload. 1.0 = pack
     *  default, 0 = IRLite volumetrics off. Default 1.0. */
    public static float vlIntensity()
    {
        return vlIntensity != null ? vlIntensity.get() : 1F;
    }

    /** Runtime toggle for VL shadows, applied live through the SSBO header
     *  flags each frame — no shader reload. Default on. */
    public static boolean vlShadowsLive()
    {
        return vlShadowsLive == null || vlShadowsLive.get();
    }

    /** Runtime toggle for VL noise, applied live through the SSBO header
     *  flags each frame — no shader reload. Default on. */
    public static boolean vlNoiseLive()
    {
        return vlNoiseLive == null || vlNoiseLive.get();
    }

    /** Ray-march steps per light in the VL pass, applied live through the
     *  globals UBO each frame — no shader reload. Default 48 (pack's
     *  IRLITE_VL_STEPS default). */
    public static int vlSteps()
    {
        return vlSteps != null ? vlSteps.get() : 48;
    }

    /** Maximum VL ray distance in blocks, applied live through the globals
     *  UBO each frame — no shader reload. Default 96 (pack's
     *  IRLITE_VL_MAX_DIST default). */
    public static float vlMaxDist()
    {
        return vlMaxDist != null ? vlMaxDist.get() : 96F;
    }

    /** Tap the shadow maps every Nth VL march step, applied live through the
     *  globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_SHADOW_STRIDE default). */
    public static int vlShadowStride()
    {
        return vlShadowStride != null ? vlShadowStride.get() : 2;
    }

    /** Extra VL glow near the light source itself, applied live through the
     *  globals UBO each frame — no shader reload. Default 1.5 (pack's
     *  IRLITE_VL_TIP_BOOST default). */
    public static float vlTipBoost()
    {
        return vlTipBoost != null ? vlTipBoost.get() : 1.5F;
    }

    /** Radius of the tip glow in blocks, applied live through the globals
     *  UBO each frame — no shader reload. Default 1.5 (pack's
     *  IRLITE_VL_TIP_RADIUS default). */
    public static float vlTipRadius()
    {
        return vlTipRadius != null ? vlTipRadius.get() : 1.5F;
    }

    /** How strongly the noise modulates the VL beam, applied live through the
     *  globals UBO each frame — no shader reload. Default 0.6 (pack's
     *  IRLITE_VL_NOISE_AMOUNT default). */
    public static float vlNoiseAmount()
    {
        return vlNoiseAmount != null ? vlNoiseAmount.get() : 0.6F;
    }

    /** Approximate size of the VL noise puffs in blocks, applied live through
     *  the globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_NOISE_SCALE default). */
    public static float vlNoiseScale()
    {
        return vlNoiseScale != null ? vlNoiseScale.get() : 2F;
    }

    /** How fast the VL noise puffs drift, applied live through the globals
     *  UBO each frame — no shader reload. The core quantizes it to 0.25 steps
     *  (whole field-periods per wind wrap cycle) so the fog never pops when
     *  the shader's time counter wraps. Default 0.25 (pack's
     *  IRLITE_VL_NOISE_SPEED default). */
    public static float vlNoiseSpeed()
    {
        return vlNoiseSpeed != null ? vlNoiseSpeed.get() : 0.25F;
    }

    /** Sample the VL density noise every Nth march step, applied live through
     *  the globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_NOISE_STRIDE default). */
    public static int vlNoiseStride()
    {
        return vlNoiseStride != null ? vlNoiseStride.get() : 2;
    }

    /** Runtime toggle for the blue-noise VL march dither, applied live through
     *  the globals UBO flags each frame — no shader reload. On replaces the
     *  pack's hash dither with the mod's blue-noise texture (banding pushed
     *  into fine grain the eye discards); off falls back to the pack's own
     *  dither. Default on. */
    public static boolean vlBlueNoise()
    {
        return vlBlueNoise == null || vlBlueNoise.get();
    }

    /** Runtime toggle for per-frame rotation of the VL blue-noise dither,
     *  applied live through the globals UBO flags each frame — no shader
     *  reload. Without TAA it can shimmer/boil on moving lamps, so it
     *  defaults off. */
    public static boolean vlDitherTemporal()
    {
        return vlDitherTemporal == null || vlDitherTemporal.get();
    }
}
