package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.EspModule;

/**
 * Boxes dungeon secret bats - the bats that spawn from secrets, so they are easy to find and pop.
 * Handled in the shared {@link dev.diego.diegoaddons.util.EntityEsp} pass; gated to dungeons.
 */
public class BatEspModule extends EspModule {
    public static BatEspModule INSTANCE;

    public BatEspModule() {
        super("batesp", Category.DUNGEONS, "Bat ESP", "Box dungeon secret bats.",
                0xFF00E5FF);
        INSTANCE = this;
    }

}
