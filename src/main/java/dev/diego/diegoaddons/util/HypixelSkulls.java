/*
 * Ported from Inventory Buttons (https://github.com/afranz29/Inventory-Buttons),
 * Copyright (C) 2026 Panda/afranz29, licensed under the LGPLv3. This file stays under the LGPLv3.
 */

package dev.diego.diegoaddons.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Every SkyBlock item that is a decorated head, as an icon the button editor can offer.
 *
 * <p>Hypixel publishes its item list, and a good part of it is player heads - the minion, the pet,
 * the fairy soul, the reforge anvil. Those are the icons anyone actually wants on a button, and
 * there are far too many to write down: they are fetched once per session instead.
 *
 * <p>Fetched off-thread and read under a lock, so nothing waits on the network. Until it lands the
 * editor simply has fewer icons - the picker works from the first frame with the bundled set and the
 * item registry, and the SkyBlock heads appear in it when they arrive.
 */
public final class HypixelSkulls {

    /** {@code name} is what the search matches; {@code configId} is the {@code skull:<hash>} written to the button. */
    public record Skull(String name, String id, ItemStack icon, String configId) {
    }

    private static final String API = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final String TEXTURE_URL = "http://textures.minecraft.net/texture/";

    /** Hypixel wraps colour codes in %%…%% in this endpoint's names. */
    private static final Pattern COLOUR_CODE = Pattern.compile("%%.*?%%");

    private static final List<Skull> SKULLS = new ArrayList<>();

    private static boolean started = false;

    private HypixelSkulls() {
    }

    /** A snapshot to search over - copied so the caller never iterates a list still being filled. */
    public static List<Skull> all() {
        synchronized (SKULLS) {
            return List.copyOf(SKULLS);
        }
    }

    /** Fetches the list, once per session. Safe to call from anywhere, as often as you like. */
    public static void load() {
        synchronized (SKULLS) {
            if (started) {
                return;
            }
            started = true;
        }
        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder().uri(URI.create(API)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    DiegoAddonsV2Client.LOGGER.warn(
                            "[DiegoAddons] SkyBlock item list came back HTTP {}", response.statusCode());
                    return;
                }
                parse(response.body());
                DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] {} SkyBlock head icons loaded",
                        SKULLS.size());
            } catch (Exception e) {
                // Including the interrupt: a failed fetch costs the editor some icons and nothing
                // else, so it is logged rather than retried.
                DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not load SkyBlock head icons", e);
            }
        });
    }

    private static void parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("items")) {
            return;
        }
        JsonArray items = root.getAsJsonArray("items");
        List<Skull> found = new ArrayList<>();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                JsonObject obj = element.getAsJsonObject();
                String material = string(obj, "material", "STONE");
                if (!material.equals("SKULL_ITEM") && !material.equals("PLAYER_HEAD")) {
                    continue;
                }
                String hash = hashOf(obj.get("skin"));
                if (hash == null) {
                    continue;
                }
                String name = COLOUR_CODE.matcher(string(obj, "name", "Unknown")).replaceAll("").trim();
                String configId = "skull:" + hash;
                ItemStack icon = InvButtons.skull(configId);
                icon.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                found.add(new Skull(name, string(obj, "id", "UNKNOWN"), icon, configId));
            } catch (RuntimeException e) {
                // One malformed entry is not a reason to lose the other three thousand.
            }
        }
        synchronized (SKULLS) {
            SKULLS.clear();
            SKULLS.addAll(found);
        }
    }

    /** The texture hash out of a {@code skin} value, which is a base64 blob or an object holding one. */
    private static String hashOf(JsonElement skin) {
        if (skin == null) {
            return null;
        }
        String base64 = null;
        if (skin.isJsonPrimitive()) {
            base64 = skin.getAsString();
        } else if (skin.isJsonObject() && skin.getAsJsonObject().has("value")) {
            base64 = skin.getAsJsonObject().get("value").getAsString();
        }
        if (base64 == null) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            int start = decoded.indexOf(TEXTURE_URL);
            if (start < 0) {
                return null;
            }
            start += TEXTURE_URL.length();
            int end = decoded.indexOf('"', start);
            return decoded.substring(start, end < 0 ? decoded.length() : end);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String string(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
