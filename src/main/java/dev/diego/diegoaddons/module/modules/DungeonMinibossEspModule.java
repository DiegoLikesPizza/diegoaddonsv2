package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Boxes dungeon minibosses (Lost Adventurer, Shadow Assassin, Diamond Guy, ...) by their name plate,
 * in a distinct colour from ordinary starred mobs. Handled in the shared
 * {@link dev.diego.diegoaddons.util.EntityEsp} pass; gated to dungeons.
 */
public class DungeonMinibossEspModule extends Module {
    public static DungeonMinibossEspModule INSTANCE;

    /** The recognised dungeon miniboss plate names, matched by {@code contains}. */
    private static final String[] NAMES = {
            "Lost Adventurer", "Shadow Assassin", "Diamond Guy", "Angry Archeologist",
            "Zombie Commander", "Skeleton Master", "Super Archer", "King Midas",
    };

    public DungeonMinibossEspModule() {
        super("dungeonminibossesp", Category.DUNGEONS, "Miniboss ESP",
                "Box dungeon minibosses (Lost Adventurer, Shadow Assassin, ...).");
        INSTANCE = this;
    }

    public int color() {
        return 0xFFFF3060;   // pink-red
    }

    /** Whether a name plate is one of the dungeon minibosses. */
    public boolean matches(String plate) {
        for (String n : NAMES) {
            if (plate.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
