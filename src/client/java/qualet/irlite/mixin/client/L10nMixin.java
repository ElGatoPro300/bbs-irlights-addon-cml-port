package qualet.irlite.mixin.client;

import mchorse.bbs_mod.l10n.L10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(L10n.class)
public class L10nMixin
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void irlite$registerStrings(CallbackInfo ci)
    {
        L10n self = (L10n) (Object) this;

        self.getKey("bbs.config.irlite.title", "IRLite");
        self.getKey("bbs.config.irlite.tooltip", "IRLite light addon settings");

        self.getKey("bbs.config.irlite_patcher.title", "Shader Patcher");
        self.getKey("bbs.config.irlite_patcher.tooltip", "Apply IRLights .irlights files onto shaderpacks");

        self.getKey("bbs.config.irlite.show_guides", "Show light guides in world");
        self.getKey("bbs.config.irlite.show_guides-comment", "Draw wireframe gizmos for placed PointLight and Spotlight forms in the world.");

        self.getKey("bbs.config.irlite.shadow_quality", "Shadow quality");
        self.getKey("bbs.config.irlite.shadow_quality-comment", "Resolution of the shadow depth maps. Higher is sharper but uses more VRAM (LOW ~40 MiB ... ULTRA ~2.5 GiB).");

        self.getKey("bbs.config.irlite.shadow_cache", "Cache static shadows");
        self.getKey("bbs.config.irlite.shadow_cache-comment", "Only re-bake shadow maps when lights or occluders move. Big FPS win for static/paused scenes. Turn off if shadows ever look stale.");

        self.getKey("bbs.config.irlite.shadow_blocks", "Block shadows");
        self.getKey("bbs.config.irlite.shadow_blocks-comment", "Cast shadows from world blocks: partial blocks (slabs, stairs, fences) by their real shape, and cutout blocks (leaves, bars, glass doors) without shadowing transparent texels. Heavier until the per-light cache lands.");

        self.getKey("bbs.config.irlite.shadow_block_radius", "Block shadow radius");
        self.getKey("bbs.config.irlite.shadow_block_radius-comment", "How far (in blocks) world blocks are collected as shadow casters around a light. Blocks beyond this cast no shadow even if the light reaches farther. Raise it for large lights; higher values make each re-collection (when a light moves or a nearby block changes) more expensive. Default 24.");

        self.getKey("bbs.config.irlite.max_shader_lights", "Max shader lights");
        self.getKey("bbs.config.irlite.max_shader_lights-comment", "Upper bound on how many lights are uploaded to the shader each frame. The injected shader loops over every uploaded light per pixel, so fewer lights is cheaper. When more lights are in range than this, the nearest (highest-priority) ones win; the rest are skipped for lighting but still cast and receive shadows and stay registered. 0 = no limit. Default 64.");

        self.getKey("bbs.config.irlite.shader_light_clustering", "Shader light clustering");
        self.getKey("bbs.config.irlite.shader_light_clustering-comment", "Split the screen into tiles and skip lights that cannot touch a pixel: the shader only loops the lights whose on-screen bounds cover a pixel's tile, so the image is identical while the per-pixel light cost drops. Off = the shader runs the plain full loop over every uploaded light. Default on.");

        self.getKey("bbs.config.irlite.vl_intensity", "VL intensity (live)");
        self.getKey("bbs.config.irlite.vl_intensity-comment", "Global multiplier on the volumetric light (fog beams) from IRLite lights. Applies instantly every frame without reloading the shaderpack. 1.0 = the pack's default, 0 = IRLite volumetrics off. Shaderpacks patched before this option keep using their compiled VL intensity setting.");

        self.getKey("bbs.config.irlite.vl_shadows_live", "VL shadows (live)");
        self.getKey("bbs.config.irlite.vl_shadows_live-comment", "Runtime toggle for shadowed volumetric light from IRLite lights. Applies instantly every frame without reloading the shaderpack. Off skips all volumetric shadow taps (beams pass through geometry). Only affects shaderpacks patched with runtime VL flags; older patches ignore it.");

        self.getKey("bbs.config.irlite.vl_noise_live", "VL noise (live)");
        self.getKey("bbs.config.irlite.vl_noise_live-comment", "Runtime toggle for the animated noise in IRLite volumetric light. Applies instantly every frame without reloading the shaderpack. Off skips the noise taps and renders uniform beams. Only affects shaderpacks patched with runtime VL flags; older patches ignore it.");

        self.getKey("bbs.config.irlite.vl_steps", "VL march steps (live)");
        self.getKey("bbs.config.irlite.vl_steps-comment", "Ray-march steps per light in the volumetric pass. Higher is smoother but costs performance — every pixel covered by a beam pays for all of its steps. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals, older patches keep their compiled setting.");

        self.getKey("bbs.config.irlite.vl_max_dist", "VL max distance (live)");
        self.getKey("bbs.config.irlite.vl_max_dist-comment", "Maximum volumetric ray distance in blocks. Longer rays cost more on sky pixels. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("bbs.config.irlite.vl_shadow_stride", "VL shadow tap stride (live)");
        self.getKey("bbs.config.irlite.vl_shadow_stride-comment", "Tap the IRLights shadow maps every Nth march step and reuse the result in between. 2 roughly halves the volumetric shadow cost for slightly softer shadows; 1 = tap every step. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("bbs.config.irlite.vl_tip_boost", "VL tip glow (live)");
        self.getKey("bbs.config.irlite.vl_tip_boost-comment", "Extra volumetric glow near the light source itself. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("bbs.config.irlite.vl_tip_radius", "VL tip radius (live)");
        self.getKey("bbs.config.irlite.vl_tip_radius-comment", "Radius of the extra glow around the light source, in blocks. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("bbs.config.irlite.vl_noise_amount", "VL noise amount (live)");
        self.getKey("bbs.config.irlite.vl_noise_amount-comment", "How strongly the animated noise modulates the beam. Low keeps it mostly uniform, 1 fully breaks it into puffs; average brightness is preserved. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("bbs.config.irlite.vl_noise_scale", "VL noise scale (live)");
        self.getKey("bbs.config.irlite.vl_noise_scale-comment", "Approximate size of the noise puffs, in blocks. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("bbs.config.irlite.vl_noise_speed", "VL noise drift speed (live)");
        self.getKey("bbs.config.irlite.vl_noise_speed-comment", "How fast the noise puffs drift through the beam, like dust in the air. 0 = static. Snaps to 0.25 steps — in-between values would make the fog pop when the shader's wind cycle wraps. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");

        self.getKey("bbs.config.irlite.vl_noise_stride", "VL noise tap stride (live)");
        self.getKey("bbs.config.irlite.vl_noise_stride-comment", "Sample the density noise every Nth march step and reuse the value in between. Cheaper at high step counts; may band along the beam. 1 = every step. Applies instantly every frame without reloading the shaderpack; only affects shaderpacks patched with runtime VL globals.");
    }
}
