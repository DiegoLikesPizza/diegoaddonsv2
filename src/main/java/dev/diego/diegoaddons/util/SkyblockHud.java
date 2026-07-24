package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Caches Hypixel SkyBlock's <b>Equipment</b> (necklace / cloak / belt / gloves) and <b>active
 * pet</b>, which are not part of the vanilla inventory - they only exist as items inside SkyBlock's
 * server-side chest GUIs. While such a menu is open we read the items out of its slots and remember
 * them, so the inventory HUD can show them afterwards.
 *
 * <p>Detection is heuristic (menu title + item lore), because the exact slot layout isn't exposed
 * and can change between SkyBlock versions. The keyword constants below are the tuning knobs: if a
 * future update renames a menu or category line, adjust them here. Everything is read-only.
 */
public final class SkyblockHud {
    // Equipment categories as they appear on the last lore line ("LEGENDARY NECKLACE", ...).
    private static final String[] CATEGORIES = {"NECKLACE", "CLOAK", "BELT", "GLOVES"};
    // Menu titles are like "(1/2) Equipment Sets" and "(1/2) Pets" - match the stable part.
    private static final String EQUIPMENT_TITLE = "equipment sets";
    private static final String PETS_TITLE = "pets";
    private static final String ACTIVE_PET_MARKER = "despawn";   // active pet lore: "Click to despawn!"
    private static final String EQUIPPED_MARKER = "equipped";    // the equipped set's items are marked

    // Pet name prefix ("[Lvl 100] ...") and the XP bar's "current/needed" tail.
    private static final Pattern LEVEL = Pattern.compile("\\[Lvl (\\d+)]");
    private static final Pattern XP_BAR = Pattern.compile("([\\d,.]+)\\s*/\\s*([\\d,.]+)\\s*$");

    private static final ItemStack[] equipment = new ItemStack[4];
    private static boolean equipmentLocked = false;   // true once the *equipped* set has been captured
    private static ItemStack pet = ItemStack.EMPTY;

    /** When on, the contents of any open container are dumped to the log (name + lore per slot). */
    public static boolean debug = false;
    private static Object lastDumped;

    static {
        Arrays.fill(equipment, ItemStack.EMPTY);
    }

    private SkyblockHud() {
    }

    /** Equipment item for slot 0=necklace, 1=cloak, 2=belt, 3=gloves (never null). */
    public static ItemStack equipment(int i) {
        ItemStack s = (i >= 0 && i < equipment.length) ? equipment[i] : null;
        return s == null ? ItemStack.EMPTY : s;
    }

    /** The currently summoned pet, or empty if none was seen / none is active. */
    public static ItemStack pet() {
        return pet == null ? ItemStack.EMPTY : pet;
    }

    /**
     * The active pet broken down for display: its name in its rarity colour, its level, and how far
     * it is from the next level.
     *
     * @param name   the pet's name without the "[Lvl n]" prefix
     * @param colour ARGB rarity colour, taken from the colour SkyBlock already gives the name
     * @param level  the pet's level, or -1 if it could not be read
     * @param xp     progress to the next level ("1.2M/2.4M"), "MAX LEVEL", or null if unknown
     */
    public record PetInfo(String name, int colour, int level, String xp) {
    }

    private static ItemStack petInfoSource;
    private static PetInfo petInfoCache;

    /** {@link PetInfo} for the active pet, or null if there is none. Parsed once per pet item. */
    public static PetInfo petInfo() {
        ItemStack p = pet();
        if (p.isEmpty()) {
            return null;
        }
        if (petInfoCache == null || petInfoSource != p) {
            petInfoCache = parsePet(p);
            petInfoSource = p;
        }
        return petInfoCache;
    }

    /**
     * Reads name, rarity colour, level and XP out of a pet item. Everything here is best-effort text
     * parsing of SkyBlock's own formatting: the name looks like {@code §7[Lvl 100] §6Golden Dragon}
     * (the name run already carries the rarity colour), and the lore holds a "Progress to Level n"
     * line followed by a bar line ending in {@code current/needed}.
     */
    private static PetInfo parsePet(ItemStack stack) {
        Component hover = stack.getHoverName();
        String plain = strip(hover.getString());

        int level = -1;
        Matcher lvl = LEVEL.matcher(plain);
        if (lvl.find()) {
            level = Integer.parseInt(lvl.group(1));
        }
        int close = plain.indexOf(']');
        String name = (close >= 0 ? plain.substring(close + 1) : plain).trim();
        if (name.isEmpty()) {
            name = plain.trim();
        }

        // The rarity colour is the colour of the last coloured run - i.e. the name itself, since the
        // "[Lvl n]" prefix is drawn grey before it.
        int[] colour = {0xFFFFFFFF};
        hover.visit((style, text) -> {
            TextColor c = style.getColor();
            if (c != null && !strip(text).isBlank()) {
                colour[0] = 0xFF000000 | c.getValue();
            }
            return Optional.empty();
        }, Style.EMPTY);

        String xp = null;
        for (String line : loreOf(stack)) {
            if (line.toUpperCase(Locale.ROOT).contains("MAX LEVEL")) {
                xp = "MAX LEVEL";
                break;
            }
            Matcher bar = XP_BAR.matcher(line);
            if (bar.find()) {
                xp = compact(bar.group(1)) + "/" + compact(bar.group(2));
            }
        }
        return new PetInfo(name, colour[0], level, xp);
    }

