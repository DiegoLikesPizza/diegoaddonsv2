package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared state for the Crystal Hollows features: whether you are there, which sub-area you are in,
 * the coordinate box the map is drawn from, and a store of the special locations found this visit.
 *
 * <p>The Hollows are regenerated every time you enter, so everything here is <b>per-visit</b> and
 * cleared on leaving - a waypoint from a previous run would point at nothing. Locations are found by
 * watching the scoreboard's area line: stepping into a named structure records where its entrance is.
 * The four upper quadrants are colour-learned the same way, so the map labels a region only once you
 * have actually been in it, instead of guessing a layout that can differ between runs.
 */
public final class CrystalHollows {
    /** The Hollows span this block range on both X and Z; the map is drawn from this square. */
    public static final int MIN = 202;
    public static final int MAX = 823;
    public static final int SIZE = MAX - MIN;
    /** The centre the four quadrants divide around. */
    public static final int MID = 512;

    public enum Type {
        STRUCTURE(0xFFFFD23F),
        NUCLEUS(0xFFE0E0FF),
        GROTTO(0xFFFF6FD0),
        CHEST(0xFFFFC24B),
        CRYSTAL(0xFF9BE86B);

        public final int color;

        Type(int color) {
            this.color = color;
        }
    }

    public record Waypoint(String name, Type type, Vec3 pos, long foundMs) {
    }

    /** Sub-areas that are point structures worth a waypoint (not the broad mining regions). */
    private static final Set<String> STRUCTURES = Set.of(
            "Jungle Temple", "Mines of Divan", "Goblin Queen's Den",
            "Lost Precursor City", "Khazad-dûm", "Khazad-dum");
    private static final String NUCLEUS = "Crystal Nucleus";
    private static final String GROTTO_AREA = "Fairy Grotto";

    /** The five mining regions and the colour the map paints their quadrant. */
    private static final Map<String, Integer> REGION_COLORS = Map.of(
            "Jungle", 0xFF3FA34D,
            "Mithril Deposits", 0xFF5FD0D0,
            "Goblin Holdout", 0xFFD0A020,
            "Precursor Remnants", 0xFF6C86D6,
            "Magma Fields", 0xFFB03A30);

    private static final Map<String, Waypoint> WAYPOINTS = new LinkedHashMap<>();
    /** Quadrant index (0..3) -> learned region name; and its colour. */
    private static final Map<Integer, String> QUADRANT_REGION = new LinkedHashMap<>();

    private static boolean inHollows;
    private static String area = "";
    private static boolean areaChanged;
    private static long nowMs;

    private CrystalHollows() {
    }

    public static boolean inHollows() {
        return inHollows;
    }

    public static String area() {
        return area;
    }

    public static boolean areaChanged() {
        return areaChanged;
    }

    public static Collection<Waypoint> waypoints() {
        return WAYPOINTS.values();
    }

    public static String quadrantRegion(int quadrant) {
        return QUADRANT_REGION.get(quadrant);
    }

    public static int regionColor(String region) {
        return REGION_COLORS.getOrDefault(region, 0);
    }

    /** True if an area name only occurs inside the Crystal Hollows (a region or a structure). */
    private static boolean isHollowsArea(String a) {
        return !a.isEmpty() && (REGION_COLORS.containsKey(a) || STRUCTURES.contains(a)
                || a.equals(NUCLEUS) || a.equals(GROTTO_AREA));
    }

    /** Which quadrant a world X/Z falls in: 0=NW, 1=NE, 2=SW, 3=SE (screen-style, +Z south). */
    public static int quadrant(double x, double z) {
        int col = x < MID ? 0 : 1;
        int row = z < MID ? 0 : 1;
        return row * 2 + col;
    }

    public static void addWaypoint(String id, String name, Type type, Vec3 pos) {
        WAYPOINTS.putIfAbsent(id, new Waypoint(name, type, pos, nowMs));
    }

    public static void removeWaypoint(String id) {
        WAYPOINTS.remove(id);
    }

    public static void reset() {
        inHollows = false;
        area = "";
        areaChanged = false;
        WAYPOINTS.clear();
        QUADRANT_REGION.clear();
    }

    /** Called every client tick (centrally). Updates location, the area transition, and regions. */
    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            inHollows = false;
            return;
        }
        nowMs = System.currentTimeMillis();
        String now = SkyblockLocation.area(mc);
        boolean was = inHollows;
        // Detect the Hollows generously: the island line, the scoreboard text, or simply standing in
        // any area that only exists inside the Hollows - so a HUD that keys off this always shows up.
        inHollows = SkyblockLocation.island(mc).equalsIgnoreCase("Crystal Hollows")
                || SkyblockLocation.sidebarLines(mc).stream().anyMatch(l -> l.contains("Crystal Hollows"))
                || isHollowsArea(now);
        if (!inHollows) {
            if (was) {
                reset();   // left the Hollows: the next visit is a fresh layout
            }
            return;
        }
        areaChanged = !now.equals(area);
        area = now;

        // Learn the quadrant's region from the broad area names as you move through them.
        if (REGION_COLORS.containsKey(area)) {
            QUADRANT_REGION.putIfAbsent(quadrant(mc.player.getX(), mc.player.getZ()), area);
        }
    }

    /**
     * Records a waypoint for the structure the player just stepped into, if any. Called by whichever
     * finder module is enabled; it acts only on the tick the area changed, so it fires once per entry
     * and both finders can call it in the same tick without clobbering each other.
     *
     * @param grotto true to look for Fairy Grottos, false for the big named structures + nucleus
     */
    public static void detect(Minecraft mc, boolean grotto) {
        if (!inHollows || !areaChanged || mc.player == null) {
            return;
        }
        Vec3 pos = mc.player.position();
        if (grotto) {
            if (area.equals(GROTTO_AREA)) {
                addWaypoint("grotto@" + key(pos), "Fairy Grotto", Type.GROTTO, pos);
            }
        } else if (area.equals(NUCLEUS)) {
            addWaypoint("nucleus", NUCLEUS, Type.NUCLEUS, pos);
        } else if (STRUCTURES.contains(area)) {
            addWaypoint("struct:" + area, pretty(area), Type.STRUCTURE, pos);
        }
    }

    private static String key(Vec3 p) {
        return (int) p.x + "_" + (int) p.z;
    }

    /** Draws a waypoint in the world: a box at its foot, a beam up from it, and a label with range. */
    public static void drawBeam(Minecraft mc, Vec3 pos, int color, String label) {
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);
        WorldRender.thickBox(new net.minecraft.world.phys.AABB(bx, by, bz, bx + 1, by + 1, bz + 1),
                color, 0.05, true);
        WorldRender.path(java.util.List.of(
                new Vec3(bx + 0.5, by, bz + 0.5), new Vec3(bx + 0.5, by + 24, bz + 0.5)), color, 0.25);
        int dist = mc.player != null ? (int) mc.player.position().distanceTo(pos) : 0;
        WorldRender.text(label + " §7(" + dist + "m)", new Vec3(bx + 0.5, by + 1.6, bz + 0.5), 1.0f);
    }

    /** X to a 0..1 fraction across the map square. */
    public static double fracX(double worldX) {
        return clamp01((worldX - MIN) / (double) SIZE);
    }

    public static double fracZ(double worldZ) {
        return clamp01((worldZ - MIN) / (double) SIZE);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Normalises the odd Khazad-dûm spelling for display. */
    public static String pretty(String name) {
        return name.toLowerCase(Locale.ROOT).startsWith("khazad") ? "Khazad-dûm" : name;
    }
}
