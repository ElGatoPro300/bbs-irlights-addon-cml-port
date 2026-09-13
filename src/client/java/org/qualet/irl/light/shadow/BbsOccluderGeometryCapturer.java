package org.qualet.irl.light.shadow;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ShapeForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.StructureFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.forms.renderers.utils.StructureData;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
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
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import qualet.irlite.mixin.client.bbs.FormRendererAccessor;
import qualet.irlite.mixin.client.bbs.MobFormRendererAccessor;
import qualet.irlite.mixin.client.bbs.StructureFormRendererAccessor;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.ItemUseRenderState;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BbsOccluderGeometryCapturer
{
    private BbsOccluderGeometryCapturer()
    {}

    private static final Capture CAPTURE = new Capture();
    private static final CaptureQueue QUEUE = new CaptureQueue(CAPTURE);
    private static final CameraRenderState CAMERA_STATE = new CameraRenderState();
    private static final IntOpenHashSet failedEntities = new IntOpenHashSet();

    private static final float[] EMPTY = new float[0];
    private static final int FULL_LIGHT = LightmapTextureManager.pack(15, 15);

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

    public static float[] captureFormTris(Form form, IEntity stub, MatrixStack matrices, Camera camera, float tickDelta)
    {
        if (form == null)
        {
            return EMPTY;
        }
        if (camera != null)
        {
            CAMERA_STATE.pos = camera.getCameraPos();
            CAMERA_STATE.orientation.set(camera.getRotation());
            CAMERA_STATE.initialized = true;
        }
        else
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null)
            {
                Camera cam = mc.gameRenderer.getCamera();
                CAMERA_STATE.pos = cam.getCameraPos();
                CAMERA_STATE.orientation.set(cam.getRotation());
                CAMERA_STATE.initialized = true;
            }
        }
        CAPTURE.reset();
        try
        {
            captureFormRecursive(form, stub, matrices, camera, tickDelta);
            return CAPTURE.toTris(false);
        }
        catch (Throwable t)
        {
            System.err.println("[IRLite-Shadow] captureFormTris failed for form " + form.getClass().getSimpleName() + ": " + t);
            t.printStackTrace();
            return EMPTY;
        }
    }

    private static void captureFormRecursive(Form form, IEntity stub, MatrixStack matrices, Camera camera, float tickDelta)
    {
        if (form == null)
        {
            return;
        }
        if (form.render != null && !form.render.get())
        {
            return;
        }
        form.applyStates(tickDelta);
        if (form.visible != null && !form.visible.get())
        {
            form.unapplyStates();
            return;
        }

        matrices.push();
        FormRenderer<?> renderer = FormUtilsClient.getRenderer(form);
        if (renderer != null)
        {
            ((FormRendererAccessor) renderer).irlite$applyTransforms(matrices, false, tickDelta);
        }

        if (form instanceof ModelForm modelForm && renderer instanceof ModelFormRenderer mfr)
        {
            mfr.ensureAnimator(tickDelta);
            ModelInstance modelInstance = mfr.getModel() != null ? mfr.getModel() : ModelFormRenderer.getModel(modelForm);
            if (modelInstance != null && modelInstance.model != null)
            {
                IModel model = modelInstance.model;
                model.resetPose();
                IAnimator animator = mfr.getAnimator();
                if (animator != null && stub != null)
                {
                    animator.applyActions(stub, modelInstance, tickDelta);
                }
                Pose pose = mfr.getPose();
                if (pose != null)
                {
                    model.applyPose(pose);
                }
                if (modelForm.poseOverlay != null && modelForm.poseOverlay.get() != null)
                {
                    model.applyPose(modelForm.poseOverlay.get());
                }
                if (modelForm.additionalOverlays != null)
                {
                    for (int i = 0, n = modelForm.additionalOverlays.size(); i < n; i++)
                    {
                        ValuePose vp = modelForm.additionalOverlays.get(i);
                        if (vp != null && vp.get() != null)
                        {
                            model.applyPose(vp.get());
                        }
                    }
                }

                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(MathHelper.PI));
                if (model instanceof Model cubicModel)
                {
                    Link defaultLink = modelForm.texture.get();
                    if (defaultLink == null && modelInstance != null)
                    {
                        defaultLink = modelInstance.texture;
                    }
                    AlphaMask defaultMask = getAlphaMask(defaultLink);
                    ShadowCubicRenderer scr = new ShadowCubicRenderer(CAPTURE, defaultMask);
                    CubicRenderer.processRenderModel(scr, null, matrices, cubicModel);
                }
                matrices.pop();
            }
        }
        else if (form instanceof StructureForm sf && renderer instanceof StructureFormRenderer sfr)
        {
            ((StructureFormRendererAccessor) sfr).irlite$ensureLoaded();
            StructureData data = ((StructureFormRendererAccessor) sfr).irlite$getData();
            if (data != null)
            {
                List<StructureData.BlockEntry> blocks = data.getBlocks();
                if (blocks != null && !blocks.isEmpty())
                {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    BlockRenderManager brm = mc.getBlockRenderManager();
                    BlockRenderView view = data.getCachedView() != null ? data.getCachedView() : mc.world;
                    Random random = Random.create();

                    BlockPos min = data.getBoundsMin();
                    BlockPos max = data.getBoundsMax();
                    float pivotX, pivotY, pivotZ;
                    if (min != null && max != null)
                    {
                        float px = (min.getX() + max.getX()) / 2.0f;
                        float py = min.getY();
                        float pz = (min.getZ() + max.getZ()) / 2.0f;
                        int w = max.getX() - min.getX() + 1;
                        int d = max.getZ() - min.getZ() + 1;
                        float sx = (w % 2 == 1) ? -0.5f : 0.0f;
                        float sz = (d % 2 == 1) ? -0.5f : 0.0f;
                        pivotX = px - sx;
                        pivotY = py;
                        pivotZ = pz - sz;
                    }
                    else
                    {
                        BlockPos size = data.getSize();
                        pivotX = size != null ? size.getX() / 2.0f : 0.0f;
                        pivotY = 0.0f;
                        pivotZ = size != null ? size.getZ() / 2.0f : 0.0f;
                    }

                    matrices.push();
                    float sx = sf.scaleX.get();
                    float sy = sf.scaleY.get();
                    float sz = sf.scaleZ.get();
                    if (Math.abs(sx - 1.0f) > 0.001f || Math.abs(sy - 1.0f) > 0.001f || Math.abs(sz - 1.0f) > 0.001f)
                    {
                        matrices.scale(sx, sy, sz);
                    }

                    for (int i = 0, n = blocks.size(); i < n; i++)
                    {
                        StructureData.BlockEntry entry = blocks.get(i);
                        if (entry == null || entry.state == null || entry.state.isAir())
                        {
                            continue;
                        }
                        BlockPos bp = entry.pos;
                        BlockState bs = entry.state;
                        BlockStateModel bsm = brm.getModel(bs);
                        if (bsm != null)
                        {
                            random.setSeed(bs.getRenderingSeed(bp));
                            List<BlockModelPart> parts = bsm.getParts(random);
                            if (parts != null && !parts.isEmpty())
                            {
                                matrices.push();
                                matrices.translate(bp.getX() - pivotX, bp.getY() - pivotY, bp.getZ() - pivotZ);
                                brm.renderBlock(bs, bp, view, matrices, CAPTURE, false, parts);
                                matrices.pop();
                            }
                        }
                    }
                    matrices.pop();
                }
            }
        }
        else if (form instanceof BlockForm bf)
        {
            BlockState bs = bf.blockState.get();
            if (bs != null && !bs.isAir())
            {
                MinecraftClient mc = MinecraftClient.getInstance();
                BlockRenderManager brm = mc.getBlockRenderManager();
                BlockStateModel bsm = brm.getModel(bs);
                if (bsm != null)
                {
                    Random random = Random.create();
                    random.setSeed(bs.getRenderingSeed(BlockPos.ORIGIN));
                    List<BlockModelPart> parts = bsm.getParts(random);
                    if (parts != null && !parts.isEmpty())
                    {
                        int rx = Math.max(1, bf.repeatX.get());
                        int ry = Math.max(1, bf.repeatY.get());
                        int rz = Math.max(1, bf.repeatZ.get());
                        int sx = BlockForm.repeatAxisStart(rx, bf.repeatCenterX.get());
                        int sy = BlockForm.repeatAxisStart(ry, bf.repeatCenterY.get());
                        int sz = BlockForm.repeatAxisStart(rz, bf.repeatCenterZ.get());
                        for (int y = 0; y < ry; y++)
                        {
                            for (int z = 0; z < rz; z++)
                            {
                                for (int x = 0; x < rx; x++)
                                {
                                    matrices.push();
                                    matrices.translate(sx + x - 0.5f, sy + y, sz + z - 0.5f);
                                    brm.renderBlock(bs, BlockPos.ORIGIN, mc.world, matrices, CAPTURE, false, parts);
                                    matrices.pop();
                                }
                            }
                        }
                    }
                }
            }
        }
        else if (form instanceof ItemForm itemForm)
        {
            ItemStack stack = itemForm.stack.get();
            if (stack != null && !stack.isEmpty())
            {
                MinecraftClient mc = MinecraftClient.getInstance();
                ItemModelManager imm = mc.getItemModelManager();
                ItemRenderState itemState = new ItemRenderState();
                ItemDisplayContext displayContext = itemForm.modelTransform != null && itemForm.modelTransform.get() != null
                    ? itemForm.modelTransform.get()
                    : ItemDisplayContext.FIXED;
                imm.clearAndUpdate(itemState, stack, displayContext, mc.world, null, 0);
                itemState.render(matrices, QUEUE, FULL_LIGHT, OverlayTexture.DEFAULT_UV, 0);
            }
        }
        else if (form instanceof MobForm mobForm && renderer instanceof MobFormRenderer mfr)
        {
            mfr.ensureRenderEntity();
            Entity mobEnt = mfr.getRenderEntity();
            if (mobEnt != null)
            {
                if (mobEnt instanceof LivingEntity living)
                {
                    living.deathTime = 0;
                    if (stub != null)
                    {
                        ((MobFormRendererAccessor) mfr).irlite$prepareMorphRenderState(living, stub);
                        ItemUseRenderState.syncEquipment(living, stub);
                        ((MobFormRendererAccessor) mfr).irlite$applyLivingAnimationState(living, stub);
                        living.hurtTime = stub.getHurtTimer();
                        living.maxHurtTime = living.hurtTime > 0 ? Math.max(stub.getHurtTimer(), living.maxHurtTime) : 0;
                        if (stub.getMountTarget() != null)
                        {
                            MobFormRendererAccessor.irlite$zeroLimbAnimator(living);
                        }
                        else
                        {
                            MobFormRendererAccessor.irlite$copyLimbAnimator(living, stub);
                        }
                    }
                    else
                    {
                        living.setYaw(0);
                        living.setBodyYaw(0);
                        living.setHeadYaw(0);
                        living.setPitch(0);
                        living.lastYaw = 0;
                        living.lastBodyYaw = 0;
                        living.lastHeadYaw = 0;
                        living.lastPitch = 0;
                        living.hurtTime = 0;
                        living.maxHurtTime = 0;
                    }
                }

                matrices.push();
                if ("minecraft:ender_dragon".equals(mobForm.mobID.get()))
                {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotation(MathHelper.PI));
                }
                MobFormRendererAccessor.irlite$setCurrentPose(mobForm.pose.get());
                MobFormRendererAccessor.irlite$setCurrentPoseOverlay(mobForm.poseOverlay.get());
                try
                {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    EntityRenderManager erm = mc.getEntityRenderDispatcher();
                    EntityRenderState ers = erm.getAndUpdateRenderState(mobEnt, tickDelta);
                    if (ers != null)
                    {
                        ers.shadowRadius = 0;
                        if (ers.shadowPieces != null)
                        {
                            ers.shadowPieces.clear();
                        }
                        erm.render(ers, CAMERA_STATE, 0, 0, 0, matrices, QUEUE);
                    }
                }
                finally
                {
                    MobFormRendererAccessor.irlite$setCurrentPose(null);
                    MobFormRendererAccessor.irlite$setCurrentPoseOverlay(null);
                    matrices.pop();
                }
            }
        }
        else if (form instanceof ShapeForm shapeForm)
        {
            float sx = shapeForm.sizeX.get();
            float sy = shapeForm.sizeY.get();
            float sz = shapeForm.sizeZ.get();
            emitBox(matrices, -sx * 0.5f, -sy * 0.5f, -sz * 0.5f, sx * 0.5f, sy * 0.5f, sz * 0.5f);
        }
        else if (form instanceof BillboardForm billboardForm)
        {
            float aspectX = 1.0f;
            float aspectY = 1.0f;
            Link link = billboardForm.texture.get();
            AlphaMask mask = getAlphaMask(link);
            if (link != null)
            {
                try
                {
                    Texture tex = BBSModClient.getTextures().getTexture(link);
                    if (tex != null && tex.width > 0 && tex.height > 0)
                    {
                        float tw = tex.width;
                        float th = tex.height;
                        Vector4f crop = billboardForm.crop.get();
                        if (billboardForm.resizeCrop != null && billboardForm.resizeCrop.get() && crop != null)
                        {
                            tw = Math.max(1f, tw - crop.x - crop.z);
                            th = Math.max(1f, th - crop.y - crop.w);
                        }
                        aspectX = th > tw ? tw / th : 1.0f;
                        aspectY = tw > th ? th / tw : 1.0f;
                    }
                }
                catch (Throwable ignored)
                {}
            }

            float hx = 0.5f * aspectX;
            float hy = 0.5f * aspectY;

            matrices.push();
            if (billboardForm.billboard != null && billboardForm.billboard.get())
            {
                if (camera != null)
                {
                    matrices.multiply(camera.getRotation());
                }
            }

            if (mask != null && mask.hasTransparency)
            {
                int stepsU = Math.min(64, Math.max(1, mask.width));
                int stepsV = Math.min(64, Math.max(1, mask.height));
                for (int x = 0; x < stepsU; x++)
                {
                    float x0 = -hx + (float) x / (float) stepsU * (2f * hx);
                    float x1 = -hx + (float) (x + 1) / (float) stepsU * (2f * hx);
                    for (int y = 0; y < stepsV; y++)
                    {
                        int px = Math.min((int) ((x + 0.5f) / (float) stepsU * mask.width), mask.width - 1);
                        int py = Math.min((int) ((y + 0.5f) / (float) stepsV * mask.height), mask.height - 1);
                        if (mask.getAlpha(px, py) >= 25)
                        {
                            float y0 = -hy + (float) y / (float) stepsV * (2f * hy);
                            float y1 = -hy + (float) (y + 1) / (float) stepsV * (2f * hy);
                            emitQuad(matrices, x0, y0, 0f, x1, y0, 0f, x1, y1, 0f, x0, y1, 0f);
                        }
                    }
                }
            }
            else
            {
                emitQuad(matrices, -hx, -hy, 0f, hx, -hy, 0f, hx, hy, 0f, -hx, hy, 0f);
            }
            matrices.pop();
        }
        else if (form instanceof ExtrudedForm extrudedForm)
        {
            Link link = extrudedForm.texture.get();
            AlphaMask mask = getAlphaMask(link);
            if (mask != null && mask.hasTransparency)
            {
                float px = 0.5F;
                float py = 0.5F;
                float d = 0.5F / 16F;
                int mw = mask.width;
                int mh = mask.height;
                if (mw > mh)
                {
                    py = (float) mh / (float) mw * 0.5F;
                }
                else if (mh > mw)
                {
                    px = (float) mw / (float) mh * 0.5F;
                }
                float sx = 1F / (float) mw * (px / 0.5F);
                float sy = 1F / (float) mh * (py / 0.5F);

                for (int x = 0; x < mw; x++)
                {
                    for (int y = 0; y < mh; y++)
                    {
                        if (mask.getAlpha(x, y) >= 25)
                        {
                            float fx0 = x * sx - px;
                            float fx1 = (x + 1) * sx - px;
                            float fy0 = -(y + 1) * sy + py;
                            float fy1 = -y * sy + py;

                            /* Front face */
                            emitQuad(matrices, fx0, fy0, d, fx1, fy0, d, fx1, fy1, d, fx0, fy1, d);
                            /* Back face */
                            emitQuad(matrices, fx1, fy0, -d, fx0, fy0, -d, fx0, fy1, -d, fx1, fy1, -d);

                            /* Side edges */
                            if (x == 0 || mask.getAlpha(x - 1, y) < 25)
                            {
                                emitQuad(matrices, fx0, fy0, -d, fx0, fy0, d, fx0, fy1, d, fx0, fy1, -d);
                            }
                            if (x == mw - 1 || mask.getAlpha(x + 1, y) < 25)
                            {
                                emitQuad(matrices, fx1, fy0, d, fx1, fy0, -d, fx1, fy1, -d, fx1, fy1, d);
                            }
                            if (y == 0 || mask.getAlpha(x, y - 1) < 25)
                            {
                                emitQuad(matrices, fx0, fy1, -d, fx1, fy1, -d, fx1, fy1, d, fx0, fy1, d);
                            }
                            if (y == mh - 1 || mask.getAlpha(x, y + 1) < 25)
                            {
                                emitQuad(matrices, fx0, fy0, d, fx1, fy0, d, fx1, fy0, -d, fx0, fy0, -d);
                            }
                        }
                    }
                }
            }
            else
            {
                emitQuad(matrices, -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f);
            }
        }

        List<BodyPart> parts = form.parts.getAllTyped();
        if (parts != null && !parts.isEmpty())
        {
            MatrixCache cache = renderer == null ? null : renderer.collectMatrices(stub, tickDelta);
            for (int i = 0, n = parts.size(); i < n; i++)
            {
                BodyPart bp = parts.get(i);
                if (bp == null || bp.getForm() == null)
                {
                    continue;
                }
                matrices.push();
                if (cache != null && bp.bone != null && !bp.bone.get().isEmpty())
                {
                    MatrixCacheEntry entry = cache.get(bp.bone.get());
                    if (entry != null && entry.matrix() != null)
                    {
                        matrices.peek().getPositionMatrix().mul(entry.matrix());
                    }
                }
                if (bp.transform != null && bp.transform.get() != null)
                {
                    MatrixStackUtils.applyTransform(matrices, bp.transform.get());
                }
                captureFormRecursive(bp.getForm(), stub, matrices, camera, tickDelta);
                matrices.pop();
            }
        }

        matrices.pop();
        form.unapplyStates();
    }

    private static void emitQuad(MatrixStack matrices,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3)
    {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        Vector4f v = new Vector4f();

        v.set(x0, y0, z0, 1.0f); mat.transform(v); CAPTURE.vertex(v.x, v.y, v.z);
        v.set(x1, y1, z1, 1.0f); mat.transform(v); CAPTURE.vertex(v.x, v.y, v.z);
        v.set(x2, y2, z2, 1.0f); mat.transform(v); CAPTURE.vertex(v.x, v.y, v.z);
        v.set(x3, y3, z3, 1.0f); mat.transform(v); CAPTURE.vertex(v.x, v.y, v.z);
    }

    private static void emitBox(MatrixStack matrices, float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
    {
        emitQuad(matrices, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        emitQuad(matrices, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ);
        emitQuad(matrices, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ);
        emitQuad(matrices, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
        emitQuad(matrices, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ);
        emitQuad(matrices, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
    }

    public static final class AlphaMask
    {
        public final int width;
        public final int height;
        public final byte[] alphas;
        public final boolean hasTransparency;

        public AlphaMask(int width, int height, byte[] alphas, boolean hasTransparency)
        {
            this.width = width;
            this.height = height;
            this.alphas = alphas;
            this.hasTransparency = hasTransparency;
        }

        public int getAlpha(int x, int y)
        {
            if (x < 0 || x >= this.width || y < 0 || y >= this.height)
            {
                return 0;
            }
            return this.alphas[x + y * this.width] & 0xFF;
        }
    }

    private static final Map<Link, AlphaMask> ALPHA_MASKS = new ConcurrentHashMap<>();

    public static AlphaMask getAlphaMask(Link link)
    {
        if (link == null)
        {
            return null;
        }
        AlphaMask cached = ALPHA_MASKS.get(link);
        if (cached != null)
        {
            return cached;
        }
        try
        {
            Pixels pixels = BBSModClient.getTextures().getPixels(link);
            if (pixels != null)
            {
                int w = pixels.width;
                int h = pixels.height;
                byte[] alphas = new byte[w * h];
                boolean hasTransparency = false;
                if (pixels.bits == 4)
                {
                    ByteBuffer buf = pixels.getBuffer();
                    for (int i = 0; i < w * h; i++)
                    {
                        int a = buf.get(i * 4 + 3) & 0xFF;
                        alphas[i] = (byte) a;
                        if (a < 250)
                        {
                            hasTransparency = true;
                        }
                    }
                }
                else
                {
                    Arrays.fill(alphas, (byte) 255);
                }
                pixels.delete();
                AlphaMask mask = new AlphaMask(w, h, alphas, hasTransparency);
                ALPHA_MASKS.put(link, mask);
                return mask;
            }
        }
        catch (Throwable ignored)
        {
        }
        return null;
    }

    public static final class ShadowCubicRenderer implements ICubicRenderer
    {
        private final VertexConsumer consumer;
        private final AlphaMask defaultMask;
        private final Vector4f v4 = new Vector4f();

        public ShadowCubicRenderer(VertexConsumer consumer)
        {
            this(consumer, null);
        }

        public ShadowCubicRenderer(VertexConsumer consumer, AlphaMask defaultMask)
        {
            this.consumer = consumer;
            this.defaultMask = defaultMask;
        }

        @Override
        public boolean renderGroup(BufferBuilder buffer, MatrixStack matrices, ModelGroup group, Model model)
        {
            AlphaMask groupMask = this.defaultMask;
            if (group.textureOverride != null)
            {
                groupMask = getAlphaMask(group.textureOverride);
            }

            if (group.cubes != null && !group.cubes.isEmpty())
            {
                for (int i = 0, n = group.cubes.size(); i < n; i++)
                {
                    ModelCube cube = group.cubes.get(i);
                    if (cube == null || !cube.visible)
                    {
                        continue;
                    }
                    matrices.push();
                    CubicCubeRenderer.moveToPivot(matrices, cube.pivot);
                    CubicCubeRenderer.rotate(matrices, cube.rotate);
                    CubicCubeRenderer.moveBackFromPivot(matrices, cube.pivot);
                    if (cube.quads == null || cube.quads.isEmpty())
                    {
                        cube.generateQuads(model == null ? 64 : model.textureWidth, model == null ? 64 : model.textureHeight);
                    }
                    if (cube.quads != null)
                    {
                        Matrix4f posMat = matrices.peek().getPositionMatrix();
                        for (int qi = 0, qn = cube.quads.size(); qi < qn; qi++)
                        {
                            ModelQuad quad = cube.quads.get(qi);
                            if (quad != null && quad.vertices != null && quad.vertices.size() >= 4)
                            {
                                emitFilteredQuad(posMat, quad, groupMask, this.consumer, this.v4);
                            }
                        }
                    }
                    matrices.pop();
                }
            }
            if (group.meshes != null && !group.meshes.isEmpty())
            {
                for (int i = 0, n = group.meshes.size(); i < n; i++)
                {
                    ModelMesh mesh = group.meshes.get(i);
                    if (mesh == null)
                    {
                        continue;
                    }
                    ModelData md = mesh.baseData;
                    if (md != null && md.vertices != null && !md.vertices.isEmpty())
                    {
                        matrices.push();
                        if (mesh.origin != null)
                        {
                            matrices.translate(mesh.origin.x, mesh.origin.y, mesh.origin.z);
                        }
                        if (mesh.rotate != null)
                        {
                            CubicCubeRenderer.rotate(matrices, mesh.rotate);
                        }
                        Matrix4f posMat = matrices.peek().getPositionMatrix();
                        List<Vector3f> verts = md.vertices;
                        for (int vi = 0, vn = verts.size(); vi + 2 < vn; vi += 3)
                        {
                            Vector3f v0 = verts.get(vi);
                            Vector3f v1 = verts.get(vi + 1);
                            Vector3f v2 = verts.get(vi + 2);
                            v4.set(v0.x, v0.y, v0.z, 1.0f); posMat.transform(v4); consumer.vertex(v4.x, v4.y, v4.z);
                            v4.set(v1.x, v1.y, v1.z, 1.0f); posMat.transform(v4); consumer.vertex(v4.x, v4.y, v4.z);
                            v4.set(v2.x, v2.y, v2.z, 1.0f); posMat.transform(v4); consumer.vertex(v4.x, v4.y, v4.z);
                            v4.set(v2.x, v2.y, v2.z, 1.0f); posMat.transform(v4); consumer.vertex(v4.x, v4.y, v4.z);
                        }
                        matrices.pop();
                    }
                }
            }
            return false;
        }

        private static void emitFilteredQuad(Matrix4f posMat, ModelQuad quad, AlphaMask mask, VertexConsumer consumer, Vector4f v4)
        {
            List<ModelVertex> verts = quad.vertices;
            if (verts == null || verts.size() < 4)
            {
                return;
            }

            if (mask == null || !mask.hasTransparency)
            {
                emitQuadDirect(posMat, verts, consumer, v4);
                return;
            }

            ModelVertex mv0 = verts.get(0);
            ModelVertex mv1 = verts.get(1);
            ModelVertex mv2 = verts.get(2);
            ModelVertex mv3 = verts.get(3);

            if (mv0 == null || mv1 == null || mv2 == null || mv3 == null ||
                mv0.vertex == null || mv1.vertex == null || mv2.vertex == null || mv3.vertex == null ||
                mv0.uv == null || mv1.uv == null || mv2.uv == null || mv3.uv == null)
            {
                emitQuadDirect(posMat, verts, consumer, v4);
                return;
            }

            float u0 = mv0.uv.x, v0 = mv0.uv.y;
            float u1 = mv1.uv.x, v1 = mv1.uv.y;
            float u2 = mv2.uv.x, v2 = mv2.uv.y;
            float u3 = mv3.uv.x, v3 = mv3.uv.y;

            float du1 = u1 - u0, dv1 = v1 - v0;
            float du2 = u3 - u0, dv2 = v3 - v0;

            int stepsU = Math.min(64, Math.max(1, Math.round((float) Math.hypot(du1 * mask.width, dv1 * mask.height))));
            int stepsV = Math.min(64, Math.max(1, Math.round((float) Math.hypot(du2 * mask.width, dv2 * mask.height))));

            int totalCount = stepsU * stepsV;
            int solidCount = 0;
            boolean[] solidGrid = new boolean[totalCount];

            for (int i = 0; i < stepsU; i++)
            {
                float sCenter = (i + 0.5f) / (float) stepsU;
                for (int j = 0; j < stepsV; j++)
                {
                    float tCenter = (j + 0.5f) / (float) stepsV;
                    float u = (1f - sCenter) * (1f - tCenter) * u0 + sCenter * (1f - tCenter) * u1 + sCenter * tCenter * u2 + (1f - sCenter) * tCenter * u3;
                    float v = (1f - sCenter) * (1f - tCenter) * v0 + sCenter * (1f - tCenter) * v1 + sCenter * tCenter * v2 + (1f - sCenter) * tCenter * v3;

                    int px = Math.min(Math.max((int) Math.floor(u * mask.width), 0), mask.width - 1);
                    int py = Math.min(Math.max((int) Math.floor(v * mask.height), 0), mask.height - 1);

                    int alpha = mask.getAlpha(px, py);
                    if (alpha >= 25)
                    {
                        solidGrid[i + j * stepsU] = true;
                        solidCount++;
                    }
                }
            }

            if (solidCount == 0)
            {
                return;
            }

            if (solidCount == totalCount)
            {
                emitQuadDirect(posMat, verts, consumer, v4);
                return;
            }

            for (int i = 0; i < stepsU; i++)
            {
                float s0 = (float) i / (float) stepsU;
                float s1 = (float) (i + 1) / (float) stepsU;
                for (int j = 0; j < stepsV; j++)
                {
                    if (!solidGrid[i + j * stepsU])
                    {
                        continue;
                    }
                    float t0 = (float) j / (float) stepsV;
                    float t1 = (float) (j + 1) / (float) stepsV;

                    emitBilinearSubQuad(posMat, consumer, v4, mv0, mv1, mv2, mv3, s0, t0, s1, t1);
                }
            }
        }

        private static void emitBilinearSubQuad(Matrix4f posMat, VertexConsumer consumer, Vector4f v4,
                                                ModelVertex mv0, ModelVertex mv1, ModelVertex mv2, ModelVertex mv3,
                                                float s0, float t0, float s1, float t1)
        {
            emitInterpolatedVertex(posMat, consumer, v4, mv0, mv1, mv2, mv3, s0, t0);
            emitInterpolatedVertex(posMat, consumer, v4, mv0, mv1, mv2, mv3, s1, t0);
            emitInterpolatedVertex(posMat, consumer, v4, mv0, mv1, mv2, mv3, s1, t1);
            emitInterpolatedVertex(posMat, consumer, v4, mv0, mv1, mv2, mv3, s0, t1);
        }

        private static void emitInterpolatedVertex(Matrix4f posMat, VertexConsumer consumer, Vector4f v4,
                                                   ModelVertex mv0, ModelVertex mv1, ModelVertex mv2, ModelVertex mv3,
                                                   float s, float t)
        {
            float w0 = (1f - s) * (1f - t);
            float w1 = s * (1f - t);
            float w2 = s * t;
            float w3 = (1f - s) * t;

            float x = w0 * mv0.vertex.x + w1 * mv1.vertex.x + w2 * mv2.vertex.x + w3 * mv3.vertex.x;
            float y = w0 * mv0.vertex.y + w1 * mv1.vertex.y + w2 * mv2.vertex.y + w3 * mv3.vertex.y;
            float z = w0 * mv0.vertex.z + w1 * mv1.vertex.z + w2 * mv2.vertex.z + w3 * mv3.vertex.z;

            v4.set(x, y, z, 1.0f);
            posMat.transform(v4);
            consumer.vertex(v4.x, v4.y, v4.z);
        }

        private static void emitQuadDirect(Matrix4f posMat, List<ModelVertex> verts, VertexConsumer consumer, Vector4f v4)
        {
            for (int vi = 0, vn = Math.min(4, verts.size()); vi < vn; vi++)
            {
                ModelVertex mv = verts.get(vi);
                if (mv != null && mv.vertex != null)
                {
                    v4.set(mv.vertex.x, mv.vertex.y, mv.vertex.z, 1.0f);
                    posMat.transform(v4);
                    consumer.vertex(v4.x, v4.y, v4.z);
                }
            }
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
        public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, MatrixStack matrices, RenderLayer renderLayer,
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
