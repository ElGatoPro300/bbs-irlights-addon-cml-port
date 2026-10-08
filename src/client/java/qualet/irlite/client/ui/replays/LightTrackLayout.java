package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.Map;

import qualet.irlite.forms.LightForm;
import qualet.irlite.forms.SpotlightForm;

/** Light-only sheet decoration; identical property names on other forms are unaffected. */
public final class LightTrackLayout
{
    private record Style(int color, Icon icon) {}

    private static final Map<String, Style> COMMON = Map.ofEntries(
        Map.entry("color", new Style(0xffd27f, Icons.COLOR)),
        Map.entry("intensity", new Style(0xffb347, Icons.SUN)),
        Map.entry("beam_strength", new Style(0x44ddee, Icons.FADING)),
        Map.entry("anisotropy", new Style(0x66ccff, Icons.ARC)),
        Map.entry("vl_density", new Style(0x33ccaa, Icons.DROP)),
        Map.entry("shadows", new Style(0x9b6dff, Icons.OUTLINE_SPHERE)),
        Map.entry("bulb_size", new Style(0xb48cff, Icons.CIRCLE)),
        Map.entry("entities_only", new Style(0xff5fa2, Icons.PLAYER)),
        Map.entry("blocks_only", new Style(0xffaa33, Icons.BLOCK)),
        Map.entry("custom_outline", new Style(0x9b6dff, Icons.OUTLINE)),
        Map.entry("outline", new Style(0xb58cff, Icons.OUTLINE)),
        Map.entry("outline_target", new Style(0xa77dff, Icons.POINTER)),
        Map.entry("outline_strength", new Style(0xc29aff, Icons.GRAPH)),
        Map.entry("outline_pixel_size", new Style(0xb58cff, Icons.OUTLINE)),
        Map.entry("outline_fresnel", new Style(0x8f6be8, Icons.ARC)),
        Map.entry("outline_back", new Style(0x7d5bd1, Icons.ARROW_LEFT)),
        Map.entry("outline_front", new Style(0xc7a6ff, Icons.ARROW_RIGHT)),
        Map.entry("outline_front_strength", new Style(0xd0b3ff, Icons.ARROW_RIGHT)),
        Map.entry("outline_glow", new Style(0xd9c2ff, Icons.SUN)),
        Map.entry("outline_glow_strength", new Style(0xe1d0ff, Icons.SUN)),
        Map.entry("custom_vl", new Style(0x22bbdd, Icons.SUN)),
        Map.entry("vl_enabled", new Style(0x22bbdd, Icons.SUN)),
        Map.entry("vl_intensity", new Style(0x33c4ee, Icons.SUN)),
        Map.entry("vl_max_dist", new Style(0x44ccee, Icons.LINE)),
        Map.entry("vl_tip_boost", new Style(0x55ddee, Icons.SUN)),
        Map.entry("vl_tip_radius", new Style(0x55ddee, Icons.CIRCLE)),
        Map.entry("vl_noise", new Style(0x33ccaa, Icons.PARTICLE)),
        Map.entry("vl_noise_amount", new Style(0x33ccaa, Icons.PARTICLE)),
        Map.entry("vl_noise_scale", new Style(0x3fd6b8, Icons.SCALE)),
        Map.entry("vl_noise_speed", new Style(0x4be0c4, Icons.ARROW_RIGHT)),
        Map.entry("vl_noise_morph", new Style(0x57e8cc, Icons.REFRESH)),
        Map.entry("vl_shadows", new Style(0x66aadd, Icons.OUTLINE_SPHERE)),
        Map.entry("selected_light_replays", new Style(0xffaa33, Icons.PLAYER)),
        Map.entry("light_replays", new Style(0xffc266, Icons.PLAYER)),
        Map.entry("selected_replays", new Style(0xff5fa2, Icons.OUTLINE)),
        Map.entry("outline_replays", new Style(0xff8fbd, Icons.OUTLINE))
    );

    private static final Map<String, Style> POINT = Map.ofEntries(
        Map.entry("radius", new Style(0xff9a3c, Icons.SPHERE))
    );

    private static final Map<String, Style> SPOT = Map.ofEntries(
        Map.entry("range", new Style(0xff9a3c, Icons.LINE)),
        Map.entry("radius", new Style(0xffc06a, Icons.FRUSTUM)),
        Map.entry("inner_radius", new Style(0xffcf8a, Icons.FRUSTUM)),
        Map.entry("cookie", new Style(0x7ddc5a, Icons.IMAGE)),
        Map.entry("cookie_rotation", new Style(0x9be877, Icons.ORBIT)),
        Map.entry("cookie_scale", new Style(0x8fe066, Icons.SCALE)),
        Map.entry("cookie_invert", new Style(0xa8f08a, Icons.EXCHANGE))
    );

    private LightTrackLayout()
    {}

    public static void decorate(UIKeyframeSheet sheet)
    {
        if (sheet.property == null || sheet.isBoneTrack)
        {
            return;
        }

        Form owner = FormUtils.getForm(sheet.property);

        if (!(owner instanceof LightForm))
        {
            return;
        }

        Map<String, Style> own = owner instanceof SpotlightForm ? SPOT : POINT;
        Style style = own.get(sheet.property.getId());

        if (style == null)
        {
            style = COMMON.get(sheet.property.getId());
        }

        if (style != null)
        {
            sheet.color = style.color();
            sheet.icon(style.icon());
        }
    }
}
