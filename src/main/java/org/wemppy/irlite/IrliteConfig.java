package org.wemppy.irlite;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

public final class IrliteConfig
{
    public static ValueBoolean showGuides;
    public static ValueInt shadowQuality;
    public static ValueBoolean shadowCache;

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

    /** Shadow resolution preset ordinal (0 LOW .. 3 ULTRA), default 1 (MEDIUM). */
    public static int shadowQuality()
    {
        return shadowQuality != null ? shadowQuality.get() : 1;
    }
}
