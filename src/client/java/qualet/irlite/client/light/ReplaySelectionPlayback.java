package qualet.irlite.client.light;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import qualet.irlite.forms.ValueReplaySelection;

/** New selection keys start at their own tick; legacy channels retain BBS's extrapolation. */
public final class ReplaySelectionPlayback
{
    private ReplaySelectionPlayback()
    {}

    public static boolean startsAfter(KeyframeChannel<?> channel, float tick)
    {
        Keyframe<?> first = channel.get(0);

        return first != null && tick < first.getTick() && first.getValue() instanceof String value
            && ReplaySelection.decode(value).mode() != ReplaySelection.Mode.LEGACY;
    }

    public static boolean resetBeforeFirst(Form root, String track, KeyframeChannel<?> channel, float tick, float blend)
    {
        if (!(track.substring(track.lastIndexOf('/') + 1).equals("light_replays") || track.substring(track.lastIndexOf('/') + 1).equals("outline_replays")))
        {
            return false;
        }

        if (!startsAfter(channel, tick))
        {
            return false;
        }

        if (!(FormUtils.getProperty(root, track) instanceof ValueReplaySelection property))
        {
            return false;
        }

        if (blend >= 1F)
        {
            property.setRuntimeValue(null);
        }

        return true;
    }
}
