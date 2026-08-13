package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.dolphin.Dolphin;

/** Boxes Joydives - the dolphin critter in the Moonglade Marsh, netted with a Medium net or better. */
public class DolphinEspModule extends HuntingEspModule {
    public DolphinEspModule() {
        super("dolphinesp", "Dolphin ESP", "Box Joydives (dolphins) on Galatea.",
                0xFF29B6F6, "Galatea", GALATEA);
    }

    @Override
    public boolean matches(Entity e) {
        return e instanceof Dolphin;
    }
}
