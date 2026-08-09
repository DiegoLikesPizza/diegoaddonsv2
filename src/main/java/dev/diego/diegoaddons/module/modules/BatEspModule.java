package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.EspModule;
import dev.diego.diegoaddons.module.NumberSetting;

/**
 * Boxes dungeon secret bats - the bats that spawn from secrets, so they are easy to find and pop.
 * Handled in the shared {@link dev.diego.diegoaddons.util.EntityEsp} pass; gated to dungeons.
 */
public class BatEspModule extends EspModule {
    public static BatEspModule INSTANCE;

    private final BooleanSetting ignoreDoorBats =
            new BooleanSetting(this, "ignoreDoorBats", "Ignore door bats", true);
    private final NumberSetting fallSpeed =
            new NumberSetting(this, "fallSpeed", "Falling faster than", 0.05, 0.01, 0.30, 0.01);

    public BatEspModule() {
        super("batesp", Category.DUNGEONS, "Bat ESP", "Box dungeon secret bats.",
                0xFF00E5FF);
        settings.add(ignoreDoorBats);
        settings.add(fallSpeed);
        INSTANCE = this;
    }

    /**
     * Whether this bat is one of the crowd Hypixel drops when a door opens, rather than a secret.
     *
     * <p>There is no flag on the entity saying which it is - both are ordinary bats - so this goes
     * on how they move. The door ones are spawned above the doorway and fall through it, which no
     * secret bat does: a real one flaps about at roughly the height it appeared. A sustained
     * downward velocity is therefore the one signal that separates them.
     *
     * <p>The threshold is a setting because it is a guess about someone else's server. If real
     * secret bats start disappearing, raise it; if the door crowd still shows, lower it.
     */
    public boolean isDoorBat(double deltaY) {
        return ignoreDoorBats.get() && deltaY < -fallSpeed.get();
    }
}
