/*
 * Ported from Inventory Buttons (https://github.com/afranz29/Inventory-Buttons),
 * Copyright (C) 2026 Panda/afranz29, licensed under the LGPLv3 - itself a port of the inventory
 * buttons from NotEnoughUpdates (Moulberry and contributors). This file stays under the LGPLv3.
 */

package dev.diego.diegoaddons.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.InvButton;
import dev.diego.diegoaddons.config.ModFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The inventory buttons themselves: the list, the icons they can wear, and the saved layouts.
 *
 * <p>The buttons live in the mod's own config (see {@link dev.diego.diegoaddons.config.AddonConfig})
 * rather than in a file of their own, so they are backed up, exported and carried between instances
 * with everything else. <b>Profiles are files</b>, one JSON per layout under
 * {@code config/diegoaddons/invbuttons/} - a profile is a thing you swap between and hand to
 * someone, which is what a file is good at and what a nested list in the settings is not.
 *
 * <p>An icon is one string, {@code itemId}, and it is one of three things: a texture id for one of
 * the sixteen bundled icons, {@code skull:<texture hash>} for a player head, or an item id like
 * {@code minecraft:diamond_sword}. One field rather than three because that is the format the
 * upstream mod exports, and reading its clipboard payloads was worth more than a tidier schema.
 */
public final class InvButtons {

    /** The bundled icons, keyed by the words the editor's search matches against. */
    public static final Map<String, Identifier> CUSTOM_TEXTURES = new LinkedHashMap<>();

    static {
        registerCustom("baubles ring", "baubles");
        registerCustom("baubles gold ring", "baubles_gold");
        registerCustom("cross x", "cross");
        registerCustom("green check mark", "green_check");
        registerCustom("white check mark", "white_check");
        registerCustom("question mark help", "question");
        registerCustom("settings cog config", "settings");
        registerCustom("accessory ring", "accessory");
        registerCustom("accessory ring gold", "accessory_gold");
        registerCustom("armor chestplate", "armor");
        registerCustom("armor gold chestplate", "armor_gold");
        registerCustom("pet cat", "pet");
        registerCustom("pet cat gold", "pet_gold");
        registerCustom("skyblock menu", "skyblock_menu");
        registerCustom("recipe book", "recipe");
        registerCustom("search glass", "search");
    }

    private InvButtons() {
    }

