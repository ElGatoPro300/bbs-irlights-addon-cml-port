package qualet.irlite;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
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
}
