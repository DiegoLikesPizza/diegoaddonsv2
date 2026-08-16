package dev.diego.diegoaddons.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
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
 *
 * <p>No slot number is ever hard-coded. Every scan finds its anchor from something an item says
 * about itself - a rarity line, a "Slot 1: Equipped" marker - and reads the rest relative to it, so
 * a menu that gains a border row or shifts a column along keeps working.
 */
public final class SkyblockHud {
    /**
     * Equipment categories as they appear on the last lore line ("LEGENDARY NECKLACE", ...).
     *
     * <p>One slot, more than one word. The fourth is the glove slot, and SkyBlock does not call
     * everything in it gloves: the Lotus Bracelet ends "RARE BRACELET", so matching only "GLOVES"
     * left that slot empty on the HUD for anyone wearing one. Each entry is therefore the list of
     * words that slot answers to, and a new one is a word added here rather than a new slot.
     */
    private static final String[][] CATEGORIES = {
            {"NECKLACE"},
            {"CLOAK"},
            {"BELT"},
            {"GLOVES", "BRACELET"},
    };
    /** The armour categories, in the order they are worn and drawn: head down to feet. */
    private static final String[] ARMOUR_CATEGORIES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
    // Menu titles are like "(1/2) Equipment Sets" and "(1/2) Pets" - match the stable part.
    /** Matches both "Equipment" (what you are wearing) and "(1/2) Equipment Sets" (the wardrobe). */
    private static final String EQUIPMENT_WORD = "equipment";
    /** What separates the two: the wardrobe is the one listing sets. */
    private static final String SETS_WORD = "sets";
    /** A chest menu is nine wide, which is what makes a column of the wardrobe one saved set. */
    private static final int COLS = 9;
    /**
     * The wardrobe's per-set state markers: {@code Slot 1: Equipped}, {@code Slot 2: Empty},
     * {@code Slot 3: Ready}.
     *
     * <p>These sit in a row of their own beneath the sets, one under each column, and they are the
     * only thing in the menu that says which set you are actually wearing.
     */
    private static final Pattern SLOT_STATE =
            Pattern.compile("Slot\\s*\\d+\\s*:\\s*([A-Za-z]+)");
    private static final String PETS_TITLE = "pets";
    /** A loadout menu carries a pet and an equipment set together, so both scans are offered it. */
    private static final String LOADOUT_TITLE = "loadout";
    private static final String ACTIVE_PET_MARKER = "despawn";   // active pet lore: "Click to despawn!"

    // Pet name prefix ("[Lvl 100] ...") and the XP bar's "current/needed" tail.
    private static final Pattern LEVEL = Pattern.compile("\\[Lvl (\\d+)]");
    private static final Pattern XP_BAR = Pattern.compile("([\\d,.]+)\\s*/\\s*([\\d,.]+)\\s*$");
    /**
     * Autopet's announcement: {@code Autopet equipped your [Lvl 100] Golden Dragon! VIEW RULE}.
     *
     * <p>Deliberately loose about what follows the name - the trailing "! VIEW RULE" is a clickable
     * suffix that has changed before, and the name is the only part worth being strict about.
     *
     * <p><b>Two things a pet skin does to this line, both of which used to break the name.</b> A
     * skinned pet is announced as {@code ... Rabbit ✦!}, and a Golden Dragon with a skin number as
     * {@code ... [122✦] Golden Dragon!}. The old pattern took everything up to the {@code !}, so the
     * name came out as "Rabbit ✦" or "[122✦] Golden Dragon" - which then matched nothing in the
     * seen-pets map, and the HUD dropped the icon for exactly the pets most worth showing. The star
     * suffix and the count prefix are now both consumed around the name rather than into it.
     */
    private static final Pattern AUTOPET = Pattern.compile(
            "Autopet equipped your \\[Lvl (?<level>\\d+)] (?:\\[\\d+✦] )?(?<pet>[\\w -]+?)(?: ✦)?!");
    /**
     * Summoning and despawning by hand: {@code You summoned your [Lvl 100] Golden Dragon!}
     *
     * <p>Needed for the same reason as {@link #AUTOPET}. Clicking a pet closes the menu, so the scan
     * that would have noticed the change is not running by the time the change happens - the HUD
     * went on showing the previous pet until the menu was opened again, which is exactly the moment
     * you no longer need to be told.
     *
     * <p><b>The level is optional, and that is the whole fix.</b> This pattern required
     * {@code [Lvl n]}, and the real line does not have one - Hypixel writes
     * {@code You summoned your Golden Dragon!} with no level at all. So it never matched once, and
     * summoning a pet by hand has been invisible to the HUD for as long as this has existed. It is
     * left optional rather than removed in case the level comes back.
     */
    private static final Pattern SUMMON = Pattern.compile(
            "You summoned your (?:\\[Lvl (?<level>\\d+)] )?(?<pet>[\\w -]+?)(?: ✦)?!");
    private static final Pattern DESPAWN = Pattern.compile("You\\s+despawned\\s+your\\s+");

