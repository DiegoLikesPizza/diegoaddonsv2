package dev.diego.diegoaddons.config;

import com.google.gson.Gson;
import dev.diego.configlib.ConfigHandle;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.ModuleManager;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.Setting;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.module.modules.PlayerHudModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carries a config written before configlib owned storage into the settings it now owns.
 *
 * <p>Runs once. The old file held a map of every module's settings keyed by name; the settings are
 * the values themselves now, so this reads that map and writes each value through the setting it
 * belongs to - after which configlib saves them in its own shape and the old file is renamed out of
 * the way rather than deleted.
 *
 * <p>Deliberately forgiving. Anything it cannot make sense of - a module that no longer exists, a
 * setting that was renamed, a number where a string now lives - is skipped, because the alternative
 * is a stack trace on startup over a config that was going to be mostly right.
 *
 * <p>This whole class is disposable: once no instance is running a build older than 2.4.2 it can be
 * deleted, along with {@link ModuleConfig} and {@link AddonConfig#legacyImported}.
 */
public final class LegacyImport {

    private static final Gson GSON = new Gson();
    // Resolved per call rather than once: ModFiles moves this file into config/diegoaddons/ at
    // startup, and a field initialised at class-load could be looking at where it used to be.
    private static Path file() {
        return ModFiles.legacyConfig();
    }

    private LegacyImport() {
    }

    /** The old file's shape, as far as this needs to understand it. */
    private static final class Legacy {
        String theme;
        Boolean introShown;
        Boolean sbHintShown;
        Boolean smoothCorners;
        Boolean gfsSeeded;
        String savedPet;
        String activeMiningRoute;
        String[] savedEquipment;
        List<BlockedPlayer> blockedPlayers;
        List<WordReplacement> wordReplacements;
        List<String> espTerms;
        Set<Integer> lockedSlots;
        List<CommandHotkey> commandHotkeys;
        List<MiningRoute> miningRoutes;
        List<GfsItem> gfsItems;
        Map<String, ModuleConfig> modules;
    }

    /** Imports the old config if there is one and it has not been imported already. */
    public static void run(ConfigHandle<?> handle) {
        AddonConfig live = ConfigManager.get();
        if (live.legacyImported || !Files.isRegularFile(file())) {
            return;
        }

        Legacy old;
        try {
            old = GSON.fromJson(Files.readString(file()), Legacy.class);
        } catch (IOException | RuntimeException e) {
            DiegoAddonsV2Client.LOGGER.warn(
                    "[DiegoAddons] Could not read the old config to import it; starting fresh", e);
            live.legacyImported = true;
            return;
        }
        if (old == null) {
            live.legacyImported = true;
            return;
        }

        // One write at the end rather than one per setting: every setter asks for a save, and there
        // are several hundred of them here.
        ConfigManager.whileLoading(() -> {
            copyState(old, live);
            for (Module m : ModuleManager.all()) {
                ModuleConfig saved = old.modules == null ? null : old.modules.get(m.id);
                if (saved != null) {
                    copyModule(m, saved);
                }
            }
        });

        live.legacyImported = true;
        handle.save();
        archive();
        DiegoAddonsV2Client.LOGGER.info(
                "[DiegoAddons] Imported the old config into configlib's; the old file is now {}.imported",
                file().getFileName());
    }

    private static void copyState(Legacy old, AddonConfig live) {
        if (old.theme != null) {
            live.theme = old.theme;
        }
        if (old.introShown != null) {
            live.introShown = old.introShown;
        }
        if (old.sbHintShown != null) {
            live.sbHintShown = old.sbHintShown;
        }
        if (old.smoothCorners != null) {
            live.smoothCorners = old.smoothCorners;
        }
        if (old.gfsSeeded != null) {
            live.gfsSeeded = old.gfsSeeded;
        }
        if (old.savedPet != null) {
            live.savedPet = old.savedPet;
        }
        if (old.activeMiningRoute != null) {
            live.activeMiningRoute = old.activeMiningRoute;
        }
        if (old.savedEquipment != null) {
            live.savedEquipment = old.savedEquipment;
        }
        if (old.blockedPlayers != null) {
            live.blockedPlayers = old.blockedPlayers;
        }
        if (old.wordReplacements != null) {
            live.wordReplacements = old.wordReplacements;
        }
        if (old.espTerms != null) {
            live.espTerms = old.espTerms;
        }
        if (old.lockedSlots != null) {
            live.lockedSlots = old.lockedSlots;
        }
        if (old.commandHotkeys != null) {
            live.commandHotkeys = old.commandHotkeys;
        }
        if (old.miningRoutes != null) {
            live.miningRoutes = old.miningRoutes;
        }
        if (old.gfsItems != null) {
            live.gfsItems = old.gfsItems;
        }
    }

    private static void copyModule(Module m, ModuleConfig saved) {
        m.setEnabledQuietly(saved.enabled);

        // The Player HUD's section order was a text setting rather than one of its Settings.
        if (m instanceof PlayerHudModule player && saved.texts != null) {
            String order = saved.texts.get("sectionOrder");
            if (order != null) {
                player.setSavedOrder(order);
            }
        }

        for (Setting s : m.settings()) {
            try {
                copySetting(s, saved);
            } catch (RuntimeException e) {
                // A renamed or retyped setting is not worth failing the whole import over.
                DiegoAddonsV2Client.LOGGER.debug("[DiegoAddons] Skipped importing {}.{}", m.id, s.key, e);
            }
        }
    }

    private static void copySetting(Setting s, ModuleConfig saved) {
        switch (s) {
            case BooleanSetting bs -> {
                Boolean v = saved.options == null ? null : saved.options.get(s.key);
                if (v != null) {
                    bs.set(v);
                }
            }
            case NumberSetting ns -> {
                Double v = saved.numbers == null ? null : saved.numbers.get(s.key);
                if (v != null) {
                    ns.set(v);
                }
            }
            // A cycle stored its chosen index in the numbers map, which is why it reads from there.
            case CycleSetting cs -> {
                Double v = saved.numbers == null ? null : saved.numbers.get(s.key);
                if (v != null) {
                    cs.set((int) Math.round(v));
                }
            }
            case KeybindSetting ks -> {
                Integer v = saved.keys == null ? null : saved.keys.get(s.key);
                if (v != null) {
                    ks.set(v);
                }
            }
            // A colour was one packed "mode|aarrggbb|aarrggbb" string; it is three values now, so it
            // is unpacked here rather than stored packed.
            case ColorSetting col -> {
                String v = saved.texts == null ? null : saved.texts.get(s.key);
                if (v == null) {
                    return;
                }
                String[] parts = v.split("\\|");
                if (parts.length != 3) {
                    return;
                }
                col.setMode(Integer.parseInt(parts[0]));
                col.setColorA((int) Long.parseLong(parts[1], 16));
                col.setColorB((int) Long.parseLong(parts[2], 16));
            }
            case StringSetting ss -> {
                String v = saved.texts == null ? null : saved.texts.get(s.key);
                if (v != null) {
                    ss.set(v);
                }
            }
            default -> {
                // An action has no value to carry over.
            }
        }
    }

    /**
     * Renames the old file rather than deleting it.
     *
     * <p>The import is one-way and the flag that records it lives in the new file, so a user who
     * rolls back to an older build would otherwise find their settings gone. Leaving the original
     * next to it costs nothing and is the difference between a reversible change and a destructive
     * one.
     */
    private static void archive() {
        try {
            Path f = file();
            Files.move(f, f.resolveSibling(f.getFileName() + ".imported"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Imported the old config but could not "
                    + "rename it; it will be skipped next time by the saved flag", e);
        }
    }
}
