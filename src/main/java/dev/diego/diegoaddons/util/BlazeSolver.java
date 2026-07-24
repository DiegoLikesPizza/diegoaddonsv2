package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Higher/Lower blaze puzzle: highlights the blaze to shoot next.
 *
 * <p>The blazes carry their health in the name plate above them, so the order itself is read from
 * those. Which <i>end</i> of the order to start at is the hard part: the dungeon tab list only ever
 * calls the puzzle "Higher Or Lower", and there is no sign in the room. It is taken from the
 * instruction hologram instead - SkyBlock writes that kind of text as a named armour stand, which is
 * also how the health plates are drawn.
 *
 * <p>Until that text has been seen the solver highlights nothing, rather than guessing an order and
 * being confidently wrong. The manual setting is the escape hatch if the wording ever changes.
 */
public final class BlazeSolver {
    /** "[Lv15] Blaze 1,234/5,678❤" - the health plate above each blaze. */
    private static final Pattern HEALTH = Pattern.compile("Blaze\\s+[\\d,]+/([\\d,]+)");
    /** How far to look for the blazes and the instruction hologram. */
    private static final double RANGE = 40.0;

    private static final int NEXT = 0x8000FF00;
    private static final int SECOND = 0x80FFFF00;
    private static final int REST = 0x30FFFFFF;

    /** True when the room wants the highest health shot first; null until the room says so. */
    private static Boolean highestFirst;

    private BlazeSolver() {
    }

    public static void reset() {
        highestFirst = null;
    }

    /** What the solver currently believes, for the readout in chat. */
    public static Boolean detectedOrder() {
        return highestFirst;
    }

    /** Called every client tick while the solver is on. */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.blaze() || mc.level == null || mc.player == null) {
            return;
        }
        List<Blaze> blazes = new ArrayList<>();
        Boolean order = scan(mc, blazes);
        if (order != null) {
            highestFirst = order;
        }
        if (blazes.size() < 2) {
            return;   // not the puzzle, or it is already finished
        }
        Boolean useHighest = highestFirst != null ? highestFirst : mod.blazeFallbackOrder();
        if (useHighest == null) {
            return;
        }

        blazes.sort(useHighest
                ? Comparator.comparingInt((Blaze b) -> b.health).reversed()
                : Comparator.comparingInt(b -> b.health));

        int shown = mod.blazeShowAll() ? blazes.size() : Math.min(2, blazes.size());
        for (int i = 0; i < shown; i++) {
            int color = i == 0 ? NEXT : (i == 1 ? SECOND : REST);
            WorldRender.filledBox(blazes.get(i).box, color, true);
        }
    }

    private record Blaze(int health, AABB box) {
    }

    /**
     * One pass over the nearby armour stands: they carry both the health plates and the instruction
     * hologram, so both come out of the same scan.
     *
     * @return the detected order, or null when the instruction text was not among them
     */
    private static Boolean scan(Minecraft mc, List<Blaze> out) {
        Boolean order = null;
        AABB area = mc.player.getBoundingBox().inflate(RANGE);
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (!(e instanceof ArmorStand stand) || !stand.hasCustomName()) {
                continue;
            }
            String name = LegacyText.strip(stand.getCustomName().getString());

            Matcher m = HEALTH.matcher(name);
            if (m.find()) {
                try {
                    int max = Integer.parseInt(m.group(1).replace(",", ""));
                    // The plate floats above the blaze; drop it back onto the body.
                    out.add(new Blaze(max, new AABB(
                            stand.getX() - 0.5, stand.getY() - 2.2, stand.getZ() - 0.5,
                            stand.getX() + 0.5, stand.getY() - 0.4, stand.getZ() + 0.5)));
                } catch (NumberFormatException ignored) {
                    // A plate that does not parse is simply not a blaze we can order.
                }
                continue;
            }

            Boolean fromText = readOrder(name);
            if (fromText != null) {
                order = fromText;
            }
        }
        return order;
    }

    /**
     * Reads the puzzle's instruction text.
     *
     * <p>When both directions are named - "from HIGHEST to LOWEST" - the one mentioned <b>first</b>
     * is where you start, so the earlier word wins rather than the sentence being discarded as
     * ambiguous.
     */
    static Boolean readOrder(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (!t.contains("blaze") && !t.contains("health") && !t.contains("order")) {
            return null;   // not the instruction, just some other hologram
        }
        int high = firstIndex(t, "highest", "higher");
        int low = firstIndex(t, "lowest", "lower");
        if (high < 0 && low < 0) {
            return null;
        }
        if (high < 0) {
            return Boolean.FALSE;
        }
        if (low < 0) {
            return Boolean.TRUE;
        }
        return high < low;
    }

    /** The earliest position any of the words appears at, or -1. */
    private static int firstIndex(String text, String... words) {
        int best = -1;
        for (String w : words) {
            int i = text.indexOf(w);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }
}
