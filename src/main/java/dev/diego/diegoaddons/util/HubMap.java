package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.HubMapModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Puts your own PNG over the Hub's big map - the wall of framed maps by the bank.
 *
 * <p><b>The wall is found rather than hard-coded.</b> It is built the way anyone builds a big map in
 * Minecraft, out of filled maps in item frames, so what is looked for is a group of map frames
 * facing the same way in the same plane - the largest such group within range wins. Coordinates
 * would have been shorter to write and wrong the day Hypixel moves a wall or adds one somewhere
 * else; this way the same module covers the map in any lobby, and any other map wall besides.
 *
 * <p>The rectangle comes from the frames' own bounding boxes, unioned, so the picture lands exactly
 * where the maps are without anything having to be assumed about how an item frame is positioned
 * against the block it hangs on. It is then hung {@link WorldRender#PICTURE_OFFSET} in front, which
 * covers the maps rather than replacing them - nothing here changes how the game draws a map, and
 * switching the module off puts the wall back.
 */
public final class HubMap {
    /** How often to look for the wall. It is a building; it does not move. */
    private static final long PERIOD_MS = 2000;

    /** One found wall: the flat side facing out, and how many frames it was built from. */
    public record Wall(Direction facing, AABB bounds, int frames) {
    }

    private static Wall wall;
    private static long lastScan;

    private HubMap() {
    }

    /** Forgets the wall, e.g. on leaving a world. */
    public static void clear() {
        wall = null;
        lastScan = 0;
    }

    /** Re-finds the wall when due, then submits the picture over it. */
    public static void tick(HubMapModule module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (module.hubOnly() && !SkyblockLocation.island(mc).toLowerCase(Locale.ROOT).contains("hub")) {
            wall = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScan > PERIOD_MS) {
            lastScan = now;
            wall = find(mc, module);
        }
        if (wall == null) {
            return;
        }
        CustomImages.Image img = CustomImages.get(module.image());
        if (img == null) {
            return;
        }
        WorldRender.image(img.id(), place(wall, img.aspect(), module.fitMode()),
                module.tint(), module.bothSides());
    }

    /**
     * The rectangle a wall's picture goes on, in the mode chosen.
     *
     * <p>Both edge vectors are worked out from the facing rather than listed per direction: the
     * picture's "up" is up, unless the wall is the floor or the ceiling, where up on an image means
     * north the way it does on every map ever drawn. The "right" edge is then what a viewer standing
     * in front of it would call right - {@code forward × up} - which is what keeps text on the image
     * readable instead of mirrored on half the four walls.
     */
    private static ImageQuad place(Wall w, double aspect, int mode) {
        Vec3 n = w.facing().getUnitVec3();
        Vec3 upDir = w.facing().getAxis() == Direction.Axis.Y
                ? new Vec3(0, 0, -1)
                : new Vec3(0, 1, 0);
        Vec3 rightDir = n.scale(-1).cross(upDir);
        AABB b = w.bounds();
        double width = extent(b, rightDir);
        double height = extent(b, upDir);
        double depth = extent(b, n);
        Vec3 face = b.getCenter().add(n.scale(depth / 2));
        Vec3 right = rightDir.scale(width);
        Vec3 up = upDir.scale(height);
        Vec3 origin = face.subtract(right.scale(0.5)).subtract(up.scale(0.5));
        return ImageQuad.fit(origin, right, up, aspect, mode);
    }

    /** How far a box reaches along one of the three axes, picked out by a unit vector. */
    private static double extent(AABB b, Vec3 axis) {
        return Math.abs(axis.x) * b.getXsize()
                + Math.abs(axis.y) * b.getYsize()
                + Math.abs(axis.z) * b.getZsize();
    }

    /**
     * The biggest group of map frames facing one way in one plane, within range.
     *
     * <p>Grouped by facing <i>and</i> by the coordinate of the plane they sit in, so a map wall does
     * not merge with the maps on the wall behind it, and the largest group is taken because the Hub
     * has single framed maps dotted around it that are not the one being asked for.
     */
    private static Wall find(Minecraft mc, HubMapModule module) {
        double r = module.radius();
        Vec3 me = mc.player.position();
        Map<String, List<ItemFrame>> groups = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemFrame frame) || frame.distanceToSqr(me) > r * r) {
                continue;
            }
            if (!module.anyItem() && !isMap(frame.getItem())) {
                continue;
            }
            Direction d = frame.getDirection();
            // The plane the frame sits in, to the block, so two walls one behind the other stay two.
            int plane = switch (d.getAxis()) {
                case X -> frame.getBlockX();
                case Y -> frame.getBlockY();
                case Z -> frame.getBlockZ();
            };
            groups.computeIfAbsent(d.name() + "@" + plane, k -> new ArrayList<>()).add(frame);
        }
        List<ItemFrame> best = null;
        for (List<ItemFrame> g : groups.values()) {
            if (best == null || g.size() > best.size()) {
                best = g;
            }
        }
        if (best == null || best.size() < module.minFrames()) {
            return null;
        }
        AABB bounds = null;
        for (ItemFrame f : best) {
            bounds = bounds == null ? f.getBoundingBox() : bounds.minmax(f.getBoundingBox());
        }
        return new Wall(best.getFirst().getDirection(), bounds, best.size());
    }

    private static boolean isMap(ItemStack stack) {
        return stack != null && stack.is(Items.FILLED_MAP);
    }

    /**
     * Says what is actually out there, in chat and in the log.
     *
     * <p>The whole module rests on one assumption - that the Hub's map is maps in item frames - and
     * this is the measurement that settles it rather than a second guess. If it counts frames but no
     * maps, "Any framed item" is the switch to try; if it counts no frames at all, the wall is built
     * from something else entirely and that is worth knowing before anything more is written.
     */
    public static void debug(HubMapModule module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        double r = module.radius();
        Vec3 me = mc.player.position();
        int frames = 0;
        int maps = 0;
        Map<String, Integer> planes = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemFrame frame) || frame.distanceToSqr(me) > r * r) {
                continue;
            }
            frames++;
            if (isMap(frame.getItem())) {
                maps++;
            }
            planes.merge(frame.getDirection().name(), 1, Integer::sum);
        }
        String summary = "item frames within " + (int) r + ": " + frames + " (" + maps + " holding a map)";
        DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] Hub Map - {}; by facing {}", summary, planes);
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§b[DiegoAddons] §f" + summary
                    + (wall == null ? " §7- no wall found" : " §7- wall of " + wall.frames() + " frames")));
        }
    }
}
