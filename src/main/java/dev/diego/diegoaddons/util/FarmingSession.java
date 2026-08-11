package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What this farming session has actually produced: crops, copper, pests, seasoning, and what the
 * crops are worth per hour.
 *
 * <p><b>Measured as deltas of Hypixel's own counters, not counted here.</b> The tab list already
 * carries the crop milestone counter and your copper, and both are authoritative in a way a
 * client-side tally never is - it would miss a crop broken by a Sprayonator, double-count a
 * replenished block, and drift over an hour. Reading a number Hypixel maintains and subtracting the
 * one from a minute ago cannot drift.
 *
 * <p>The counter resets when the milestone does, and copper only ever goes up, so a negative delta
 * means the baseline is no longer the same thing it was: the session re-bases rather than reporting
 * a loss. The rate is over the session's own elapsed time, so standing still lowers it - which is
 * the honest reading of "per hour" and the point of measuring at all.
 */
public final class FarmingSession {
    private FarmingSession() {
    }

    private static long startedAt;
    private static long lastActivity;
    /**
     * Time the session has actually been <b>working</b>, which is what the rates divide by.
     *
     * <p>Wall-clock would mean a lunch break halves your crops an hour and makes two setups
     * incomparable unless you farmed them for the same stretch without pausing. Idle time is simply
     * not counted, so the numbers answer "how good is this while I am doing it".
     */
    private static long activeMs;
    private static long lastTickAt;

    /** Pest kill drops valued at bazaar prices, and the flat coins the kills themselves paid. */
    private static double pestDropValue;
    private static long pestCoins;

    private static long cropStart = -1;
    private static long cropNow = -1;
    private static long copperStart = -1;
    private static long copperNow = -1;
    private static int seasoningStart = -1;
    private static int pests;
    private static String cropName = "";

    /** Whether a session is running. */
    public static boolean active() {
        return startedAt != 0;
    }

    /** Working time, excluding whatever was spent idle. */
    public static long elapsed() {
        return activeMs;
    }

    /** Wall-clock since the session began, for the "how long ago did I start" question. */
    public static long wallClock() {
        return startedAt == 0 ? 0 : System.currentTimeMillis() - startedAt;
    }

    /** Whether the session is currently paused for inactivity. */
    public static boolean paused() {
        return startedAt != 0 && idle() > pauseAfterMs;
    }

    /** How long without activity before the clock stops, and before the session is dropped. */
    private static long pauseAfterMs = 2 * 60_000L;
    private static long endAfterMs = 60 * 60_000L;

    public static void setTimeouts(long pauseMs, long endMs) {
        pauseAfterMs = pauseMs;
        endAfterMs = endMs;
    }

    /** What the pests paid: their drops at bazaar prices plus the coins for the kills. */
    public static double pestProfit() {
        return pestDropValue + pestCoins;
    }

    /** Everything the session earned - the crops plus the pests. */
    public static double profit() {
        double crops = coins();
        return (crops < 0 ? 0 : crops) + pestProfit();
    }

    /** Crops harvested since the session began, or -1 when the counter has not been read. */
    public static long crops() {
        return cropStart < 0 || cropNow < 0 ? -1 : Math.max(0, cropNow - cropStart);
    }

    public static long copper() {
        return copperStart < 0 || copperNow < 0 ? -1 : Math.max(0, copperNow - copperStart);
    }

    public static int seasoning() {
        return seasoningStart < 0 ? -1 : Math.max(0, HarvestFeast.seasoning() - seasoningStart);
    }

    public static int pests() {
        return pests;
    }

    /** The crop the milestone counter is currently for, e.g. "Nether Wart". */
    public static String crop() {
        return cropName;
    }

    /** Per hour, from a total and the session's own length. -1 when the total is unknown. */
    public static double perHour(long total) {
        long ms = elapsed();
        if (total < 0 || ms < 60_000) {
            // Under a minute the rate is mostly noise - a single crop would read as thousands an
            // hour - so it is withheld rather than shown as a number nobody should act on.
            return -1;
        }
        return total / (ms / 3_600_000.0);
    }

    /**
     * What the crops harvested this session are worth at bazaar prices, or -1 when they cannot be
     * priced - which is the case for every crop until the bazaar snapshot has loaded.
     *
     * <p>The raw crop, not what it could be crafted into: this is a measure of the session, not a
     * projection of what you might do with the output.
     */
    public static double coins() {
        long harvested = crops();
        if (harvested < 0 || cropName.isEmpty() || !Bazaar.fresh()) {
            return -1;
        }
        double each = Bazaar.priceOf(cropName);
        return each <= 0 ? -1 : each * harvested;
    }

    // --- running it --------------------------------------------------------------------------------

    /** Starts over. Also called on the first tick in the Garden. */
    public static void reset() {
        startedAt = System.currentTimeMillis();
        lastActivity = startedAt;
        cropStart = -1;
        cropNow = -1;
        copperStart = -1;
        copperNow = -1;
        seasoningStart = HarvestFeast.seasoning();
        pests = 0;
        activeMs = 0;
        lastTickAt = startedAt;
        pestDropValue = 0;
        pestCoins = 0;
        cropName = "";
    }