    private static void registerCustom(String name, String file) {
        CUSTOM_TEXTURES.put(name, Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID,
                "textures/invbuttons/icons/" + file + ".png"));
    }

    // ------------------------------------------------------------------ the list

    public static List<InvButton> buttons() {
        return ConfigManager.get().invButtons;
    }

    public static void save() {
        ConfigManager.save();
    }

    // ------------------------------------------------------------------ icons

    /**
     * The bundled icon a button wears, or null if its icon is an item or a skull.
     *
     * <p>Matched on the texture id's full string because that is what the editor writes into
     * {@code itemId} when one of these is picked - the icon set is small enough that a scan costs
     * nothing and a second lookup table would only be another thing to keep in step.
     */
    public static Identifier customTexture(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        for (Identifier id : CUSTOM_TEXTURES.values()) {
            if (id.toString().equals(itemId)) {
                return id;
            }
        }
        return null;
    }

    /** The item a button's icon names, or {@link ItemStack#EMPTY} if it names nothing the game has. */
    public static ItemStack stackFor(InvButton button) {
        String itemId = button.itemId;
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (itemId.startsWith("skull:")) {
            return skull(itemId);
        }
        try {
            String key = itemId.contains(":")
                    ? itemId
                    : "minecraft:" + itemId.toLowerCase(Locale.ROOT);
            var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(key));
            return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * A player head wearing the skin at {@code skull:<hash>}.
     *
     * <p>The hash is the tail of a textures.minecraft.net URL, which is what every head site quotes
     * and what Hypixel's own item list carries. It is wrapped back into the base64 property the game
     * expects, so the head resolves through the ordinary skin path rather than needing a download of
     * our own.
     */
    public static ItemStack skull(String skullId) {
        try {
            String hash = skullId.substring("skull:".length());
            String json = "{\"textures\":{\"SKIN\":{\"url\":\""
                    + "http://textures.minecraft.net/texture/" + hash + "\"}}}";
            String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

            Multimap<String, Property> map = ArrayListMultimap.create();
            map.put("textures", new Property("textures", base64));
            // The UUID is derived from the hash rather than random, so the same skull is the same
            // profile every time - a new id each frame would defeat the skin cache.
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)),
                    "Skull", new PropertyMap(map));

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            return head;
        } catch (RuntimeException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] bad skull icon {}", skullId, e);
            return new ItemStack(Items.PLAYER_HEAD);
        }
    }

    // ------------------------------------------------------------------ profiles

    /** {@code config/diegoaddons/invbuttons/}, created on demand. */
    private static Path profilesDir() {
        return ModFiles.folder("invbuttons");
    }

    public static List<String> profileNames() {
        Path dir = profilesDir();
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> !Files.isDirectory(f))
                    .map(f -> f.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not list button profiles", e);
            return Collections.emptyList();
        }
    }

    /** Writes the current layout out under {@code name}, replacing a profile of that name. */
    public static void saveProfile(String name) {
        try {
            Files.writeString(profilesDir().resolve(sanitise(name) + ".json"),
                    new Gson().toJson(buttons()));
        } catch (IOException | RuntimeException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not save button profile {}", name, e);
        }
    }

    /** Replaces the current layout with a saved one. Does nothing if there is no such profile. */
    public static void loadProfile(String name) {
        Path file = profilesDir().resolve(sanitise(name) + ".json");
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<InvButton> loaded = new Gson().fromJson(Files.readString(file),
                    new TypeToken<List<InvButton>>() { }.getType());
            if (loaded != null) {
                ConfigManager.get().invButtons = new ArrayList<>(loaded);
                save();
            }
        } catch (IOException | RuntimeException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not load button profile {}", name, e);
        }
    }

    public static void deleteProfile(String name) {
        try {
            Files.deleteIfExists(profilesDir().resolve(sanitise(name) + ".json"));
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not delete button profile {}", name, e);
        }
    }

    /** A profile name is a file name, so it may not carry a path in it. */
    private static String sanitise(String name) {
        return name.replaceAll("[^A-Za-z0-9 _.-]", "_").trim();
    }

    // ------------------------------------------------------------------ clipboard

    /** The whole layout as one base64 blob, in the format the upstream mod reads. */
    public static String exportToClipboard() {
        return Base64.getEncoder().encodeToString(
                new Gson().toJson(buttons()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads a layout out of whatever is on the clipboard, and says whether it found one.
     *
     * <p>Deliberately forgiving, because the payloads in circulation are not one format: base64 or
     * raw JSON, a bare array or an object with the array under {@code buttons} or {@code layout},
     * a button's command under any of three names and its icon under any of four. Anything it
     * cannot make sense of leaves the current layout alone rather than half-replacing it.
     */
    public static boolean importFromClipboard(String clipboard) {
        if (clipboard == null || clipboard.isBlank()) {
            return false;
        }
        try {
            String text = clipboard.trim();
            try {
                text = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException notBase64) {
                // Already JSON, or something else entirely - the parse below decides which.
            }
            // Some exports carry a "NEUBUTTONS/" style prefix in front of the JSON.
            int brace = text.indexOf('{');
            int bracket = text.indexOf('[');
            int start = brace < 0 ? bracket : bracket < 0 ? brace : Math.min(brace, bracket);
            if (start > 0) {
                text = text.substring(start);
            }

            JsonElement root = JsonParser.parseString(text);
            JsonArray array = null;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                for (String key : new String[]{"buttons", "layout"}) {
                    if (obj.has(key) && obj.get(key).isJsonArray()) {
                        array = obj.getAsJsonArray(key);
                        break;
                    }
                }
            }
            if (array == null) {
                return false;
            }

            List<InvButton> loaded = new ArrayList<>();
            for (JsonElement element : array) {
                InvButton button = readButton(element);
                if (button != null) {
                    loaded.add(button);
                }
            }
            if (loaded.isEmpty()) {
                return false;
            }
            ConfigManager.get().invButtons = loaded;
            save();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static InvButton readButton(JsonElement element) {
        JsonObject obj = null;
        if (element.isJsonObject()) {
            obj = element.getAsJsonObject();
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            // A list of JSON strings, each holding a button - one of the shapes in the wild.
            try {
                JsonElement inner = JsonParser.parseString(element.getAsString());
                if (inner.isJsonObject()) {
                    obj = inner.getAsJsonObject();
                }
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (obj == null) {
            return null;
        }

        InvButton button = new InvButton();
        button.x = obj.has("x") ? obj.get("x").getAsInt() : 0;
        button.y = obj.has("y") ? obj.get("y").getAsInt() : 0;
        for (String key : new String[]{"command", "cmd", "action"}) {
            if (obj.has(key)) {
                button.command = obj.get(key).getAsString();
                break;
            }
        }
        JsonElement icon = null;
        for (String key : new String[]{"itemId", "item", "icon", "id"}) {
            if (obj.has(key)) {
                icon = obj.get(key);
                break;
            }
        }
        String itemId = "";
        if (icon != null && icon.isJsonPrimitive()) {
            itemId = icon.getAsString();
        } else if (icon != null && icon.isJsonObject()) {
            JsonObject iconObj = icon.getAsJsonObject();
            for (String key : new String[]{"id", "itemId"}) {
                if (iconObj.has(key)) {
                    itemId = iconObj.get(key).getAsString();
                    break;
                }
            }
        }
        button.itemId = normaliseItemId(itemId);
        if (obj.has("backgroundIndex")) {
            button.backgroundIndex = obj.get("backgroundIndex").getAsInt();
        }
        if (obj.has("anchorRight")) {
            button.anchorRight = obj.get("anchorRight").getAsBoolean();
        }
        if (obj.has("anchorBottom")) {
            button.anchorBottom = obj.get("anchorBottom").getAsBoolean();
        }
        return button;
    }

    /**
     * An imported icon name, in the ids this version of the game uses.
     *
     * <p>The layouts people share were written against 1.8 - that is where the feature comes from -
     * and a good half of the item names have changed since. Without this an imported layout comes
     * back as a page of question marks.
     */
    private static String normaliseItemId(String raw) {
        if (raw == null) {
            return "";
        }
        String id = raw.trim();
        if (id.isEmpty() || id.startsWith("skull:")) {
            return id;
        }
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        id = id.toLowerCase(Locale.ROOT);
        String renamed = switch (id) {
            case "gold_barding" -> "golden_horse_armor";
            case "iron_barding" -> "iron_horse_armor";
            case "diamond_barding" -> "diamond_horse_armor";
            case "wood_button" -> "oak_button";
            case "wood_door" -> "oak_door";
            case "sign", "sign_item" -> "oak_sign";
            case "skull", "skull_item" -> "player_head";
            case "redstone_torch_on", "redstone_torch_off" -> "redstone_torch";
            case "sulphur" -> "gunpowder";
            case "pork" -> "porkchop";
            case "grilled_pork" -> "cooked_porkchop";
            case "empty_map" -> "map";
            case "map" -> "filled_map";
            case "raw_fish" -> "cod";
            case "cooked_fish" -> "cooked_cod";
            case "clownfish" -> "tropical_fish";
            case "speckled_melon" -> "glistering_melon_slice";
            case "carrot_item" -> "carrot";
            case "potato_item" -> "potato";
            case "fireball" -> "fire_charge";
            case "exp_bottle" -> "experience_bottle";
            case "netherbrick" -> "nether_brick";
            case "mycel" -> "mycelium";
            case "water_lily" -> "lily_pad";
            case "cauldron_item" -> "cauldron";
            case "brewing_stand_item" -> "brewing_stand";
            case "flower_pot_item" -> "flower_pot";
            case "log" -> "oak_log";
            case "log_2" -> "acacia_log";
            case "wood" -> "oak_planks";
            case "stained_glass" -> "white_stained_glass";
            case "stained_glass_pane" -> "white_stained_glass_pane";
            case "sapling" -> "oak_sapling";
            case "leaves" -> "oak_leaves";
            case "wool" -> "white_wool";
            case "carpet" -> "white_carpet";
            default -> id;
        };
        return renamed.contains(":") ? renamed : "minecraft:" + renamed;
    }
}
