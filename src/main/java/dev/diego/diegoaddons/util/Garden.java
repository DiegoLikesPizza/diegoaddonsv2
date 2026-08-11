package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Garden itself: where its plots are, what has been sprayed on them, how the pest traps are
 * doing, and what Jacob is running. {@link Pests} owns the pests; everything else the Garden says
 * about itself is here.
 *
 * <p><b>The plot grid is fixed geometry, not something to be discovered.</b> The Garden is 5×5 plots
 * of 96 blocks each, spanning -240 to 240 on both axes with the Barn in the middle, and the ids are
 * dealt out in rings from that centre. That never changes, so a plot can be named from a position
 * with arithmetic rather than by asking the server - which is what lets a border be drawn for a plot
 * you have never walked into.
 *
 * <p>Like the pest cooldown, the rest is <b>read from the tab list rather than worked out</b> (see
 * {@link Pests} for why), with one exception: a spray's expiry. The widget only prints the spray on
 * the plot you are standing on, so every other plot's timer is kept from the moment its spray was
 * announced in chat, and corrected by the widget whenever you walk back onto it.
 */
public final class Garden {
    /** One plot's side, in blocks. */
    private static final int PLOT_SIZE = 96;
    /** Where the grid starts and ends on both axes. */
    private static final int GRID_MIN = -240;
    private static final int GRID_MAX = 240;

    /**
     * How long a spray lasts, by the Sprayonator that laid it down: 30 minutes for the plain one,
     * 45 for the Juicy, 60 for the Salty.
     *
     * <p><b>The chat line does not say which one was used</b> - "You sprayed Plot - 6 with Compost"
     * names the spray, not the sprayer - so the duration is taken from what is in your hand at the
     * moment the message arrives, which is necessarily the Sprayonator you just fired. When that
     * cannot be read the plain 30 minutes is assumed, because it is the one that under-counts: a
     * timer that expires early sends you back to a plot that is still sprayed, while one that
     * expires late tells you a plot is covered when it is not.
     */
    private static final long PLAIN_MS = 30 * 60 * 1000L;
    private static final long JUICY_MS = 45 * 60 * 1000L;
    private static final long SALTY_MS = 60 * 60 * 1000L;

    /**
     * Plot ids by [z][x]. The Barn is 0 in the middle and the rest spiral outwards, which is
     * Hypixel's own numbering - the plot menu is laid out in exactly this shape.
     */
    private static final int[][] PLOT_MAP = {
            {21, 13, 9, 14, 22},
            {15, 5, 1, 6, 16},
            {10, 2, 0, 3, 11},
            {17, 7, 4, 8, 18},
            {23, 19, 12, 20, 24},
    };

    private Garden() {
    }

    // --- geometry ----------------------------------------------------------------------------------

    /** The plot id at these world coordinates, or -1 outside the grid. 0 is the Barn. */
    public static int plotAt(double x, double z) {
        int ix = index(x);
        int iz = index(z);
        return ix < 0 || iz < 0 ? -1 : PLOT_MAP[iz][ix];
    }

    private static int index(double v) {
        if (v < GRID_MIN || v > GRID_MAX) {
            return -1;
        }
        int i = (int) Math.floor((v - GRID_MIN) / PLOT_SIZE);
        return Math.max(0, Math.min(4, i));
    }

    /** The plot the player is standing in, or -1. */
    public static int currentPlot(Minecraft mc) {
        return mc.player == null ? -1 : plotAt(mc.player.getX(), mc.player.getZ());
    }