    public static void tick(Minecraft mc) {
        if (!Pests.inGarden()) {
            return;
        }
        if (startedAt == 0) {
            reset();
        }
        advanceClock();
        for (String line : SkyblockLocation.tabLines(mc)) {
            if (line.startsWith("Copper:")) {
                long value = number(line.substring("Copper:".length()));
                if (copperStart < 0) {
                    copperStart = value;
                } else if (value > copperNow) {
                    lastActivity = System.currentTimeMillis();
                }
                copperNow = value;
            } else {
                Matcher m = COUNTER.matcher(line);
                if (m.matches()) {
                    long value = number(m.group("count"));
                    readCounter(value);
                }
            }
        }
        readCropName(mc);
    }

    /**
     * Moves the working clock on, and drops the session when it has been idle long enough.
     *
     * <p>Ending rather than pausing forever matters: a session left running overnight would come
     * back with an hour of crops against three minutes of working time, and every rate on the card
     * would be nonsense. An hour of nothing is a different session.
     */
    private static void advanceClock() {
        long now = System.currentTimeMillis();
        if (lastTickAt == 0) {
            lastTickAt = now;
            return;
        }
        if (idle() > endAfterMs) {
            reset();
            return;
        }
        if (idle() <= pauseAfterMs) {
            activeMs += now - lastTickAt;
        }
        lastTickAt = now;
    }

    /**
     * The milestone counter, re-based rather than trusted blindly.
     *
     * <p>It resets on a milestone and changes meaning when the crop changes, and either would show
     * up here as the number going backwards. A drop therefore starts the count again from the new
     * value instead of reporting a negative harvest.
     */
    private static void readCounter(long value) {
        if (cropStart < 0 || value < cropNow) {
            cropStart = value;
        }
        // Only a rise counts as activity. The line is read every tick whether or not it changed, so
        // marking activity on every read meant the session could never go idle at all.
        if (value > cropNow) {
            lastActivity = System.currentTimeMillis();
        }
        cropNow = value;
    }

    /** "Counter: 106,271,778" under the crop milestone widget. */
    private static final Pattern COUNTER =
            Pattern.compile("\\s*Counter:\\s*(?<count>[\\d,]+)\\s*");

    /**
     * Which crop the counter belongs to, from the milestone widget's own heading.
     *
     * <p>Needed for the coins figure and for nothing else: the counter is per crop, so pricing it
     * without knowing which crop would be a number made up out of two unrelated ones.
     */
    private static void readCropName(Minecraft mc) {
        boolean next = false;
        for (String line : SkyblockLocation.tabLines(mc)) {
            if (line.startsWith("Crop Milestones")) {
                next = true;
                continue;
            }
            if (next) {
                Matcher m = CROP_LINE.matcher(line);
                if (m.matches()) {
                    String name = m.group("crop").trim();
                    if (!name.equalsIgnoreCase(cropName)) {
                        // A different crop is a different counter; keeping the old baseline would
                        // report the switch as a harvest of millions.
                        cropName = name;
                        cropStart = -1;
                    }
                }
                return;
            }
        }
    }

    /** " Nether Wart MAXED", " Wheat 12" - the crop is the leading words. */
    private static final Pattern CROP_LINE =
            Pattern.compile("\\s*(?<crop>[A-Za-z][A-Za-z ]+?)\\s*(?:MAXED|\\d+)?\\s*");

    /**
     * A pest kill and what it dropped: "You received 7x Enchanted Potato for killing a Locust!".
     *
     * <p>One message carries both facts, which is why the kill count and the drop value are read
     * from the same line rather than from two features that could disagree about how many pests
     * died.
     */
    private static final Pattern PEST_KILL = Pattern.compile(
            "^You received (?:(?<amount>[\\d,]+)x )?(?<item>.+?) for killing an? (?<pest>.+)!$");

    /**
     * What Hypixel pays for a kill on top of the drop, from the wiki: 1,000 coins, and ten times
     * that for a Field Mouse.
     *
     * <p>A constant rather than a reading, because the coins are not announced in a line this can
     * match - and leaving them out would understate pest farming by roughly the value of the drop
     * itself. If Hypixel changes it, this number is the one place to change.
     */
    private static final long COINS_PER_KILL = 1_000;
    private static final long COINS_PER_FIELD_MOUSE = 10_000;

    public static void onMessage(String plain) {
        if (!Pests.inGarden()) {
            return;
        }
        Matcher m = PEST_KILL.matcher(plain.trim());
        if (!m.matches()) {
            return;
        }
        pests++;
        lastActivity = System.currentTimeMillis();

        String pest = m.group("pest").trim();
        pestCoins += pest.equalsIgnoreCase("Field Mouse") ? COINS_PER_FIELD_MOUSE : COINS_PER_KILL;

        // The drop is priced when the bazaar knows it and skipped when it does not - an unpriced
        // rare drop leaves the total low, which is the direction to be wrong in.
        long amount = m.group("amount") == null
                ? 1 : Long.parseLong(m.group("amount").replace(",", ""));
        double each = Bazaar.priceOf(m.group("item").trim());
        if (each > 0) {
            pestDropValue += each * amount;
        }
    }

    /** How long since anything was harvested or killed - an idle session is not a slow one. */
    public static long idle() {
        return lastActivity == 0 ? 0 : System.currentTimeMillis() - lastActivity;
    }

    private static long number(String text) {
        Matcher m = DIGITS.matcher(text);
        return m.find() ? Long.parseLong(m.group().replace(",", "")) : -1;
    }

    private static final Pattern DIGITS = Pattern.compile("[\\d,]+");
}
