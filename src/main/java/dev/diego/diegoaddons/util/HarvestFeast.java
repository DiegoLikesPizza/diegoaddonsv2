package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.module.modules.FeastHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Harvest Feast: how much seasoning is in the stew, which milestone that is heading for, and
 * which four crops are in season right now.
 *
 * <p><b>Two sources, and they answer different questions.</b> The tab list says whether the event is
 * running at all, which is what the HUD keys off - a display about a finished event is clutter.
 * Everything else comes from <b>Feast Chef Ted's menu</b>, read once when you open it and kept: the
 * seasoning total and the crops are not in the tab list, and the alternative is the external contest
 * API SkyHanni uses, which is a dependency this mod does not have and does not need for a display of
 * your own progress.
 *
 * <p>The cost of reading a menu is that the reading has a date on it. Four crops rotate every
 * SkyBlock month - 10 hours 20 minutes of real time - so a reading from the last month is wrong
 * rather than merely old, and the HUD says how long ago it was taken instead of quietly presenting
 * it as now. Seasoning only ever goes up, so a stale total is a floor, not a fiction.
 */
public final class HarvestFeast {
    /** Where Ted puts the four crops that are in season. */
    private static final int[] CROP_SLOTS = {11, 12, 14, 15};

    /**
     * The milestone ladder, from the wiki: five tiers to 250 seasoning normally, nine to 750 while
     * Finnegan is in office and the Grand Feast runs.
     *
     * <p>Only the last tier of each is documented, so the ladder is <b>read from the menu when it
     * can be</b> and these are the fallback for a HUD that has to show something before you have
     * opened Ted. A wrong intermediate threshold shows the wrong "next milestone"; it cannot make
     * the seasoning count wrong, which is the number that matters.
     */
    private static final int[] TIERS = {5, 25, 75, 150, 250};
    private static final int[] GRAND_TIERS = {5, 25, 75, 150, 250, 350, 450, 600, 750};

    private HarvestFeast() {
    }

    // --- the reading -------------------------------------------------------------------------------

    private static boolean eventInTab;
    private static boolean inAutumn;

    /**
     * Whether the feast is on.
     *
     * <p>Three signals, taken as "any of them", because the first one on its own was wrong: the tab
     * list does not reliably name the event, and keying the whole display on a line that may not
     * exist meant the card never appeared for Diego even while the feast was running and its own
     * numbers were correct.
     *
     * <ul>
     *   <li><b>The calendar.</b> The Harvest Feast runs Early Autumn through Late Autumn, and the
     *       season is written on the scoreboard every second of the game. This is the signal that
     *       cannot go missing.</li>
     *   <li><b>The tab list</b>, when it does name the event - it is the only one that would catch a
     *       feast running outside its usual months.</li>
     *   <li><b>A Grand Feast reading from Ted</b>, which is the case the calendar gets wrong: with
     *       Finnegan in office the feast runs all year, so autumn is no longer the boundary.</li>
     * </ul>
     */
    public static boolean running() {
        return inAutumn || eventInTab || (grand() && !stale());
    }

    /** The four crops in season, as Ted named them, or empty when Ted has not been visited. */
    public static List<String> crops() {
        String saved = ConfigManager.get().feastCrops;
        if (saved == null || saved.isBlank()) {
            return List.of();
        }
        return List.of(saved.split("\\s*,\\s*"));
    }

    public static int seasoning() {
        return ConfigManager.get().feastSeasoning;
    }

    public static boolean grand() {
        return ConfigManager.get().feastGrand;
    }

    /** When Ted's menu was last read, or 0 if never. */
    public static long readAt() {
        return ConfigManager.get().feastReadAt;
    }

    /** How long ago the reading was taken, or -1 when there has never been one. */
    public static long age() {
        long at = readAt();
        return at == 0 ? -1 : System.currentTimeMillis() - at;
    }

    /**
     * Whether the crops are older than the month they were read in.
     *
     * <p>A SkyBlock month is 10h20m of real time and the crops rotate with it, so anything older
     * than that is describing a season that has ended.
     */
    public static boolean stale() {
        long age = age();
        return age < 0 || age > 10 * 3600_000L + 20 * 60_000L;
    }

    /** The next milestone above the current seasoning, or -1 when the last one is passed. */
    public static int nextMilestone() {
        int[] tiers = milestones();
        for (int tier : tiers) {
            if (seasoning() < tier) {
                return tier;
            }
        }
        return -1;
    }

    /** Which milestone number the seasoning has reached, 0 before the first. */
    public static int tierReached() {
        int[] tiers = milestones();
        int reached = 0;
        for (int tier : tiers) {
            if (seasoning() >= tier) {
                reached++;
            }
        }
        return reached;
    }

