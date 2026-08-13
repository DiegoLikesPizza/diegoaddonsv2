package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.HuntingEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;

/**
 * The hunting ESPs, decided once per entity rather than once per module.
 *
 * <p>Ten modules each walking the entities in range would be ten passes over the same list for an
 * answer that only one of them can give. This is instead asked from inside {@link EntityEsp}'s
 * single pass: which module, if any, wants this entity boxed.
 *
 * <p>The set of modules that are actually drawing is worked out once a tick and held, because
 * {@link HuntingEspModule#shouldDraw} reads the tab list and the scoreboard - cheap, but not cheap
 * enough to do per entity per module.
 */
public final class Hunting {
    /** The modules drawing this tick. Rebuilt by {@link #tick}, read by everything else. */
    private static final List<HuntingEspModule> active = new ArrayList<>();

    private Hunting() {
    }

    /**
     * Works out which hunting ESPs are drawing, and draws the one that is not entity-driven.
     *
     * <p>Called before {@link EntityEsp#tick}, which is what {@link #active()} then answers off.
     */
    public static void tick(Minecraft mc) {
        active.clear();
        for (HuntingEspModule m : HuntingEspModule.all()) {
            if (m.shouldDraw(mc)) {
                active.add(m);
            }
        }
        // Invisibugs are not found in the entity pass at all - they are found by their particles,
        // and drawn from what that has already remembered. See Invisibug.
        Invisibug.tick(mc);
    }

    /** Whether anything hunting-related wants a look at this tick's entities. */
    public static boolean active() {
        return !active.isEmpty();
    }

    /**
     * Boxes {@code e} if one of the drawing modules claims it.
     *
     * @return whether it was claimed, so the caller can stop considering it
     */
    public static boolean onEntity(Entity e) {
        for (HuntingEspModule m : active) {
            if (m.matches(e)) {
                // Not a pet, and this is not a corner case: a Dolphin, a Turtle, an Axolotl and a
                // Bat are all real SkyBlock pets, so four of these ten would otherwise box whatever
                // is following you around - the same mistake Pest ESP made with a Slug.
                if (isPet(e)) {
                    return false;
                }
                EspRender.draw(e, e.getBoundingBox().inflate(m.inflate()), m);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this mob is somebody's pet, told by the {@code [Lvl n]} its plate carries.
     *
     * <p>SkyBlock mounts a mob's name plate on the mob, so the plate is a passenger. A pet's plate
     * has the level prefix and a mob's never does - see {@link Pests#isPetPlate}, which is the same
     * check the Garden already relies on.
     */
    private static boolean isPet(Entity e) {
        for (Entity rider : e.getPassengers()) {
            if (rider instanceof ArmorStand stand && stand.hasCustomName()
                    && Pests.isPetPlate(LegacyText.strip(stand.getCustomName().getString()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The module that boxes a mob wearing this name plate, or null.
     *
     * <p>Separate from {@link #onEntity} because a plate is not the thing to box: the caller has to
     * find the body underneath it first, and it already knows how - see {@code EntityEsp.box}.
     */
    public static HuntingEspModule onPlate(String plate) {
        for (HuntingEspModule m : active) {
            if (m.matchesPlate(plate)) {
                return m;
            }
        }
        return null;
    }
}
