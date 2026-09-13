package qualet.irlite.client.graphics;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.iris.IrisFormPipelines;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class IrliteLayers
{
    private static RenderPipeline.Builder withUniforms(RenderPipeline source)
    {
        RenderPipeline.Builder builder = RenderPipeline.builder();

        for (RenderPipeline.UniformDescription uniform : source.getUniforms())
        {
            if (uniform.textureFormat() == null)
            {
                builder.withUniform(uniform.name(), uniform.type());
            }
            else
            {
                builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
            }
        }

        return builder;
    }

    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    private static final RenderPipeline POSITION_COLOR_LINES = RenderPipelines.register(
        withUniforms(RenderPipelines.DEBUG_FILLED_BOX)
            .withLocation(Identifier.fromNamespaceAndPath("irlite", "pipeline/draw_position_color_lines"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES)
            .withColorTargetState(new ColorTargetState(BLEND))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS = RenderPipelines.register(
        withUniforms(RenderPipelines.DEBUG_FILLED_BOX)
            .withLocation(Identifier.fromNamespaceAndPath("irlite", "pipeline/draw_position_color"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BLEND))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS_NO_DEPTH = RenderPipelines.register(
        withUniforms(RenderPipelines.DEBUG_FILLED_BOX)
            .withLocation(Identifier.fromNamespaceAndPath("irlite", "pipeline/draw_position_color_no_depth"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BLEND))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS_STENCIL = RenderPipelines.register(
        withUniforms(RenderPipelines.DEBUG_FILLED_BOX)
            .withLocation(Identifier.fromNamespaceAndPath("irlite", "pipeline/draw_position_color_stencil"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_TEX_COLOR_QUADS_NO_DEPTH = RenderPipelines.register(
        withUniforms(RenderPipelines.GUI_TEXTURED)
            .withLocation(Identifier.fromNamespaceAndPath("irlite", "pipeline/draw_position_tex_color_no_depth"))
            .withVertexShader(RenderPipelines.GUI_TEXTURED.getVertexShader())
            .withFragmentShader(RenderPipelines.GUI_TEXTURED.getFragmentShader())
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BLEND))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_TEX_COLOR_TRIS_NO_DEPTH = RenderPipelines.register(
        withUniforms(RenderPipelines.GUI_TEXTURED)
            .withLocation(Identifier.fromNamespaceAndPath("irlite", "pipeline/draw_position_tex_color_tris_no_depth"))
            .withVertexShader(RenderPipelines.GUI_TEXTURED.getVertexShader())
            .withFragmentShader(RenderPipelines.GUI_TEXTURED.getFragmentShader())
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BLEND))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
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

    private static RenderType positionColorLinesLayer;
    private static RenderType positionColorLayer;
    private static RenderType positionColorNoDepthLayer;
    private static RenderType positionColorStencilLayer;

    public static RenderType getPositionColorLinesLayer()
    {
        if (positionColorLinesLayer == null)
        {
            positionColorLinesLayer = RenderType.create("irlite_draw_position_color_lines",
                RenderSetup.builder(POSITION_COLOR_LINES).sortOnUpload().createRenderSetup());
        }
        return positionColorLinesLayer;
    }

    public static RenderType getPositionColorLayer()
    {
        if (positionColorLayer == null)
        {
            positionColorLayer = RenderType.create("irlite_draw_position_color",
                RenderSetup.builder(POSITION_COLOR_TRIS).sortOnUpload().createRenderSetup());
        }
        return positionColorLayer;
    }

    public static RenderType getPositionColorNoDepthLayer()
    {
        if (positionColorNoDepthLayer == null)
        {
            positionColorNoDepthLayer = RenderType.create("irlite_draw_position_color_no_depth",
                RenderSetup.builder(POSITION_COLOR_TRIS_NO_DEPTH).sortOnUpload().createRenderSetup());
        }
        return positionColorNoDepthLayer;
    }

    public static RenderType getPositionColorStencilLayer()
    {
        if (positionColorStencilLayer == null)
        {
            positionColorStencilLayer = RenderType.create("irlite_draw_position_color_stencil",
                RenderSetup.builder(POSITION_COLOR_TRIS_STENCIL).createRenderSetup());
        }
        return positionColorStencilLayer;
    }

    public static RenderType getPositionTexColorNoDepthLayer(Identifier texture)
    {
        RenderSetup setup = RenderSetup.builder(POSITION_TEX_COLOR_QUADS_NO_DEPTH)
            .withTexture("Sampler0", texture)
            .sortOnUpload()
            .createRenderSetup();
        return RenderType.create("irlite_billboard_" + texture.getPath(), setup);
    }

    public static RenderType getPositionTexColorTrisNoDepthLayer(Identifier texture)
    {
        RenderSetup setup = RenderSetup.builder(POSITION_TEX_COLOR_TRIS_NO_DEPTH)
            .withTexture("Sampler0", texture)
            .sortOnUpload()
            .createRenderSetup();
        return RenderType.create("irlite_billboard_tris_" + texture.getPath(), setup);
    }

    public static void flush(BufferBuilder builder, RenderType layer)
    {
        MeshData built = builder.build();
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
