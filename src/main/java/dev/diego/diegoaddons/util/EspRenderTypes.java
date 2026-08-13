package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Optional;

/**
 * Render types for drawing through walls. The engine's own line and filled-box pipelines are
 * depth-tested, so a box behind terrain is hidden - useless for an ESP, whose whole point is seeing
 * a mob through a wall. These rebuild the same pipelines with the depth-stencil state cleared, so
 * the geometry always draws. Built once, statically, from the engine's line / filled snippets.
 */
public final class EspRenderTypes {
    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withDepthStencilState(Optional.empty())
                    .withLocation("diegoaddonsv2/lines_esp")
                    .build());

    private static final RenderPipeline QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withDepthStencilState(Optional.empty())
                    .withLocation("diegoaddonsv2/quads_esp")
                    .build());

    /** See-through line outlines. */
    public static final RenderType LINES = RenderType.create("diegoaddons_lines_esp",
            RenderSetup.builder(LINES_PIPELINE)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup());

    /** See-through filled quads (the thick-box edges). */
    public static final RenderType QUADS = RenderType.create("diegoaddons_quads_esp",
            RenderSetup.builder(QUADS_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    /**
     * The same quads, <b>depth-tested</b>: hidden behind terrain like anything else in the world.
     *
     * <p>This is the whole of the "Highlight" style. Everything else here exists to defeat the depth
     * test; this one deliberately keeps it, so the mark reads as paint on the mob rather than as a
     * shape floating in front of the landscape. The engine's own debug-box pipeline already has the
     * state wanted, so it is used as it comes rather than rebuilt with a hole in it.
     *
     * <p>No output target is set, unlike {@link #QUADS}: the depth test has to run against the main
     * framebuffer's depth, which is where the terrain was just drawn.
     */
    public static final RenderType QUADS_DEPTH = RenderType.create("diegoaddons_quads_depth",
            RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    private EspRenderTypes() {
    }
}