    /**
     * A loadout's contents, which it lists in its own tooltip.
     *
     * <pre>
     *   Necklace: Peony Necklace
     *   Cloak: Zorro's Cape
     *   Belt: Peony Belt
     *   Gloves/Bracelet: Peony Bracelet
     *   Pet: [Lvl 189] Rose Dragon
     * </pre>
     *
     * <p>Names rather than items, which is the catch: they are resolved against the pieces and pets
     * seen in their own menus, and anything never seen there cannot be drawn - a name is not an item
     * model. So this is the fallback. What the menu shows down its left is the gear itself, and
     * {@link #scanEquippedPanel} reads that; the tooltips are only reached when the panel is not
     * where it is expected to be.
     */
    private static final Pattern LOADOUT_PIECE =
            Pattern.compile("(Necklace|Cloak|Belt|Gloves/Bracelet|Gloves|Bracelet)\\s*:\\s*(.+)");
    private static final Pattern LOADOUT_PET =
            Pattern.compile("Pet\\s*:\\s*\\[Lvl (\\d+)]\\s*(.+)");
    /**
     * How a loadout says it is <i>not</i> the one you are wearing: {@code Left-click to equip!}
     *
     * <p>Inverted, because that is how the menu actually distinguishes them. The two tooltips are
     * otherwise identical - same gear, same pet, same "Right-click to edit" - and the equipped one
     * carries no badge of its own. What it lacks is the offer to equip it, which it does not need.
     *
     * <p>So the test is an absence, and an absence is only meaningful about something already known
     * to be a loadout - see {@link #scanLoadouts}, which never applies this to anything that has not
     * already declared a pet or an equipment piece.
     */
    private static final Pattern LOADOUT_EQUIP_PROMPT =
            Pattern.compile("(?i)click\\s+to\\s+equip");

    private static final ItemStack[] equipment = new ItemStack[4];
    /**
     * The armour the Loadouts menu says you have on, head to feet.
     *
     * <p>A fallback, not the source: your armour is in your real inventory and the HUD reads it from
     * there, live. This is only reached when the live slots are <i>all</i> empty, which is the one
     * case the menu can answer and the inventory cannot.
     */
    private static final ItemStack[] armour = new ItemStack[4];
    private static ItemStack pet = ItemStack.EMPTY;

    /**
     * Every pet seen in a menu this session, by lower-cased name without its level prefix.
     *
     * <p>So an Autopet swap has an item to show. The menus are the only place a pet item exists, and
     * a swap does not open one - without this the icon would either stay on the previous pet or go
     * blank every time a rule fired.
     */
    private static final java.util.Map<String, ItemStack> seenPets = new java.util.HashMap<>();

    /**
     * Every equipment piece seen in a menu this session, by lower-cased name.
     *
     * <p>The loadout menu names its pieces but does not contain them, so this is what turns
     * "Peony Necklace" back into something with a model to draw.
     */
    private static final java.util.Map<String, ItemStack> seenEquipment = new java.util.HashMap<>();

