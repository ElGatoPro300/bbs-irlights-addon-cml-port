package qualet.irlite.client.light;

import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import qualet.irlite.mixin.client.bbs.IFormRenderingContext;

/**
 * Resolves the absolute world position of a light form during rendering.
 *
 * <p>Uses {@code context.world} (populated via mixin for ENTITY/morph forms) when present,
 * or falls back to combining camera position with {@code context.stack}.</p>
 */
public final class IRLightPositionResolver
{
    private IRLightPositionResolver()
    {}

    public static Vector3d resolve(FormRenderingContext context)
    {
        MatrixStack world = ((IFormRenderingContext) context).irlite$getWorld();
        if (world != null && context.type == FormRenderType.ENTITY)
        {
            Vector3f p = world.peek().getPositionMatrix().transformPosition(new Vector3f());
            return new Vector3d(p.x, p.y, p.z);
        }

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Matrix4f worldMatrix = context.stack.peek().getPositionMatrix();
        Vector3f offset = worldMatrix.getTranslation(new Vector3f());

        Vec3d camPos = camera.getPos();

        return new Vector3d(camPos.x + offset.x, camPos.y + offset.y, camPos.z + offset.z);
    }
}
