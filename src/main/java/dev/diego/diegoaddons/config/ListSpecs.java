package dev.diego.diegoaddons.config;

import dev.diego.configlib.core.SpecBuilder;

/**
 * The mod's editable lists, declared to configlib.
 *
 * <p>Each of these had its own screen once - blocked players, word replacements, command shortcuts,
 * the Auto GFS list - and each screen did the same four things as the last: add a row, fill it in,
 * move it, delete it. They are now one screen in the library, described five times instead of built
 * five times.
 *
 * <p>Only text fields, which is what the shared editor handles. Where an item carries something else
 * - a hotkey's key code, a GFS threshold, a route's recorded points - that part is still set the way
 * it always was, by the command or the module that captures it. Those are noted per list below.
 */
public final class ListSpecs {

    private ListSpecs() {
    }

    /** Every sound id the client knows, sorted so the picker reads predictably. */
    private static java.util.List<String> allSounds() {
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet().stream()
                .map(net.minecraft.resources.Identifier::toString)
                .sorted()
                .toList();
    }

    /**
     * The user's own sound files, with an empty first entry.
     *
     * <p>The empty entry is what "no custom sound" is: a picker can only offer a value, so the way
     * back to the game's own chime has to be one of the choices rather than a second control beside
     * it saying whether the first one counts.
     */
    private static java.util.List<String> customSounds() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("");
        out.addAll(dev.diego.diegoaddons.util.CustomSounds.list());
        return out;
    }

    /** A file name as a label: "long_beep.mp3" reads as "long beep", "" as the default. */
    private static String prettyFile(String name) {
        if (name == null || name.isBlank()) {
            return "Default (game chime)";
        }
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name).replace('_', ' ');
    }

    /** "minecraft:block.note_block.pling" reads better as "block.note block.pling". */
    private static String prettySound(String id) {
        String s = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        return s.replace('_', ' ');
    }

    /** "equipment" reads as "Equipment"; the keys are storage names, not labels. */
    /**
     * A whole count typed into a list row, or {@code fallback} if it is not one.
     *
     * <p>The list editor's fields are text, so a number has to survive being half-typed: a box
     * momentarily reading "" or "-" must not become a threshold of zero and start refilling
     * constantly. Anything unparseable keeps the previous value.
     */
    private static int count(String typed, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(typed.trim()));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String prettyKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    /** Adds every list to the spec, each under the module it belongs to. */
    public static void declare(SpecBuilder b, String moduleId) {
        switch (moduleId) {
            case "betterignorelist" -> b.list("blocked", "Blocked players",
                    "People whose chat is hidden", () -> ConfigManager.get().blockedPlayers,
                    BlockedPlayer::new, f -> f.itemName("player")
                            .field("Name", p -> p.name, (p, v) -> p.name = v, "Player name")
                            .field("Reason", p -> p.reason, (p, v) -> p.reason = v, "Optional"));

            case "replacewords" -> b.list("words", "Word replacements",
                    "Swapped in your own outgoing chat", () -> ConfigManager.get().wordReplacements,
                    WordReplacement::new, f -> f.itemName("replacement")
                            .field("Find", w -> w.from, (w, v) -> w.from = v, "Text to replace")
                            .field("Replace with", w -> w.to, (w, v) -> w.to = v, "Replacement"));

            // The key is a key field: pressed rather than typed, which is new in configlib and is
            // what this list had been missing - the command was editable and the key was not, so a
            // hotkey added from the menu could never actually fire.
            case "commandhotkeys" -> b.list("hotkeys", "Command hotkeys",
                    "Commands bound to a key", () -> ConfigManager.get().commandHotkeys,
                    CommandHotkey::new, f -> f.itemName("hotkey")
                            .field("Command", h -> h.command, (h, v) -> h.command = v, "/warp hub")
                            .key("Key", h -> h.key, (h, v) -> h.key = v));

            case "loadoutkeys" -> b.list("loadouts", "Loadout keybinds",
                    "Loadouts bound to a key", () -> ConfigManager.get().loadoutKeys,
                    dev.diego.diegoaddons.config.LoadoutKey::new, f -> f.itemName("loadout")
                            .field("Loadout name", l -> l.name, (l, v) -> l.name = v, "Mining")
                            .key("Key", l -> l.key, (l, v) -> l.key = v));

            // Threshold stays where it was; a number field is not something the shared editor does.
            case "autogfs" -> b.list("gfs", "Auto GFS items",
                    "Kept topped up from your sacks", () -> ConfigManager.get().gfsItems,
                    GfsItem::new, f -> f.itemName("item")
                            .field("Name", i -> i.name, (i, v) -> i.name = v, "Cobblestone")
                            .field("Item id", i -> i.id, (i, v) -> i.id = v, "COBBLESTONE")
                            // How low it may get before a refill. This was lost when the three
                            // hard-coded toggles became a list: every item carries its own
                            // threshold, and nothing was showing it.
                            .field("Refill below", i -> String.valueOf(i.threshold),
                                    (i, v) -> i.threshold = count(v, i.threshold), "4"));

            // Points are recorded in the world with /da route add, never typed.
            case "miningroutes" -> b.list("routes", "Mining routes",
                    "Saved paths you can draw", () -> ConfigManager.get().miningRoutes,
                    MiningRoute::new, f -> f.itemName("route")
                            .field("Name", r -> r.name, (r, v) -> r.name = v, "Route name"));

            // Not a list but the same idea: a fixed set the user arranges, which configlib's
            // OrderOption owns the screen for.
            case "playerhud" -> b.order("layout", "Section order",
                    "The order the parts are drawn in",
                    dev.diego.diegoaddons.module.modules.PlayerHudModule.SECTIONS,
                    () -> dev.diego.diegoaddons.module.modules.PlayerHudModule.INSTANCE.sectionOrder(),
                    o -> dev.diego.diegoaddons.module.modules.PlayerHudModule.INSTANCE.setSectionOrder(o),
                    ListSpecs::prettyKey);

            // Every sound the game knows is thousands of entries - a dropdown cannot carry that,
            // so it is a searchable picker. Read lazily: the registry is not populated when the
            // config is declared.
            // The same idea over a folder instead of a registry: whatever the user has dropped into
            // config/diegoaddons/sounds/. Listed lazily, so a file added while the game is running
            // is there the next time the picker is opened rather than after a restart.
            case "hydration" -> b.picker("customSound", "Custom sound",
                    "An MP3, OGG or WAV from config/diegoaddons/sounds/ - empty plays the game's chime",
                    ListSpecs::customSounds,
                    () -> dev.diego.diegoaddons.module.modules.HydrationReminderModule.INSTANCE.customSound(),
                    v -> dev.diego.diegoaddons.module.modules.HydrationReminderModule.INSTANCE
                            .setCustomSound(v),
                    ListSpecs::prettyFile, "Pick a sound file");

            // Button layouts are files rather than a list in the settings, so this is a picker over
            // the folder like the sound ones. Picking is what loads it - see the module - and the
            // Delete row beneath removes whichever is picked.
            case "inventorybuttons" -> b.picker("profile", "Load profile",
                    "A saved button layout from config/diegoaddons/invbuttons/",
                    dev.diego.diegoaddons.util.InvButtons::profileNames,
                    () -> dev.diego.diegoaddons.module.modules.InventoryButtonsModule.INSTANCE.profile(),
                    v -> dev.diego.diegoaddons.module.modules.InventoryButtonsModule.INSTANCE
                            .setProfile(v),
                    name -> name.isEmpty() ? "None" : name, "Pick a profile");

            case "secretchime" -> b.picker("sound", "Sound", "Played when a secret is found",
                    ListSpecs::allSounds,
                    () -> dev.diego.diegoaddons.module.modules.SecretChimeModule.INSTANCE.soundId(),
                    v -> dev.diego.diegoaddons.module.modules.SecretChimeModule.INSTANCE.setSoundId(v),
                    ListSpecs::prettySound, "Pick a sound");

            default -> {
                // Most modules have no list; nothing to add.
            }
        }
    }
}
