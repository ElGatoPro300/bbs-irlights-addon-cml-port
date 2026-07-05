package qualet.irlite.client;

import net.fabricmc.api.ClientModInitializer;
import org.lwjgl.opengl.GL30;
import org.qualet.irl.light.IrlSamplers;
import org.qualet.irl.light.shadow.IRLiteBbsCasterSource;
import org.qualet.irl.light.shadow.ShadowEngine;
import org.qualet.irl.patcher.Patcher;
import qualet.irlite.client.light.cookie.CookieArray;
import qualet.irlite.client.patcher.BbsPatcherHost;

public class IrliteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Wire the shared patcher core to BBS (UIUtils + Iris + bundled assets).
        Patcher.install(new BbsPatcherHost());

        // Install the BBS Form/Film/Morph shadow caster source + config so the shared
        // irl-core shadow orchestration can reach this mod's per-mod pieces.
        ShadowEngine.install(new IRLiteBbsCasterSource(), IrliteShadowConfig.INSTANCE);

        // Register the per-mod gobo/cookie mask array into the shared sampler registry;
        // rebound from its 2D registration to GL_TEXTURE_2D_ARRAY at bind time.
        IrlSamplers.register("irl_cookieArray", CookieArray::getGlTextureId, GL30.GL_TEXTURE_2D_ARRAY);
    }
}
