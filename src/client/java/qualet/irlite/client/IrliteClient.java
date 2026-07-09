package qualet.irlite.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.lwjgl.opengl.GL30;
import org.qualet.irl.light.IrlSamplers;
import org.qualet.irl.light.shadow.IRLiteBbsCasterSource;
import org.qualet.irl.light.shadow.ShadowCasterSource;
import org.qualet.irl.light.shadow.ShadowEngine;
import org.qualet.irl.patcher.Patcher;
import qualet.irlite.client.compat.CompositeShadowCasterSource;
import qualet.irlite.client.compat.IrliteCalCompat;
import qualet.irlite.client.light.cookie.CookieArray;

public class IrliteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        installSharedCore();

        // CAL may initialize after irlite; re-wire once the client is fully up.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> installSharedCore());
    }

    private static void installSharedCore() {
        // Wire the shared patcher core. When CAL Editor is also installed, merge both
        // hosts so bundled patches from either mod are available.
        Patcher.install(IrliteCalCompat.createPatcherHost());

        ShadowCasterSource bbsCaster = new IRLiteBbsCasterSource();
        ShadowCasterSource calCaster = IrliteCalCompat.createCalCasterSource();
        ShadowCasterSource caster = calCaster != null
            ? new CompositeShadowCasterSource(bbsCaster, calCaster)
            : bbsCaster;
        ShadowEngine.install(caster, IrliteShadowConfig.INSTANCE);

        // Register the per-mod gobo/cookie mask array into the shared sampler registry;
        // rebound from its 2D registration to GL_TEXTURE_2D_ARRAY at bind time.
        IrlSamplers.register("irl_cookieArray", CookieArray::getGlTextureId, GL30.GL_TEXTURE_2D_ARRAY);

        if (IrliteCalCompat.isLoaded()) {
            IrliteCalCompat.syncShadowSettings();
        }
    }
}
