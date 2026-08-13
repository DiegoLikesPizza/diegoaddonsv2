package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;

/**
 * Boxes Cinderbats - the bats in the caves under the Blazing Volcano.
 *
 * <p>Matched by entity type rather than by a name plate, because a Cinderbat has no plate: the wiki
 * is explicit that they carry nothing above their heads and are found by their fire particles. They
 * hang from the ceiling in unlit caves, which is exactly where a particle is hardest to see.
 *
 * <p>Nothing distinguishes one bat from another here, so the island gate is doing real work: with it
 * off this boxes every bat in the game, dungeon secret bats included, in a second colour on top of
 * {@link BatEspModule}.
 */
public class CinderbatEspModule extends HuntingEspModule {
    public CinderbatEspModule() {
        super("cinderbatesp", "Cinderbat ESP", "Box Cinderbats on the Crimson Isle.",
                0xFFFF7043, "the Crimson Isle", CRIMSON);
    }

    @Override
    public boolean matches(Entity e) {
        return e instanceof Bat;
    }
}
