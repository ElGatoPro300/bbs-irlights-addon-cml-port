package qualet.irlite.client;

import net.fabricmc.api.ClientModInitializer;
import org.qualet.irl.light.shadow.IRLiteBbsCasterSource;
import org.qualet.irl.light.shadow.ShadowEngine;
import org.qualet.irl.patcher.Patcher;
import qualet.irlite.client.patcher.BbsPatcherHost;

public class IrliteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Wire the shared patcher core to BBS (UIUtils + Iris + bundled assets).
        Patcher.install(new BbsPatcherHost());

        // Install the BBS Form/Film/Morph shadow caster source + config so the shared
        // irl-core shadow orchestration can reach this mod's per-mod pieces.
        ShadowEngine.install(new IRLiteBbsCasterSource(), new IrliteShadowConfig());
    }
}
