package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the Garden is currently saying about pests: how long until the next one can spawn, how many
 * are alive, and which plots they are in. Read once a tick and handed to everything that wants it.
 *
 * <p><b>The cooldown is Hypixel's own number, not ours.</b> It could be worked out - the base is five
 * minutes and every piece of pest gear shortens it, down to 1:10 with Pest Eradicator - but that sum
 * depends on a reforge, an attribute and a perk we would each have to read and keep in step with
 * whatever Hypixel changes next. The Pests widget in the tab list already prints the answer, so this
 * reads it. The cost of that choice is one requirement worth stating plainly: <b>the widget has to be
 * switched on in {@code /widget}</b>, and when it is not, this says so rather than guessing.
 *
 * <p>The widget only prints whole seconds and only updates about once a second, so the reading is
 * turned into an <i>end time</i> and counted down locally. Re-syncing on every packet would make the
 * display stutter by a second either way, so a fresh reading is only taken when it disagrees with
 * what we are already counting down to by more than {@link #RESYNC_MS}.
 */
public final class Pests {
    /** Hypixel's cap: at eight pests alive the cooldown stops entirely. */
    public static final int MAX_ALIVE = 8;

    /** How far the widget may disagree with our own countdown before we believe it over ourselves. */
    private static final long RESYNC_MS = 1500;

    private Pests() {
    }

    // --- the reading -------------------------------------------------------------------------------

    private static boolean inGarden;
    private static boolean widgetSeen;
    private static boolean ready;
    private static boolean maxPests;
    /** When the cooldown runs out, in {@code System.currentTimeMillis()}, or 0 when not known. */
    private static long cooldownEndMs;
    private static int alive;
    private static String plots = "";
    private static long lastSpawnMs;

    /** Whether the player is in the Garden at all - nothing here means anything anywhere else. */
    public static boolean inGarden() {
        return inGarden;
    }

    /**
     * Whether the Pests widget was found in the tab list. False means every other reading here is
     * unknown rather than zero, and that difference is the whole reason this is exposed.
     */
    public static boolean widgetSeen() {
        return widgetSeen;
    }

    /** The cooldown has run out: a pest can spawn on the next crop you break. */
    public static boolean ready() {
        return ready || (cooldownEndMs != 0 && System.currentTimeMillis() >= cooldownEndMs);
    }

    /** Eight pests are alive, so the cooldown is not running at all until some are killed. */
    public static boolean maxPests() {
        return maxPests;
    }

    /** Milliseconds until a pest can spawn, or -1 when that is not known. */
    public static long msLeft() {
        if (maxPests || ready || cooldownEndMs == 0) {
            return -1;
        }
        return Math.max(0, cooldownEndMs - System.currentTimeMillis());
    }

    /** The moment the cooldown ends, or 0 when unknown - the identity of the current cooldown. */
    public static long cooldownEnd() {
        return cooldownEndMs;
    }

    public static int alive() {
        return alive;
    }

    /** The infested plots as the widget lists them ("4, 12, 13"), or "" when there are none. */
    public static String plots() {
        return plots;
    }

    /** When a pest last spawned, or 0 if none has since joining. */
    public static long lastSpawn() {
        return lastSpawnMs;
    }

    // --- reading it --------------------------------------------------------------------------------

    /**
     * The Garden, from either surface that names it. The tab list's {@code Area:} line is the island
     * and the scoreboard's {@code ⏣} line is the sub-area - and in the Garden that second one reads
     * "The Garden" out on the plots but "Plot - 4" once you are standing on one, which is why both
     * forms are accepted rather than just the first.
     */
    private static boolean readInGarden(Minecraft mc) {
        if (SkyblockLocation.island(mc).equalsIgnoreCase("Garden")) {
            return true;
        }
        String area = SkyblockLocation.area(mc);
        return area.equalsIgnoreCase("The Garden") || area.startsWith("Plot -");
    }

    public static void tick(Minecraft mc) {
        boolean garden = mc.player != null && readInGarden(mc);
        if (!garden) {
            if (inGarden) {
                reset();
            }
            inGarden = false;
            return;
        }
        inGarden = true;
        readWidget(mc);
    }

    /** Leaving the Garden drops everything: a cooldown from an hour ago is not a reading. */
    private static void reset() {
        widgetSeen = false;
        ready = false;
        maxPests = false;
        cooldownEndMs = 0;
        alive = 0;
        plots = "";
        lastSpawnMs = 0;
    }

    private static void readWidget(Minecraft mc) {
        boolean seenHeader = false;
        boolean seenCooldown = false;
        for (String line : SkyblockLocation.tabLines(mc)) {
            if (line.startsWith("Pests:")) {
                seenHeader = true;
            } else if (line.startsWith("Alive:")) {
                alive = number(line.substring("Alive:".length()));
            } else if (line.startsWith("Plots:")) {
                plots = line.substring("Plots:".length()).trim();
            } else if (line.startsWith("Cooldown:")) {
                seenCooldown = true;
                readCooldown(line.substring("Cooldown:".length()).trim());
            }
        }
        widgetSeen = seenHeader || seenCooldown;
        if (!widgetSeen) {
            cooldownEndMs = 0;
            ready = false;
            maxPests = false;
        }
        // The widget lists no plots at all when there are none, so an absent line means empty rather
        // than unchanged - otherwise the last infested plot stays on the HUD after it is cleared.
        if (alive == 0) {
            plots = "";
        }
    }

    private static void readCooldown(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.startsWith("READY")) {
            ready = true;
            maxPests = false;
            cooldownEndMs = 0;
            return;
        }
        if (upper.startsWith("MAX")) {
            maxPests = true;
            ready = false;
            cooldownEndMs = 0;
            return;
        }
        long secs = seconds(value);
        if (secs < 0) {
            return;
        }
        ready = false;
        maxPests = false;
        long end = System.currentTimeMillis() + secs * 1000L;
        if (cooldownEndMs == 0 || Math.abs(end - cooldownEndMs) > RESYNC_MS) {
            cooldownEndMs = end;
        }
    }

    /** "1m 58s", "1m", "58s" - the three shapes the widget prints. -1 when it is none of them. */
    private static long seconds(String text) {
        Matcher m = TIME.matcher(text);
        if (!m.find()) {
            return -1;
        }
        long total = 0;
        boolean any = false;
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

    private static final Pattern TIME =
            Pattern.compile("(?:(?<m>\\d+)m)?\\s*(?:(?<s>\\d+)s)?");

    private static int number(String text) {
        Matcher m = DIGITS.matcher(text);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    // --- the spawn itself --------------------------------------------------------------------------

    /**
     * Hypixel's announcement that pests have spawned, in its three forms. The double space is not a
     * typo: the message carries the pest icon between the count and the word, and with the colour
     * codes stripped the icon leaves the gap it was drawn in.
     */
    private static final Pattern SPAWNED = Pattern.compile(
            "^\\w+! (?:A|\\d+) {1,2}Pests? (?:has appeared|have spawned) in .+!$");

    private static final Pattern OFFLINE_SPAWNED = Pattern.compile(
            "^\\w+! While you were offline, {1,2}Pests? spawned in Plots .+!$");

    /**
     * Notes the moment a pest spawned, which is what "last spawn" is measured from.
     *
     * <p>The offline message is deliberately not counted: those pests were already there when you
     * arrived, and treating the announcement as a spawn would start the clock at the wrong end.
     */
    public static void onMessage(String plain) {
        if (!inGarden) {
            return;
        }
        String s = plain.trim();
        if (OFFLINE_SPAWNED.matcher(s).matches()) {
            return;
        }
        if (SPAWNED.matcher(s).matches()) {
            lastSpawnMs = System.currentTimeMillis();
        }
    }

    // --- pests as entities -------------------------------------------------------------------------

    /**
     * Every pest's name, for finding one by its name plate. SkyBlock's pests are ordinary mobs
     * wearing a plate, exactly like dungeon mobs, so this is the same trick {@link EntityEsp} uses
     * everywhere else - and it is why an ESP can box a pest at all without knowing its entity type.
     */
    private static final List<String> NAMES = List.of(
            "Beetle", "Cricket", "Dragonfly", "Earthworm", "Field Mouse", "Firefly", "Fly",
            "Locust", "Lunar Moth", "Mite", "Mosquito", "Moth", "Praying Mantis", "Rat", "Slug");

    /**
     * Names joined into one alternation, longest first so "Lunar Moth" is matched as itself rather
     * than as a "Moth" with a word in front of it. Word boundaries either side, because "Rat" and
     * "Fly" are three letters that turn up inside other words.
     */
    private static final Pattern PEST_NAME = Pattern.compile(
            "\\b(" + String.join("|", NAMES.stream()
                    .sorted((a, b) -> b.length() - a.length()).toList()) + ")\\b");

    /**
     * A pet's name plate, which carries its level: {@code [Lvl 189] Rose Dragon}.
     *
     * <p>Six pests share a name with a pet - Slug, Mosquito, Rat, Beetle, Moth and Cricket among
     * them - so matching the name alone boxes your own pet as vermin, which is exactly what Diego
     * saw with a Slug. The level prefix is the tell: SkyBlock writes it on every pet plate and on no
     * mob plate, and {@code SkyblockHud} has been reading pets by that same prefix since the pet HUD
     * was written, so this is not a new assumption to maintain.
     *
     * <p>Deliberately <b>not</b> "skip whatever your active pet is called": that would hide a real
     * Slug pest for as long as you have a Slug out, and the pest is the thing you are looking for.
     * Other players' pets carry the prefix too, so the one check covers them as well.
     */
    private static final Pattern PET_PLATE = Pattern.compile("\\[Lvl\\s*\\d+]");

    /**
     * Whether this name plate belongs to a pet rather than to a mob.
     *
     * <p>Public because the hunting ESPs need the same answer for the same reason: several critters
     * are the entity type of a real SkyBlock pet - a Dolphin, a Turtle, an Axolotl, a Bat - so
     * boxing by type alone boxes whatever is following the player around. One regex, one place.
     */
    public static boolean isPetPlate(String plate) {
        return PET_PLATE.matcher(plate).find();
    }

    /** Whether this name plate belongs to a pest, and which one - or null for anything else. */
    public static String pestOnPlate(String plate) {
        if (isPetPlate(plate)) {
            return null;
        }
        Matcher m = PEST_NAME.matcher(plate);
        return m.find() ? m.group(1) : null;
    }
}
