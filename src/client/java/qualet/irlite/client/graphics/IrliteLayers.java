package qualet.irlite.client.graphics;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.iris.IrisFormPipelines;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class IrliteLayers
{
    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    private static final RenderPipeline POSITION_COLOR_LINES = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("irlite", "pipeline/draw_position_color_lines"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("irlite", "pipeline/draw_position_color"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("irlite", "pipeline/draw_position_color_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS_STENCIL = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("irlite", "pipeline/draw_position_color_stencil"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_TEX_COLOR_QUADS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation(Identifier.of("irlite", "pipeline/draw_position_tex_color_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_TEX_COLOR_TRIS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation(Identifier.of("irlite", "pipeline/draw_position_tex_color_tris_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );

    static
    {
        if (BBSRendering.isIrisLoaded())
        {
            IrisFormPipelines.registerColor(POSITION_COLOR_LINES);
            IrisFormPipelines.registerColor(POSITION_COLOR_TRIS);
            IrisFormPipelines.registerColor(POSITION_COLOR_TRIS_NO_DEPTH);
            IrisFormPipelines.registerColor(POSITION_COLOR_TRIS_STENCIL);
            IrisFormPipelines.register(POSITION_TEX_COLOR_QUADS_NO_DEPTH, null, false);
            IrisFormPipelines.register(POSITION_TEX_COLOR_TRIS_NO_DEPTH, null, false);
        }
    }

    private static RenderLayer positionColorLinesLayer;
    private static RenderLayer positionColorLayer;
    private static RenderLayer positionColorNoDepthLayer;
    private static RenderLayer positionColorStencilLayer;

    public static RenderLayer getPositionColorLinesLayer()
    {
        if (positionColorLinesLayer == null)
        {
            positionColorLinesLayer = RenderLayer.of("irlite_draw_position_color_lines",
                RenderSetup.builder(POSITION_COLOR_LINES).translucent().build());
        }
        return positionColorLinesLayer;
    }

    public static RenderLayer getPositionColorLayer()
    {
        if (positionColorLayer == null)
        {
            positionColorLayer = RenderLayer.of("irlite_draw_position_color",
                RenderSetup.builder(POSITION_COLOR_TRIS).translucent().build());
        }
        return positionColorLayer;
    }

    public static RenderLayer getPositionColorNoDepthLayer()
    {
        if (positionColorNoDepthLayer == null)
        {
            positionColorNoDepthLayer = RenderLayer.of("irlite_draw_position_color_no_depth",
                RenderSetup.builder(POSITION_COLOR_TRIS_NO_DEPTH).translucent().build());
        }
        return positionColorNoDepthLayer;
    }

    public static RenderLayer getPositionColorStencilLayer()
    {
        if (positionColorStencilLayer == null)
        {
            positionColorStencilLayer = RenderLayer.of("irlite_draw_position_color_stencil",
                RenderSetup.builder(POSITION_COLOR_TRIS_STENCIL).build());
        }
        return positionColorStencilLayer;
    }

    public static RenderLayer getPositionTexColorNoDepthLayer(Identifier texture)
    {
        RenderSetup setup = RenderSetup.builder(POSITION_TEX_COLOR_QUADS_NO_DEPTH)
            .texture("Sampler0", texture)
            .translucent()
            .build();
        return RenderLayer.of("irlite_billboard_" + texture.getPath(), setup);
    }

    public static RenderLayer getPositionTexColorTrisNoDepthLayer(Identifier texture)
    {
        RenderSetup setup = RenderSetup.builder(POSITION_TEX_COLOR_TRIS_NO_DEPTH)
            .texture("Sampler0", texture)
            .translucent()
            .build();
        return RenderLayer.of("irlite_billboard_tris_" + texture.getPath(), setup);
    }

    public static void flush(BufferBuilder builder, RenderLayer layer)
    {
        BuiltBuffer built = builder.endNullable();
        if (built != null)
        {
            layer.draw(built);
        }
    }

    public static void flushLines(BufferBuilder builder)
    {
        flush(builder, getPositionColorLinesLayer());
    }

    public static void flushTriangles(BufferBuilder builder)
    {
        flush(builder, getPositionColorLayer());
    }

    public static void flushTrianglesNoDepth(BufferBuilder builder)
    {
        flush(builder, getPositionColorNoDepthLayer());
    }

    public static void flushStencilTriangles(BufferBuilder builder)
    {
        flush(builder, getPositionColorStencilLayer());
    }

    private IrliteLayers()
    {}
}
