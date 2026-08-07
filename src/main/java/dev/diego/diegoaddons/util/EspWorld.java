package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The ESP boxes.
 *
 * <p>A gradient here is one gradient rather than a stack of slices pretending to be one: the fade is
 * carried on the vertex colours, so the GPU interpolates it and a box is a box. That is what
 * {@link WorldGeometry#fillBox} does, and an outline is the same call twelve times over the box's
 * edges - each edge taking its colour from where it sits in the whole box, so the twelve read as one
 * shape.
 *
 * <p>Everything is built at <b>queue</b> time. The render callback runs per frame and the queue is
 * replaced per tick, so the callback does nothing but walk a list and emit vertices; none of the
 * geometry can change underneath it.
 */
public final class EspWorld {
    /**
     * One queued box, ready to draw.
     *
     * <p>{@code gradMinY}/{@code gradMaxY} are the height range the two colours belong to, which for
     * an outline's edge is the whole box rather than that thin edge - see {@link WorldGeometry#fillBox}.
     */
    private record Box(AABB geom, int argbBottom, int argbTop, double gradMinY, double gradMaxY) {
    }

    private static final List<Box> BUILDING = new ArrayList<>();
    private static volatile List<Box> RENDER = new ArrayList<>();

    private static boolean registered;

    private EspWorld() {
    }

    /** Hooks the level renderer once. Safe to call repeatedly. */
    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(EspWorld::draw);
    }

    // --- queuing ------------------------------------------------------------------------------------

    /** An outlined box in one colour. */
    public static void outline(AABB box, int argb, double thickness) {
        outlineFade(box, argb, argb, thickness);
    }

    /** An outlined box fading from {@code argbA} at the bottom to {@code argbB} at the top. */
    public static void outlineFade(AABB box, int argbA, int argbB, double thickness) {
        init();
        synchronized (BUILDING) {
            for (AABB edge : WorldGeometry.edges(box, thickness)) {
                BUILDING.add(new Box(edge, argbA, argbB, box.minY, box.maxY));
            }
        }
    }

    /** A filled box in one colour. */
    public static void fill(AABB box, int argb) {
        fillFade(box, argb, argb);
    }

    /** A filled box fading up its height. */
    public static void fillFade(AABB box, int argbA, int argbB) {
        init();
        synchronized (BUILDING) {
            BUILDING.add(new Box(box, argbA, argbB, box.minY, box.maxY));
        }
    }

    /**
     * Asks for an entity's own model to be outlined.
     *
     * <p><b>Currently draws the box instead.</b> Outlining the model rather than a box around it is a
     * different mechanism entirely - it needs the entity's own geometry re-rendered into an outline
     * buffer, which the game does for its glowing effect and does not expose for anything else. Until
     * that is wired up by hand, the honest thing is to draw the shape we can draw rather than nothing
     * at all, so a mob set to the model style is still marked.
     */
    public static void outlineModel(Entity entity, int argb) {
        outline(entity.getBoundingBox(), argb, 0.05);
    }

    /** Promotes this tick's boxes and starts a fresh set. */
    public static void flip() {
        synchronized (BUILDING) {
            RENDER = new ArrayList<>(BUILDING);
            BUILDING.clear();
        }
    }

    /** Drops everything, e.g. on leaving a world. */
    public static void clear() {
        synchronized (BUILDING) {
            BUILDING.clear();
        }
        RENDER = new ArrayList<>();
    }

    // --- drawing ------------------------------------------------------------------------------------

    /**
     * Emits this tick's boxes. Deliberately does no work beyond emitting vertices - everything else
     * was done when the box was queued, because this runs once per frame and that runs once per tick.
     */
    private static void draw(LevelRenderContext ctx) {
        List<Box> boxes = RENDER;
        if (boxes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = ctx.poseStack().last();
        MultiBufferSource.BufferSource buffers = ctx.bufferSource();

        // An ESP you cannot see through a wall defeats the point, so this is the see-through variant
        // throughout - the engine's own filled pipeline with the depth test cleared.
        VertexConsumer vc = buffers.getBuffer(EspRenderTypes.QUADS);
        for (Box b : boxes) {
            WorldGeometry.fillBox(pose, vc, b.geom(), cam,
                    b.argbBottom(), b.argbTop(), b.gradMinY(), b.gradMaxY());
        }
        buffers.endBatch();
    }
}
