package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Makes the mobs that turn invisible visible again - Shadow Assassins, fels, and anything else that
 * disappears mid-fight.
 *
 * <p>Invisibility on those is not a stealth mechanic you are meant to lose to; it is a mob you are
 * already fighting becoming impossible to aim at. See {@code EntityInvisibilityMixin}, which simply
 * answers "no" when the renderer asks whether one is invisible.
 *
 * <p>Restricted to dungeons by default, because outside them an invisible mob is usually invisible
 * on purpose and the world is better left as the server describes it.
 */
public class ShowHiddenMobsModule extends Module {
    public static ShowHiddenMobsModule INSTANCE;

    private final BooleanSetting dungeonsOnly =
            new BooleanSetting(this, "dungeonsOnly", "Only in dungeons", true);
    private final BooleanSetting includePlayers =
            new BooleanSetting(this, "players", "Include player-shaped mobs", true);

    public ShowHiddenMobsModule() {
        super("showhiddenmobs", Category.DUNGEONS, "Show Hidden Mobs",
                "Reveal invisible mobs like Shadow Assassins and fels.");
        settings.add(dungeonsOnly);
        settings.add(includePlayers);
        INSTANCE = this;
    }

    public boolean dungeonsOnly() {
        return dungeonsOnly.get();
    }

    public boolean includePlayers() {
        return includePlayers.get();
    }
}
