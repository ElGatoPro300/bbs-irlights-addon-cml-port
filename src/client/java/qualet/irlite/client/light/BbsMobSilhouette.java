package qualet.irlite.client.light;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;

import org.qualet.irl.light.shadow.CasterRevision;

/** CML poses are conservatively baked every frame until a revision audit is available. */
public final class BbsMobSilhouette
{
    public static final boolean DEBUG = Boolean.getBoolean("irlite.debugCasterRevisions");

    public void beginFrame()
    {}

    public CasterRevision sample(ModelBlockEntity block, float tickDelta)
    {
        return CasterRevision.UNKNOWN;
    }
}
