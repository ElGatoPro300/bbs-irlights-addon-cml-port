package qualet.irlite.client.compat;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.qualet.irl.light.shadow.OccluderBatch;
import org.qualet.irl.light.shadow.OccluderSink;
import org.qualet.irl.light.shadow.ShadowCasterSource;

/**
 * Chains multiple {@link ShadowCasterSource} instances so BBS forms and CAL
 * world entities can both contribute occluders in the same frame.
 */
public final class CompositeShadowCasterSource implements ShadowCasterSource
{
    private final ShadowCasterSource[] sources;

    public CompositeShadowCasterSource(ShadowCasterSource... sources)
    {
        this.sources = sources;
    }

    @Override
    public void collect(ClientWorld world, Vec3d camPos, float tickDelta, OccluderSink sink)
    {
        for (ShadowCasterSource source : this.sources)
        {
            if (source != null)
            {
                source.collect(world, camPos, tickDelta, sink);
            }
        }
    }

    @Override
    public void emitOccluder(Object handle, int lightIndex, float tickDelta, OccluderBatch batch)
    {
        for (ShadowCasterSource source : this.sources)
        {
            if (source != null)
            {
                source.emitOccluder(handle, lightIndex, tickDelta, batch);
            }
        }
    }
}
