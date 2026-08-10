package dev.diego.diegoaddons.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ModFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the storage overlay knows: your ender chest pages and your backpacks, as they were the
 * last time a menu holding them was open.
 *
 * <p>SkyBlock hands the client an item only while the menu containing it is on screen, so the
 * overlay cannot ask for your storage - it can only remember what it has been shown. What is here is
 * therefore a <b>cache with a date on it</b>: every page carries when it was last seen, and a page
 * nobody has opened this profile is listed as unscanned rather than as empty. Confusing "I have not
 * looked" with "there is nothing in it" is the one way a storage overlay actively lies to you.
 *
 * <p>The cache is per profile, in {@code config/diegoaddons/storage/<player>-<profile>.json}: bank
 * and backpacks are profile-scoped in SkyBlock, so one file per profile is the only shape that
 * cannot show you another profile's items. The stacks are stored through {@code ItemStack.CODEC},
 * the same route the pet and equipment cache uses, so enchants, skull textures and SkyBlock's own
 * lore all survive a restart rather than being flattened into a name and a count.
 */
public final class StorageData {

    /** Which of the two storages a page belongs to. */
    public enum Kind {
        ENDER_CHEST("Ender Chest"),
        BACKPACK("Backpack");

        public final String display;

        Kind(String display) {
            this.display = display;
        }
    }

    /** One ender chest page or one backpack, with whatever was last read out of it. */
    public static final class Page {
        public final Kind kind;
        /** 1-based: ender chest page number, or the backpack's slot in the storage menu. */
        public final int index;
        /** What the menu called it, e.g. "Large Backpack". Never blank. */
        public String name;
        /** The backpack item itself, for the icon beside its name. Empty for an ender chest page. */
        public ItemStack icon = ItemStack.EMPTY;
        /** The container's own slots, player inventory excluded. Empty array until first scanned. */
        public ItemStack[] items = new ItemStack[0];
        /**
         * How many leading slots are the menu's navigation row rather than storage.
         *
         * <p>Kept as an offset instead of being trimmed out of {@link #items}, because a click has
         * to go back to the menu as the slot number the <i>menu</i> uses. Trimming would make every
         * index in the overlay a lie that had to be undone at exactly one place, correctly.
         */
        public int offset;
        /** When the contents were last read, epoch millis. 0 while unscanned. */
        public long seen;

        /**
         * Per-slot search text and rarity colour, derived once rather than per frame.
         *
         * <p>Both were being recomputed for every visible slot on every frame - stripping the colour
         * codes out of a full SkyBlock lore, sixty times a second, for a thousand items. Neither can
         * change without the page being read again, which is what clears them.
         */
        private String[] search;
        private int[] rarity;

        /**
         * The menu's own stack objects as of the last capture, for telling a changed page from an
         * unchanged one. Never read as items - only compared by identity - and never persisted.
         */
        ItemStack[] source;

        Page(Kind kind, int index, String name) {
            this.kind = kind;
            this.index = index;
            this.name = name;
        }

        /** Whether this page's contents have ever been read, as opposed to merely being known to exist. */
        public boolean scanned() {
            return seen > 0;
        }

        /** Storage slots, the navigation row excluded. */
        public int size() {
            return Math.max(0, items.length - offset);
        }

        /** The stack in storage slot {@code i}, never null. */
        public ItemStack item(int i) {
            int at = i + offset;
            return at >= 0 && at < items.length && items[at] != null ? items[at] : ItemStack.EMPTY;
        }

        /** The menu slot number storage slot {@code i} actually is, for sending a click. */
        public int menuSlot(int i) {
            return i + offset;
        }

        public int filled() {
            int n = 0;
            for (int i = 0; i < size(); i++) {
                if (!item(i).isEmpty()) {
                    n++;
                }
            }
            return n;
        }

        public int rows() {
            return Math.max(1, (size() + COLUMNS - 1) / COLUMNS);
        }

        /** Lower-cased name and lore of storage slot {@code i}, to match a query against. */
        public String searchText(int i) {
            derive();
            return i >= 0 && i < search.length ? search[i] : "";
        }

        /** The rarity colour of storage slot {@code i}, or 0 when it has none. */
        public int rarity(int i) {
            derive();
            return i >= 0 && i < rarity.length ? rarity[i] : 0;
        }

