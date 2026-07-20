package qualet.irlite.mixin.client;

import mchorse.bbs_mod.l10n.L10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Strings for the IRLights settings module. Keys are
 *  {@code irlights.config.<category>.<value>} — the module id is the prefix, so
 *  these all moved off {@code bbs.config.irlite.*} when the settings became
 *  their own module (see IrlightsAddon). */
@Mixin(L10n.class)
public class L10nMixin
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void irlite$registerStrings(CallbackInfo ci)
    {
        L10n self = (L10n) (Object) this;

        // Tooltip of the module icon in the settings overlay's right-hand strip.
        self.getKey("irlights.config.title", "IRLights");

        self.getKey("irlights.config.presets.title", "Presets");
        self.getKey("irlights.config.presets.tooltip", "Quality and beam style presets, plus the knobs worth having on their own");

        self.getKey("irlights.config.volumetric.title", "Volumetric");
        self.getKey("irlights.config.volumetric.tooltip", "Beams and haze: march cost, shadowing and the animated noise");

        self.getKey("irlights.config.shadows.title", "Shadows");
        self.getKey("irlights.config.shadows.tooltip", "Shadow maps baked for IRLights lights");

        self.getKey("irlights.config.patcher.title", "Shader Patcher");
        self.getKey("irlights.config.patcher.tooltip", "Apply IRLights .irlights files onto shaderpacks");

        self.getKey("irlights.config.presets.vl_intensity", "VL intensity");
        self.getKey("irlights.config.presets.vl_intensity-comment", "Global multiplier on the volumetric light (fog beams) from IRLite lights. Applies instantly every frame without reloading the shaderpack. 1.0 = the pack's default, 0 = IRLite volumetrics off. Shaderpacks patched before this option keep using their compiled VL intensity setting.");

        self.getKey("irlights.config.presets.max_shader_lights", "Max shader lights");
        self.getKey("irlights.config.presets.max_shader_lights-comment", "Upper bound on how many lights are uploaded to the shader each frame. The injected shader loops over every uploaded light per pixel, so fewer lights is cheaper. When more lights are in range than this, the nearest (highest-priority) ones win; the rest are skipped for lighting but still cast and receive shadows and stay registered. 0 = no limit, and that is the default. Quality presets never touch this knob.");

        self.getKey("irlights.config.presets.show_guides", "Show light guides in world");
        self.getKey("irlights.config.presets.show_guides-comment", "Draw wireframe gizmos for placed PointLight and Spotlight forms in the world.");

        self.getKey("irlights.config.volumetric.vl_steps", "March steps");
        self.getKey("irlights.config.volumetric.vl_steps-comment", "Ray-march steps per light in the volumetric pass. Higher is smoother but costs performance — every pixel covered by a beam pays for all of its steps. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals, older patches keep their compiled setting.");

        self.getKey("irlights.config.volumetric.vl_max_dist", "Max distance");
        self.getKey("irlights.config.volumetric.vl_max_dist-comment", "Maximum volumetric ray distance in blocks. Longer rays cost more on sky pixels. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_shadows_live", "Beam shadows");
        self.getKey("irlights.config.volumetric.vl_shadows_live-comment", "Runtime toggle for shadowed volumetric light from IRLite lights. Applies instantly every frame without reloading the shaderpack. Off skips all volumetric shadow taps (beams pass through geometry). Only affects shaderpacks patched with runtime VL flags; older patches ignore it.");

        self.getKey("irlights.config.volumetric.vl_shadow_stride", "Shadow tap stride");
        self.getKey("irlights.config.volumetric.vl_shadow_stride-comment", "Tap the IRLights shadow maps every Nth march step and reuse the result in between. 2 roughly halves the volumetric shadow cost for slightly softer shadows; 1 = tap every step. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_tip_boost", "Tip glow");
        self.getKey("irlights.config.volumetric.vl_tip_boost-comment", "Extra volumetric glow near the light source itself. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_tip_radius", "Tip radius");
        self.getKey("irlights.config.volumetric.vl_tip_radius-comment", "Radius of the extra glow around the light source, in blocks. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_noise_live", "Beam noise");
        self.getKey("irlights.config.volumetric.vl_noise_live-comment", "Runtime toggle for the animated noise in IRLite volumetric light. Applies instantly every frame without reloading the shaderpack. Off skips the noise taps and renders uniform beams. Only affects shaderpacks patched with runtime VL flags; older patches ignore it.");

        self.getKey("irlights.config.volumetric.vl_noise_amount", "Noise amount");
        self.getKey("irlights.config.volumetric.vl_noise_amount-comment", "How strongly the animated noise modulates the beam. Low keeps it mostly uniform, 1 fully breaks it into puffs; average brightness is preserved. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_noise_scale", "Noise scale");
        self.getKey("irlights.config.volumetric.vl_noise_scale-comment", "Approximate size of the noise puffs, in blocks. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_noise_speed", "Drift speed");
        self.getKey("irlights.config.volumetric.vl_noise_speed-comment", "How fast the noise puffs drift through the beam, like dust in the air. 0 = static. Snaps to 0.25 steps — in-between values would make the fog pop when the shader's wind cycle wraps. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_noise_morph", "Noise morph");
        self.getKey("irlights.config.volumetric.vl_noise_morph-comment", "How fast the noise puffs reshape into new shapes, on top of the drift. 0 (default) = classic drifting-only fog; enabling costs a second noise tap per refresh — measured pricier than the noise itself, which is why no beam style preset turns it on. Snaps to 0.25 steps — in-between values would make the fog pop when the shader's morph cycle wraps. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_noise_stride", "Noise tap stride");
        self.getKey("irlights.config.volumetric.vl_noise_stride-comment", "Sample the density noise every Nth march step and reuse the value in between. Cheaper at high step counts; may band along the beam. 1 = every step. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.volumetric.vl_dither_temporal", "Dither temporal rotation");
        self.getKey("irlights.config.volumetric.vl_dither_temporal-comment", "Rotate the blue-noise dither pattern every frame so the grain averages out over time. If recorded footage shimmers/boils on moving lamps without temporal anti-aliasing, switch this off for that shot. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("irlights.config.shadows.shadow_quality", "Shadow quality");
        self.getKey("irlights.config.shadows.shadow_quality-comment", "Resolution of the shadow depth maps. Higher is sharper but uses more VRAM (LOW ~40 MiB ... ULTRA ~2.5 GiB).");

        self.getKey("irlights.config.shadows.shadow_blocks", "Block shadows");
        self.getKey("irlights.config.shadows.shadow_blocks-comment", "Cast shadows from world blocks: partial blocks (slabs, stairs, fences) by their real shape, and cutout blocks (leaves, bars, glass doors) without shadowing transparent texels. Heavier until the per-light cache lands.");
    }
}
