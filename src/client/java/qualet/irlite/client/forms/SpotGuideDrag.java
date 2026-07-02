package qualet.irlite.client.forms;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.Pair;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import qualet.irlite.client.ui.forms.editors.panels.UISpotlightFormPanel;
import qualet.irlite.forms.SpotlightForm;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drag state + math for the interactive spotlight guide handles in the form
 * editor preview ({@link UIPickableFormRenderer}).
 *
 * <p>The handles are extra entries in BBS's stencil picking pass (registered
 * by {@link SpotlightFormRenderer#renderStencilHandles}), named with the
 * {@link #HANDLE_RADIUS}/{@link #HANDLE_INNER}/{@link #HANDLE_RANGE} pseudo-bone
 * strings. A mixin on the renderer routes clicks on those entries here instead
 * of BBS's form/bone picking, then calls {@link #update} once per frame.</p>
 *
 * <p>All math runs in guide-local space. The local&rarr;view matrix is captured
 * each frame at visible-guide render time ({@link #captureGuideMatrix}); the
 * mouse ray is built in the preview camera's frame (BBS convention: the stack
 * is {@code view * translate(-cam.pos) * chain}, see {@code Gizmo#computeWorldOrigin})
 * and transformed into that local space, where the cap plane and the axis are
 * trivial. Values are written straight into the form's {@code ValueFloat}s —
 * the form editor's {@code preCallback} undo hook picks them up like any
 * trackpad edit.</p>
 */
public final class SpotGuideDrag
{
    public static final String HANDLE_RADIUS = "radius";
    public static final String HANDLE_INNER = "inner_radius";
    public static final String HANDLE_RANGE = "range";

    private static final float EPSILON = 1e-5F;

    /** Guide local->view matrices captured at visible render, per live form. */
    private static final Map<SpotlightForm, Matrix4f> GUIDE_MATRICES = new WeakHashMap<>();

    private static WeakReference<UISpotlightFormPanel> panel = new WeakReference<>(null);

    private static SpotlightForm dragForm;
    private static String dragHandle;
    private static WeakReference<UIPickableFormRenderer> dragRenderer = new WeakReference<>(null);

    private SpotGuideDrag()
    {}

    /** The active spotlight panel registers itself so drags can live-sync its trackpads. */
    public static void bindPanel(UISpotlightFormPanel activePanel)
    {
        panel = new WeakReference<>(activePanel);
    }

    /** Captured from the editor-preview visible pass so the handles track the rendered guide. */
    public static void captureGuideMatrix(SpotlightForm form, MatrixStack stack)
    {
        GUIDE_MATRICES.put(form, new Matrix4f(stack.peek().getPositionMatrix()));
    }

    public static boolean isDragging()
    {
        return dragForm != null;
    }

    /**
     * Start a drag if the stencil pixel under the cursor is one of our handles.
     * Called from the renderer mixin at the head of {@code subMouseClicked};
     * returning true consumes the click before BBS's gizmo/bone picking.
     */
    public static boolean tryStart(UIPickableFormRenderer renderer, UIContext context)
    {
        if (context.mouseButton != 0)
        {
            return false;
        }

        StencilFormFramebuffer stencil = renderer.getStencil();

        if (!stencil.hasPicked())
        {
            return false;
        }

        Pair<Form, String> pair = stencil.getPicked();

        if (pair == null || !(pair.a instanceof SpotlightForm spot) || pair.b == null)
        {
            return false;
        }

        if (!isHandle(pair.b) || !GUIDE_MATRICES.containsKey(spot))
        {
            return false;
        }

        dragForm = spot;
        dragHandle = pair.b;
        dragRenderer = new WeakReference<>(renderer);

        return true;
    }

    /** Ends the drag; true when a drag was active (the release is then consumed). */
    public static boolean mouseReleased()
    {
        if (dragForm == null)
        {
            return false;
        }

        stop();

        return true;
    }

    public static void stop()
    {
        dragForm = null;
        dragHandle = null;
        dragRenderer = new WeakReference<>(null);
    }

    /** Per-frame drag update, driven from the renderer mixin at the tail of {@code renderUserModel}. */
    public static void update(UIPickableFormRenderer renderer, UIContext context)
    {
        if (dragForm == null || dragRenderer.get() != renderer)
        {
            return;
        }

        Matrix4f guide = GUIDE_MATRICES.get(dragForm);

        if (guide == null)
        {
            stop();

            return;
        }

        Camera camera = renderer.camera;
        Area area = renderer.area;

        /* Guide-local -> camera-relative "world" of the preview scene. */
        Matrix4f localToWorld = new Matrix4f(camera.view).invert().mul(guide);

        if (Math.abs(localToWorld.determinant()) < 1e-12F)
        {
            return;
        }

        Matrix4f worldToLocal = new Matrix4f(localToWorld).invert();

        /* Mouse ray: origin = camera (0,0,0 camera-relative), direction from the projection. */
        Vector3f dirWorld = CameraUtils.getMouseDirection(
            camera.projection, camera.view,
            context.mouseX, context.mouseY,
            area.x, area.y, area.w, area.h
        );

        Vector3f o = worldToLocal.transformPosition(new Vector3f(0F, 0F, 0F));
        Vector3f d = worldToLocal.transformDirection(new Vector3f(dirWorld));

        if (d.lengthSquared() < EPSILON * EPSILON)
        {
            return;
        }

        if (HANDLE_RANGE.equals(dragHandle))
        {
            updateRange(o, d);
        }
        else
        {
            updateAngle(o, d, HANDLE_INNER.equals(dragHandle));
        }

        UISpotlightFormPanel activePanel = panel.get();

        if (activePanel != null)
        {
            activePanel.syncLightShape(dragForm);
        }
    }

    /**
     * Range handle: the cap-center disc slides along the guide's Z axis. New
     * range = Z of the closest point on that axis to the mouse ray.
     */
    private static void updateRange(Vector3f o, Vector3f d)
    {
        float a = d.lengthSquared();
        float b = d.z;
        float denom = a - b * b;

        /* Ray (anti)parallel to the axis — no stable closest point. */
        if (denom < EPSILON)
        {
            return;
        }

        float oDotD = o.dot(d);
        float axisZ = (a * o.z - b * oDotD) / denom;
        float capSign = Math.signum(LightGuideRenderer.spotRingZ(1F));
        float range = clamp(capSign * axisZ, 0.1F, 128F);

        dragForm.range.set(range);
    }

    /**
     * Radius / inner radius handles: the rings live in the cap plane. Intersect
     * the ray with that plane; the radial distance to the axis maps back to the
     * cone angle via {@code angle = 2 * atan(radial / range)}.
     */
    private static void updateAngle(Vector3f o, Vector3f d, boolean inner)
    {
        float range = Math.max(dragForm.range.get(), 0.05F);
        float capZ = LightGuideRenderer.spotRingZ(range);

        if (Math.abs(d.z) < EPSILON)
        {
            return;
        }

        float t = (capZ - o.z) / d.z;

        if (t <= 0F)
        {
            return;
        }

        float hx = o.x + d.x * t;
        float hy = o.y + d.y * t;
        float radial = (float) Math.sqrt(hx * hx + hy * hy);
        float angle = (float) Math.toDegrees(2D * Math.atan2(radial, Math.abs(capZ)));

        if (inner)
        {
            dragForm.innerRadius.set(clamp(angle, 1F, dragForm.radius.get()));
        }
        else
        {
            dragForm.radius.set(clamp(angle, 1F, 179F));
        }
    }

    public static boolean isHandle(String bone)
    {
        return HANDLE_RADIUS.equals(bone) || HANDLE_INNER.equals(bone) || HANDLE_RANGE.equals(bone);
    }

    private static float clamp(float value, float min, float max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
