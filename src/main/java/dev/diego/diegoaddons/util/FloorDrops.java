package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.FloorDropsEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds Floor Drops by looking for the block that marks them.
 *
 * <p>Unlike everything else in the ESP family this reads <b>blocks</b>, not entities, and that is
 * the whole of its cost: a block scan asks the level what is at every position in a cube, and a
 * 24-block radius is about eighty thousand of those. So the scan does not run per tick - it runs on
 * a timer, and again immediately whenever the player has moved far enough that the last answer is
 * about somewhere else. What it found is redrawn every tick from the cached list, which is what
 * keeps the boxes solid between scans.
 *
 * <p>The failure mode worth naming: a Floor Drop that is picked up stays boxed until the next scan,
 * up to {@link #PERIOD_MS} later. That is the right way round - a ghost box for a second is a
 * shrug, and a box that flickers because the scan is chasing the player is unusable.
 */
public final class FloorDrops {
    /** How often to re-scan while standing still. */
    private static final long PERIOD_MS = 2000;
    /** Re-scan at once once the player has moved this far from where the last scan was centred. */
    private static final double MOVED = 8.0;

    /** The three islands Floor Drops exist on, as the tab list names them. */
    private static final String[] ISLANDS = {"Moonglade Marsh", "Galatea", "Torrhus Canyon", "Critter Safari"};

    private static final List<BlockPos> found = new ArrayList<>();
    private static long lastScan;
    private static Vec3 lastCentre;
    /** The id last resolved, and what it resolved to, so a text setting is not parsed per scan. */
    private static String cachedId;
    private static Block cachedBlock;
    private static boolean warned;

    /**
     * The Critter Safari's known Floor Drop spots, x/y/z flattened.
     *
     * <p>Community-collected and published on the wiki (113 of them), and they are <b>preset spots,
     * not drops</b> - the wiki is explicit that a drop has "a chance to spawn in preset locations",
     * so a marker here means "something can be here", never "something is here". That is why these
     * are drawn as a second, quieter layer rather than replacing the block scan: the scan says which
     * of them are live right now, and the list says where to walk when none of them are in sight.
     *
     * <p>Safari only. The Marsh and the Canyon have their own spots which nobody has written down.
     */
    private static final int[] SAFARI_SPOTS = {
            -107, 56, 66, -122, 53, 77, -131, 53, 82, -75, 67, 81, -76, 65, 89,
            -88, 58, 58, -135, 56, 38, -141, 50, 21, -117, 42, 18, -70, 39, 57,
            -35, 66, 60, -23, 65, 52, -5, 65, 62, 13, 66, 64, 27, 57, 37,
            2, 64, 30, 20, 66, 45, -23, 81, 53, -20, 84, 62, -79, 63, 31,
            -65, 85, 82, -120, 59, 47, -128, 59, 56, -123, 42, 31, -126, 39, 53,
            -112, 38, 80, -79, 38, 65, 6, 65, -11, 5, 65, -23, 27, 67, -16,
            40, 50, -29, 32, 48, -18, 22, 50, -27, 52, 53, -23, 11, 68, -44,
            1, 68, -36, -6, 68, -40, -26, 65, -28, -39, 65, -40, -31, 68, -73,
            -8, 68, -93, 22, 69, -88, 29, 68, -58, -77, 65, -30, -106, 59, 37,
            -94, 39, 22, -78, 39, 29, -90, 44, 88, -101, 71, -39, -127, 80, 34,
            -99, 77, -61, -116, 79, -62, -122, 80, -91, -119, 78, -106, -98, 76, -105,
            -77, 75, -96, -76, 73, -75, -60, 72, -79, -56, 72, -78, -98, 99, -68,
            -123, 93, -48, -105, 79, -43, -119, 79, -34, -18, 65, -21, -7, 65, -16,
            20, 67, -23, 38, 67, -22, 7, 47, -25, 24, 68, -42, 35, 69, -46,
            22, 69, -74, 4, 68, -100, -26, 69, -90, 26, 66, 55, -26, 79, 40,
            -81, 85, 87, -85, 66, 80, -97, 78, -82, -119, 93, -60, -112, 88, -22,
            -104, 87, -9, -100, 81, 60, -115, 80, 22, -32, 65, -53, 9, 68, -94,
            49, 67, -22, -24, 65, 30, 8, 66, 11, 21, 50, 55, 22, 66, 53,
            -1, 74, 47, -77, 71, -58, 7, 49, 27, -19, 64, 34, 2, 75, 55,
            -29, 88, 69, 112, 80, 36, 111, 80, 47, -98, 94, 22, -95, 38, 73,
            8, 66, 65, 7, 48, 60, 0, 65, 24, -69, 85, 78, -90, 70, -42,
            -4, 76, 11, -9, 64, 27, 25, 55, 43, -92, 71, 42, 15, 49, 23,
            4, 46, 47, -7, 65, 68, 22, 55, 47, 11, 88, 10, 14, 85, 51,
    };

    private FloorDrops() {
    }

    /** How many preset spots are known. */
    public static int presetCount() {
        return SAFARI_SPOTS.length / 3;
    }

    /** Whether the player is somewhere Floor Drops can be. */
    public static boolean onAFloorDropIsland(Minecraft mc) {
        String island = SkyblockLocation.island(mc).toLowerCase(Locale.ROOT);
        String area = SkyblockLocation.area(mc).toLowerCase(Locale.ROOT);
        for (String s : ISLANDS) {
            String needle = s.toLowerCase(Locale.ROOT);
            if (island.contains(needle) || area.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Re-scans when it is due, then draws whatever is known. Called from the module's tick. */
    public static void tick(Minecraft mc, FloorDropsEspModule module) {
        if (mc.level == null || mc.player == null || !module.here(mc)) {
            found.clear();
            lastCentre = null;
            return;
        }
        Vec3 me = mc.player.position();
        long now = System.currentTimeMillis();
        boolean moved = lastCentre == null || lastCentre.distanceToSqr(me) > MOVED * MOVED;
        if (moved || now - lastScan > PERIOD_MS) {
            scan(mc, module, me);
            lastScan = now;
            lastCentre = me;
        }
        draw(mc, module);
        if (module.presets() && Safari.onSafari(mc)) {
            drawPresets(mc, module, me);
        }
    }

    /**
     * The known spots, drawn under the live ones.
     *
     * <p>Quieter than a found drop on purpose - an outline at half the box rather than a filled
     * marker - because these two things mean different things and a player glancing at the screen
     * has to be able to tell "there is one there" from "there can be one there". A spot the scan has
     * already found is skipped entirely, so the two layers never draw on top of each other.
     */
    private static void drawPresets(Minecraft mc, FloorDropsEspModule module, Vec3 me) {
        double range = module.presetRange();
        int argb = dim(module.color());
        for (int i = 0; i < SAFARI_SPOTS.length; i += 3) {
            int x = SAFARI_SPOTS[i];
            int y = SAFARI_SPOTS[i + 1];
            int z = SAFARI_SPOTS[i + 2];
            if (me.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) > range * range) {
                continue;
            }
            boolean live = false;
            for (BlockPos p : found) {
                // Within a block or two: the wiki's coordinates are somebody's F3 readout, and a
                // drop sitting on the block next to the one written down is the same drop.
                if (Math.abs(p.getX() - x) <= 2 && Math.abs(p.getZ() - z) <= 2
                        && Math.abs(p.getY() - y) <= 2) {
                    live = true;
                    break;
                }
            }
            if (live) {
                continue;
            }
            WorldRender.box(new AABB(x + 0.25, y, z + 0.25, x + 0.75, y + 0.25, z + 0.75),
                    argb, true);
        }
    }

    /** Half alpha: a possible spot should never read as loudly as a real one. */
    private static int dim(int argb) {
        int alpha = (argb >>> 24) / 3;
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Walks the cube around the player once.
     *
     * <p>One mutable position rather than eighty thousand {@link BlockPos} objects, and the block
     * compared by identity against the resolved one rather than by looking up its id per position -
     * both of those are the difference between a scan you do not notice and a stutter every two
     * seconds.
     */
    private static void scan(Minecraft mc, FloorDropsEspModule module, Vec3 me) {
        found.clear();
        Block target = resolve(module.blockId());
        if (target == null) {
            return;
        }
        int r = module.radius();
        int h = module.height();
        int cx = (int) Math.floor(me.x);
        int cy = (int) Math.floor(me.y);
        int cz = (int) Math.floor(me.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = cy - h; y <= cy + h; y++) {
                    pos.set(x, y, z);
                    // Unloaded chunks answer "air", so nothing has to be asked about loading here.
                    if (mc.level.getBlockState(pos).is(target)) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
    }

    /** Draws the cached positions. Runs every tick, so it does no work beyond queueing boxes. */
    private static void draw(Minecraft mc, FloorDropsEspModule module) {
        for (BlockPos p : found) {
            // A tripwire is a few pixels tall, and a box that height is invisible at any distance -
            // which is the problem this module exists to solve. So the box is the full block.
            AABB box = new AABB(p.getX(), p.getY(), p.getZ(),
                    p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            EspRender.draw(null, box, module);
            if (module.beam()) {
                WorldRender.path(List.of(
                                new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5),
                                new Vec3(p.getX() + 0.5, p.getY() + 12, p.getZ() + 0.5)),
                        module.color(), 0.15);
            }
        }
    }

    /**
     * The block a typed id names, or null.
     *
     * <p>An unknown id is reported once and then left alone: it is a typo in a text box, not a
     * failure worth a line a tick, and the module simply finds nothing until it is corrected.
     */
    private static Block resolve(String id) {
        if (id.equals(cachedId)) {
            return cachedBlock;
        }
        cachedId = id;
        warned = false;
        Identifier key = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
        Block block = key == null ? null : BuiltInRegistries.BLOCK.getValue(key);
        // getValue answers air for anything it does not know, which is not an answer worth scanning
        // eighty thousand positions for.
        cachedBlock = block == null || block == Blocks.AIR ? null : block;
        if (cachedBlock == null && !warned) {
            warned = true;
            DiegoAddonsV2Client.LOGGER.warn(
                    "[floor drops] '{}' is not a block id; nothing will be found until it is fixed", id);
        }
        return cachedBlock;
    }

    /** Drops everything, e.g. on leaving a world. */
    public static void clear() {
        found.clear();
        lastCentre = null;
    }
}