        private void derive() {
            if (search != null) {
                return;
            }
            int n = size();
            search = new String[n];
            rarity = new int[n];
            for (int i = 0; i < n; i++) {
                ItemStack stack = item(i);
                if (stack.isEmpty()) {
                    search[i] = "";
                    continue;
                }
                StringBuilder text = new StringBuilder(
                        LegacyText.strip(stack.getHoverName().getString()));
                var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
                if (lore != null) {
                    for (var line : lore.lines()) {
                        text.append('\n').append(LegacyText.strip(line.getString()));
                    }
                }
                search[i] = text.toString().toLowerCase(Locale.ROOT);
                rarity[i] = ItemRarity.color(stack);
            }
        }

        /** Called whenever the contents change: the derived arrays describe the old ones. */
        void invalidate() {
            search = null;
            rarity = null;
        }
    }

    /** Every SkyBlock container is nine wide, which is what makes a page a grid rather than a list. */
    public static final int COLUMNS = 9;

    private static final Map<String, Page> PAGES = new LinkedHashMap<>();

    /**
     * {@link #pages()}'s answer, held until a page is added.
     *
     * <p>The overlay asks for this several times a frame - to draw, to hit-test, to size itself -
     * and it was building and sorting a fresh list every time. The set only changes when a page is
     * first seen, which is a handful of times per session.
     */
    private static List<Page> sorted;

    /** {@code <player>-<profile>}, or null while we do not yet know which profile this is. */
    private static String profileKey;

    /** True once this profile's file has been read (or found not to exist). */
    private static boolean loaded;

    /** Set by every write, cleared by a save. Saving is not free and a menu is scanned every tick. */
    private static boolean dirty;

    private static long lastSave;

    /** Milliseconds between writes while pages keep arriving. */
    private static final long SAVE_INTERVAL = 3_000L;

    private StorageData() {
    }

    // ---------------------------------------------------------------- reading

    /**
     * Every known page: the ender chest in page order, then the backpacks in slot order.
     *
     * <p>A fresh list each call - the sidebar and the search both iterate it while the scanner may
     * be adding to the map on the same tick.
     */
    public static List<Page> pages() {
        if (sorted == null) {
            List<Page> out = new ArrayList<>(PAGES.values());
            out.sort(Comparator.<Page, Integer>comparing(p -> p.kind.ordinal())
                    .thenComparingInt(p -> p.index));
            sorted = List.copyOf(out);
        }
        return sorted;
    }

    public static boolean isEmpty() {
        return PAGES.isEmpty();
    }

    /**
     * The page for this kind and index, created empty if it is not known yet.
     *
     * <p>Creating rather than returning null is what puts the page you are standing in on the sheet
     * the first time you open it, before anything has been read out of it.
     */
    public static Page page(Kind kind, int index) {
        return ensure(kind, index, null);
    }

    /** The profile these pages belong to, for the overlay's header. Blank until one is known. */
    public static String profile() {
        return profileKey == null ? "" : profileKey;
    }

    /** Total stacks held across every scanned page. Counted once per change, not per frame. */
    public static int itemCount() {
        if (itemCount < 0) {
            int n = 0;
            for (Page p : PAGES.values()) {
                n += p.filled();
            }
            itemCount = n;
        }
        return itemCount;
    }

    /** Negative when it needs counting again. */
    private static int itemCount = -1;

    // ---------------------------------------------------------------- writing

    /**
     * Records that a page exists and what it is called, without claiming to know its contents.
     *
     * <p>This is what the storage menu itself tells us: it holds one icon per page, so it can name
     * every backpack you own long before any of them has been opened.
     */
    public static Page note(Kind kind, int index, String name, ItemStack icon) {
        Page page = ensure(kind, index, name);
        if (name != null && !name.isBlank() && !name.equals(page.name)) {
            page.name = name;
            dirty = true;
        }
        if (icon != null && !icon.isEmpty()) {
            page.icon = icon.copy();
            dirty = true;
        }
        return page;
    }