    public static int totalMilestones() {
        return milestones().length;
    }

    /** The ladder read from the menu, or the documented one when the menu has not been read. */
    private static int[] milestones() {
        int[] read = readTiers;
        if (read != null && read.length > 0) {
            return read;
        }
        return grand() ? GRAND_TIERS : TIERS;
    }

    /** The thresholds Ted's own milestone items named, when they could be read. */
    private static int[] readTiers;

    // --- reading it --------------------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        FeastHudModule module = FeastHudModule.INSTANCE;
        if (module == null || !module.isEnabled() || mc.player == null) {
            return;
        }
        readTab(mc);
        readSeason(mc);
        readMenu(mc, module);
        // The calendar answer is recomputed each tick; a lore reading, when there was one, wins by
        // being written back over it below.
        readSeasonEndFromCalendar(mc);
        long fromLore = ConfigManager.get().feastSeasonEnd;
        if (fromLore > System.currentTimeMillis()) {
            seasonEnd = fromLore;
        }
    }

    /**
     * The item that stands for a crop on the HUD.
     *
     * <p>Vanilla items rather than SkyBlock's own icons: these are the blocks you actually break,
     * every client already has them, and they are what the crop looks like in your hand.
     *
     * <p><b>Empty when there is no world.</b> An item's component map is bound when the server's
     * data arrives, not at startup, so {@code new ItemStack(...)} on the title screen dies inside
     * the constructor with "Components not bound yet" - which is what crashed the game when the HUD
     * editor was opened from the main menu and this element drew its preview. {@code HudSlots.item}
     * skips an empty stack, so the card keeps its size and its countdown and simply shows no icons
     * until you are in a world.
     */
    public static ItemStack icon(String cropName) {
        if (Minecraft.getInstance().level == null) {
            return ItemStack.EMPTY;
        }
        String s = cropName.toLowerCase(Locale.ROOT);
        if (s.contains("wheat")) {
            return new ItemStack(net.minecraft.world.item.Items.WHEAT);
        }
        if (s.contains("carrot")) {
            return new ItemStack(net.minecraft.world.item.Items.CARROT);
        }
        if (s.contains("potato")) {
            return new ItemStack(net.minecraft.world.item.Items.POTATO);
        }
        if (s.contains("pumpkin")) {
            return new ItemStack(net.minecraft.world.item.Items.PUMPKIN);
        }
        if (s.contains("melon")) {
            return new ItemStack(net.minecraft.world.item.Items.MELON_SLICE);
        }
        if (s.contains("mushroom")) {
            return new ItemStack(net.minecraft.world.item.Items.RED_MUSHROOM);
        }
        if (s.contains("cactus")) {
            return new ItemStack(net.minecraft.world.item.Items.CACTUS);
        }
        if (s.contains("cane") || s.contains("sugar")) {
            return new ItemStack(net.minecraft.world.item.Items.SUGAR_CANE);
        }
        if (s.contains("wart")) {
            return new ItemStack(net.minecraft.world.item.Items.NETHER_WART);
        }
        if (s.contains("cocoa")) {
            return new ItemStack(net.minecraft.world.item.Items.COCOA_BEANS);
        }
        // A crop this does not know still gets a slot, so four icons stay four.
        return new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS);
    }

    /**
     * Whether the tab list names the feast.
     *
     * <p>Matched on the words rather than on one exact line: Hypixel writes the running event into
     * the {@code Event:} widget and also into the event-tracker block, and which of those is present
     * depends on which widgets the player has switched on. Either will do to answer "is it running".
     */
    private static void readTab(Minecraft mc) {
        boolean found = false;
        for (String line : SkyblockLocation.tabLines(mc)) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("harvest feast") || lower.contains("grand feast")) {
                found = true;
                break;
            }
        }
        eventInTab = found;
    }

    /**
     * Whether the SkyBlock season is one of the three autumn months, from the scoreboard's date line.
     *
     * <p>"Early Autumn", "Autumn" and "Late Autumn" all contain the word, so one check covers the
     * whole feast. Read from the sidebar rather than computed from the epoch: the server writes the
     * date there itself, and a season derived from arithmetic is one more thing that can drift.
     */
    private static void readSeason(Minecraft mc) {
        for (String line : SkyblockLocation.sidebarLines(mc)) {
            if (SEASON.matcher(line).find()) {
                inAutumn = line.toLowerCase(Locale.ROOT).contains("autumn");
                return;
            }
        }
    }

    /** Any SkyBlock date line, e.g. "Late Autumn 19th". */
    private static final Pattern SEASON = Pattern.compile(
            "(?:Early |Late )?(?:Spring|Summer|Autumn|Winter)\\s+\\d{1,2}(?:st|nd|rd|th)");

    /** The container already read, so a menu left open is not re-read twenty times a second. */
    private static int readContainer = -1;

    private static void readMenu(Minecraft mc, FeastHudModule module) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            readContainer = -1;
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (menu.containerId == readContainer) {
            return;
        }
        String title = LegacyText.strip(screen.getTitle().getString()).trim();
        String lower = title.toLowerCase(Locale.ROOT);
        if (!lower.equals("harvest feast") && !lower.equals("grand feast")) {
            return;
        }
        readContainer = menu.containerId;

        if (module.debugScan()) {
            dump(screen, title);
        }
        readCrops(menu);
        readSeasoning(menu);
        readSeasonEnd(mc, menu);
        ConfigManager.get().feastGrand = lower.startsWith("grand");
        ConfigManager.get().feastReadAt = System.currentTimeMillis();
        ConfigManager.save();
    }

    /** The four crop names Ted shows in the middle of the menu. */
    private static void readCrops(AbstractContainerMenu menu) {
        List<String> found = new ArrayList<>(4);
        for (int slot : CROP_SLOTS) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack stack = menu.slots.get(slot).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            found.add(LegacyText.strip(stack.getHoverName().getString()).trim());
        }
        // All four or none: a partial read is a menu that is not the one this expects, and half a
        // season list on the HUD is worse than the previous month's whole one.
        if (found.size() == CROP_SLOTS.length) {
            ConfigManager.get().feastCrops = String.join(", ", found);
        }
    }

    /**
     * The seasoning total and, when the menu spells them out, the milestone thresholds.
     *
     * <p>Both patterns are guesses at Hypixel's wording - hence "Debug scan (log)" on the card. The
     * total is taken from the highest "N/M Seasoning" style figure in the menu rather than the first:
     * a milestone item shows the same shape for its own tier, and the overall progress is the one
     * with the largest target.
     */
    private static void readSeasoning(AbstractContainerMenu menu) {
        int best = -1;
        int bestTarget = -1;
        List<Integer> tiers = new ArrayList<>();
        int own = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < own; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            List<String> lines = new ArrayList<>(Visitors.lore(stack));
            lines.add(LegacyText.strip(stack.getHoverName().getString()));
            for (String line : lines) {
                Matcher progress = PROGRESS.matcher(line);
                if (progress.find()) {
                    int have = number(progress.group(1));
                    int target = number(progress.group(2));
                    tiers.add(target);
                    if (target > bestTarget) {
                        bestTarget = target;
                        best = have;
                    }
                    continue;
                }
                Matcher total = TOTAL.matcher(line);
                if (total.find() && best < 0) {
                    best = number(total.group(1));
                }
            }
        }
        if (best >= 0) {
            ConfigManager.get().feastSeasoning = best;
        }
        if (tiers.size() > 1) {
            readTiers = tiers.stream().distinct().sorted().mapToInt(Integer::intValue).toArray();
        }
    }

    /** "37/250 Seasoning", "Seasoning: 37/250". */
    private static final Pattern PROGRESS =
            Pattern.compile("([\\d,]+)\\s*/\\s*([\\d,]+)\\s*(?:Seasoning)?", Pattern.CASE_INSENSITIVE);

    /** "Seasoning: 37". */
    private static final Pattern TOTAL =
            Pattern.compile("Seasoning:?\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);

    private static int number(String text) {
        return Integer.parseInt(text.replace(",", ""));
    }

    // --- how long the season has left ---------------------------------------------------------------

    /** When the current four rotate out, in epoch millis, or 0 when it is not known. */
    private static long seasonEnd;

    /** Milliseconds until the crops rotate, or -1 when that cannot be worked out. */
    public static long msLeftInSeason() {
        long end = seasonEnd;
        return end == 0 ? -1 : Math.max(0, end - System.currentTimeMillis());
    }

    /**
     * The remaining season, from Ted's own wording when he gives it and from the calendar otherwise.
     *
     * <p>Ted is preferred because he is telling you the answer; the calendar is arithmetic on an
     * assumption. A SkyBlock day is 20 real minutes and a month is 31 of them, so the time to the
     * month's end is exact - what is assumed is that the crops rotate <i>on</i> that boundary, which
     * is the natural reading of "four crops rotate each month" but is not something the wiki states
     * outright. If the countdown is consistently off by a fixed amount, that assumption is why, and
     * the lore pattern is the thing to fix rather than the maths.
     */
    private static void readSeasonEnd(Minecraft mc, AbstractContainerMenu menu) {
        for (int slot : CROP_SLOTS) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack stack = menu.slots.get(slot).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            for (String line : Visitors.lore(stack)) {
                Matcher m = SEASON_LEFT.matcher(line);
                if (m.find()) {
                    long secs = duration(m.group("time"));
                    if (secs > 0) {
                        seasonEnd = System.currentTimeMillis() + secs * 1000L;
                        ConfigManager.get().feastSeasonEnd = seasonEnd;
                        return;
                    }
                }
            }
        }
    }

    /** "In season for another 3h 20m!", "3h 20m left", "In season for 3h!". */
    private static final Pattern SEASON_LEFT = Pattern.compile(
            "(?:in season for(?: another)?|for another|left in season)\\s*(?<time>[\\dhms ]+)"
                    + "|(?<time2>[\\dhms ]+?)\\s*(?:left|remaining)", Pattern.CASE_INSENSITIVE);

    /** "3h 20m", "20m", "45s". -1 when it is none of those. */
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

    /** One SkyBlock day in real milliseconds, and the 31 of them that make a month. */
    private static final long SB_DAY_MS = 20 * 60 * 1000L;
    private static final int SB_MONTH_DAYS = 31;

    /**
     * The month's end from the SkyBlock date on the scoreboard, e.g. "Autumn 19th" and "12:30am".
     *
     * <p>Recomputed every tick rather than kept: it needs no menu, it cannot go stale, and it is the
     * only source that survives a session where Ted was never opened.
     */
    private static void readSeasonEndFromCalendar(Minecraft mc) {
        int day = -1;
        int minutesIntoDay = -1;
        for (String line : SkyblockLocation.sidebarLines(mc)) {
            Matcher d = DATE.matcher(line);
            if (d.find()) {
                day = Integer.parseInt(d.group("day"));
                continue;
            }
            Matcher t = CLOCK.matcher(line);
            if (t.find()) {
                int hour = Integer.parseInt(t.group("h")) % 12;
                int minute = Integer.parseInt(t.group("m"));
                if (t.group("ampm").equalsIgnoreCase("pm")) {
                    hour += 12;
                }
                minutesIntoDay = hour * 60 + minute;
            }
        }
        if (day < 1) {
            return;
        }
        long left = (long) (SB_MONTH_DAYS - day) * SB_DAY_MS;
        if (minutesIntoDay >= 0) {
            // A SkyBlock day is 1,440 of its own minutes squeezed into 20 real ones.
            left += SB_DAY_MS - Math.round(SB_DAY_MS * (minutesIntoDay / 1440.0));
        }
        seasonEnd = System.currentTimeMillis() + left;
    }

    private static final Pattern DATE =
            Pattern.compile("(?:Spring|Summer|Autumn|Winter)\\s+(?<day>\\d{1,2})(?:st|nd|rd|th)");
    private static final Pattern CLOCK =
            Pattern.compile("(?<h>\\d{1,2}):(?<m>\\d{2})\\s*(?<ampm>am|pm)", Pattern.CASE_INSENSITIVE);

    /** Dumps Ted's menu, for tuning the two patterns against the real wording. */
    private static void dump(AbstractContainerScreen<?> screen, String title) {
        DiegoAddonsV2Client.LOGGER.info("[feast] --- {} ---", title);
        AbstractContainerMenu menu = screen.getMenu();
        int own = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < own; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            DiegoAddonsV2Client.LOGGER.info("[feast] slot {}: {}", i,
                    LegacyText.strip(stack.getHoverName().getString()));
            for (String line : Visitors.lore(stack)) {
                DiegoAddonsV2Client.LOGGER.info("[feast]     {}", line);
            }
        }
    }

    /**
     * Seasoning announced in chat, so the total climbs between visits to Ted.
     *
     * <p>Seasoning is a 1-in-2,500 drop that never becomes an item - it goes straight into the
     * communal stew - so the only sign you got one is the message. Counting those keeps the HUD
     * moving while you farm instead of freezing at whatever Ted last said.
     */
    private static final Pattern GAINED = Pattern.compile(
            "^.*\\bSEASONING!.*?\\+\\s*([\\d,]+)\\s*Seasoning.*$", Pattern.CASE_INSENSITIVE);

    public static void onMessage(String plain) {
        FeastHudModule module = FeastHudModule.INSTANCE;
        if (module == null || !module.isEnabled()) {
            return;
        }
        Matcher m = GAINED.matcher(plain.trim());
        if (m.matches()) {
            ConfigManager.get().feastSeasoning += number(m.group(1));
            ConfigManager.save();
        }
    }
}
