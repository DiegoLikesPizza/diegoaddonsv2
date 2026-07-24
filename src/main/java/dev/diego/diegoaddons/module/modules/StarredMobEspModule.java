package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.EntityEsp;

import java.util.Locale;

/**
 * Boxes the starred mobs in a dungeon, the ones that actually count towards clearing a room.
 *
 * <p>See {@link EntityEsp} - starred mobs are found by the star on their name plate rather than by
 * entity type, since SkyBlock reuses ordinary vanilla mobs for all of them.
 */
public class StarredMobEspModule extends Module {
    public static StarredMobEspModule INSTANCE;

    /** A mob whose plate says it is already dead is not worth boxing. */
    private final BooleanSetting hideDead =
            new BooleanSetting(this, "hideDead", "Hide dead mobs", true);

    public StarredMobEspModule() {
        super("starredmobesp", Category.DUNGEONS, "Starred Mob ESP",
                "Box the starred mobs in dungeons.");
        settings.add(hideDead);
        INSTANCE = this;
    }

    public int color() {
        return 0xFFFFAA00;
    }

    /** True when the plate shows no health left, so the mob is on its way out. */
    public boolean hideDead(String plate) {
        if (!hideDead.get()) {
            return false;
        }
        String p = plate.toLowerCase(Locale.ROOT);
        return p.contains("0/") && p.contains("\u2764");
    }
}