    /**
     * Stores what a page holds, read from the open menu.
     *
     * <p>The stacks are copied: the ones in the menu belong to it and the server rewrites them in
     * place, so keeping the references would leave the cache changing under itself and emptying out
     * the moment the menu closed.
     */
    public static void capture(Kind kind, int index, String name, List<ItemStack> items, int offset) {
        Page page = ensure(kind, index, name);
        if (name != null && !name.isBlank()) {
            page.name = name;
        }
        int off = Math.clamp(offset, 0, items.size());
        // The scan runs on every tick a page is open, and a page you are standing in front of is
        // almost never changing. The menu hands out the same stack objects until the server sends
        // a new one, so identity is enough to tell "nothing happened" from "something did" - and
        // that skips the copy, the derived text and the save for nineteen ticks out of twenty.
        if (page.offset == off && unchanged(page, items)) {
            return;
        }
        page.offset = off;
        ItemStack[] copy = new ItemStack[items.size()];
        ItemStack[] refs = new ItemStack[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            refs[i] = s;
            copy[i] = s == null || s.isEmpty() ? ItemStack.EMPTY : s.copy();
        }
        page.items = copy;
        page.source = refs;
        page.invalidate();
        page.seen = System.currentTimeMillis();
        itemCount = -1;
        dirty = true;
    }

    /** Whether every slot still holds the very stack object it held at the last capture. */
    private static boolean unchanged(Page page, List<ItemStack> items) {
        ItemStack[] refs = page.source;
        if (refs == null || refs.length != items.size()) {
            return false;
        }
        for (int i = 0; i < refs.length; i++) {
            if (refs[i] != items.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static Page ensure(Kind kind, int index, String name) {
        return PAGES.computeIfAbsent(key(kind, index), k -> {
            dirty = true;
            sorted = null;
            return new Page(kind, index, fallbackName(kind, index, name));
        });
    }

    private static String fallbackName(Kind kind, int index, String name) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return kind == Kind.ENDER_CHEST ? "Ender Chest Page " + index : "Backpack " + index;
    }

    private static String key(Kind kind, int index) {
        return kind.name() + ":" + index;
    }

    /** Forgets everything, on disk as well as in memory. Behind the module's own button. */
    public static void clear() {
        PAGES.clear();
        sorted = null;
        itemCount = -1;
        dirty = false;
        Path file = file();
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not delete {}", file, e);
            }
        }
    }

    /** Leaving the server: write what has not been written, then forget the profile. */
    public static void reset() {
        save(Minecraft.getInstance());
        PAGES.clear();
        sorted = null;
        itemCount = -1;
        profileKey = null;
        loaded = false;
        dirty = false;
    }

    // ---------------------------------------------------------------- profile and files

    /**
     * Keeps the cache pointed at the profile you are actually on, and writes out what has changed.
     *
     * <p>Called every tick by the module. The profile is read from the tab list, which means it is
     * unknown for the first seconds after joining and changes the moment you swap profile - so a
     * change is treated as "different storage entirely": what is held is written under the old key
     * before anything is read under the new one.
     */
    public static void tick(Minecraft mc) {
        String key = profileKey(mc);
        if (key != null && !key.equals(profileKey)) {
            // Swapped profile (or just learned which one this is): the pages in memory belong to the
            // previous key, so they go to its file rather than into this one's.
            if (profileKey != null) {
                save(mc);
                PAGES.clear();
                sorted = null;
            }
            profileKey = key;
            loaded = false;
        }
        if (profileKey != null && !loaded) {
            load(mc);
        }
        if (dirty && System.currentTimeMillis() - lastSave > SAVE_INTERVAL) {
            save(mc);
        }
    }

