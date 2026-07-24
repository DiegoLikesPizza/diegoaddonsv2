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

    /** One queued label: what to write, where, and how big. */
    private record Label(String text, Vec3 pos, float scale) {
    }

    private static final List<Box> QUEUE = new ArrayList<>();
    private static final List<Label> LABELS = new ArrayList<>();
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

    /**
     * Queues a path as a chain of thin boxes between consecutive points.
     *
     * <p>Segments are axis-aligned in every puzzle that uses this, so a box spanning two points is
     * exactly the line between them - and unlike a line primitive it has a width worth seeing.
     */
    public static void path(java.util.List<net.minecraft.world.phys.Vec3> points, int argb, double thickness) {
        double t = thickness / 2.0;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            filledBox(new AABB(
                    Math.min(a.x, b.x) - t, Math.min(a.y, b.y) - t, Math.min(a.z, b.z) - t,
                    Math.max(a.x, b.x) + t, Math.max(a.y, b.y) + t, Math.max(a.z, b.z) + t),
                    argb, true);
        }
    }

    /** Queues a one-block box around a position. */
    public static void blockBox(double x, double y, double z, int argb, boolean throughWalls) {
        box(new AABB(x, y, z, x + 1, y + 1, z + 1), argb, throughWalls);
    }

    /**
     * Queues a label floating at a world position. It always faces the camera and draws through
     * walls, because a countdown you cannot read is worse than none.
     */
    public static void text(String text, Vec3 pos, float scale) {
        synchronized (QUEUE) {
            LABELS.add(new Label(text, pos, scale));
        }
    }

    /** Drops everything queued, e.g. when leaving a world. */
    public static void clear() {
        synchronized (QUEUE) {
            LABELS.clear();
            QUEUE.clear();
        }
    }

    private static void draw(LevelRenderContext ctx) {
        List<Box> boxes;
        List<Label> labels;
        synchronized (QUEUE) {
            if (QUEUE.isEmpty() && LABELS.isEmpty()) {
                return;
            }
            boxes = new ArrayList<>(QUEUE);
            labels = new ArrayList<>(LABELS);
            QUEUE.clear();
            LABELS.clear();
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
        for (Label l : labels) {
            label(mc, pose, buffers, l, cam);
        }
        buffers.endBatch();
    }

    /**
     * Draws a label as a billboard: rotated by the camera so it always faces the viewer, and flipped
     * on X and Y because text is laid out top-down while the world is not.
     */
    private static void label(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers,
                              Label l, Vec3 cam) {
        var font = mc.font;
        var text = net.minecraft.network.chat.Component.literal(l.text());
        pose.pushPose();
        pose.translate((float) (l.pos().x - cam.x), (float) (l.pos().y - cam.y), (float) (l.pos().z - cam.z));
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        pose.scale(-0.025f * l.scale(), -0.025f * l.scale(), 0.025f * l.scale());
        font.drawInBatch(text, -font.width(text) / 2f, 0f, 0xFFFFFFFF, false,
                pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                0, 15728880);
        pose.popPose();
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
