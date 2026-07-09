package qualet.irlite.client.light;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import qualet.irlite.client.compat.IrliteCalCompat;

/**
 * Single {@link org.qualet.irl.light.FramePipeline} collector for irlite and,
 * when present, IRL CAL Editor lights.
 */
public final class IrliteLightPipeline
{
    private IrliteLightPipeline()
    {
    }

    public static void collect(ClientWorld world, Vec3d cameraPos, float tickDelta)
    {
        LightCollector.collect(world, cameraPos, tickDelta);

        if (IrliteCalCompat.isLoaded())
        {
            IrliteCalCompat.syncShadowSettings();
            IrliteCalCompat.collect(world, cameraPos, tickDelta);
        }
    }

    public static void afterFrame()
    {
        if (IrliteCalCompat.isLoaded())
        {
            IrliteCalCompat.resetAutoShadowRamp();
        }
    }
}
