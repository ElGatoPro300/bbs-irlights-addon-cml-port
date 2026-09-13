package org.qualet.irl.light.shadow;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;
import mchorse.bbs_mod.selectors.SelectorOwner;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.chunk.WorldChunk;
import qualet.irlite.IrliteConfig;
import qualet.irlite.client.light.LightCollector;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;
import qualet.irlite.mixin.client.bbs.FilmsAccessor;
import qualet.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import java.util.List;

/**
 * The IRLite {@link ShadowCasterSource}: BBS Form/Film/Morph silhouettes — the
 * distinguishing CAST half of the seam that makes a baked shadow match the BBS
 * in-editor preview (custom morph), where the redactor casts the real vanilla
 * model. Three caster kinds, union of three {@code collect} arms and three
 * {@code emitOccluder} draw arms:
 *
 * <ul>
 *   <li>ENTITY — world {@link LivingEntity}/{@link ItemEntity}, drawn BBS-morph
 *       first, vanilla fallback.</li>
 *   <li>MODEL_BLOCK — BBS {@link ModelBlockEntity} props.</li>
 *   <li>REPLAY — active Film replay stubs (non-actor).</li>
 * </ul>
 */
public final class IRLiteBbsCasterSource implements ShadowCasterSource
{
    /** Match the light collector's camera horizon. This removes the old 72-block
     *  mismatch for co-located lamp/caster scenes; the global bounded pool remains
     *  intentionally camera-prioritized for casters beyond this horizon. */
    private static final double COLLECT_DIST = LightCollector.MAX_DIST;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;

    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;

    /** Mirror of {@code ShadowBaker.OVERLAP_MARGIN} (private there). The model-block
     *  arm computes its bounding sphere by hand and emits via the raw
     *  {@link OccluderSink#emit} escape, which (unlike {@code emitFromBox}) does NOT
     *  add the cull slack — so add it here to match the entity/replay arms. Keep in sync. */
    private static final float OVERLAP_MARGIN = 0.5f;

    // ===================================================================== //
    //  collect — WHAT casts (entity -> model-block -> replay; stable arm      //
    //  order preserves deterministic equal-distance ties in the bounded set).//
    // ===================================================================== //

    @Override
    public void collect(ClientWorld world, Vec3d camPos, float tickDelta, OccluderSink sink)
    {
        double camX = camPos.x, camY = camPos.y, camZ = camPos.z;

        // --- Arm 1: world entities (vanilla / BBS-morph render path) ---
        for (Entity entity : world.getEntities())
        {
            if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity))
            {
                continue;
            }

            double ex = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double ey = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double ez = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
            double dx = ex - camX, dy = ey - camY, dz = ez - camZ;
            if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
            {
                continue;
            }

