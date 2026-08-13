package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.fish.Pufferfish;

/**
 * Boxes Spikes - the pufferfish critter in the Moonglade Marsh.
 *
 * <p>A Spike has to be netted from ten blocks away or it puffs up and the catch fails, which makes
 * it the one critter where spotting it early is the difficulty rather than a convenience.
 */
public class PufferEspModule extends HuntingEspModule {
    public PufferEspModule() {
        super("pufferesp", "Puffer ESP", "Box Spikes (pufferfish) on Galatea.",
                0xFFFFC107, "Galatea", GALATEA);
    }

    @Override
    public boolean matches(Entity e) {
        return e instanceof Pufferfish;
    }
}
