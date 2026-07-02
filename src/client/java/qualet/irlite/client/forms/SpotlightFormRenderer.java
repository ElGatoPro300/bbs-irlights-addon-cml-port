package qualet.irlite.client.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;
import qualet.irlite.client.light.IRLightPositionResolver;
import org.qualet.irl.light.LightRegistry;
import qualet.irlite.forms.SpotlightForm;

public class SpotlightFormRenderer extends AbstractLightFormRenderer<SpotlightForm>
{
    public SpotlightFormRenderer(SpotlightForm form)
    {
        super(form);
    }

    @Override
    protected Color lightColor()
    {
        return this.form.color.get();
    }

    @Override
    protected Icon icon()
    {
        return Icons.FRUSTUM;
    }

    @Override
    protected void renderGuide(FormRenderingContext context, Color color)
    {
        if (context.modelRenderer)
        {
            SpotGuideDrag.captureGuideMatrix(this.form, context.stack);
        }

        LightGuideRenderer.renderSpotlight(context.stack, color, this.form.range.get(), this.form.radius.get(), this.form.innerRadius.get());
    }

    @Override
    protected void renderStencilHandles(FormRenderingContext context)
    {
        float range = this.form.range.get();
        float outer = this.form.radius.get();
        float inner = Math.min(this.form.innerRadius.get(), outer);

        /* Per handle: draw the grab zone with the CURRENT free stencil index
         * encoded as vertex color, then register — addPicking assigns that
         * index and increments. Inner is drawn after outer so it wins the
         * overlap when angles meet; the range disc wins the cap center. */
        LightGuideRenderer.renderSpotlightGrabRing(context.stack, range, outer, context.getPickingIndex());
        context.stencilMap.addPicking(this.form, SpotGuideDrag.HANDLE_RADIUS);

        LightGuideRenderer.renderSpotlightGrabRing(context.stack, range, inner, context.getPickingIndex());
        context.stencilMap.addPicking(this.form, SpotGuideDrag.HANDLE_INNER);

        LightGuideRenderer.renderSpotlightGrabCap(context.stack, range, context.getPickingIndex());
        context.stencilMap.addPicking(this.form, SpotGuideDrag.HANDLE_RANGE);
    }

    @Override
    protected void registerLight(FormRenderingContext context)
    {
        Vector3d p = IRLightPositionResolver.resolve(context);

        // Direction: local +Z through inverseViewRot * stack.peek (strips view roll),
        // matching the editor gizmo convention.
        Matrix4f matrix = new Matrix4f((Matrix3fc) RenderSystem.getInverseViewRotationMatrix());
        matrix.mul(context.stack.peek().getPositionMatrix());
        Vector4f forward = new Vector4f(0F, 0F, 1F, 0F);
        matrix.transform(forward);
        float len = (float) Math.sqrt(forward.x * forward.x + forward.y * forward.y + forward.z * forward.z);
        float dx = 0F, dy = 0F, dz = 1F;
        if (len > 1e-4F)
        {
            dx = forward.x / len;
            dy = forward.y / len;
            dz = forward.z / len;
        }

        float outer = this.form.radius.get();
        float inner = Math.min(this.form.innerRadius.get(), outer);
        float cosOuter = (float) Math.cos(Math.toRadians(outer * 0.5F));
        float cosInner = (float) Math.cos(Math.toRadians(inner * 0.5F));

        Color c = this.form.color.get();
        LightRegistry.registerSpot(
            (float) p.x, (float) p.y, (float) p.z,
            dx, dy, dz,
            c.r, c.g, c.b,
            this.form.intensity.get(), this.form.range.get(),
            cosOuter, cosInner,
            this.form.entitiesOnly.get(), this.form.blocksOnly.get(),
            this.form.anisotropy.get(), this.form.vlDensity.get(), this.form.beamStrength.get(),
            this.form.bulbSize.get(), this.form.shadows.get(),
            System.identityHashCode(this.form)
        );
    }
}