    /**
     * {@code <player>-<profile>}, or null until both halves are known.
     *
     * <p>Both halves, because one instance can be logged into more than one account and one account
     * has more than one profile; either alone would show somebody else's ender chest.
     */
    private static String profileKey(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            return null;
        }
        String profile = SkyblockLocation.profile(mc);
        if (profile.isEmpty()) {
            return null;
        }
        // The account's own name rather than the entity's: on Hypixel the latter carries a rank
        // prefix, which would key the file on a cosmetic that can change.
        String account = mc.getUser() == null ? "player" : mc.getUser().getName();
        return sanitise(account + "-" + profile);
    }

    /** A file name that survives every filesystem: letters, digits, dash and underscore only. */
    private static String sanitise(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            out.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        }
        return out.toString();
    }

    private static Path file() {
        return profileKey == null ? null : ModFiles.folder("storage").resolve(profileKey + ".json");
    }

    /**
     * The last registry view this session had, kept after the connection goes.
     *
     * <p>{@link #reset()} runs <b>on</b> the disconnect, by which point there is no connection left
     * to ask for a registry - and without one nothing can be encoded, so the last pages read would
     * be dropped on the way out. The registries are plain data and outlive the connection that
     * carried them, so holding the last one is what makes that final write possible.
     */
    private static RegistryOps<JsonElement> lastOps;

    private static RegistryOps<JsonElement> ops(Minecraft mc) {
        if (mc != null && mc.getConnection() != null) {
            lastOps = RegistryOps.create(JsonOps.INSTANCE, mc.getConnection().registryAccess());
        }
        return lastOps;
    }

    private static void load(Minecraft mc) {
        RegistryOps<JsonElement> ops = ops(mc);
        Path file = file();
        if (ops == null || file == null) {
            return;
        }
        // Marked read whatever happens below: a file that cannot be parsed is not going to parse on
        // the next tick either, and retrying twenty times a second would only fill the log.
        loaded = true;
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonArray pages = root.getAsJsonArray("pages");
            if (pages == null) {
                return;
            }
            for (JsonElement element : pages) {
                readPage(ops, element.getAsJsonObject());
            }
            // Building the pages set the flag on every one of them; what was just read is by
            // definition already on disk, so the first save has nothing to do.
            dirty = false;
            DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] storage: read {} page(s) for {}",
                    PAGES.size(), profileKey);
        } catch (IOException | RuntimeException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not read {} - the overlay starts "
                    + "empty and fills again as menus are opened", file, e);
        }
    }

    private static void readPage(RegistryOps<JsonElement> ops, JsonObject json) {
        Kind kind = Kind.valueOf(json.get("kind").getAsString());
        int index = json.get("index").getAsInt();
        Page page = ensure(kind, index, json.has("name") ? json.get("name").getAsString() : null);
        page.seen = json.has("seen") ? json.get("seen").getAsLong() : 0L;
        page.offset = json.has("offset") ? json.get("offset").getAsInt() : 0;
        if (json.has("icon") && !json.get("icon").isJsonNull()) {
            page.icon = decode(ops, json.get("icon"));
        }
        JsonArray items = json.getAsJsonArray("items");
        if (items == null) {
            return;
        }
        ItemStack[] out = new ItemStack[items.size()];
        for (int i = 0; i < items.size(); i++) {
            out[i] = decode(ops, items.get(i));
        }
        page.items = out;
        page.invalidate();
        itemCount = -1;
    }

    private static void save(Minecraft mc) {
        RegistryOps<JsonElement> ops = ops(mc);
        Path file = file();
        if (ops == null || file == null || !dirty) {
            return;
        }
        lastSave = System.currentTimeMillis();
        JsonArray pages = new JsonArray();
        for (Page page : pages()) {
            pages.add(writePage(ops, page));
        }
        JsonObject root = new JsonObject();
        root.addProperty("profile", profileKey);
        root.add("pages", pages);
        try {
            Files.writeString(file, root.toString());
            // Cleared only once it is actually on disk. Clearing first would turn a failed write
            // into a silent loss: the next save would see nothing to do.
            dirty = false;
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not write {}", file, e);
        }
    }

    private static JsonObject writePage(RegistryOps<JsonElement> ops, Page page) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", page.kind.name());
        json.addProperty("index", page.index);
        json.addProperty("name", page.name);
        json.addProperty("seen", page.seen);
        json.addProperty("offset", page.offset);
        json.add("icon", encode(ops, page.icon));
        JsonArray items = new JsonArray();
        for (ItemStack stack : page.items) {
            items.add(encode(ops, stack));
        }
        json.add("items", items);
        return json;
    }

    private static JsonElement encode(RegistryOps<JsonElement> ops, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        return ItemStack.CODEC.encodeStart(ops, stack).result().orElse(JsonNull.INSTANCE);
    }

    private static ItemStack decode(RegistryOps<JsonElement> ops, JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.CODEC.parse(ops, json).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            // One item the current version cannot rebuild costs that slot, not the page.
            return ItemStack.EMPTY;
        }
    }
}