            // emitFromBox raises the center to mid-height and derives the
            // circumscribing box-diagonal radius (INVARIANT 5); the sink retains
            // the bounded nearest set. Entities are always dynamic -> isStatic
            // false, staticHash 0 (INVARIANT 2).
            sink.emitFromBox(entity, CasterType.ENTITY, false, ex, ey, ez, entity.getBoundingBox(), 1f, 0L);
        }

        // --- Arm 2: BBS model blocks (BlockEntity, not in world.getEntities()) ---
        collectModelBlocks(world, camX, camY, camZ, sink);

        // --- Arm 3: BBS film replays (non-actor stubs; actors come via Arm 1) ---
        collectFilmReplays(camX, camY, camZ, tickDelta, sink);
    }

    private static void collectModelBlocks(ClientWorld world, double camX, double camY, double camZ, OccluderSink sink)
    {
        if (world == null || world.getChunkManager() == null)
        {
            return;
        }

        int minChunkX = MathHelper.floor((camX - COLLECT_DIST) / 16.0);
        int maxChunkX = MathHelper.floor((camX + COLLECT_DIST) / 16.0);
        int minChunkZ = MathHelper.floor((camZ - COLLECT_DIST) / 16.0);
        int maxChunkZ = MathHelper.floor((camZ + COLLECT_DIST) / 16.0);

        LongOpenHashSet visited = new LongOpenHashSet();

        for (int cx = minChunkX; cx <= maxChunkX; cx++)
        {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
            {
                WorldChunk chunk;
                try { chunk = world.getChunkManager().getWorldChunk(cx, cz); }
                catch (Throwable t) { continue; }
                if (chunk == null)
                {
                    continue;
                }

                for (BlockEntity be : chunk.getBlockEntities().values())
                {
                    if (!(be instanceof ModelBlockEntity mbe))
                    {
                        continue;
                    }
                    BlockPos pos = mbe.getPos();
                    if (pos == null || !visited.add(pos.asLong()))
                    {
                        continue;
                    }

                    double dx = pos.getX() + 0.5 - camX;
                    double dy = pos.getY() + 0.5 - camY;
                    double dz = pos.getZ() + 0.5 - camZ;
                    if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
                    {
                        continue;
                    }

                    ModelProperties props;
                    try { props = mbe.getProperties(); }
                    catch (Throwable t) { continue; }
                    if (props == null || !props.isEnabled())
                    {
                        continue;
                    }
                    Form form = props.getForm();
                    if (!hasShadowGeometry(form))
                    {
                        continue;
                    }
                    Transform t = props.getTransform();
                    emitModelBlock(sink, mbe, form, t);
                }
            }
        }
    }

    private static void collectFilmReplays(double camX, double camY, double camZ, float tickDelta, OccluderSink sink)
    {
        Films films;
        try { films = BBSModClient.getFilms(); }
        catch (Throwable t) { films = null; }

        List<BaseFilmController> ctrls;
        try { ctrls = films != null ? ((FilmsAccessor) (Object) films).irlite$getControllers() : null; }
        catch (Throwable t) { ctrls = null; }

        FilmEditorController editor = getActiveEditorController();
        int worldN = ctrls == null ? 0 : ctrls.size();
        int total = worldN + (editor != null ? 1 : 0);

        for (int ci = 0; ci < total; ci++)
        {
            BaseFilmController ctrl = ci < worldN ? ctrls.get(ci) : editor;
            if (ctrl == null || ctrl.film == null)
            {
                continue;
            }
            if (ctrl.getEntities() == null || ctrl.getEntities().isEmpty())
            {
                continue;
            }

            for (IntObjectMap.PrimitiveEntry<IEntity> e : ctrl.getEntities().entries())
            {
                IEntity ent = e.value();
                if (ent == null)
                {
                    continue;
                }
                Form form = ent.getForm();
                if (!hasShadowGeometry(form))
                {
                    continue;
                }

                double wx = MathHelper.lerp(tickDelta, ent.getPrevX(), ent.getX());
                double wy = MathHelper.lerp(tickDelta, ent.getPrevY(), ent.getY());
                double wz = MathHelper.lerp(tickDelta, ent.getPrevZ(), ent.getZ());

                double dx = wx - camX, dy = wy - camY, dz = wz - camZ;
                if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
                {
                    continue;
                }

                float hbW = Math.max(0.1f, form.hitboxWidth.get());
                float hbH = Math.max(0.1f, form.hitboxHeight.get());
                Box box = new Box(-hbW * 0.5, 0, -hbW * 0.5, hbW * 0.5, hbH, hbW * 0.5);

                sink.emitFromBox(ent, CasterType.REPLAY, false, wx, wy, wz, box, 1f, 0L);
            }
        }
    }

    private static boolean hasShadowGeometry(Form form)
    {
        if (form == null)
        {
            return false;
        }
        if (!(form instanceof PointLightForm) && !(form instanceof SpotlightForm))
        {
            return true;
        }

        List<BodyPart> parts = form.parts.getAllTyped();
        if (parts == null)
        {
            return false;
        }
        for (int i = 0, n = parts.size(); i < n; i++)
        {
            BodyPart part = parts.get(i);
            if (part != null && hasShadowGeometry(part.getForm()))
            {
                return true;
            }
        }
        return false;
    }

    private static FilmEditorController getActiveEditorController()
    {
        try
        {
            UIDashboard dashboard = BBSModClient.getDashboard();
            if (dashboard == null)
            {
                return null;
            }
            if (!(dashboard.getPanels().panel instanceof UIFilmPanel filmPanel))
            {
                return null;
            }
            UIFilmController uiCtrl = filmPanel.getController();
            return uiCtrl == null ? null : uiCtrl.editorController;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static void emitModelBlock(OccluderSink sink, ModelBlockEntity mbe, Form form, Transform t)
    {
        float hbW = Math.max(0.1f, form.hitboxWidth.get());
        float hbH = Math.max(0.1f, form.hitboxHeight.get());

        float sx = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.x));
        float sy = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.y));
        float sz = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.z));
        float hx = hbW * 0.5f * sx;
        float hy = hbH * 0.5f * sy;
        float hz = hbW * 0.5f * sz;

        BlockPos pos = mbe.getPos();
        double tx = t == null ? 0 : t.translate.x;
        double ty = t == null ? 0 : t.translate.y;
        double tz = t == null ? 0 : t.translate.z;
        double pivotX = pos.getX() + 0.5 + tx;
        double pivotY = pos.getY() + ty;
        double pivotZ = pos.getZ() + 0.5 + tz;

        float ehx, ehy, ehz;
        double offX, offY, offZ;
        if (t == null)
        {
            ehx = hx; ehy = hy; ehz = hz;
            offX = 0.0; offY = hy; offZ = 0.0;
        }
        else
        {
            Matrix3f rot = t.createRotationMatrix();
            ehx = Math.abs(rot.m00) * hx + Math.abs(rot.m10) * hy + Math.abs(rot.m20) * hz;
            ehy = Math.abs(rot.m01) * hx + Math.abs(rot.m11) * hy + Math.abs(rot.m21) * hz;
            ehz = Math.abs(rot.m02) * hx + Math.abs(rot.m12) * hy + Math.abs(rot.m22) * hz;
            offX = rot.m10 * hy; offY = rot.m11 * hy; offZ = rot.m12 * hy;
        }

        double cx = pivotX + offX;
        double cy = pivotY + offY;
        double cz = pivotZ + offZ;
        float poseReach = IrliteConfig.shadowPoseReach();
        if (!(poseReach >= 0f))
        {
            poseReach = 1.0f;
        }
        float slack = Math.max(OVERLAP_MARGIN, poseReach * ehy);
        float radius = (float) Math.sqrt((double) ehx * ehx + (double) ehy * ehy + (double) ehz * ehz) + slack;

        boolean isStatic = false;
        long staticHash = isStatic
            ? modelBlockHash((float) cx, (float) cy, (float) cz, t, System.identityHashCode(form))
            : 0L;

        sink.emit(mbe, CasterType.MODEL_BLOCK, isStatic, (float) cx, (float) cy, (float) cz, radius, staticHash);
    }

    private static long modelBlockHash(float wx, float wy, float wz, Transform t, int formIdentity)
    {
        long h = FNV_OFFSET;
        h = (h ^ (formIdentity & 0xffffffffL)) * FNV_PRIME;
        h = mix(h, wx); h = mix(h, wy); h = mix(h, wz);
        if (t != null)
        {
            h = mix(h, t.scale.x); h = mix(h, t.scale.y); h = mix(h, t.scale.z);
            h = mix(h, t.rotate.x); h = mix(h, t.rotate.y); h = mix(h, t.rotate.z);
            h = mix(h, t.rotate2.x); h = mix(h, t.rotate2.y); h = mix(h, t.rotate2.z);
        }
        return h;
    }

    private static long mix(long h, float v)
    {
        return (h ^ (Float.floatToRawIntBits(v) & 0xffffffffL)) * FNV_PRIME;
    }

    // ===================================================================== //
    //  emitOccluder — HOW to draw ONE shortlisted caster.                   //
    // ===================================================================== //

    @Override
    public void emitOccluder(Object caster, int type, float tickDelta, OccluderBatch batch)
    {
        RawOccluderBatch rawBatch = (RawOccluderBatch) batch;
        Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        try
        {
            switch (type)
            {
                case CasterType.MODEL_BLOCK ->
                    drawModelBlock((ModelBlockEntity) caster, rawBatch, cam, tickDelta);
                case CasterType.REPLAY ->
                    drawReplay((IEntity) caster, rawBatch, cam, tickDelta);
                default /* ENTITY */ ->
                    drawEntity((Entity) caster, rawBatch, cam, tickDelta);
            }
        }
        catch (Throwable t)
        {
            System.err.println("[IRLite-Shadow] emitOccluder failed for type " + type + " (" + caster + "): " + t);
            t.printStackTrace();
        }
    }

    private static void drawEntity(Entity entity, RawOccluderBatch batch, Camera camera, float tickDelta)
    {
        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();
        double cx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - ox;
        double cy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - oy;
        double cz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - oz;

        if (entity instanceof AbstractClientPlayerEntity player)
        {
            Morph morph = Morph.getMorph(player);
            Form form = morph == null ? null : morph.getForm();
            if (form != null)
            {
                float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);
                MatrixStack matrices = new MatrixStack();
                matrices.translate(cx, cy, cz);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
                float[] tris = BbsOccluderGeometryCapturer.captureFormTris(form, morph.entity, matrices, camera, tickDelta);
                if (tris != null && tris.length > 0)
                {
                    batch.append(tris);
                    return;
                }
            }
        }
        else if (entity instanceof LivingEntity living && entity instanceof ISelectorOwnerProvider provider)
        {
            SelectorOwner owner = provider.getOwner();
            if (owner != null)
            {
                owner.check();
                Form form = owner.getForm();
                if (form != null)
                {
                    float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, living.lastBodyYaw, living.bodyYaw);
                    MatrixStack matrices = new MatrixStack();
                    matrices.translate(cx, cy, cz);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
                    float[] tris = BbsOccluderGeometryCapturer.captureFormTris(form, owner.entity, matrices, camera, tickDelta);
                    if (tris != null && tris.length > 0)
                    {
                        batch.append(tris);
                        return;
                    }
                }
            }
        }

        float[] tris = BbsOccluderGeometryCapturer.captureEntityTris(entity, tickDelta);
        if (tris != null && tris.length > 0)
        {
            batch.append(tris);
        }
    }

    private static void drawModelBlock(ModelBlockEntity mbe, RawOccluderBatch batch, Camera camera, float tickDelta)
    {
        if (mbe.getProperties() == null)
        {
            return;
        }
        Form form = mbe.getProperties().getForm();
        if (form == null)
        {
            return;
        }
        Transform t = mbe.getProperties().getTransform();

        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();
        double feetX = mbe.getPos().getX() + 0.5 - ox;
        double feetY = mbe.getPos().getY() - oy;
        double feetZ = mbe.getPos().getZ() + 0.5 - oz;

        MatrixStack matrices = new MatrixStack();
        matrices.translate(feetX, feetY, feetZ);
        if (t != null)
        {
            MatrixStackUtils.applyTransform(matrices, t);
        }
        float[] tris = BbsOccluderGeometryCapturer.captureFormTris(form, mbe.getEntity(), matrices, camera, tickDelta);
        if (tris != null && tris.length > 0)
        {
            batch.append(tris);
        }
    }

    private static void drawReplay(IEntity stub, RawOccluderBatch batch, Camera camera, float tickDelta)
    {
        Form form = stub.getForm();
        if (form == null)
        {
            return;
        }

        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();

        Matrix4f baseMatrix = BaseFilmController.getMatrixForRenderWithRotation(stub, ox, oy, oz, tickDelta);
        MatrixStack matrices = new MatrixStack();
        matrices.peek().getPositionMatrix().mul(baseMatrix);

        float[] tris = BbsOccluderGeometryCapturer.captureFormTris(form, stub, matrices, camera, tickDelta);
        if (tris != null && tris.length > 0)
        {
            batch.append(tris);
        }
    }
}
