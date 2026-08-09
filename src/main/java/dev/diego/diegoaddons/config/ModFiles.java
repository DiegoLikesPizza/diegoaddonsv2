package dev.diego.diegoaddons.config;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Every file the mod owns, under one folder: {@code config/diegoaddons/}.
 *
 * <p>It used to scatter them across the config directory - {@code diegoaddonsv2-configlib.json} and
 * {@code diegoaddonsv2.json} loose at the top level, while the skins and the media script already
 * lived in a folder of their own. Anyone dropping a file in for the mod to read had to be told which
 * of the two conventions applied to it, and the answer depended on which year the feature was added.
 *
 * <p>The only file left outside is the staged update jar, which lives in the game directory rather
 * than the config directory on purpose - see {@link dev.diego.diegoaddons.util.Updater}. It is not
 * config; it is a jar waiting to be moved into the mods folder.
 *
 * <p>{@link #migrate()} moves the old paths in on first run and is safe to call every start.
 */
public final class ModFiles {

    /** The folder itself. Created on demand rather than at class-load. */
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("diegoaddons");

    private ModFiles() {
    }

    /** {@code config/diegoaddons/}, created if it is not there yet. */
    public static Path dir() {
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not create {}", DIR, e);
        }
        return DIR;
    }

    /** A folder inside it - {@code sounds}, {@code skins} - created if it is not there yet. */
    public static Path folder(String name) {
        Path p = dir().resolve(name);
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not create {}", p, e);
        }
        return p;
    }

    /** The settings file configlib reads and writes. */
    public static Path config() {
        return dir().resolve("config.json");
    }

    /** Sound files the user drops in, played by name - see {@link dev.diego.diegoaddons.util.CustomSounds}. */
    public static Path sounds() {
        return folder("sounds");
    }

    /** Player skins the Skin Changer reads. */
    public static Path skins() {
        return folder("skins");
    }

    /** The pre-2.4.2 config, wherever it is - the new folder first, then where it used to live. */
    public static Path legacyConfig() {
        Path moved = DIR.resolve("diegoaddonsv2.json");
        return Files.exists(moved)
                ? moved
                : FabricLoader.getInstance().getConfigDir().resolve("diegoaddonsv2.json");
    }

    /**
     * Moves the files that used to sit loose in {@code config/} into the folder.
     *
     * <p>Runs before configlib is handed its path, so the settings arrive where they are expected
     * rather than being written out fresh at their defaults beside a file nobody reads again. A move
     * that fails is logged and left alone: the old file staying put costs the user their settings for
     * one start, and deleting or half-writing it costs them permanently.
     */
    public static void migrate() {
        Path config = FabricLoader.getInstance().getConfigDir();
        move(config.resolve("diegoaddonsv2-configlib.json"), config());
        move(config.resolve("diegoaddonsv2.json"), dir().resolve("diegoaddonsv2.json"));
        // Auto Update moved from the Misc page to Client in 2.5.2. Every option id carries the
        // category it was declared in, so the move renames its keys - and a renamed key is a key
        // configlib does not find, which reads back as the module having reset itself.
        rekey("misc.autoupdate", "appearance.autoupdate");
    }

    /**
     * Renames the saved key {@code from}, and every key under it, to {@code to}.
     *
     * <p>Both, deliberately. A module's own on/off state is saved under the bare id and its settings
     * under {@code id.something}, so matching only the dotted form moves the settings and leaves the
     * switch behind - which turns the module off while carrying its configuration across.
     *
     * <p>Done on the file rather than in the config layer because it has to happen <b>before</b>
     * configlib reads it: after the read the defaults are already in the settings, and writing them
     * back is what would make the loss permanent.
     *
     * <p>An existing key at the new name always wins - that is what makes this safe to run on every
     * start. Once the new key is there, the old one is a leftover and is dropped.
     */
    private static void rekey(String from, String to) {
        Path file = config();
        if (!Files.exists(file)) {
            return;
        }
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser
                    .parseString(Files.readString(file)).getAsJsonObject();
            // Every group the file keeps ids in - "options", "hidden", "ui". A module's switch and
            // its settings are not in the same one, so renaming only the first would move half of it.
            int moved = 0;
            for (String group : java.util.List.copyOf(root.keySet())) {
                if (root.get(group).isJsonObject()) {
                    moved += rekeyIn(root.getAsJsonObject(group), from, to);
                }
            }
            if (moved == 0) {
                return;
            }
            Files.writeString(file, root.toString());
            DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] moved {} saved setting(s) from {} to {}",
                    moved, from, to);
        } catch (IOException | RuntimeException e) {
            // Including a file that is not an object, or not JSON at all. Leaving it alone costs
            // the settings under the old key their values; rewriting a file we did not understand
            // costs the whole config.
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not re-key {} in {}", from, file, e);
        }
    }

    /** Renames the matching keys of one group, and says how many it moved. */
    private static int rekeyIn(com.google.gson.JsonObject group, String from, String to) {
        int moved = 0;
        for (String key : java.util.List.copyOf(group.keySet())) {
            if (!key.equals(from) && !key.startsWith(from + ".")) {
                continue;
            }
            String renamed = to + key.substring(from.length());
            if (!group.has(renamed)) {
                group.add(renamed, group.get(key));
            }
            group.remove(key);
            moved++;
        }
        return moved;
    }

    private static void move(Path from, Path to) {
        if (!Files.exists(from) || Files.exists(to)) {
            return;
        }
        try {
            Files.createDirectories(to.getParent());
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] moved {} into {}",
                    from.getFileName(), to.getParent());
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not move {} to {} - it stays where "
                    + "it is and will be read from there", from, to, e);
        }
    }
}
