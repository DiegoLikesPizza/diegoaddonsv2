package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;

import java.util.Locale;

/**
 * The Torrhus Canyon hunting mobs, nine of them, each with its own switch.
 *
 * <p>One card rather than nine, at Diego's ask, and it suits them: unlike the Galatea critters -
 * which are ordinary animals you might want boxed one at a time in different colours - these are all
 * the same job on the same island, and what you actually change between runs is <i>which of them you
 * are still after</i>. That is a row of toggles, not nine cards.
 *
 * <p><b>Two ways in, because neither covers all nine.</b> Seven are vanilla animals wearing a
 * SkyBlock name, so they are matched by entity type - the only thing that works when a mob carries
 * no plate. The other two are not vanilla mobs at all: a Grizzly Bear is a custom level-101 mob and
 * a Tiki is a totem of three rotating heads, so those are matched by their name plate. Anything
 * matched either way is boxed once; see {@link dev.diego.diegoaddons.util.Hunting}.
 */
public class TorrhusEspModule extends HuntingEspModule {
    /** One mob: its plate name, the vanilla class it wears (or null), and its switch. */
    private record Mob(String name, Class<?> type, BooleanSetting on) {
    }

    private final BooleanSetting firefox =
            new BooleanSetting(this, "firefox", "Firefox", true);
    private final BooleanSetting mountainGoat =
            new BooleanSetting(this, "mountainGoat", "Mountain Goat", true);
    private final BooleanSetting drybark =
            new BooleanSetting(this, "drybark", "Drybark", true);
    private final BooleanSetting grizzlyBear =
            new BooleanSetting(this, "grizzlyBear", "Grizzly Bear", true);
    private final BooleanSetting groundhog =
            new BooleanSetting(this, "groundhog", "Groundhog", true);
    /**
     * Honeybuzz is a bee, and so are Beeheemoth and Pollendart.
     *
     * <p>Nothing on the entity separates them, so with this on every Torrhus bee is boxed unless it
     * happens to carry a plate that names it. Said plainly on the row rather than left as a
     * surprise: the alternative is silently missing the Honeybuzz you turned it on for.
     */
    private final BooleanSetting honeybuzz =
            new BooleanSetting(this, "honeybuzz", "Honeybuzz (boxes every bee)", true);
    private final BooleanSetting pangolin =
            new BooleanSetting(this, "pangolin", "Pangolin", true);
    private final BooleanSetting blueJay =
            new BooleanSetting(this, "blueJay", "Blue Jay", true);
    /** Every Tiki - Sneaky, Cheeky and Shrieky share a bestiary and a puzzle. */
    private final BooleanSetting tiki =
            new BooleanSetting(this, "tiki", "Tiki", true);

    private final Mob[] mobs;

    public TorrhusEspModule() {
        super("torrhusesp", "Torrhus ESP",
                "Box the Torrhus Canyon hunting mobs, each one switchable.",
                0xFFFF9800, "Torrhus Canyon", TORRHUS);
        settings.add(firefox);
        settings.add(mountainGoat);
        settings.add(drybark);
        settings.add(grizzlyBear);
        settings.add(groundhog);
        settings.add(honeybuzz);
        settings.add(pangolin);
        settings.add(blueJay);
        settings.add(tiki);
        // Built after the settings so each row is the one the entry points at. Null type means the
        // mob is not a vanilla entity and can only be found by its plate.
        mobs = new Mob[] {
                new Mob("Firefox", Fox.class, firefox),
                new Mob("Mountain Goat", Goat.class, mountainGoat),
                new Mob("Drybark", Creaking.class, drybark),
                new Mob("Grizzly Bear", null, grizzlyBear),
                new Mob("Groundhog", Hoglin.class, groundhog),
                new Mob("Honeybuzz", Bee.class, honeybuzz),
                new Mob("Pangolin", Armadillo.class, pangolin),
                new Mob("Blue Jay", Parrot.class, blueJay),
                new Mob("Tiki", null, tiki),
        };
    }

    @Override
    public boolean matches(Entity e) {
        for (Mob m : mobs) {
            if (m.type() != null && m.on().get() && m.type().isInstance(e)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matchesPlate(String plate) {
        String haystack = plate.toLowerCase(Locale.ROOT);
        for (Mob m : mobs) {
            // Every mob is checked by plate, not only the two without a type: a plate is the one
            // thing that says which bee this is, and it costs a string search either way.
            if (m.on().get() && haystack.contains(m.name().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