    /** The middle of a plot at ground height, or null for an id that is not on the grid. */
    public static Vec3 plotMiddle(int id) {
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                if (PLOT_MAP[z][x] == id) {
                    return new Vec3((x - 2) * PLOT_SIZE, 70, (z - 2) * PLOT_SIZE);
                }
            }
        }
        return null;
    }

    /**
     * A plot's footprint as a box between two heights.
     *
     * <p>Heights are the caller's because a plot is 256 blocks tall and drawing that is a wall, not a
     * border: the current plot wants a band at eye level, a plot across the Garden wants something
     * tall enough to see over the crops.
     */
    public static AABB plotBox(int id, double minY, double maxY) {
        Vec3 mid = plotMiddle(id);
        if (mid == null) {
            return null;
        }
        double half = PLOT_SIZE / 2.0;
        return new AABB(mid.x - half, minY, mid.z - half, mid.x + half, maxY, mid.z + half);
    }

    /** "The Barn" for 0, "Plot 4" otherwise. */
    public static String plotName(int id) {
        return id == 0 ? "The Barn" : "Plot " + id;
    }

    // --- the reading -------------------------------------------------------------------------------

    /** Spray on one plot: what it is and when it runs out. */
    public record Spray(String type, long expiry) {
        public long msLeft() {
            return Math.max(0, expiry - System.currentTimeMillis());
        }
    }

    private static final Map<Integer, Spray> SPRAYS = new HashMap<>();

    private static int trapsPlaced = -1;
    private static int trapsMax = -1;
    private static final Set<Integer> FULL_TRAPS = new LinkedHashSet<>();
    private static final Set<Integer> NO_BAIT_TRAPS = new LinkedHashSet<>();
    private static List<String> contest = List.of();

    /** Every plot with a live spray, soonest to expire first. */
    public static List<Map.Entry<Integer, Spray>> sprays() {
        List<Map.Entry<Integer, Spray>> out = new ArrayList<>(SPRAYS.entrySet());
        out.sort((a, b) -> Long.compare(a.getValue().expiry(), b.getValue().expiry()));
        return out;
    }

    public static Spray spray(int plot) {
        return SPRAYS.get(plot);
    }

    /** How many traps are placed, or -1 when the Pest Traps widget is not on. */
    public static int trapsPlaced() {
        return trapsPlaced;
    }

    public static int trapsMax() {
        return trapsMax;
    }

    /** The traps that are full, by their number. Empty when there are none. */
    public static Set<Integer> fullTraps() {
        return FULL_TRAPS;
    }

    /** The traps that have run out of bait, by their number. */
    public static Set<Integer> noBaitTraps() {
        return NO_BAIT_TRAPS;
    }

    /**
     * Jacob's Contest as the tab list writes it - the header line and the crops under it.
     *
     * <p>Passed through rather than re-worded on purpose. The widget already says the one thing worth
     * knowing ("Jacob's Contest: 19m left" and which three crops), and a mod that re-words it can
     * only ever be wrong in ways the original is not. What SkyHanni adds on top of this comes from an
     * external contest API, which is a different feature and a different dependency.
     */
    public static List<String> contest() {
        return contest;
    }

    /** The infested plot nearest the player, or -1 when none is known. */
    public static int nearestInfested(Minecraft mc) {
        if (mc.player == null) {
            return -1;
        }
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int id : infestedPlots()) {
            Vec3 mid = plotMiddle(id);
            if (mid == null) {
                continue;
            }
            double dx = mid.x - mc.player.getX();
            double dz = mid.z - mc.player.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = id;
            }
        }
        return best;
    }

    /** The plot ids the pest widget lists as infested. */
    public static List<Integer> infestedPlots() {
        List<Integer> out = new ArrayList<>();
        String list = Pests.plots();
        if (list.isEmpty()) {
            return out;
        }
        for (String part : list.split(",")) {
            String s = part.trim();
            if (s.matches("\\d+")) {
                out.add(Integer.parseInt(s));
            }
        }
        return out;
    }

    // --- reading it --------------------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        if (!Pests.inGarden()) {
            if (!SPRAYS.isEmpty() || trapsPlaced >= 0) {
                reset();
            }
            // The contest widget is not the Garden's - it shows anywhere in SkyBlock - so it is read
            // wherever we are rather than dropped at the gate.
            readContest(mc);
            return;
        }
        readTab(mc);
        expire();
    }

    private static void reset() {
        SPRAYS.clear();
        trapsPlaced = -1;
        trapsMax = -1;
        FULL_TRAPS.clear();
        NO_BAIT_TRAPS.clear();
    }

    /** Drops sprays that have run out, so nothing has to check an expiry twice. */
    private static void expire() {
        SPRAYS.entrySet().removeIf(e -> e.getValue().msLeft() <= 0);
    }

    private static void readTab(Minecraft mc) {
        boolean seenTraps = false;
        boolean seenFull = false;
        boolean seenNoBait = false;
        for (String line : SkyblockLocation.tabLines(mc)) {
            if (line.startsWith("Pest Traps:")) {
                seenTraps = true;
                readTraps(line.substring("Pest Traps:".length()));
            } else if (line.startsWith("Full Traps:")) {
                seenFull = true;
                readTrapList(line.substring("Full Traps:".length()), FULL_TRAPS);
            } else if (line.startsWith("No Bait:")) {
                seenNoBait = true;
                readTrapList(line.substring("No Bait:".length()), NO_BAIT_TRAPS);
            } else if (line.startsWith("Spray:")) {
                readSpray(mc, line.substring("Spray:".length()).trim());
            }
        }
        if (!seenTraps) {
            trapsPlaced = -1;
            trapsMax = -1;
        }
        if (!seenFull) {
            FULL_TRAPS.clear();
        }
        if (!seenNoBait) {
            NO_BAIT_TRAPS.clear();
        }
        readContest(mc);
    }

    private static void readTraps(String value) {
        Matcher m = TRAP_COUNT.matcher(value);
        if (m.find()) {
            trapsPlaced = Integer.parseInt(m.group(1));
            trapsMax = Integer.parseInt(m.group(2));
        }
    }

    private static final Pattern TRAP_COUNT = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    /** "#1, #2, #3" or "None". */
    private static void readTrapList(String value, Set<Integer> into) {
        into.clear();
        Matcher m = TRAP_NUMBER.matcher(value);
        while (m.find()) {
            into.add(Integer.parseInt(m.group(1)));
        }
    }

    private static final Pattern TRAP_NUMBER = Pattern.compile("#(\\d+)");

    /**
     * The spray line, which is about the plot you are standing on and no other. "None" clears it -
     * the widget saying there is no spray here is a reading, not a gap.
     */
    private static void readSpray(Minecraft mc, String value) {
        int plot = currentPlot(mc);
        if (plot < 0) {
            return;
        }
        Matcher m = SPRAY_LINE.matcher(value);
        if (!m.matches()) {
            return;
        }
        String type = m.group("spray").trim();
        String time = m.group("time");
        if (time == null || type.equalsIgnoreCase("None")) {
            SPRAYS.remove(plot);
            return;
        }
        long secs = duration(time);
        if (secs < 0) {
            return;
        }
        long end = System.currentTimeMillis() + secs * 1000L;
        Spray held = SPRAYS.get(plot);
        // Same tolerance as the pest cooldown, and for the same reason: the widget prints whole
        // seconds, so believing it every tick would make a countdown jitter by a second either way.
        if (held == null || !held.type().equalsIgnoreCase(type) || Math.abs(end - held.expiry()) > 1500) {
            SPRAYS.put(plot, new Spray(type, end));
        }
    }

    private static final Pattern SPRAY_LINE =
            Pattern.compile("(?<spray>[\\w\\s]+?)\\s*(?:\\((?<time>[^)]*)\\))?");

    /** "12m", "1m 3s", "53s". -1 when it is none of those. */
    private static long duration(String text) {
        Matcher m = TIME.matcher(text.trim());
        if (!m.matches()) {
            return -1;
        }
        long total = 0;
        boolean any = false;
        if (m.group("h") != null) {
            total += Long.parseLong(m.group("h")) * 3600;
            any = true;
        }
        if (m.group("m") != null) {
            total += Long.parseLong(m.group("m")) * 60;
            any = true;
        }
        if (m.group("s") != null) {
            total += Long.parseLong(m.group("s"));
            any = true;
        }
        return any ? total : -1;
    }

    private static final Pattern TIME = Pattern.compile(
            "(?:(?<h>\\d+)h)?\\s*(?:(?<m>\\d+)m)?\\s*(?:(?<s>\\d+)s)?");

    /**
     * The contest widget's own lines: its header, then the crop lines under it.
     *
     * <p>The tab list arrives here as one flat list with the blank separators already dropped, so
     * "which lines belong to this widget" cannot be answered by position. The crops answer it
     * instead: a line under the header that names one of the ten contest crops is part of it, and
     * the first line that does not ends it.
     */
    private static void readContest(Minecraft mc) {
        List<String> lines = SkyblockLocation.tabLines(mc);
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.startsWith("Jacob's Contest")) {
                inside = true;
                out.add(line);
                continue;
            }
            if (inside) {
                if (isContestCrop(line)) {
                    out.add(line);
                } else {
                    break;
                }
            }
        }
        contest = out;
    }

    /** The ten crops a contest can be for, with or without the marker Hypixel prefixes them with. */
    private static final Set<String> CONTEST_CROPS = Set.of(
            "wheat", "carrot", "potato", "pumpkin", "melon", "mushroom",
            "cactus", "sugar cane", "nether wart", "cocoa beans");

    private static boolean isContestCrop(String line) {
        String s = line.replaceAll("^[^\\p{L}]+", "").trim().toLowerCase(Locale.ROOT);
        return CONTEST_CROPS.contains(s);
    }

    // --- chat --------------------------------------------------------------------------------------

    /**
     * "SPRAYONATOR! You sprayed Plot - 6 with Compost!", and the amount form the Juicy one uses.
     * This is the only announcement a plot other than the one you are standing on ever gets, which
     * is why the timer it starts is kept rather than re-read.
     */
    private static final Pattern SPRAYED = Pattern.compile(
            "^SPRAYONATOR! You sprayed Plot - (?<plot>.+?) with (?:\\d+ )?(?<spray>.+)!$");

    private static final Pattern WASHED = Pattern.compile(
            "^SPLASH! Your Garden was cleared of all active Sprayonator effects!$");

    public static void onMessage(String plain) {
        String s = plain.trim();
        if (WASHED.matcher(s).matches()) {
            SPRAYS.clear();
            return;
        }
        Matcher m = SPRAYED.matcher(s);
        if (!m.matches()) {
            return;
        }
        String plot = m.group("plot").trim();
        // A renamed plot cannot be tied back to the grid, so its spray is not filed at all rather
        // than filed under a guess - a timer on the wrong plot is worse than no timer.
        if (!plot.matches("\\d+")) {
            return;
        }
        SPRAYS.put(Integer.parseInt(plot),
                new Spray(m.group("spray").trim(), System.currentTimeMillis() + sprayDuration()));
    }

    /** The duration of the spray just fired, from the Sprayonator still in hand. */
    private static long sprayDuration() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return PLAIN_MS;
        }
        String held = mc.player.getMainHandItem().getHoverName().getString().toLowerCase(Locale.ROOT);
        if (!held.contains("sprayonator")) {
            return PLAIN_MS;
        }
        if (held.contains("salty")) {
            return SALTY_MS;
        }
        return held.contains("juicy") ? JUICY_MS : PLAIN_MS;
    }

    /** "12m 3s" / "3s", for anything here that shows a countdown. */
    public static String time(long ms) {
        long total = Math.max(0, ms) / 1000;
        long m = total / 60;
        long s = total % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
}
