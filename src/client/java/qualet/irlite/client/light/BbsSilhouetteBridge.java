package qualet.irlite.client.light;

import mchorse.bbs_mod.cubic.ModelInstance;

import org.joml.Vector3f;

/** Access to the supported CML model configuration; no legacy pose probing. */
public final class BbsSilhouetteBridge
{
    private BbsSilhouetteBridge()
    {}

    public static Vector3f scale(ModelInstance instance)
    {
        return instance.scale;
    }

    public static void reportFailure(Throwable failure)
    {
        failure.printStackTrace();
    }
}
