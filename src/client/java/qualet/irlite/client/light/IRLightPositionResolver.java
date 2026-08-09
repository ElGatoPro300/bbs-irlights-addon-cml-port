package qualet.irlite.client.light;

import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Resolves the absolute world position of a light form during rendering.
 *
 * <p>In 1.21.1, {@code context.stack} is already in camera-relative world orientation,
 * so adding {@code camera.getPos()} to its translation yields the exact world position.</p>
 */
public final class IRLightPositionResolver
{
    private IRLightPositionResolver()
    {}

    public static Vector3d resolve(FormRenderingContext context)
    {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Matrix4f worldMatrix = context.stack.peek().getPositionMatrix();
        Vector3f offset = worldMatrix.getTranslation(new Vector3f());

        Vec3d camPos = camera.getPos();

        return new Vector3d(camPos.x + offset.x, camPos.y + offset.y, camPos.z + offset.z);
    }
}
