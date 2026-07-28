package dev.diego.diegoaddons.util;

import com.render.api.RenderColor;
import com.render.api.RenderLibWorld;
import com.render.api.RenderRegistration;
import com.render.api.world.WorldDepthMode;
import com.render.api.world.WorldGradient;
import com.render.api.world.WorldMaterial;
import com.render.api.world.WorldPaint;
import com.render.api.world.WorldRenderContext;
import com.render.api.world.WorldStroke;
import com.render.api.world.WorldWidthMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ESP boxes, drawn through RenderLib.
 *
 * <p>They used to go through this mod's own queue, which can only fill an axis-aligned box in one
 * flat colour. A gradient there had to be faked by stacking six slices - visibly six things rather
 * than one fade - and an outline was twelve thin boxes rather than twelve lines. RenderLib takes a
 * gradient as a property of the paint, so a fade is one box and one line is one line.
 *
 * <p>Outlining the <em>model</em> rather than a box around it is a different mechanism again -
 * RenderLib tracks the entity and outlines whatever it draws - so that lives here too, with the
 * bookkeeping it needs: a tracked entity stops being tracked when nothing asks for it this tick.
 */
public final class EspWorld {
    /** One queued box: outline or fill, one colour or a fade up its height. */
    private record Box(AABB box, int argbA, int argbB, boolean gradient, boolean filled,
                       double thickness) {
    }

    private static final List<Box> BUILDING = new ArrayList<>();
    private static volatile List<Box> RENDER = new ArrayList<>();

    /** Entities RenderLib is outlining for us, and the ones asked for this tick. */
    private static final Map<Integer, RenderRegistration> OUTLINED = new HashMap<>();
    private static final Set<Integer> WANTED = new HashSet<>();

    private static boolean registered;

    private EspWorld() {
    }

    /** Hooks the world renderer once. Safe to call repeatedly. */
    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        RenderLibWorld.register(EspWorld::extract);
    }

    // --- queuing ------------------------------------------------------------------------------------

    /** An outlined box in one colour. */
    public static void outline(AABB box, int argb, double thickness) {
        submit(new Box(box, argb, argb, false, false, thickness));
    }

    /** An outlined box fading from {@code argbA} at the bottom to {@code argbB} at the top. */
    public static void outlineFade(AABB box, int argbA, int argbB, double thickness) {
        submit(new Box(box, argbA, argbB, true, false, thickness));
    }

    /** A filled box in one colour. */
    public static void fill(AABB box, int argb) {
        submit(new Box(box, argb, argb, false, true, 0));
    }

    /** A filled box fading up its height. */
    public static void fillFade(AABB box, int argbA, int argbB) {
        submit(new Box(box, argbA, argbB, true, true, 0));
    }

    private static void submit(Box box) {
        init();
        synchronized (BUILDING) {
            BUILDING.add(box);
        }
    }

    /**
     * Asks for an entity's own model to be outlined this tick.
     *
     * <p>Submit it every tick it should stay outlined, like everything else here; {@link #flip()}
     * drops the ones that stopped being asked for.
     */
    public static void outlineModel(Entity entity, int argb) {
        init();
        synchronized (OUTLINED) {
            WANTED.add(entity.getId());
            if (!OUTLINED.containsKey(entity.getId())) {
                OUTLINED.put(entity.getId(), RenderLibWorld.outlines().track(entity,
                        new com.render.api.EntityOutlineStyle().color(RenderColor.argb(argb))));
            }
        }
    }

    /** Promotes this tick's boxes, and forgets the outlines nobody asked for. */
    public static void flip() {
        synchronized (BUILDING) {
            RENDER = new ArrayList<>(BUILDING);
            BUILDING.clear();
        }
        synchronized (OUTLINED) {
            OUTLINED.entrySet().removeIf(e -> {
                if (WANTED.contains(e.getKey())) {
                    return false;
                }
                e.getValue().unregister();
                return true;
            });
            WANTED.clear();
        }
    }

    /** Drops everything, e.g. on leaving a world. */
    public static void clear() {
        synchronized (BUILDING) {
            BUILDING.clear();
        }
        RENDER = new ArrayList<>();
        synchronized (OUTLINED) {
            OUTLINED.values().forEach(RenderRegistration::unregister);
            OUTLINED.clear();
            WANTED.clear();
        }
    }

    // --- drawing ------------------------------------------------------------------------------------

    private static void extract(WorldRenderContext ctx) {
        List<Box> boxes = RENDER;
        for (Box b : boxes) {
            WorldMaterial material = material(b);
            if (b.filled()) {
                ctx.box(b.box(), material);
            } else {
                edges(ctx, b, material);
            }
        }
    }

    /** Through walls, and a fade up the box when one was asked for. */
    private static WorldMaterial material(Box b) {
        WorldMaterial.Builder builder = WorldMaterial.builder()
                .depthMode(WorldDepthMode.SEE_THROUGH)
                .doubleSided(true);
        if (b.gradient()) {
            AABB box = b.box();
            builder.paint(WorldPaint.gradient(WorldGradient.linear(
                    new Vec3(box.minX, box.minY, box.minZ),
                    new Vec3(box.minX, box.maxY, box.minZ),
                    new WorldGradient.Stop(0f, RenderColor.argb(b.argbA())),
                    new WorldGradient.Stop(1f, RenderColor.argb(b.argbB())))));
        } else {
            builder.color(RenderColor.argb(b.argbA()));
        }
        return builder.build();
    }

    /** The twelve edges of the box, as lines rather than as twelve thin boxes. */
    private static void edges(WorldRenderContext ctx, Box b, WorldMaterial material) {
        AABB a = b.box();
        WorldStroke stroke = new WorldStroke(material.paint(), (float) b.thickness(),
                WorldWidthMode.WORLD, com.render.api.world.WorldLineJoin.MITER,
                com.render.api.world.WorldLineCap.BUTT, null, 0f);
        double[][] corners = {
                {a.minX, a.minY, a.minZ}, {a.maxX, a.minY, a.minZ},
                {a.maxX, a.minY, a.maxZ}, {a.minX, a.minY, a.maxZ},
                {a.minX, a.maxY, a.minZ}, {a.maxX, a.maxY, a.minZ},
                {a.maxX, a.maxY, a.maxZ}, {a.minX, a.maxY, a.maxZ},
        };
        int[][] pairs = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},     // bottom ring
                {4, 5}, {5, 6}, {6, 7}, {7, 4},     // top ring
                {0, 4}, {1, 5}, {2, 6}, {3, 7},     // uprights
        };
        for (int[] p : pairs) {
            ctx.line(vec(corners[p[0]]), vec(corners[p[1]]), stroke, material);
        }
    }

    private static Vec3 vec(double[] xyz) {
        return new Vec3(xyz[0], xyz[1], xyz[2]);
    }
}
