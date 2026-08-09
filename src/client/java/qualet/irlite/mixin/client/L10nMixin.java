package qualet.irlite.mixin.client;

import mchorse.bbs_mod.l10n.L10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Strings for the IRLights settings module. Keys are registered under both
 *  {@code irlights.config.<category>.<value>} (for standard multi-module BBS) and
 *  {@code bbs.config.<category>.<value>} (for CML where categories are in BBS settings). */
@Mixin(L10n.class)
public class L10nMixin
{
    private void reg(L10n self, String keySuffix, String text)
    {
        self.getKey("irlights.config." + keySuffix, text);
        self.getKey("bbs.config." + keySuffix, text);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void irlite$registerStrings(CallbackInfo ci)
    {
        L10n self = (L10n) (Object) this;

        // Tooltip of the module icon in the settings overlay's right-hand strip.
        self.getKey("irlights.config.title", "IRLights");
        self.getKey("bbs.config.title", "IRLights");

        reg(self, "presets.title", "Presets");
        reg(self, "presets.tooltip", "Quality and beam style presets, plus the knobs worth having on their own");

        reg(self, "volumetric.title", "Volumetric");
        reg(self, "volumetric.tooltip", "Beams and haze: march cost, shadowing and the animated noise");

        reg(self, "shadows.title", "Shadows");
        reg(self, "shadows.tooltip", "Shadow maps baked for IRLights lights");

        reg(self, "outline.title", "Outline");
        reg(self, "outline.tooltip", "Light-driven rim outline: silhouette, Fresnel halo and the front catch-light");

        reg(self, "patcher.title", "Shader Patcher");
        reg(self, "patcher.tooltip", "Apply IRLights .irlights files onto shaderpacks");

        reg(self, "presets.vl_intensity", "VL intensity");
        reg(self, "presets.vl_intensity-comment", "Global multiplier on the volumetric light (fog beams) from IRLite lights. Applies instantly every frame without reloading the shaderpack. 1.0 = the pack's default, 0 = IRLite volumetrics off. Shaderpacks patched before this option keep using their compiled VL intensity setting.");

        reg(self, "presets.max_shader_lights", "Max shader lights");
        reg(self, "presets.max_shader_lights-comment", "Upper bound on how many lights are uploaded to the shader each frame. The injected shader loops over every uploaded light per pixel, so fewer lights is cheaper. When more lights are in range than this, the nearest (highest-priority) ones win; the rest are skipped for lighting but still cast and receive shadows and stay registered. 0 = no limit, and that is the default. Quality presets never touch this knob.");

        reg(self, "presets.show_guides", "Show light guides in world");
        reg(self, "presets.show_guides-comment", "Draw wireframe gizmos for placed PointLight and Spotlight forms in the world.");

        reg(self, "volumetric.vl_steps", "March steps");
        reg(self, "volumetric.vl_steps-comment", "Ray-march steps per light in the volumetric pass. Higher is smoother but costs performance — every pixel covered by a beam pays for all of its steps. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals, older patches keep their compiled setting.");

        reg(self, "volumetric.vl_max_dist", "Max distance");
        reg(self, "volumetric.vl_max_dist-comment", "Maximum volumetric ray distance in blocks. Longer rays cost more on sky pixels. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_shadows_live", "Beam shadows");
        reg(self, "volumetric.vl_shadows_live-comment", "Runtime toggle for shadowed volumetric light from IRLite lights. Applies instantly every frame without reloading the shaderpack. Off skips all volumetric shadow taps (beams pass through geometry). Only affects shaderpacks patched with runtime VL flags; older patches ignore it.");

        reg(self, "volumetric.vl_shadow_stride", "Shadow tap stride");
        reg(self, "volumetric.vl_shadow_stride-comment", "Tap the IRLights shadow maps every Nth march step and reuse the result in between. 2 roughly halves the volumetric shadow cost for slightly softer shadows; 1 = tap every step. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_tip_boost", "Tip glow");
        reg(self, "volumetric.vl_tip_boost-comment", "Extra volumetric glow near the light source itself. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_tip_radius", "Tip radius");
        reg(self, "volumetric.vl_tip_radius-comment", "Radius of the extra glow around the light source, in blocks. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_noise_live", "Beam noise");
        reg(self, "volumetric.vl_noise_live-comment", "Runtime toggle for the animated noise in IRLite volumetric light. Applies instantly every frame without reloading the shaderpack. Off skips the noise taps and renders uniform beams. Only affects shaderpacks patched with runtime VL flags; older patches ignore it.");

        reg(self, "volumetric.vl_noise_amount", "Noise amount");
        reg(self, "volumetric.vl_noise_amount-comment", "How strongly the animated noise modulates the beam. Low keeps it mostly uniform, 1 fully breaks it into puffs; average brightness is preserved. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_noise_scale", "Noise scale");
        reg(self, "volumetric.vl_noise_scale-comment", "Approximate size of the noise puffs, in blocks. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_noise_speed", "Drift speed");
        reg(self, "volumetric.vl_noise_speed-comment", "How fast the noise puffs drift through the beam, like dust in the air. 0 = static. Snaps to 0.25 steps — in-between values would make the fog pop when the shader's wind cycle wraps. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_noise_morph", "Noise morph");
        reg(self, "volumetric.vl_noise_morph-comment", "How fast the noise puffs reshape into new shapes, on top of the drift. 0 (default) = classic drifting-only fog; enabling costs a second noise tap per refresh — measured pricier than the noise itself, which is why no beam style preset turns it on. Snaps to 0.25 steps — in-between values would make the fog pop when the shader's morph cycle wraps. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_noise_stride", "Noise tap stride");
        reg(self, "volumetric.vl_noise_stride-comment", "Sample the density noise every Nth march step and reuse the value in between. Cheaper at high step counts; may band along the beam. 1 = every step. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "volumetric.vl_dither_temporal", "Dither temporal rotation");
        reg(self, "volumetric.vl_dither_temporal-comment", "Rotate the blue-noise dither pattern every frame so the grain averages out over time. If recorded footage shimmers/boils on moving lamps without temporal anti-aliasing, switch this off for that shot. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        reg(self, "shadows.shadow_quality", "Shadow quality");
        reg(self, "shadows.shadow_quality-comment", "Resolution of the shadow depth maps. Higher is sharper but uses more VRAM (LOW ~40 MiB ... ULTRA ~2.5 GiB).");

        reg(self, "shadows.shadow_blocks", "Block shadows");
        reg(self, "shadows.shadow_blocks-comment", "Cast shadows from world blocks: partial blocks (slabs, stairs, fences) by their real shape, and cutout blocks (leaves, bars, glass doors) without shadowing transparent texels. Heavier until the per-light cache lands.");

        reg(self, "shadows.shadows_live", "Light shadows");
        reg(self, "shadows.shadows_live-comment", "Cast shadows from IRLights lights. Applies instantly every frame without reloading the shaderpack. With this off (and beam shadows off too) the mod also stops baking the shadow maps, so it recovers the bake cost and VRAM, not just the on-screen shadows. This is the everyday on/off — the shaderpack also has its own IRLITE_SHADOWS option, but that one is a compatibility escape hatch that strips the shadow code out of the compiled shader. Only affects shaderpacks patched with runtime globals.");

        reg(self, "shadows.shadow_softness", "Softness");
        reg(self, "shadows.shadow_softness-comment", "Apparent size of the light source, which sets how fast the shadow edge spreads with distance from whatever casts it — contact stays sharp, far shadows go soft. 0 gives hard edges everywhere. A light with its own bulb size set ignores this and uses that. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline", "Outline");
        reg(self, "outline.outline-comment", "Draw a rim outline on surfaces lit by IRLights lights: a depth silhouette plus a Fresnel halo, tinted by the light colour and faded by its falloff. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_target", "Draw on");
        reg(self, "outline.outline_target-comment", "Which surfaces get the outline, read from the gbuffer material mask: ALL, ENTITIES only (mobs, players, model blocks) or BLOCKS only. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_strength", "Strength");
        reg(self, "outline.outline_strength-comment", "Overall brightness of the rim. 0 = invisible. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_pixel_size", "Thickness");
        reg(self, "outline.outline_pixel_size-comment", "Tap offset of the depth-edge detector, in pixels. Larger reads a wider silhouette — thicker but coarser, and it starts catching edges that are not really silhouettes. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_fresnel_power", "Fresnel falloff");
        reg(self, "outline.outline_fresnel_power-comment", "How tightly the rim hugs grazing angles. Higher = a thinner band right at the silhouette; lower = the glow spreads across the surface. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_back", "Backlight rim");
        reg(self, "outline.outline_back-comment", "Rim strength on surfaces facing AWAY from the light — the classic backlight silhouette. 0 turns it off; it has no separate toggle by design. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_front", "Front catch-light");
        reg(self, "outline.outline_front-comment", "Add a rim on surfaces facing TOWARD the light, on top of the backlight rim. Off by default. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_front_strength", "Front strength");
        reg(self, "outline.outline_front_strength-comment", "Strength of the front catch-light rim. Only used while Front catch-light is on — switching that off keeps this value for when you come back. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_glow", "Inner glow");
        reg(self, "outline.outline_glow-comment", "Add a soft Fresnel halo inside the silhouette, which the shaderpack's bloom then picks up. Off by default. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");

        reg(self, "outline.outline_glow_strength", "Glow strength");
        reg(self, "outline.outline_glow_strength-comment", "Strength of the inner glow halo. Only used while Inner glow is on — switching that off keeps this value for when you come back. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime globals.");
    }
}
