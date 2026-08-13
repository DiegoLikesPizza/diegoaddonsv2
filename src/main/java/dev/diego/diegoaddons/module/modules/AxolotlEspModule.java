package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;

/** Boxes Coralots - the axolotl critter in the Moonglade Marsh. */
public class AxolotlEspModule extends HuntingEspModule {
    public AxolotlEspModule() {
        super("axolotlesp", "Axolotl ESP", "Box Coralots (axolotls) on Galatea.",
                0xFFFF80AB, "Galatea", GALATEA);
    }

    @Override
    public boolean matches(Entity e) {
        return e instanceof Axolotl;
    }
}
