package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.panda.Panda;

/**
 * Boxes Bambuleafs and Mochibears - the panda critters in the Moonglade Marsh.
 *
 * <p>One card for both: they are the same entity in two coats (the Mochibear is the brown one) and
 * both are caught the same way, by being fed forty-odd bamboo.
 */
public class PandaEspModule extends HuntingEspModule {
    public PandaEspModule() {
        super("pandaesp", "Panda ESP", "Box Bambuleafs and Mochibears (pandas) on Galatea.",
                0xFFECEFF1, "Galatea", GALATEA);
    }

    @Override
    public boolean matches(Entity e) {
        return e instanceof Panda;
    }
}
