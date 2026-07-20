package qualet.irlite.client.ui.debug;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import qualet.irlite.client.diag.StressTestLights;

/** Debug controls rendered at the bottom of the IRLite settings section. */
public final class UIStressTestSection
{
    private UIStressTestSection()
    {}

    public static void append(UIScrollView options, Runnable rebuild)
    {
        UILabel header = UI.label(IKey.constant("Debug"));
        header.marginTop(6);
        options.add(header);

        String label = StressTestLights.isActive()
            ? "Remove stress test lights"
            : "Stress test: " + StressTestLights.COUNT + " lights";
        UIButton button = new UIButton(IKey.constant(label), (b) ->
        {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null)
            {
                return;
            }

            StressTestLights.toggle(player.getPos());
            if (rebuild != null)
            {
                rebuild.run();
            }
        });
        button.tooltip(IKey.constant(StressTestLights.isActive()
            ? "Removes the synthetic light field"
            : "Spawns an even square field of colored point lights (no shadows) centered on you. "
                + "Respects Max shader lights — set that to 0 or 2048 to see the whole field."));
        options.add(button);
    }
}
