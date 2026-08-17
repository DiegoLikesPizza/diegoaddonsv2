package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;
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
    private static String cropName = "";

    /** Kills read from chat, and kills read from the Pests widget - see {@link #pests()}. */
    private static int chatPests;
    private static int widgetPests;
    private static boolean chatKillSeen;
    /** The widget's alive count as of the last tick, or -1 when it was not being read. */
    private static int lastAlive = -1;

    /** The farming XP segment of the action bar as it last read, for the idle clock. */
    private static String lastXpText = "";

    private static boolean debug;
    private static long lastDump;

    public static void setDebug(boolean on) {
        debug = on;
    }

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

    /**
     * What the pests paid: their drops at bazaar prices plus the coins for the kills.
     *
     * <p>When the count came from the widget rather than from chat there is no drop to price - the
     * widget says a pest died and nothing about what it left - so only the flat kill coins are
     * counted. That understates pest farming, which is the direction to be wrong in.
     */
    public static double pestProfit() {
        if (!chatKillSeen) {
            return (double) widgetPests * COINS_PER_KILL;
        }
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

    /**
     * Pests killed, from chat when chat has ever named a kill and from the Pests widget when it has
     * not.
     *
     * <p>Two sources because the chat line is a guessed string and the widget is a number Hypixel
     * maintains, and the failure of the first is exactly what left this at zero all session: one
     * unmatched message shape and the tracker reports no pests at all. They are never added: a kill
     * shows up in both, so summing would double every one of them. Chat wins where it works because
     * it carries the drop as well, which is most of what a pest is worth.
     */
    public static int pests() {
        return chatKillSeen ? chatPests : widgetPests;
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
        chatPests = 0;
        widgetPests = 0;
        chatKillSeen = false;
        lastAlive = -1;
        activeMs = 0;
        lastTickAt = startedAt;
        pestDropValue = 0;
        pestCoins = 0;
        cropName = "";
        lastXpText = "";
    }

    public static void tick(Minecraft mc) {
        if (!Pests.inGarden()) {
            return;
        }
        if (startedAt == 0) {
            reset();
        }
        advanceClock();
        readTab(mc);
        readPestWidget();
    }

    /**
     * The whole tab read in one pass: copper, the crop the milestone widget is on, and its counter.
     *
     * <p>It used to be two passes with two independent guesses at the widget's lines, and both
     * guesses were wrong together - which is why the card showed a clock and copper and nothing
     * else. The crop is now only ever taken from {@link #CROPS}, so a heading, a visitor count or
     * any other line that happens to sit under the widget can no longer be adopted as a crop name
     * and price the session as something it is not.
     */
    private static void readTab(Minecraft mc) {
        List<String> lines = SkyblockLocation.tabLines(mc);
        String crop = null;
        long counter = -1;
        // Blank lines are dropped before this sees them, so the widget's entries follow its heading
        // directly and the block is bounded by a line count rather than by a separator.
        int inWidget = 0;
        for (String line : lines) {
            if (line.startsWith("Copper:")) {
                readCopper(number(line.substring("Copper:".length())));
                continue;
            }
            Matcher c = COUNTER.matcher(line);
            if (c.matches()) {
                counter = number(c.group("count"));
                continue;
            }
            if (MILESTONE_HEADING.matcher(line).find()) {
                // Matched on the word "Milestone" rather than on the exact heading: "Crop Milestones"
                // was itself a guess, and a widget called "Crop Milestone" would have failed the
                // whole read on the letter s.
                inWidget = WIDGET_LINES;
                // "Crop Milestones: Nether Wart 42" is one of the shapes it takes; the crop is read
                // off the heading itself when it is there.
                String rest = line.replaceAll("(?i).*milestones?", "");
                String named = cropIn(rest);
                if (named != null) {
                    crop = named;
                    long inline = trailingCount(rest);
                    if (inline >= 0) {
                        counter = inline;
                    }
                }
                continue;
            }
            if (inWidget > 0) {
                inWidget--;
                if (crop == null) {
                    crop = cropIn(line);
                    // Only off the crop's own line, never off a neighbouring one. The widget under
                    // this one is Jacob's Contest, which carries both a crop and a big number, and a
                    // count taken from it would be a contest total reported as your harvest.
                    if (crop != null) {
                        long value = trailingCount(line);
                        if (value >= 0 && counter < 0) {
                            counter = value;
                        }
                    }
                }
            }
        }
        if (crop != null && !crop.equalsIgnoreCase(cropName)) {
            // A different crop is a different counter; keeping the old baseline would report the
            // switch as a harvest of millions.
            cropName = crop;
            cropStart = -1;
            cropNow = -1;
        }
        if (counter >= 0) {
            readCounter(counter);
        }
        dump(lines, crop, counter);
    }

    private static void readCopper(long value) {
        if (copperStart < 0) {
            copperStart = value;
        } else if (value > copperNow) {
            lastActivity = System.currentTimeMillis();
        }
        copperNow = value;
    }

    /** How many lines after the heading are still the milestone widget's own. */
    private static final int WIDGET_LINES = 4;

    /**
     * The milestone widget's heading, and deliberately not Jacob's Contest.
     *
     * <p>The contest widget names a crop too, and sits near this one. Taking a crop from it would
     * price the session as whatever the contest happens to be running rather than what is being
     * farmed - a wrong number, which is worse than the missing one this replaces.
     */
    private static final Pattern MILESTONE_HEADING =
            Pattern.compile("(?i)^(?!.*contest).*milestones?\\b");

    /**
     * Kills counted off the Pests widget, as the fallback for a chat line that did not match.
     *
     * <p>Only a fall is counted, and only while the widget is being read: pests do not despawn, so
     * the alive count going down is a pest that died. A kill and a spawn inside the same tick cancel
     * out and are missed, which makes this a floor rather than a tally - the reason chat wins when
     * chat works.
     */
    private static void readPestWidget() {
        if (!Pests.widgetSeen()) {
            lastAlive = -1;
            return;
        }
        int alive = Pests.alive();
        if (lastAlive > alive) {
            widgetPests += lastAlive - alive;
            lastActivity = System.currentTimeMillis();
        }
        lastAlive = alive;
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

    /** "Counter: 106,271,778", wherever in the tab it sits. */
    private static final Pattern COUNTER =
            Pattern.compile("\\s*Counter:\\s*(?<count>[\\d,]+)\\s*");

    /**
     * Every crop the Garden has a milestone for, longest first so "Nether Wart" is matched as itself
     * rather than as a "Wart" with a word in front of it - the same reason {@link Pests} sorts its
     * pest names.
     *
     * <p>A closed list rather than "the leading words of the line", because the widget's shape is
     * the one thing here that was never confirmed against the game. With a list, an unrecognised
     * line is no crop; with a pattern, it was a crop called whatever happened to be written there,
     * and the coins figure then priced a heading.
     */
    private static final List<String> CROPS = List.of(
            "Nether Wart", "Cocoa Beans", "Sugar Cane", "Mushroom", "Pumpkin", "Potato", "Carrot",
            "Cactus", "Melon", "Wheat", "Seeds");

    private static final Pattern CROP_NAME = Pattern.compile(
            "\\b(" + String.join("|", CROPS.stream()
                    .sorted((a, b) -> b.length() - a.length()).toList()) + ")\\b",
            Pattern.CASE_INSENSITIVE);

    /** The crop named in this line, in the game's own spelling, or null when there is none. */
    private static String cropIn(String line) {
        Matcher m = CROP_NAME.matcher(line);
        if (!m.find()) {
            return null;
        }
        String found = m.group(1);
        for (String crop : CROPS) {
            if (crop.equalsIgnoreCase(found)) {
                return crop;
            }
        }
        return found;
    }

    /**
     * The last number on the line, for the shapes that write the counter beside the crop rather than
     * on a "Counter:" line of its own - "Nether Wart 42: 1,234,567".
     *
     * <p>The last one and not the first: where a line carries both, the leading number is the
     * milestone tier and the trailing one is the count. A tier read as a harvest would show a
     * session of twelve crops.
     */
    private static long trailingCount(String line) {
        Matcher m = DIGITS.matcher(line);
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        if (last == null || last.replace(",", "").length() < 4) {
            // Under four digits it is a tier, a percentage or a plot number, not a crop counter.
            return -1;
        }
        return Long.parseLong(last.replace(",", ""));
    }

    /**
     * The tab list as it actually reads, once every {@link #DUMP_MS} while Debug scan is on.
     *
     * <p>This exists because the widget's lines were guessed from a screenshot and shipped unread:
     * the session then showed a clock and copper for weeks with nothing to say why. One log line
     * settles it.
     */
    private static void dump(List<String> lines, String crop, long counter) {
        if (!debug) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDump < DUMP_MS) {
            return;
        }
        lastDump = now;
        DiegoAddonsV2Client.LOGGER.info(
                "[FarmingSession] crop={} counter={} copper={} pests={} ({}) idle={}s",
                crop, counter, copperNow, pests(), chatKillSeen ? "chat" : "widget",
                idle() / 1000);
        for (String line : lines) {
            DiegoAddonsV2Client.LOGGER.info("[FarmingSession] tab | {}", line);
        }
    }

    private static final long DUMP_MS = 15_000;

    /**
     * A pest kill and what it dropped: "You received 7x Enchanted Potato for killing a Locust!".
     *
     * <p>One message carries both facts, which is why the kill count and the drop value are read
     * from the same line rather than from two features that could disagree about how many pests
     * died.
     *
     * <p>Anchored at the start it never matched anything: Hypixel prefixes the line with its own
     * banner ("PEST DROP! You received ..."), exactly as it does the spawn announcement two fields
     * up, so the kill count sat at zero for the whole session. It is now searched for rather than
     * matched, and the pest name is a word run rather than "everything to the end", so a trailing
     * banner cannot swallow it either.
     */
    private static final Pattern PEST_KILL = Pattern.compile(
            "You received (?:(?<amount>[\\d,]+)x )?(?<item>[A-Za-z' -]+?) "
                    + "for killing an? (?<pest>[A-Za-z ]+?)!");

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
        String line = plain.trim();
        Matcher m = PEST_KILL.matcher(line);
        if (!m.find()) {
            if (debug && line.toLowerCase(Locale.ROOT).contains("killing")) {
                // The one line that would have told us the pattern was wrong, printed at the moment
                // it fails rather than left to be guessed at from a zero on the HUD.
                DiegoAddonsV2Client.LOGGER.info("[FarmingSession] unmatched kill line | {}", line);
            }
            return;
        }
        if (!chatKillSeen) {
            // The first readable kill hands over from the widget rather than starting again, or the
            // count on the card would drop back to one the moment chat started working.
            chatKillSeen = true;
            chatPests = widgetPests;
            pestCoins += (long) widgetPests * COINS_PER_KILL;
        }
        chatPests++;
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

    /**
     * The action bar's farming XP segment: {@code +12.3 Farming (72.45%)}, in any of the forms it
     * takes - a percentage, a fraction, or a raw total.
     */
    private static final Pattern FARMING_XP =
            Pattern.compile("\\+[\\d,.]+ Farming(?<detail>\\s*\\([^)]*\\))?");

    /**
     * Farming XP as the clock's real activity signal.
     *
     * <p>The counters it used before are both slow and both conditional: copper only moves on a
     * visitor or a contest, and the milestone counter only moves if the widget is on and parsed. A
     * farmer holding the button therefore looked idle, the clock stopped inside two minutes, and
     * every rate on the card divided by a time that had stopped growing. XP is gained on the crop
     * itself, which is the thing "am I farming" actually means.
     *
     * <p>The segment's <i>text</i> has to change to count, not merely be present: SkyBlock leaves the
     * last one drawn for a few seconds, so presence alone would keep a session that stopped ten
     * seconds ago looking alive - and a bar frozen by a mod or a lag spike would keep it alive
     * forever.
     */
    public static void onActionBar(String plain) {
        if (!Pests.inGarden()) {
            return;
        }
        Matcher m = FARMING_XP.matcher(plain);
        if (!m.find()) {
            lastXpText = "";
            return;
        }
        String text = m.group();
        if (!text.equals(lastXpText)) {
            lastXpText = text;
            lastActivity = System.currentTimeMillis();
        }
    }

    /** How long since anything was farmed, harvested or killed - an idle session is not a slow one. */
    public static long idle() {
        return lastActivity == 0 ? 0 : System.currentTimeMillis() - lastActivity;
    }

    private static long number(String text) {
        Matcher m = DIGITS.matcher(text);
        return m.find() ? Long.parseLong(m.group().replace(",", "")) : -1;
    }

    private static final Pattern DIGITS = Pattern.compile("[\\d,]+");
}
