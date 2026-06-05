package org.wemppy.irlite;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

public final class IrliteConfig
{
    public static ValueBoolean showGuides;

    private IrliteConfig()
    {}

    public static boolean showGuides()
    {
        return showGuides != null && showGuides.get();
    }
}