    /** False until the persisted equipment/pet have been restored this session. */
    private static boolean restored = false;

    /** When on, the contents of any open container are dumped to the log (name + lore per slot). */
    public static boolean debug = false;
    private static Object lastDumped;
    /**
     * The loadout menu currently open, by identity, so opening one can be told from keeping it open.
     *
     * <p>The screen object is the only thing that changes when a menu is reopened - the title and
     * the contents may be identical - so identity is what "opened it again" means here.
     */
    private static Object lastLoadoutScreen;

    static {
        Arrays.fill(equipment, ItemStack.EMPTY);
        Arrays.fill(armour, ItemStack.EMPTY);
    }

    private SkyblockHud() {
    }

    /** Equipment item for slot 0=necklace, 1=cloak, 2=belt, 3=gloves (never null). */
    public static ItemStack equipment(int i) {
        ItemStack s = (i >= 0 && i < equipment.length) ? equipment[i] : null;
        return s == null ? ItemStack.EMPTY : s;
    }

    /**
     * Armour read from the Loadouts menu: 0=helmet, 1=chestplate, 2=leggings, 3=boots.
     *
     * <p>For the case where the live inventory has nothing to show - see {@link #armour}.
     */
    public static ItemStack armour(int i) {
        ItemStack s = (i >= 0 && i < armour.length) ? armour[i] : null;
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
            // With no item there may still be an answer: Autopet announced a swap to a pet this
            // session has never seen in a menu, so the name and level came from the message.
            return petInfoSource == null ? petInfoCache : null;
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

        int colour = nameColour(stack);

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
        return new PetInfo(name, colour, level, xp);
    }

