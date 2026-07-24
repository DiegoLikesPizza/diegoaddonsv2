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
    /** One queued box: where, what colour, whether it shows through walls, and whether it is solid. */
    private record Box(AABB box, int argb, boolean throughWalls, boolean filled) {
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

    /** Queues an outlined box for this frame. Call every frame it should stay visible. */
    public static void box(AABB box, int argb, boolean throughWalls) {
        synchronized (QUEUE) {
            QUEUE.add(new Box(box, argb, throughWalls, false));
        }
    }

    /** Queues a solid box. */
    public static void filledBox(AABB box, int argb, boolean throughWalls) {
        synchronized (QUEUE) {
            QUEUE.add(new Box(box, argb, throughWalls, true));
        }
    }

    /**
     * Queues an outline of the given thickness, as twelve thin solid edges rather than lines.
     *
     * <p>Line primitives render one pixel wide no matter the distance, so they cannot be made
     * heavier. Building each edge as a thin box gives an outline whose weight is actually visible
     * and scales with perspective like everything else in the world.
     *
     * @param thickness edge width in blocks
     */
    public static void thickBox(AABB b, int argb, double thickness, boolean throughWalls) {
        double t = thickness / 2.0;
        // Four edges along X, four along Z, four uprights along Y.
        for (double[] e : new double[][]{
                {b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ},
                {b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ},
                {b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ},
                {b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ},
                {b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ},
                {b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ},
                {b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ},
                {b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ},
                {b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ},
                {b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ},
        }) {
            filledBox(new AABB(
                    Math.min(e[0], e[3]) - t, Math.min(e[1], e[4]) - t, Math.min(e[2], e[5]) - t,
                    Math.max(e[0], e[3]) + t, Math.max(e[1], e[4]) + t, Math.max(e[2], e[5]) + t),
                    argb, throughWalls);
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
            if (b.filled()) {
                fill(pose, buffers.getBuffer(RenderTypes.debugFilledBox()), b.box(), b.argb(), cam);
            } else {
                VertexConsumer vc = buffers.getBuffer(
                        b.throughWalls() ? RenderTypes.linesTranslucent() : RenderTypes.lines());
                ShapeRenderer.renderShape(pose, vc, Shapes.create(b.box()),
                        -cam.x, -cam.y, -cam.z, b.argb(), 1.0f);
            }
        }
        buffers.endBatch();
    }

    /** The six faces of a box, wound so it is solid from any side. */
    private static void fill(PoseStack pose, VertexConsumer vc, AABB b, int argb, Vec3 cam) {
        PoseStack.Pose p = pose.last();
        float x1 = (float) (b.minX - cam.x), y1 = (float) (b.minY - cam.y), z1 = (float) (b.minZ - cam.z);
        float x2 = (float) (b.maxX - cam.x), y2 = (float) (b.maxY - cam.y), z2 = (float) (b.maxZ - cam.z);

        quad(vc, p, argb, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);   // north
        quad(vc, p, argb, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);   // south
        quad(vc, p, argb, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1);   // west
        quad(vc, p, argb, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);   // east
        quad(vc, p, argb, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);   // bottom
        quad(vc, p, argb, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1);   // top
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vc.addVertex(p, ax, ay, az).setColor(argb);
        vc.addVertex(p, bx, by, bz).setColor(argb);
        vc.addVertex(p, cx, cy, cz).setColor(argb);
        vc.addVertex(p, dx, dy, dz).setColor(argb);
    }
}
