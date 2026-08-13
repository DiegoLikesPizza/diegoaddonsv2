package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;

/**
 * Boxes Mossybits and Birries - the frog and tadpole critters in the Moonglade Marsh.
 *
 * <p>Both on one card because they are the same animal at two ages, and both are caught by being
 * pestered rather than killed: a Mossybit has to be clicked into jumping four times, a Birries
 * netted or lassoed. Neither is worth a card of its own.
 */
public class FrogEspModule extends HuntingEspModule {
    public FrogEspModule() {
        super("frogesp", "Frog ESP", "Box Mossybits (frogs) and Birries (tadpoles) on Galatea.",
                0xFF8BC34A, "Galatea", GALATEA);
    }

    @Override
    public boolean matches(Entity e) {
        return e instanceof Frog || e instanceof Tadpole;
    }
}
