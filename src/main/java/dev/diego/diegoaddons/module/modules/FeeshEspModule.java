package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.frog.Tadpole;

/**
 * Boxes the plain fish critters - Cod and Salmon in the Moonglade Marsh.
 *
 * <p>Matched as "a fish that is not one of the others" rather than as a list of two: the marsh has
 * shipped several fish critters and matching the base type keeps working when the next one arrives.
 * Two are carved out because they have cards of their own - a pufferfish is a Spike, and a tadpole
 * is a Birries, which belongs with the frogs it grows into rather than with the fish it extends.
 */
public class FeeshEspModule extends HuntingEspModule {
    public FeeshEspModule() {
        super("feeshesp", "Feesh ESP", "Box the cod and salmon critters on Galatea.",
                0xFF9575CD, "Galatea", GALATEA);
    }

    @Override
    public boolean matches(Entity e) {
        return e instanceof AbstractFish && !(e instanceof Pufferfish) && !(e instanceof Tadpole);
    }
}
