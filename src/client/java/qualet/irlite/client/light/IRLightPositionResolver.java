package qualet.irlite.client.light;

import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Resolves a light form's absolute world position on the render path.
 *
 * Primary source is BBS's second, parallel matrix stack — {@code context.world} —
 * kept in ABSOLUTE world coordinates. Its base is the actor's interpolated world
 * position + body yaw, and it receives the exact same bone / body-part / form
 * transforms as {@code context.stack} (FormRenderer.render, renderBodyPart and
 * ModelFormRenderer.renderBodyParts all push to BOTH stacks). So the form origin
 * read straight out of it is the true world position — the same point the guide is
 * drawn at from context.stack — with no camera / view-rotation reconstruction.
 *
 * Why not rebuild from the camera: 1.20 exposed
 * {@code RenderSystem.getInverseViewRotationMatrix()}, captured from the real
 * render stack so it always matched. 1.21 removed it, and rebuilding the inverse
 * view rotation from {@code camera.getRotation()} only matches the MAIN world pass.
 * For a morphed actor — especially a bone-attached light rendered through
 * ModelFormRenderer's rig sub-stacks — it desyncs from the stack, so the light
 * "wandered" beside its true spot while the stack-drawn guide stayed correct.
 * Reading context.world sidesteps that and is version-independent.
 */
public final class IRLightPositionResolver
{
    private IRLightPositionResolver()
    {}

    public static Vector3d resolve(FormRenderingContext context)
    {
        // context.world carries the actor world base (pos + yaw) for ENTITY renders;
        // the render path that calls this IS ENTITY (live actors / in-world film
        // replays), so it is always based here. Read the form origin directly.
        if (context.world != null && context.type == FormRenderType.ENTITY)
        {
            Vector3f p = context.world.peek().getPositionMatrix().transformPosition(new Vector3f());

            return new Vector3d(p.x, p.y, p.z);
        }

        // Fallback (no based world stack): reconstruct the inverse view rotation from
        // the camera, strip it off the render stack, then add the camera position.
        net.minecraft.client.render.Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Matrix4f matrix = new Matrix4f(new org.joml.Matrix3f().rotation(camera.getRotation()));
        matrix.mul(context.stack.peek().getPositionMatrix());
        Vector3f offset = matrix.getTranslation(new Vector3f());

        net.minecraft.util.math.Vec3d cam = camera.getPos();

        return new Vector3d(cam.x + offset.x, cam.y + offset.y, cam.z + offset.z);
    }
}
