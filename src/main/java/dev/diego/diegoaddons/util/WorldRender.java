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

    /** One queued 3-D line segment. */
    private record Line(Vec3 a, Vec3 b, int argb) {
    }

    // Features submit once per client tick (20/s) but the world is drawn per frame (60+/s). If the
    // draw list were cleared every frame, boxes would show for only the one frame after each tick and
    // flicker. Instead submissions accumulate into BUILDING, and once per tick {@link #flip()} swaps
    // them into RENDER - which every frame draws without clearing - so a box stays solid until the
    // next tick replaces it.
    private static final List<Box> BUILDING = new ArrayList<>();
    private static final List<Label> BUILDING_LABELS = new ArrayList<>();
    private static final List<Line> BUILDING_LINES = new ArrayList<>();
    private static volatile List<Box> RENDER = new ArrayList<>();
    private static volatile List<Label> RENDER_LABELS = new ArrayList<>();
    private static volatile List<Line> RENDER_LINES = new ArrayList<>();
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

    /** Queues an outlined box. Submit it every tick it should stay visible; {@link #flip()} keeps it
     * drawn on the frames in between. */
    public static void box(AABB box, int argb, boolean throughWalls) {
        synchronized (BUILDING) {
            BUILDING.add(new Box(box, argb, throughWalls, false));
        }
    }

    /** Queues a solid box. */
    public static void filledBox(AABB box, int argb, boolean throughWalls) {
        synchronized (BUILDING) {
            BUILDING.add(new Box(box, argb, throughWalls, true));
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
        synchronized (BUILDING) {
            BUILDING_LABELS.add(new Label(text, pos, scale));
        }
    }

    /** Queues a single 3-D line segment, drawn through walls. */
    public static void line(Vec3 a, Vec3 b, int argb) {
        synchronized (BUILDING) {
            BUILDING_LINES.add(new Line(a, b, argb));
        }
    }

    /** Queues a polyline: a line through each consecutive pair of points. */
    public static void lines(List<Vec3> points, int argb) {
        synchronized (BUILDING) {
            for (int i = 0; i + 1 < points.size(); i++) {
                BUILDING_LINES.add(new Line(points.get(i), points.get(i + 1), argb));
            }
        }
    }

    /**
     * Promotes this tick's submissions to the set drawn every frame, and starts a fresh one. Called
     * once per client tick after all features have submitted, so the next tick fully replaces the
     * last - a feature that stops submitting simply drops out on the following tick.
     */
    public static void flip() {
        synchronized (BUILDING) {
            RENDER = new ArrayList<>(BUILDING);
            RENDER_LABELS = new ArrayList<>(BUILDING_LABELS);
            RENDER_LINES = new ArrayList<>(BUILDING_LINES);
            BUILDING.clear();
            BUILDING_LABELS.clear();
            BUILDING_LINES.clear();
        }
    }

    /** Drops everything, e.g. when leaving a world. */
    public static void clear() {
        synchronized (BUILDING) {
            BUILDING.clear();
            BUILDING_LABELS.clear();
            BUILDING_LINES.clear();
        }
        RENDER = new ArrayList<>();
        RENDER_LABELS = new ArrayList<>();
        RENDER_LINES = new ArrayList<>();
    }

    private static void draw(LevelRenderContext ctx) {
        List<Box> boxes = RENDER;
        List<Label> labels = RENDER_LABELS;
        List<Line> segments = RENDER_LINES;
        if (boxes.isEmpty() && labels.isEmpty() && segments.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack pose = ctx.poseStack();
        MultiBufferSource.BufferSource buffers = ctx.bufferSource();

        // "Through walls" needs the depth test off, which the engine's own line/filled types keep on;
        // the ESP render types are the same pipelines with it cleared, so they draw over terrain.
        for (Box b : boxes) {
            if (b.filled()) {
                var rt = b.throughWalls() ? EspRenderTypes.QUADS : RenderTypes.debugFilledBox();
                fill(pose, buffers.getBuffer(rt), b.box(), b.argb(), cam);
            } else {
                VertexConsumer vc = buffers.getBuffer(
                        b.throughWalls() ? EspRenderTypes.LINES : RenderTypes.lines());
                ShapeRenderer.renderShape(pose, vc, Shapes.create(b.box()),
                        -cam.x, -cam.y, -cam.z, b.argb(), 1.0f);
            }
        }
        try {
            for (Line l : segments) {
                line(pose, buffers.getBuffer(EspRenderTypes.LINES), l, cam);
            }
        } catch (Throwable ignored) {
            // A line-render hiccup must never take the whole batch (boxes/labels) down with it.
        }
        for (Label l : labels) {
            label(mc, pose, buffers, l, cam);
        }
        buffers.endBatch();
    }

    /** Draws one line segment, camera-relative, with a normal along its direction. */
    private static void line(PoseStack pose, VertexConsumer vc, Line l, Vec3 cam) {
        PoseStack.Pose p = pose.last();
        float ax = (float) (l.a().x - cam.x), ay = (float) (l.a().y - cam.y), az = (float) (l.a().z - cam.z);
        float bx = (float) (l.b().x - cam.x), by = (float) (l.b().y - cam.y), bz = (float) (l.b().z - cam.z);
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f) {
            return;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        // Each line vertex must carry a line width, or the buffer fails to flush and the game crashes.
        vc.addVertex(p, ax, ay, az).setColor(l.argb()).setNormal(p, nx, ny, nz).setLineWidth(2.0f);
        vc.addVertex(p, bx, by, bz).setColor(l.argb()).setNormal(p, nx, ny, nz).setLineWidth(2.0f);
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
