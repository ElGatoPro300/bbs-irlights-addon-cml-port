package qualet.irlite.client.light;

import mchorse.bbs_mod.forms.renderers.FormRenderingContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

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
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Matrix4f worldMatrix = context.stack.last().pose();
        Vector3f offset = worldMatrix.getTranslation(new Vector3f());

        Vec3 camPos = camera.position();

        return new Vector3d(camPos.x + offset.x, camPos.y + offset.y, camPos.z + offset.z);
    }
}
