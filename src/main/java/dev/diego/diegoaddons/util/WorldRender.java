package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws boxes in the world, for features that need to point at something out there rather than on
 * the HUD.
 *
 * <p>Boxes are <b>queued rather than drawn directly</b>: they are decided on the client thread, from
 * chat and entity state, but may only be drawn during the render pass. Each frame the queue is
 * replayed and then cleared, so a feature simply re-submits whatever it currently wants to show and
 * never has to clean up after itself.
 *
 * <p>Vertices go in camera-relative, which is what the level renderer expects - the camera position
 * is subtracted here so callers can work in plain world coordinates.
 */
public final class WorldRender {
    /** One queued box: where, what colour, and whether it shows through walls. */
    private record Box(AABB box, int argb, boolean throughWalls) {
    }

    private static final List<Box> QUEUE = new ArrayList<>();
    private static boolean registered;

    private WorldRender() {
    }

    /** Hooks the level renderer once. Safe to call repeatedly. */
    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WorldRender::draw);
    }

    /** Queues a box for this frame. Call every frame it should stay visible. */
    public static void box(AABB box, int argb, boolean throughWalls) {
        synchronized (QUEUE) {
            QUEUE.add(new Box(box, argb, throughWalls));
        }
    }

    /** Queues a one-block box around a position. */
    public static void blockBox(double x, double y, double z, int argb, boolean throughWalls) {
        box(new AABB(x, y, z, x + 1, y + 1, z + 1), argb, throughWalls);
    }

    /** Drops everything queued, e.g. when leaving a world. */
    public static void clear() {
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }

    private static void draw(LevelRenderContext ctx) {
        List<Box> boxes;
        synchronized (QUEUE) {
            if (QUEUE.isEmpty()) {
                return;
            }
            boxes = new ArrayList<>(QUEUE);
            QUEUE.clear();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack pose = ctx.poseStack();
        MultiBufferSource.BufferSource buffers = ctx.bufferSource();

        // Translucent lines ignore depth, which is what "through walls" needs.
        for (Box b : boxes) {
            VertexConsumer vc = buffers.getBuffer(
                    b.throughWalls() ? RenderTypes.linesTranslucent() : RenderTypes.lines());
            ShapeRenderer.renderShape(pose, vc, Shapes.create(b.box()),
                    -cam.x, -cam.y, -cam.z, b.argb(), 1.0f);
        }
        buffers.endBatch();
    }
}
