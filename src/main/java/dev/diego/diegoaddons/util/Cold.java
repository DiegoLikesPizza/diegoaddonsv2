package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How cold the player is, read from Hypixel's own sidebar line.
 *
 * <p>Cold is a countdown to dying: one point every five seconds in a cold zone, faster in water, and
 * at 100 you die instantly and lose the run. That is eight minutes from zero, which is long enough
 * to forget about and short enough to catch you inside one cave.
 *
 * <p><b>Hypixel writes it as a negative number</b> - the sidebar says {@code Cold: -14❄} for fourteen
 * cold - so the sign is dropped here and everything above works in the positive number people
 * actually think in. The line is the same one the Glacite Tunnels have used since 0.20, which is why
 * this is not gated to the Safari: the Icy biome borrowed the mechanic wholesale, so reading the
 * line wherever it appears covers both places for free.
 */
public final class Cold {
    /** {@code Cold: -14❄}, colour codes already stripped by {@link SkyblockLocation}. */
    private static final Pattern LINE = Pattern.compile("Cold:\\s*(?<cold>-?\\d+)");

    /** Death at this much. Hypixel's number, not ours. */
    public static final int LETHAL = 100;

    private static int cold = -1;
    private static long lastSeen;

    private Cold() {
    }

    /**
     * The current cold, or -1 when the sidebar is not showing it.
     *
     * <p>-1 rather than 0 on purpose: "not in a cold place" and "in a cold place at zero cold" want
     * opposite behaviour from a warning, and a zero cannot tell them apart.
     */
    public static int cold() {
        return cold;
    }

    /** Whether the reading is current - i.e. the player is somewhere cold. */
    public static boolean active() {
        return cold >= 0 && System.currentTimeMillis() - lastSeen < 3000;
    }

    /** How many points of cold are left before death, or -1 when not applicable. */
    public static int headroom() {
        return active() ? Math.max(0, LETHAL - cold) : -1;
    }

    /**
     * Roughly how long that is, in seconds, at the base rate.
     *
     * <p>Deliberately labelled "roughly" wherever it is shown: the rate moves with Cold Resistance
     * and doubles in water, neither of which is read here. It is a sense of urgency, not a timer.
     */
    public static int secondsLeft() {
        int left = headroom();
        return left < 0 ? -1 : left * 5;
    }

    /** Reads the sidebar. Called once a tick, before anything that warns off it. */
    public static void tick(Minecraft mc) {
        List<String> lines = SkyblockLocation.sidebarLines(mc);
        for (String line : lines) {
            Matcher m = LINE.matcher(line);
            if (m.find()) {
                cold = Math.abs(Integer.parseInt(m.group("cold")));
                lastSeen = System.currentTimeMillis();
                return;
            }
        }
        // The line going away is the reading going away, not the cold going to zero - stepping out
        // of a cold zone is exactly when a stale number would fire a warning at the wrong moment.
        if (System.currentTimeMillis() - lastSeen > 3000) {
            cold = -1;
        }
    }

    /**
     * A campfire reset or a death, either of which zeroes the count.
     *
     * <p>Watched so the warning re-arms at the moment the danger passes rather than waiting for the
     * sidebar to catch up - the whole point of a warning is that it fires once per approach to the
     * limit, and that needs a definite "this approach is over".
     */
    public static boolean isReset(String plain) {
        String lower = plain.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("reduced your") && lower.contains("cold")
                || lower.contains("froze to death");
    }

    public static void reset() {
        cold = -1;
        lastSeen = 0;
    }
}