    /** "1,234,567" -> "1.2M", so the XP line stays narrow enough for a HUD element. */
    private static String compact(String number) {
        long n;
        try {
            n = Long.parseLong(number.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return number;
        }
        if (n >= 1_000_000) {
            return trimZero(n / 1_000_000.0) + "M";
        }
        if (n >= 1_000) {
            return trimZero(n / 1_000.0) + "k";
        }
        return Long.toString(n);
    }

    private static String trimZero(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Called each client tick: if a matching SkyBlock menu is open, refresh the cache from it. */
    public static void tick(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            lastDumped = null;
            return;
        }
        String rawTitle = screen.getTitle().getString();
        String title = strip(rawTitle).toLowerCase(Locale.ROOT);
        List<Slot> slots = screen.getMenu().slots;
        // The last 36 slots of a chest menu are the player's own inventory - skip them.
        int limit = Math.max(0, slots.size() - 36);

        if (debug && screen != lastDumped) {
            dump(rawTitle, slots, limit);
            lastDumped = screen;
        }

        if (title.contains(EQUIPMENT_TITLE)) {
            scanEquipment(slots, limit);
        } else if (title.contains(PETS_TITLE)) {
            scanPets(slots, limit);
        }
    }

    /** Log every non-empty slot's name + lore so real menu structure can be inspected. */
    private static void dump(String title, List<Slot> slots, int limit) {
        DiegoAddonsV2Client.LOGGER.info("[SB DEBUG] === '{}' ({} container slots) ===", title, limit);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String name = strip(stack.getHoverName().getString());
            DiegoAddonsV2Client.LOGGER.info("[SB DEBUG]  #{} \"{}\" | lore: {}", i, name,
                    String.join(" | ", loreOf(stack)));
        }
        DiegoAddonsV2Client.LOGGER.info("[SB DEBUG] === end ===");
    }

    /**
     * The equipment wardrobe is paged and shows several saved sets. We prefer the items belonging to
     * the <i>equipped</i> set (their lore carries {@link #EQUIPPED_MARKER}); once we've captured that
     * set we "lock" it, so flipping to other pages doesn't overwrite it. Before we've ever seen the
     * equipped set we show whatever pieces are visible, so something appears immediately.
     */
    private static void scanEquipment(List<Slot> slots, int limit) {
        ItemStack[] any = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        ItemStack[] marked = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        boolean hasMarked = false;
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            int cat = categoryOf(stack);
            if (cat < 0) {
                continue;
            }
            any[cat] = stack;
            if (loreContains(stack, EQUIPPED_MARKER)) {
                marked[cat] = stack;
                hasMarked = true;
            }
        }
        if (hasMarked) {
            System.arraycopy(marked, 0, equipment, 0, 4);
            equipmentLocked = true;
        } else if (!equipmentLocked) {
            System.arraycopy(any, 0, equipment, 0, 4);
        }
    }

    private static void scanPets(List<Slot> slots, int limit) {
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (!stack.isEmpty() && loreContains(stack, ACTIVE_PET_MARKER)) {
                pet = stack;
                return; // found the active pet on this page; keep it (don't clear on other pages)
            }
        }
    }

    /** @return equipment category index (0..3), or -1 if this isn't an equipment piece. */
    private static int categoryOf(ItemStack stack) {
        List<String> lore = loreOf(stack);
        if (lore.isEmpty()) {
            return -1;
        }
        // The rarity/category line is the last non-blank lore line, e.g. "LEGENDARY NECKLACE".
        String last = "";
        for (int i = lore.size() - 1; i >= 0; i--) {
            String l = lore.get(i).trim();
            if (!l.isEmpty()) {
                last = l.toUpperCase(Locale.ROOT);
                break;
            }
        }
        for (int c = 0; c < CATEGORIES.length; c++) {
            if (last.endsWith(CATEGORIES[c])) {
                return c;
            }
        }
        return -1;
    }

    private static boolean loreContains(ItemStack stack, String needle) {
        for (String line : loreOf(stack)) {
            if (line.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Clear the cache when leaving a server (a different profile has different pet/equipment). */
    public static void reset() {
        Arrays.fill(equipment, ItemStack.EMPTY);
        equipmentLocked = false;
        pet = ItemStack.EMPTY;
        petInfoSource = null;
        petInfoCache = null;
    }

    private static List<String> loreOf(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return List.of();
        }
        return lore.lines().stream().map(c -> strip(c.getString())).toList();
    }

    /** Remove Minecraft section-sign colour/format codes. */
    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
