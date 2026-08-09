package qualet.irlite.mixin.client.bbs;

import net.minecraft.client.util.math.MatrixStack;

public interface IFormRenderingContext
{
    MatrixStack irlite$getWorld();
    void irlite$setWorld(MatrixStack world);
}