    /**
     * The ARGB colour SkyBlock draws an item's name in - which is its rarity colour. Taken from the
     * <i>last</i> coloured run of the name, because prefixes such as a pet's grey "[Lvl 100]" come
     * first and would otherwise win. Falls back to white.
     */
    private static int nameColour(ItemStack stack) {
        int[] colour = {0xFFFFFFFF};
        stack.getHoverName().visit((style, text) -> {
            TextColor c = style.getColor();
            if (c != null && !strip(text).isBlank()) {
                colour[0] = 0xFF000000 | c.getValue();
            }
            return Optional.empty();
        }, Style.EMPTY);
        return colour[0];
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

    /**
     * Item stacks are stored as JSON so the equipment and pet survive a restart - they only exist
     * inside server-side menus, so otherwise the HUD is blank until those menus are opened again.
     * Encoding needs the server's registries, so this can only run once connected.
     */
    private static RegistryOps<JsonElement> ops(Minecraft mc) {
        if (mc.getConnection() == null) {
            return null;
        }
        return RegistryOps.create(JsonOps.INSTANCE, mc.getConnection().registryAccess());
    }

    private static String encode(Minecraft mc, ItemStack stack) {
        RegistryOps<JsonElement> ops = ops(mc);
        if (ops == null || stack == null || stack.isEmpty()) {
            return null;
        }
        return ItemStack.CODEC.encodeStart(ops, stack).result().map(JsonElement::toString).orElse(null);
    }

    private static ItemStack decode(Minecraft mc, String json) {
        RegistryOps<JsonElement> ops = ops(mc);
        if (ops == null || json == null || json.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Write the current cache to the config so it outlives this session.
     *
     * <p>Only when something actually changed. The scans run every tick a matching menu is open, and
     * this used to write the file on each of them - twenty encodes and a disk write per second for
     * as long as you stood in your wardrobe, to save what was already saved.
     */
    private static void persist(Minecraft mc) {
        String[] saved = ConfigManager.get().savedEquipment;
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            String encoded = encode(mc, equipment[i]);
            if (!java.util.Objects.equals(saved[i], encoded)) {
                saved[i] = encoded;
                changed = true;
            }
        }
        String petJson = encode(mc, pet);
        if (!java.util.Objects.equals(ConfigManager.get().savedPet, petJson)) {
            ConfigManager.get().savedPet = petJson;
            changed = true;
        }
        if (changed) {
            ConfigManager.save();
        }
    }

    /** Load what the last session saw, so the HUD shows something before any menu is opened. */
    private static void restore(Minecraft mc) {
        if (restored || mc.getConnection() == null) {
            return;
        }
        restored = true;
        String[] saved = ConfigManager.get().savedEquipment;
        if (saved != null) {
            for (int i = 0; i < 4 && i < saved.length; i++) {
                ItemStack st = decode(mc, saved[i]);
                if (!st.isEmpty()) {
                    equipment[i] = st;
                }
            }
        }
        ItemStack p = decode(mc, ConfigManager.get().savedPet);
        if (!p.isEmpty()) {
            pet = p;
        }
    }

    /** Called each client tick: if a matching SkyBlock menu is open, refresh the cache from it. */
    public static void tick(Minecraft mc) {
        restore(mc);
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            lastDumped = null;
            // The menu is gone, so the next one to open is a fresh look rather than a continuation.
            lastLoadoutScreen = null;
            panelSignature = null;
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

        // A loadout menu holds both, so it is offered to both scans rather than being a third case.
        boolean loadout = title.contains(LOADOUT_TITLE);

        // A loadout menu holds one icon per loadout rather than the gear itself, so it gets its own
        // scan - the equipment and pet scans would find nothing in it to read.
        if (loadout) {
            // Opening the menu is itself a request to look again, so the fingerprint is dropped and
            // the first scan of a newly opened menu always applies. Without this, opening /loadout
            // after an Autopet swap would compare against what was on screen last time and decide
            // nothing had happened - which is exactly the case the menu is being opened for.
            if (screen != lastLoadoutScreen) {
                lastLoadoutScreen = screen;
                panelSignature = null;
            }
            scanLoadouts(slots, limit);
            persist(mc);
            return;
        }
        lastLoadoutScreen = null;
        if (title.contains(EQUIPMENT_WORD)) {
            // Two different menus say "equipment". The wardrobe lists every set you have saved, so
            // a piece in it is only yours if it is marked as the equipped one - that is what was
            // showing pieces from sets you are not wearing. The plain equipment menu shows what you
            // have on and nothing else, so there is nothing to pick out.
            scanEquipment(slots, limit, !title.contains(SETS_WORD));
            persist(mc);
        }
        if (title.contains(PETS_TITLE)) {
            scanPets(slots, limit);
            persist(mc);
        }
    }

    /**
     * Reads what the Loadouts menu says you have on.
     *
     * <p>The menu is in two halves. Down the left is a panel of what is <b>equipped right now</b>:
     * a column of trees and stones (Heart of the Forest, Heart of the Mountain, power stone, tuning
     * template), then your four equipment pieces, then your four armour pieces, with the active pet
     * beside the chestplate. To the right of that is a grid of saved loadout presets, which are only
     * icons - each names its contents in its tooltip and holds none of them.
     *
     * <p>So the panel is read first and the presets are the fallback. That is what makes a swap show
     * up at once: the menu does not close when you change a loadout, so the panel updates under an
     * open screen, while the tooltip route could only ever draw a piece this session had already
     * seen somewhere else. It also removes the guess about <i>which</i> preset is worn - the panel
     * states it rather than it being inferred from a tooltip that lacks the offer to equip it.
     */
    private static void scanLoadouts(List<Slot> slots, int limit) {
        if (scanEquippedPanel(slots, limit)) {
            return;
        }
        List<String> equipped = null;
        int count = 0;
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            List<String> lore = loreOf(stack);
            boolean isLoadout = false;
            boolean offersEquip = false;
            for (String raw : lore) {
                String line = strip(raw);
                if (LOADOUT_PET.matcher(line).find() || LOADOUT_PIECE.matcher(line).find()) {
                    isLoadout = true;
                }
                if (LOADOUT_EQUIP_PROMPT.matcher(line).find()) {
                    offersEquip = true;
                }
            }
            if (isLoadout && !offersEquip) {
                equipped = lore;
                count++;
            }
        }
        // Exactly one, or none of them. Only one loadout can be worn, so two candidates means the
        // test has stopped meaning what it is assumed to mean - and applying either would swap the
        // whole HUD, gear and pet, to something that is not on your body.
        if (count == 1) {
            applyLoadout(equipped);
        }
    }

    /**
     * Reads the equipped panel down the left of the Loadouts menu.
     *
     * <p>Anchored on the equipment rather than on slot numbers. The four pieces are found by their
     * own category lines, which puts them in one column and on four consecutive rows; armour is the
     * column to the right of that one and the pet is the column after, beside the chestplate. So the
     * whole panel is located from one thing it states about itself, and a menu that gains a border
     * row or moves down a slot is still read correctly.
     *
     * <p>The presets cannot be mistaken for the panel: an icon's bottom lore line is its price or
     * its creation date, so it has no equipment category at all. If one ever did, the two columns
     * would disagree and this gives up rather than mixing them - the tooltip route still answers.
     *
     * @return whether the panel was found, i.e. whether the caller can stop here
     */
    private static boolean scanEquippedPanel(List<Slot> slots, int limit) {
        ItemStack[] set = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        int[] rows = {-1, -1, -1, -1};
        int col = -1;
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            int cat = categoryOf(stack);
            if (cat < 0) {
                continue;
            }
            if (col >= 0 && i % COLS != col) {
                return false;   // two columns claim a piece; this is not the panel
            }
            col = i % COLS;
            set[cat] = stack;
            rows[cat] = i / COLS;
            seenEquipment.put(strip(stack.getHoverName().getString()).trim()
                    .toLowerCase(Locale.ROOT), stack.copy());
        }
        if (col < 0) {
            return false;
        }

        // The row the panel starts on, from whichever piece was found: the four sit in category
        // order, so a piece's own category is how far down the panel it is.
        int top = -1;
        for (int c = 0; c < 4; c++) {
            if (rows[c] >= 0) {
                top = rows[c] - c;
                break;
            }
        }

        // Nothing has moved since the last look, so there is nothing to write. The menu is read
        // every tick it is open, and nineteen ticks in twenty the answer is the same one.
        if (!panelChanged(slots, limit, set, top, col)) {
            return true;
        }

        System.arraycopy(set, 0, equipment, 0, 4);
        if (top >= 0) {
            scanPanelArmour(slots, limit, top, col + 1);
            scanPanelPet(slots, limit, top, col + 2);
        }
        return true;
    }

