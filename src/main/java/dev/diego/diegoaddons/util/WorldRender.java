package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

    /**
     * How much a label shrinks per block of size. Minecraft's own name plates use this number; a
     * label at scale 1 comes out the size of a name plate, which is what every caller means by it.
     */
    private static final float LABEL_SCALE = 0.025f;

    /** Full daylight on both sky and block channels - a label is lit by nothing but itself. */
    private static final int FULL_BRIGHT = 0x00F000F0;

    /**
     * Draws this tick's labels: each one turned to face the camera, drawn through walls, centred on
     * its world position.
     */
    private static void drawLabels(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        List<Label> labels = RENDER_LABELS;
        if (labels.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Camera camera = mc.gameRenderer.getMainCamera();
        for (Label l : labels) {
            pose.pushPose();
            pose.translate(l.pos().x - cam.x, l.pos().y - cam.y, l.pos().z - cam.z);
            // The camera's own rotation turns the quad to face it; the negative scale is what flips
            // the text the right way up, since screen Y grows downwards and world Y grows up.
            pose.mulPose(camera.rotation());
            pose.scale(-LABEL_SCALE * l.scale(), -LABEL_SCALE * l.scale(), LABEL_SCALE * l.scale());
            font.drawInBatch(l.text(), -font.width(l.text()) / 2f, 0f, 0xFFFFFFFF, false,
                    pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
            pose.popPose();
        }
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
        for (AABB edge : WorldGeometry.edges(b, thickness)) {
            filledBox(edge, argb, throughWalls);
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
        List<Line> segments = RENDER_LINES;
        if (boxes.isEmpty() && segments.isEmpty() && RENDER_LABELS.isEmpty()) {
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
                WorldGeometry.fillBox(pose.last(), buffers.getBuffer(rt), b.box(), cam, b.argb());
            } else {
                VertexConsumer vc = buffers.getBuffer(
                        b.throughWalls() ? EspRenderTypes.LINES : RenderTypes.lines());
                ShapeRenderer.renderShape(pose, vc, Shapes.create(b.box()),
                        -cam.x, -cam.y, -cam.z, b.argb(), 1.0f);
            }
        }
        try {
            VertexConsumer vc = buffers.getBuffer(EspRenderTypes.LINES);
            for (Line l : segments) {
                WorldGeometry.line(pose.last(), vc, l.a(), l.b(), cam, l.argb(), 2.0f);
            }
        } catch (Throwable ignored) {
            // A line-render hiccup must never take the whole batch (boxes/labels) down with it.
        }
        drawLabels(pose, buffers, cam);
        buffers.endBatch();
    }
}
