package dev.diego.diegoaddons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves {@link AddonConfig} as JSON in the instance config directory. Gson is bundled with
 * Minecraft, so no extra dependency is needed.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("diegoaddonsv2.json");

    private static AddonConfig config = new AddonConfig();

    private ConfigManager() {
    }

    public static AddonConfig get() {
        return config;
    }

    /** Get (creating if absent) the settings block for a module id. */
    public static ModuleConfig moduleConfig(String id) {
        if (config.modules == null) {
            config.modules = new java.util.LinkedHashMap<>();
        }
        ModuleConfig mc = config.modules.computeIfAbsent(id, k -> new ModuleConfig());
        if (mc.numbers == null) {
            mc.numbers = new java.util.HashMap<>();
        }
        if (mc.keys == null) {
            mc.keys = new java.util.HashMap<>();
        }
        if (mc.options == null) {
            mc.options = new java.util.HashMap<>();
        }
        return mc;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try {
                String json = Files.readString(FILE);
                AddonConfig loaded = GSON.fromJson(json, AddonConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | RuntimeException e) {
                DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Failed to read config, using defaults", e);
            }
        } else {
            save(); // materialise defaults on first launch
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(config));
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Failed to write config", e);
        }
    }
}