    /**
     * A fingerprint of the equipped panel as it was last applied, or null when there is none.
     *
     * <p>Reset when the loadout menu is opened, so opening it always counts as a change and always
     * produces a fresh read - which is the point: reopening the menu is how you ask the mod to look
     * again, and it should not be answered from a cache.
     */
    private static String panelSignature;

    /**
     * Whether the panel says something different from what is already on the HUD.
     *
     * <p>This is what makes a loadout swap land: the menu does not close when you switch, so the
     * only signal that anything happened is its contents changing under you. Comparing them turns
     * that into an event, and everything downstream - including clearing a pet that the new loadout
     * does not have - hangs off it.
     *
     * <p>Built from the item names rather than from the stacks: the server sends new stack objects
     * for slots that did not actually change, so identity would report a swap every time the menu
     * refreshed, and the pet clear below would then fire against a panel that is still filling in.
     */
    private static boolean panelChanged(List<Slot> slots, int limit, ItemStack[] set,
                                        int top, int col) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack piece : set) {
            sb.append(nameOf(piece)).append('|');
        }
        if (top >= 0) {
            for (int c = 0; c < 4; c++) {
                sb.append(nameOf(at(slots, limit, (top + c) * COLS + col + 1))).append('|');
            }
            sb.append(nameOf(at(slots, limit, (top + 1) * COLS + col + 2)));
        }
        String signature = sb.toString();
        if (signature.equals(panelSignature)) {
            return false;
        }
        panelSignature = signature;
        return true;
    }

    /** The stack at a slot index, or empty when the index is outside the menu. */
    private static ItemStack at(List<Slot> slots, int limit, int i) {
        return i >= 0 && i < limit ? slots.get(i).getItem() : ItemStack.EMPTY;
    }

    private static String nameOf(ItemStack stack) {
        return stack.isEmpty() ? "" : strip(stack.getHoverName().getString()).trim();
    }

    /**
     * The armour column of the panel, one piece per row beside the equipment.
     *
     * <p>Each slot still has to look like the armour piece its position claims - the column beyond
     * the equipment is only armour in the layout as it stands today, and a wrong piece drawn
     * confidently is worse than an empty slot.
     */
    private static void scanPanelArmour(List<Slot> slots, int limit, int top, int col) {
        if (col >= COLS) {
            return;
        }
        for (int c = 0; c < 4; c++) {
            int i = (top + c) * COLS + col;
            ItemStack stack = i >= 0 && i < limit ? slots.get(i).getItem() : ItemStack.EMPTY;
            armour[c] = armourCategoryOf(stack) == c ? stack : ItemStack.EMPTY;
        }
    }

    /**
     * The active pet, which sits beside the chestplate - the panel's second row.
     *
     * <p><b>An empty pet slot is an answer, not a missing one.</b> This used to return early when
     * the slot held nothing, which meant switching to a loadout with no pet left the previous pet
     * sitting on the HUD indefinitely: the panel said "none" and the HUD went on showing the last
     * one it had happened to read. Since this only runs once the panel has been located <i>and</i>
     * its contents have actually changed, an empty slot here is the menu stating that no pet is out,
     * and it is now believed.
     */
    private static void scanPanelPet(List<Slot> slots, int limit, int top, int col) {
        if (col >= COLS) {
            return;
        }
        int i = (top + 1) * COLS + col;
        if (i < 0 || i >= limit) {
            return;
        }
        ItemStack stack = slots.get(i).getItem();
        boolean isPet = !stack.isEmpty()
                && LEVEL.matcher(strip(stack.getHoverName().getString())).find();
        if (isPet) {
            seenPets.put(petKey(stack), stack.copy());
        }
        pet = isPet ? stack : ItemStack.EMPTY;
        petInfoSource = null;   // force a re-parse against the new stack
        petInfoCache = null;
    }

    /** @return armour category index (0=helmet .. 3=boots), or -1 if this isn't a piece of armour. */
    private static int armourCategoryOf(ItemStack stack) {
        String last = lastLoreLine(stack);
        for (int c = 0; c < ARMOUR_CATEGORIES.length; c++) {
            if (last.endsWith(ARMOUR_CATEGORIES[c])) {
                return c;
            }
        }
        return -1;
    }

    /**
     * Fills the equipment and pet from a loadout's tooltip, as far as the names can be resolved.
     *
     * <p>The fallback for when the equipped panel could not be read - see {@link #scanLoadouts}.
     */
    private static void applyLoadout(List<String> lore) {
        ItemStack[] set = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        boolean anyPiece = false;
        for (String raw : lore) {
            String line = strip(raw).trim();

            Matcher piece = LOADOUT_PIECE.matcher(line);
            if (piece.matches()) {
                int cat = loadoutCategory(piece.group(1));
                ItemStack known = seenEquipment.get(piece.group(2).trim().toLowerCase(Locale.ROOT));
                if (cat >= 0 && known != null) {
                    set[cat] = known;
                    anyPiece = true;
                }
                continue;
            }

            Matcher p = LOADOUT_PET.matcher(line);
            if (p.matches()) {
                String name = p.group(2).trim();
                ItemStack known = seenPets.get(name.toLowerCase(Locale.ROOT));
                petInfoSource = null;
                if (known != null) {
                    pet = known;
                    petInfoCache = null;
                } else {
                    // Named but never seen, so there is no model - the card shows the name and
                    // level on their own rather than the pet you had before.
                    pet = ItemStack.EMPTY;
                    petInfoCache = new PetInfo(name, 0xFFFFFFFF, parseInt(p.group(1)), null);
                }
            }
        }
        if (anyPiece) {
            System.arraycopy(set, 0, equipment, 0, 4);
        }
    }

    /** Which of the four equipment slots a loadout's label names. */
    private static int loadoutCategory(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        if (l.startsWith("necklace")) {
            return 0;
        }
        if (l.startsWith("cloak")) {
            return 1;
        }
        if (l.startsWith("belt")) {
            return 2;
        }
        // "Gloves/Bracelet" - SkyBlock renamed the slot and the tooltip carries both words.
        return l.contains("glove") || l.contains("bracelet") ? 3 : -1;
    }

    /**
     * Notices Autopet swapping your pet, from the message it announces it with.
     *
     * <p>The pet menus are the only place the item itself exists, so a swap that happens while you
     * are mining is invisible to a scan - the HUD would go on showing the pet you had when you last
     * opened the menu, which is exactly when it is most wrong. The message names the pet, and every
     * pet seen in a menu this session is remembered by name, so the right item is usually already
     * in hand; when it is not, the name and level from the message are shown on their own.
     *
     * <p>Called from the chat pipeline. Returns true when the line was an Autopet announcement.
     */
    public static boolean onChat(String raw) {
        String line = strip(raw);
        if (DESPAWN.matcher(line).find()) {
            pet = ItemStack.EMPTY;
            petInfoSource = null;
            petInfoCache = null;
            return true;
        }
        Matcher m = AUTOPET.matcher(line);
        if (!m.find()) {
            m = SUMMON.matcher(line);
            if (!m.find()) {
                return false;
            }
        }
        String name = m.group("pet").trim();
        ItemStack known = seenPets.get(name.toLowerCase(Locale.ROOT));
        if (known != null && !known.isEmpty()) {
            pet = known;
            petInfoSource = null;   // force a re-parse against the new stack
            petInfoCache = null;
            return true;
        }
        // No item for it, so stand in with what the message itself said. The icon is dropped rather
        // than left showing the previous pet, which would be a confident wrong answer.
        pet = ItemStack.EMPTY;
        petInfoSource = null;
        petInfoCache = new PetInfo(name, 0xFFFFFFFF, parseInt(m.group("level")), null);
        return true;
    }

    /** -1 for "not stated", which a summon message now legitimately is. */
    private static int parseInt(String s) {
        if (s == null) {
            return -1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
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
     * Reads the equipment out of whichever menu is open.
     *
     * <p>Two menus say "equipment" and they need opposite treatment. The plain one shows what is on
     * your body, so everything in it is yours. The wardrobe is paged and lists every set you have
     * saved, so it needs {@link #scanEquippedColumn} to find the one you are wearing - taking
     * whatever is on the page gives four pieces from four different sets.
     */
    /**
     * @param worn true when this menu shows only the equipment you have on, so every piece in it is
     *             yours; false for the wardrobe, where only the marked set is
     */
    private static void scanEquipment(List<Slot> slots, int limit, boolean worn) {
        ItemStack[] any = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
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
            // Remembered by name whether or not it is worn: the loadout menu names its pieces
            // instead of holding them, so this is the only place their models can come from.
            seenEquipment.put(strip(stack.getHoverName().getString()).trim()
                    .toLowerCase(Locale.ROOT), stack.copy());
        }
        if (worn) {
            // Everything here is on your body, so there is nothing to pick out and no page to be
            // on the wrong one of.
            System.arraycopy(any, 0, equipment, 0, 4);
            return;
        }
        // The wardrobe proper: one saved set per column, and a marker row saying which is worn.
        if (scanEquippedColumn(slots, limit)) {
            return;
        }
        // Nothing else is tried. Searching the page for items whose lore mentions "equipped" is
        // what produced a set of four pieces that were never worn together, and the wardrobe is
        // paged - so a page without your set is a page of equipment you are not wearing. Whatever
        // was last read correctly stays until an equipped column is actually seen.
    }

    /**
     * The equipped set, read from the wardrobe's own layout.
     *
     * <p>The menu is a grid: each <b>column</b> is one saved set, its four pieces stacked down it,
     * and below them a row of markers reading {@code Slot 1: Equipped} / {@code Empty} / {@code
     * Ready}. So the set you are wearing is the column whose marker says Equipped - which is a fact
     * the menu states outright, rather than something to infer from an item's lore.
     *
     * <p>This is what was wrong before: pieces were picked by searching every slot for the word
     * "equipped", which matches items across several different sets, so the HUD showed four pieces
     * that were never worn together.
     *
     * @return whether an equipped column was found and read
     */
    private static boolean scanEquippedColumn(List<Slot> slots, int limit) {
        int marker = -1;
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (!stack.isEmpty() && "equipped".equals(slotState(stack))) {
                marker = i;
                break;
            }
        }
        int row = marker / COLS;
        if (marker < 0 || row == 0) {
            return false;   // no marker, or nothing above it to read
        }

        ItemStack[] set = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        int col = marker % COLS;
        boolean any = false;
        for (int r = 0; r < row && r < 4; r++) {
            ItemStack stack = slots.get(r * COLS + col).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            // The piece's own category when it declares one; otherwise its position in the column,
            // which the wardrobe lays out in the same order the HUD draws.
            int cat = categoryOf(stack);
            set[cat >= 0 ? cat : r] = stack;
            any = true;
        }
        if (!any) {
            return false;
        }
        System.arraycopy(set, 0, equipment, 0, 4);
        return true;
    }

    /** The state word from a {@code Slot n: ...} marker, lower case, or null if this is not one. */
    private static String slotState(ItemStack stack) {
        Matcher m = SLOT_STATE.matcher(strip(stack.getHoverName().getString()));
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }
        for (String line : loreOf(stack)) {
            m = SLOT_STATE.matcher(strip(line));
            if (m.find()) {
                return m.group(1).toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** The pet's name without its {@code [Lvl n]} prefix, as Autopet's message spells it. */
    private static String petKey(ItemStack stack) {
        String name = strip(stack.getHoverName().getString());
        return LEVEL.matcher(name).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    private static void scanPets(List<Slot> slots, int limit) {
        // Remember every pet on the page first, whether or not it is the active one - an Autopet
        // swap later has no menu to read from, so this is the only chance to learn the item.
        for (int i = 0; i < limit; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (!stack.isEmpty() && LEVEL.matcher(stack.getHoverName().getString()).find()) {
                seenPets.put(petKey(stack), stack.copy());
            }
        }
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
        String last = lastLoreLine(stack);
        for (int c = 0; c < CATEGORIES.length; c++) {
            for (String word : CATEGORIES[c]) {
                if (last.endsWith(word)) {
                    return c;
                }
            }
        }
        return -1;
    }

    /**
     * The item's rarity line, upper-cased - its last non-blank lore line, e.g. "LEGENDARY NECKLACE".
     *
     * <p>Empty for anything without lore, which is how a loadout preset icon fails both category
     * tests: its bottom line is a price or a date, not a rarity.
     */
    private static String lastLoreLine(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        List<String> lore = loreOf(stack);
        for (int i = lore.size() - 1; i >= 0; i--) {
            String l = lore.get(i).trim();
            if (!l.isEmpty()) {
                return l.toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    /** True when any lore line contains {@code needle} (lower-case, colour codes already stripped). */
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
        Arrays.fill(armour, ItemStack.EMPTY);
        pet = ItemStack.EMPTY;
        petInfoSource = null;
        petInfoCache = null;
        restored = false;   // the saved copy stays; it is restored again on the next join
        // A fingerprint describes a menu on a server we have just left; keeping it would let the
        // first loadout menu on the next one be compared against the last one on this.
        lastLoadoutScreen = null;
        panelSignature = null;
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
