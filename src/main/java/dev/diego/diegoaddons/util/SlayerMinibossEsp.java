package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.SlayerMinibossEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Boxes Slayer minibosses - the bonus mobs that spawn during a quest (Revenant Sycophant, Tarantula
 * Vermin, ...). They are matched by the name on their floating health plate, the same way
 * {@link EntityEsp} matches starred mobs. Because those names are unique to Slayer, the pass stays on
 * whenever the module is enabled rather than depending on the sidebar quest parse - so it is a useful
 * signal on its own even if {@link SlayerState} misreads the quest.
 *
 * <p>The plate floats above the mob, so the actual mob under it is found and its own bounding box is
 * boxed - that way an Enderman miniboss gets a tall box and a Wolf a short one, instead of one fixed
 * guess for all.
 */
public final class SlayerMinibossEsp {
    /**
     * Miniboss health-plate names, matched by {@code contains}. Zombie / Spider / Wolf / Enderman are
     * confirmed; Blaze (Inferno Demonlord) and Vampire (Riftstalker Bloodfiend) minis are left out
     * until their exact plate names are verified in-game rather than guessed.
     */
    private static final String[] NAMES = {
            // Zombie - Revenant Horror
            "Revenant Sycophant", "Revenant Champion", "Deformed Revenant",
            "Atoned Champion", "Atoned Revenant",
            // Spider - Tarantula Broodfather
            "Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula",
            // Wolf - Sven Packmaster
            "Pack Enforcer", "Sven Follower", "Sven Alpha",
            // Enderman - Voidgloom Seraph
            "Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac",
    };

    private static final double RANGE = 30.0;
    private static final double EDGE = 0.06;
    private static final int COLOR = 0xFFFFFF55;   // yellow, distinct from the boss box
    /** Max horizontal distance from a plate to the mob it belongs to. */
    private static final double MATCH = 3.0;

    private SlayerMinibossEsp() {
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        SlayerMinibossEspModule mod = SlayerMinibossEspModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null || mc.level == null) {
            return;
        }
        AABB area = mc.player.getBoundingBox().inflate(RANGE);
        List<ArmorStand> plates = new ArrayList<>();
        List<LivingEntity> mobs = new ArrayList<>();
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (e instanceof ArmorStand as && as.hasCustomName()) {
                if (matches(LegacyText.strip(as.getCustomName().getString()))) {
                    plates.add(as);
                }
            } else if (e instanceof LivingEntity le && !(e instanceof Player)) {
                mobs.add(le);
            }
        }
        if (plates.isEmpty()) {
            return;
        }

        // Each plate gets the nearest not-yet-taken mob below it; box that mob's real bounding box.
        Set<Entity> taken = new HashSet<>();
        for (ArmorStand plate : plates) {
            LivingEntity best = null;
            double bestDist = MATCH * MATCH;
            for (LivingEntity m : mobs) {
                if (taken.contains(m) || m.getY() > plate.getY() + 0.5) {
                    continue;
                }
                double dx = m.getX() - plate.getX();
                double dz = m.getZ() - plate.getZ();
                double d = dx * dx + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    best = m;
                }
            }
            if (best != null) {
                taken.add(best);
                EspRender.draw(best, best.getBoundingBox().inflate(0.05), mod);
            }
        }
    }

    private static boolean matches(String name) {
        for (String n : NAMES) {
            if (name.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
