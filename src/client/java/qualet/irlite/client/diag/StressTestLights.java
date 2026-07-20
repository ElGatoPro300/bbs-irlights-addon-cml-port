package qualet.irlite.client.diag;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.qualet.irl.light.LightRegistry;

import java.util.Locale;

/**
 * Debug stress test: a square field of {@link #COUNT} synthetic rainbow point
 * lights, evenly spread over a {@link #SIDE}x{@link #SIDE} block area centered
 * on the player position captured when the field is turned on. The lights are
 * re-registered into the per-frame {@link LightRegistry} every frame like any
 * real lamp, so turning the field off simply stops emitting them.
 *
 * The lights cast no shadows on purpose: the feature stresses the source count
 * (SSBO loop, clustering, VL march), not the shadow pool.
 *
 * The field respects the max_shader_lights upload cap: raise the slider (or 0
 * = unlimited) to see the whole field, lower it to a small value to A/B how
 * much of the frame cost scales with the uploaded-light count.
 */
public final class StressTestLights
{
    public static final int COUNT = 500;

    /** Side of the square field in blocks. Wide enough (~5-6 block pitch at 500)
     *  that neighbouring pools never touch and every source reads individually. */
    private static final double SIDE = 120.0;
    /** Height of the lights above the anchor (player feet at press time). */
    private static final double HOVER = 1.5;
    /** Small radius + high intensity: the pool saturates to clip over most of
     *  its area and cuts off at the radius — a visually crisp disc (points have
     *  no spot-style inner range; falloff shape is fixed in the shader). */
    private static final float INTENSITY = 3F;
    private static final float RADIUS = 3F;

    /** Synthetic identity block far above the int range of
     *  System.identityHashCode, so a stress light can never collide with (and
     *  steal the registry slot of) a real form. */
    private static final long ID_BASE = 1L << 40;

    private static boolean active;
    private static double anchorX, anchorY, anchorZ;

    private StressTestLights()
    {}

    public static boolean isActive()
    {
        return active;
    }

    /** Turns the field on centered at {@code anchor} (player position), or off
     *  when it is already on. */
    public static void toggle(Vec3d anchor)
    {
        if (active)
        {
            active = false;
            System.out.println("[irlite] stress: OFF");
            return;
        }

        anchorX = anchor.x;
        anchorY = anchor.y;
        anchorZ = anchor.z;
        active = true;
        System.out.printf(Locale.ROOT, "[irlite] stress: ON %d lights, %.0fx%.0f blocks @ (%.1f, %.1f, %.1f)%n",
            COUNT, SIDE, SIDE, anchorX, anchorY, anchorZ);
    }

    /** Registers the field into this frame's registry; called by the light
     *  collector after the real sources. No-op while off. */
    public static void emit()
    {
        if (!active)
        {
            return;
        }

        // Divisor pair of COUNT closest to a square grid: a full (never ragged)
        // last row; per-axis spacing differs slightly so the covered area stays
        // an exact SIDE x SIDE square.
        int rows = (int) Math.floor(Math.sqrt(COUNT));
        while (COUNT % rows != 0)
        {
            rows--;
        }
        int cols = COUNT / rows;

        double stepX = cols > 1 ? SIDE / (cols - 1) : 0.0;
        double stepZ = rows > 1 ? SIDE / (rows - 1) : 0.0;

        for (int i = 0; i < COUNT; i++)
        {
            int col = i % cols;
            int row = i / cols;
            double x = anchorX + (col - (cols - 1) * 0.5) * stepX;
            double z = anchorZ + (row - (rows - 1) * 0.5) * stepZ;

            int rgb = MathHelper.hsvToRgb(i / (float) COUNT, 1F, 1F);
            float r = (rgb >> 16 & 0xFF) / 255F;
            float g = (rgb >> 8 & 0xFF) / 255F;
            float b = (rgb & 0xFF) / 255F;

            // beam=0: a nonzero beamStrength gives every light the prioritizer's
            // BEAM_BONUS, flooring all 500 scores to zero — the "nearest-first"
            // upload order (and which 64 get cluster-mask bits) degenerates to
            // grid order. Zero keeps the stress field representative.
            LightRegistry.registerPoint(x, anchorY + HOVER, z, r, g, b, INTENSITY, RADIUS,
                false, false, 0.4F, 0.05F, 0F, 0F, false, ID_BASE + i);
        }
    }
}
