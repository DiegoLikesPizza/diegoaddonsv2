package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.EspModule;

/**
 * Boxes dungeon minibosses - Shadow Assassin, Lost Adventurer, Frozen Adventurer and Angry
 * Archeologist - by their name plate,
 * in a distinct colour from ordinary starred mobs. Handled in the shared
 * {@link dev.diego.diegoaddons.util.EntityEsp} pass; gated to dungeons.
 */
public class DungeonMinibossEspModule extends EspModule {
    public static DungeonMinibossEspModule INSTANCE;

    /** The recognised dungeon miniboss plate names, matched by {@code contains}. */
    private static final String[] NAMES = {
            "Shadow Assassin", "Lost Adventurer", "Frozen Adventurer",
            "Angry Archeologist", "Angry Archaeologist",   // Hypixel has used both spellings
    };

    public DungeonMinibossEspModule() {
        super("dungeonminibossesp", Category.DUNGEONS, "Miniboss ESP",
                "Box Shadow Assassins, Lost/Frozen Adventurers and Angry Archeologists.",
                0xFFFF3060);
        INSTANCE = this;
    }


    /** Whether a name plate is one of the dungeon minibosses. */
    public boolean matches(String plate) {
        String haystack = plate.toLowerCase(java.util.Locale.ROOT);
        for (String n : NAMES) {
            // Case-insensitive: Hypixel has shipped these plates with different casing over time.
            if (haystack.contains(n.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
