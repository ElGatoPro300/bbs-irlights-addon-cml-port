package org.qualet.irl.light.shadow;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import org.joml.Quaternionf;

import java.util.List;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public final class OccluderGeometryCapturer
{
    private OccluderGeometryCapturer()
    {}

    private static final Capture CAPTURE = new Capture();
    private static final CaptureQueue QUEUE = new CaptureQueue(CAPTURE);
    private static final CameraRenderState CAMERA_STATE = new CameraRenderState();
    private static final IntOpenHashSet failedEntities = new IntOpenHashSet();

    private static final float[] EMPTY = new float[0];

    public static float[] captureEntityTris(Entity entity, float tickDelta)
    {
        if (entity == null || failedEntities.contains(entity.getId()))
        {
            return EMPTY;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null)
        {
            return EMPTY;
        }
        EntityRenderManager mgr = mc.getEntityRenderDispatcher();
        if (mgr == null)
        {
            return EMPTY;
        }

        try
        {
            double wx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double wy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double wz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

            EntityRenderState state = mgr.getAndUpdateRenderState(entity, tickDelta);
            if (state == null)
            {
                return EMPTY;
            }

            Camera cam = mgr.camera;
            if (cam != null)
            {
                CAMERA_STATE.pos = cam.getCameraPos();
                CAMERA_STATE.orientation.set(cam.getRotation());
                CAMERA_STATE.initialized = true;
            }

            CAPTURE.reset();
            double ox = ShadowRenderer.currentOriginX();
            double oy = ShadowRenderer.currentOriginY();
            double oz = ShadowRenderer.currentOriginZ();
            mgr.render(state, CAMERA_STATE, wx - ox, wy - oy, wz - oz, new MatrixStack(), QUEUE);
            return CAPTURE.toTris(false);
        }
        catch (Throwable t)
        {
            failedEntities.add(entity.getId());
            return EMPTY;
        }
    }

    public static float[] captureCutoutBlockTris(BlockRenderView world, BlockRenderManager brm,
                                                 BlockPos pos, BlockState state, Random random)
    {
        if (world == null || brm == null || state == null)
        {
            return EMPTY;
        }
        try
        {
            BlockStateModel model = brm.getModel(state);
            if (model == null)
            {
                return EMPTY;
            }
            random.setSeed(state.getRenderingSeed(pos));
            List<BlockModelPart> parts = model.getParts(random);
            if (parts == null || parts.isEmpty())
            {
                return EMPTY;
            }

            MatrixStack ms = new MatrixStack();
            ms.translate(pos.getX(), pos.getY(), pos.getZ());

            CAPTURE.reset();
            brm.renderBlock(state, pos, world, ms, CAPTURE, true, parts);
            return CAPTURE.toTris(true);
        }
        catch (Throwable t)
        {
            return EMPTY;
        }
    }

    public static VertexConsumer getDirectCaptureConsumer()
    {
        return CAPTURE;
    }

    public static void resetCapture()
    {
        CAPTURE.reset();
    }

    public static float[] finishCapture(boolean withUv)
    {
        return CAPTURE.toTris(withUv);
    }

    private static final class Capture implements VertexConsumer
    {
        private final FloatArrayList verts = new FloatArrayList(2048);
        private float cx, cy, cz, cu, cv;
        private boolean pending;

        void reset()
        {
            verts.clear();
            pending = false;
        }

        private void commit()
        {
            if (pending)
            {
                verts.add(cx);
                verts.add(cy);
                verts.add(cz);
                verts.add(cu);
                verts.add(cv);
                pending = false;
            }
        }

        float[] toTris(boolean withUv)
        {
            commit();
            int n = verts.size() / 5;
            int quads = n / 4;
            if (quads == 0)
            {
                return EMPTY;
            }
            int per = withUv ? 5 : 3;
            float[] out = new float[quads * 6 * per];
            float[] v = verts.elements();
            int w = 0;
            for (int q = 0; q < quads; q++)
            {
                int b = q * 4 * 5;
                w = put(out, w, v, b, 0, withUv);
                w = put(out, w, v, b, 1, withUv);
                w = put(out, w, v, b, 2, withUv);
                w = put(out, w, v, b, 0, withUv);
                w = put(out, w, v, b, 2, withUv);
                w = put(out, w, v, b, 3, withUv);
            }
            return out;
        }

        private static int put(float[] out, int w, float[] v, int quadBase, int corner, boolean withUv)
        {
            int s = quadBase + corner * 5;
            out[w++] = v[s];
            out[w++] = v[s + 1];
            out[w++] = v[s + 2];
            if (withUv)
            {
                out[w++] = v[s + 3];
                out[w++] = v[s + 4];
            }
            return w;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z)
        {
            commit();
            cx = x; cy = y; cz = z; cu = 0f; cv = 0f;
            pending = true;
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v)
        {
            cu = u; cv = v;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha)
        {
            return this;
        }

        @Override
        public VertexConsumer color(int argb)
        {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v)
        {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v)
        {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z)
        {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width)
        {
            return this;
        }
    }

    private static final class CaptureQueue implements OrderedRenderCommandQueue
    {
        private final Capture capture;

        CaptureQueue(Capture capture)
        {
            this.capture = capture;
        }

        @Override
        public RenderCommandQueue getBatchingQueue(int order)
        {
            return this;
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, MatrixStack matrices, RenderLayer renderLayer,
                                    int light, int overlay, int tintedColor, Sprite sprite, int outlineColor,
                                    ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay)
        {
            if (model == null)
            {
                return;
            }
            try
            {
                model.setAngles(state);
                model.render(matrices, capture, light, overlay, tintedColor);
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitModelPart(ModelPart part, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay,
                                    Sprite sprite, boolean sheeted, boolean hasGlint, int tintedColor,
                                    ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay, int i)
        {
            if (part == null)
            {
                return;
            }
            try
            {
                part.render(matrices, capture, light, overlay, tintedColor);
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitItem(MatrixStack matrices, ItemDisplayContext displayContext, int light, int overlay,
                               int outlineColors, int[] tintLayers, List<BakedQuad> quads, RenderLayer renderLayer,
                               ItemRenderState.Glint glintType)
        {
            if (quads == null || quads.isEmpty())
            {
                return;
            }
            try
            {
                MatrixStack.Entry e = matrices.peek();
                for (int qi = 0, n = quads.size(); qi < n; qi++)
                {
                    BakedQuad q = quads.get(qi);
                    if (q != null)
                    {
                        capture.quad(e, q, 1f, 1f, 1f, 1f, light, overlay);
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<EntityRenderState.ShadowPiece> shadowPieces)
        {
        }

        @Override
        public void submitLabel(MatrixStack matrices, Vec3d nameLabelPos, int y, Text label, boolean notSneaking,
                                int light, double squaredDistanceToCamera, CameraRenderState cameraState)
        {
        }

        @Override
        public void submitText(MatrixStack matrices, float x, float y, OrderedText text, boolean dropShadow,
                               TextRenderer.TextLayerType layerType, int light, int color, int backgroundColor, int outlineColor)
        {
        }

        @Override
        public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation)
        {
        }

        @Override
        public void submitLeash(MatrixStack matrices, EntityRenderState.LeashData leashData)
        {
        }

        @Override
        public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor)
        {
            if (state == null || state.getRenderType() == BlockRenderType.INVISIBLE)
            {
                return;
            }
            try
            {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null)
                {
                    return;
                }
                BlockStateModel model = mc.getBlockRenderManager().getModel(state);
                if (model != null)
                {
                    BlockModelRenderer.render(matrices.peek(), capture, model, 1f, 1f, 1f, light, overlay);
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state)
        {
        }

        @Override
        public void submitBlockStateModel(MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model,
                                          float r, float g, float b, int light, int overlay, int outlineColor)
        {
            if (model == null)
            {
                return;
            }
            try
            {
                BlockModelRenderer.render(matrices.peek(), capture, model, r, g, b, light, overlay);
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, OrderedRenderCommandQueue.Custom customRenderer)
        {
        }

        @Override
        public void submitCustom(OrderedRenderCommandQueue.LayeredCustom customRenderer)
        {
        }
    }
}
