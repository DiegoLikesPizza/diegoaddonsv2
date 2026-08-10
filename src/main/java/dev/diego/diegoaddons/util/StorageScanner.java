package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.gui.StorageOverlay;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.StorageOverlayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {@link StorageData} from the SkyBlock menus as they are opened.
 *
 * <p>Three menus matter, and they say different things:
 *
 * <ul>
 *   <li><b>Storage</b> ({@code /storage}) is the index. It holds one icon per ender chest page and
 *       per backpack, so it names everything you own without holding any of it - which is what lets
 *       the overlay list a backpack you have not opened this session instead of pretending it is
 *       not there.</li>
 *   <li><b>An ender chest page</b> and <b>a backpack</b> hold the actual items, and are the only
 *       places the client is ever handed them.</li>
 * </ul>
 *
 * <p>Everything here is a <b>read</b>. Nothing is clicked, moved or sent on your behalf; opening a
 * page from the overlay sends the same command you would have typed, and that is the only outgoing
 * act in the whole feature.
 *
 * <h2>Which backpack is this?</h2>
 * A backpack's own menu does not reliably say which slot it came from - some titles carry
 * "(Slot #3)", some are just the backpack's name. So the index is worked out in three steps, in
 * descending order of how much it is really known: the title if it says, otherwise the storage-menu
 * icon you clicked to get here, otherwise a unique name match against the pages already known. If
 * none of the three answers, the scan is skipped rather than guessed - writing a backpack's contents
 * under the wrong index is worse than not having read it.
 */
public final class StorageScanner {

    /** "Ender Chest Page 3", "Ender Chest (3/9)", or plain "Ender Chest" (which is page 1). */
    private static final Pattern EC_PAGE = Pattern.compile("(?i)ender chest\\D*(\\d+)");

    /** The slot number a menu title or an icon name may carry: "(Slot #3)". */
    private static final Pattern SLOT_NUMBER = Pattern.compile("(?i)slot\\s*#?\\s*(\\d+)");

    /** The last 36 slots of any SkyBlock menu are your own inventory rather than the container's. */
    private static final int PLAYER_SLOTS = 36;

    /** The page whose icon was last clicked in the storage menu, so its own menu can be attributed. */
    private static StorageData.Kind pendingKind;
    private static int pendingIndex;

    /** The screen last dumped to the log, so the debug dump happens once per menu and not per tick. */
    private static Screen lastDumped;

