package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.turtle.Turtle;

/** Boxes Shellwises - the turtle critter in the Moonglade Marsh, netted with a Turbo Fishing Net. */
public class TurtleEspModule extends HuntingEspModule {
    public TurtleEspModule() {
        super("turtleesp", "Turtle ESP", "Box Shellwises (turtles) on Galatea.",
                0xFF4CAF50, "Galatea", GALATEA);
    }

    @Override
    public boolean matches(Entity e) {
        return e instanceof Turtle;
    }
}