    private StorageScanner() {
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        StorageData.tick(mc);
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            lastDumped = null;
            // Still asked, because a page opening in answer to the overlay's command arrives
            // *through* the moment when no screen is up at all - and a navigation that never
            // arrives has to be able to time out.
            StorageOverlay.tick(mc);
            return;
        }
        String title = LegacyText.strip(screen.getTitle().getString()).trim();
        List<Slot> slots = screen.getMenu().slots;
        int limit = Math.max(0, slots.size() - PLAYER_SLOTS);
        if (limit == 0) {
            return;
        }
        if (debug() && screen != lastDumped) {
            dump(title, slots, limit);
            lastDumped = screen;
        }

        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("backpack")) {
            scanBackpack(title, slots, limit);
        } else if (lower.contains("ender chest")) {
            scanEnderChest(title, slots, limit);
        } else if (isStorageOverview(title)) {
            scanStorageMenu(slots, limit);
            // The overview is what raises the sheet: it is the menu the overlay stands in for, and
            // the one that names every page you own.
            StorageOverlay.onStorageMenu();
        }
        // After the scan, and that order matters: a page arriving this tick is captured just above,
        // and the follow-up click the overlay may be holding is aimed at a slot number that only
        // means anything once the page's navigation offset has been read off the real menu.
        StorageOverlay.tick(mc);
    }

    /** The storage overview - the menu that lists the pages rather than holding any of them. */
    public static boolean isStorageOverview(String title) {
        return title.toLowerCase(Locale.ROOT).startsWith("storage");
    }

    /**
     * Whether a menu is part of storage at all: the overview, an ender chest page, or a backpack.
     *
     * <p>The overlay stays up across all three, because navigating to a page is how it works - it
     * gets out of the way only when something unrelated is opened.
     */
    public static boolean isStorageFamily(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("backpack") || lower.contains("ender chest") || isStorageOverview(title);
    }

    /**
     * Which page a menu <i>is</i>, or null for the overview and for anything unrecognised.
     *
     * <p>The overlay asks this every frame to know which of its blocks is the live one, so it goes
     * through the same title parsing the scan does rather than a second, quietly different one.
     */
    public static StorageData.Page identify(String title) {
        // Asked every frame by the overlay and answered by regexes and a walk over every known
        // page. The title cannot change without the menu changing, so one answer per title is all
        // the work there is.
        if (title.equals(identifiedTitle)) {
            return identified;
        }
        identifiedTitle = title;
        identified = identifyUncached(title);
        return identified;
    }

    private static String identifiedTitle;
    private static StorageData.Page identified;

    private static StorageData.Page identifyUncached(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("backpack")) {
            int index = backpackIndex(title, null, -1);
            if (index <= 0 && pendingKind == StorageData.Kind.BACKPACK) {
                index = pendingIndex;
            }
            if (index <= 0) {
                index = indexByName(title);
            }
            return index > 0 ? StorageData.page(StorageData.Kind.BACKPACK, index) : null;
        }
        if (lower.contains("ender chest")) {
            Matcher m = EC_PAGE.matcher(title);
            int page = m.find() ? Integer.parseInt(m.group(1)) : 1;
            return StorageData.page(StorageData.Kind.ENDER_CHEST, page);
        }
        return null;
    }

    /**
     * Remembers which page's icon was clicked in the storage menu.
     *
     * <p>Registered on every container screen (see {@code ModuleManager}) and does nothing unless
     * this is the storage menu. It is the middle of the three ways a backpack's menu is attributed
     * to a slot, and in practice the one that answers: you got there by clicking the icon.
     */
    public static void onContainerClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (!enabled()) {
            return;
        }
        String title = LegacyText.strip(screen.getTitle().getString()).trim().toLowerCase(Locale.ROOT);
        if (!title.startsWith("storage")) {
            return;
        }
        Slot slot = slotAt(screen, mouseX, mouseY);
        if (slot == null) {
            return;
        }
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return;
        }
        String name = LegacyText.strip(stack.getHoverName().getString()).trim();
        Matcher ec = EC_PAGE.matcher(name);
        if (ec.find()) {
            pendingKind = StorageData.Kind.ENDER_CHEST;
            pendingIndex = Integer.parseInt(ec.group(1));
            return;
        }
        if (name.toLowerCase(Locale.ROOT).contains("backpack")) {
            int index = backpackIndex(name, stack, -1);
            if (index > 0) {
                pendingKind = StorageData.Kind.BACKPACK;
                pendingIndex = index;
            }
        }
    }

    /** The slot under the cursor, worked out from the menu's own origin. Null when between slots. */
    private static Slot slotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int left = ((AbstractContainerScreenAccessor) screen).diego$leftPos();
        int top = ((AbstractContainerScreenAccessor) screen).diego$topPos();
        for (Slot slot : screen.getMenu().slots) {
            double dx = mouseX - (left + slot.x);
            double dy = mouseY - (top + slot.y);
            if (dx >= 0 && dx < 16 && dy >= 0 && dy < 16) {
                return slot;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- the three menus

    private static void scanEnderChest(String title, List<Slot> slots, int limit) {
        Matcher m = EC_PAGE.matcher(title);
        // A title with no number is page one: that is what "/ec" alone opens.
        int page = m.find() ? Integer.parseInt(m.group(1)) : 1;
        StorageData.capture(StorageData.Kind.ENDER_CHEST, page,
                "Ender Chest Page " + page, contents(slots, limit), navSlots());
        pendingKind = null;
    }

    private static void scanBackpack(String title, List<Slot> slots, int limit) {
        int index = backpackIndex(title, null, -1);
        if (index <= 0 && pendingKind == StorageData.Kind.BACKPACK) {
            index = pendingIndex;
        }
        if (index <= 0) {
            index = indexByName(title);
        }
        if (index <= 0) {
            // Said once per menu rather than swallowed: a backpack that never appears in the overlay
            // is otherwise a mystery, and the title is exactly what has to be added to the patterns.
            if (debug()) {
                DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] storage: could not tell which backpack "
                        + "'{}' is - open it from the storage menu once and it will be attributed", title);
            }
            return;
        }
        StorageData.capture(StorageData.Kind.BACKPACK, index, cleanName(title),
                contents(slots, limit), navSlots());
        pendingKind = null;
    }

    /**
     * Reads the storage menu: every page it lists, whether or not it has ever been opened.
     *
     * <p>Backpacks are numbered by the slot number their icon carries where there is one, and by the
     * order they appear otherwise - the menu lays them out in slot order, so counting them gives the
     * same answer as reading them. Icons for slots you have not bought are skipped: they are an
     * offer to buy storage, not storage.
     */
    private static void scanStorageMenu(List<Slot> slots, int limit) {
        int counted = 0;
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String name = LegacyText.strip(stack.getHoverName().getString()).trim();
            Matcher ec = EC_PAGE.matcher(name);
            if (ec.find()) {
                StorageData.note(StorageData.Kind.ENDER_CHEST, Integer.parseInt(ec.group(1)),
                        name, ItemStack.EMPTY);
                continue;
            }
            if (!name.toLowerCase(Locale.ROOT).contains("backpack")) {
                continue;
            }
            if (locked(stack)) {
                continue;
            }
            counted++;
            StorageData.note(StorageData.Kind.BACKPACK, backpackIndex(name, stack, counted),
                    cleanName(name), stack);
        }
    }

    // ---------------------------------------------------------------- naming

    /**
     * The slot number of a backpack, from its title or icon.
     *
     * @param fallback used when nothing says; pass a negative number to mean "do not guess"
     */
    private static int backpackIndex(String text, ItemStack stack, int fallback) {
        Matcher m = SLOT_NUMBER.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        if (stack != null) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    Matcher lm = SLOT_NUMBER.matcher(LegacyText.strip(line.getString()));
                    if (lm.find()) {
                        return Integer.parseInt(lm.group(1));
                    }
                }
            }
        }
        return fallback;
    }

    /** The one known backpack whose name matches this title, or -1 when none or more than one does. */
    private static int indexByName(String title) {
        String name = cleanName(title).toLowerCase(Locale.ROOT);
        int found = -1;
        for (StorageData.Page page : StorageData.pages()) {
            if (page.kind == StorageData.Kind.BACKPACK
                    && cleanName(page.name).toLowerCase(Locale.ROOT).equals(name)) {
                if (found > 0) {
                    // Two backpacks of the same type, which is the normal case - so the name alone
                    // cannot say which of them this is.
                    return -1;
                }
                found = page.index;
            }
        }
        return found;
    }

    /** A title without its "(Slot #3)" suffix - that is drawn beside the name, not part of it. */
    private static String cleanName(String raw) {
        return SLOT_NUMBER.matcher(raw).replaceAll("").replace("()", "").trim();
    }

    /** Whether an icon is an offer to buy a storage slot rather than a slot you own. */
    private static boolean locked(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        for (Component line : lore.lines()) {
            String text = LegacyText.strip(line.getString()).toLowerCase(Locale.ROOT);
            if (text.contains("click to purchase") || text.contains("cost:")
                    || text.contains("locked") || text.contains("unlock")) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many leading slots of a page are its navigation row rather than storage.
     *
     * <p>SkyBlock puts close, back and the page arrows along the top of an ender chest page and a
     * backpack, so the first nine slots are controls the overlay has no use for - it has its own.
     * A setting rather than a constant because it is a fact about Hypixel's layout, and those move.
     */
    private static int navSlots() {
        StorageOverlayModule m = StorageOverlayModule.INSTANCE;
        return (m == null ? 1 : m.navRows()) * StorageData.COLUMNS;
    }

    private static List<ItemStack> contents(List<Slot> slots, int limit) {
        List<ItemStack> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(slots.get(i).getItem());
        }
        return out;
    }

    // ---------------------------------------------------------------- module state

    private static boolean enabled() {
        StorageOverlayModule m = StorageOverlayModule.INSTANCE;
        return m != null && m.isEnabled();
    }

    private static boolean debug() {
        StorageOverlayModule m = StorageOverlayModule.INSTANCE;
        return m != null && m.debug();
    }

    /** Every container slot's name, for tuning the patterns above against a real menu. */
    private static void dump(String title, List<Slot> slots, int limit) {
        DiegoAddonsV2Client.LOGGER.info("[STORAGE DEBUG] === '{}' ({} container slots) ===", title, limit);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            DiegoAddonsV2Client.LOGGER.info("[STORAGE DEBUG]   {}: {} x{}", i,
                    LegacyText.strip(stack.getHoverName().getString()), stack.getCount());
        }
    }
}
