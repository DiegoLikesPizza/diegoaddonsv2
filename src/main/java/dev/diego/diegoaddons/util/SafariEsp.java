package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.CritterEspModule;
import dev.diego.diegoaddons.module.modules.SparklingCritterModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The Safari's two entity features, decided from the same name plate.
 *
 * <p>Both the Critter ESP and the Sparkling notification are answers to "what is this plate", so
 * they are read together inside {@link EntityEsp}'s single pass rather than each walking the
 * entities themselves. Sparkling wins where both apply: it is the rarer fact and the one worth
 * seeing in its own colour.
 *
 * <p>The box comes from the critter's <b>own body</b> where it can be found, not from a fixed drop
 * below the plate. That matters more here than anywhere else in the mod: this island's critters run
 * from a bee to a ravager, and one box size for both would float off almost all of them.
 */
public final class SafariEsp {
    /** Sparkling critters already shouted about, by entity id, with when that was. */
    private static final Map<Integer, Long> announced = new HashMap<>();
    /** Whether the two modules want this tick's plates at all. Set by {@link #tick}. */
    private static boolean critters;
    private static boolean sparkling;

    private SafariEsp() {
    }

    /** Works out whether either feature is drawing, and ages out the announcement cooldowns. */
    public static void tick(Minecraft mc) {
        CritterEspModule c = CritterEspModule.INSTANCE;
        SparklingCritterModule s = SparklingCritterModule.INSTANCE;
        boolean here = (c != null && c.isEnabled()) || (s != null && s.isEnabled());
        here = here && Safari.onSafari(mc);
        critters = here && c != null && c.isEnabled();
        sparkling = here && s != null && s.isEnabled();
        if (!here) {
            announced.clear();
            return;
        }
        long cutoff = System.currentTimeMillis() - (s == null ? 60000 : s.repeatMs());
        Iterator<Map.Entry<Integer, Long>> it = announced.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < cutoff) {
                it.remove();
            }
        }
    }

    /** Whether anything here wants a look at this tick's plates. */
    public static boolean active() {
        return critters || sparkling;
    }

    /**
     * Handles one name plate.
     *
     * @return whether it was a critter, so the caller can stop considering it
     */
    public static boolean onPlate(Minecraft mc, ArmorStand stand, String plate) {
        Safari.Critter critter = Safari.onPlate(plate);
        boolean isSparkling = sparkling && Safari.isSparkling(plate);
        // A sparkling plate whose name we do not recognise is still worth marking: the prefix is the
        // valuable half, and a critter the wiki has not caught up with must not be the silent one.
        if (critter == null && !isSparkling) {
            if (critters && CritterEspModule.INSTANCE != null
                    && CritterEspModule.INSTANCE.debugPlates() && !plate.isBlank()) {
                DiegoAddonsV2Client.LOGGER.info("[critter esp] unmatched plate: {}", plate);
            }
            return false;
        }

        Entity body = stand.getVehicle();
        AABB box = body != null
                ? body.getBoundingBox().inflate(0.1)
                : new AABB(stand.getX() - 0.4, stand.getY() - 1.2, stand.getZ() - 0.4,
                        stand.getX() + 0.4, stand.getY() - 0.2, stand.getZ() + 0.4);

        if (isSparkling) {
            drawSparkling(mc, stand, box, body, critter);
            return true;
        }

        CritterEspModule c = CritterEspModule.INSTANCE;
        if (!critters || c == null || !c.wants(critter)) {
            return false;
        }
        if (c.debugPlates()) {
            DiegoAddonsV2Client.LOGGER.info("[critter esp] {} ({} / {}) from plate: {}",
                    critter.name(), critter.biome().display, critter.rarity().display, plate);
        }
        EspRender.draw(body, box, c, c.colorFor(critter));
        if (c.labels()) {
            WorldRender.text(critter.name(),
                    new Vec3(box.getCenter().x, box.maxY + 0.4, box.getCenter().z), 1.0f);
        }
        return true;
    }

    /** The rare one: its own colour, an optional beam, and a shout the first time it is seen. */
    private static void drawSparkling(Minecraft mc, ArmorStand stand, AABB box, Entity body,
                                      Safari.Critter critter) {
        SparklingCritterModule s = SparklingCritterModule.INSTANCE;
        if (s == null) {
            return;
        }
        EspRender.draw(body, box, s);
        Vec3 centre = box.getCenter();
        if (s.beam()) {
            WorldRender.path(List.of(
                            new Vec3(centre.x, box.minY, centre.z),
                            new Vec3(centre.x, box.minY + 20, centre.z)),
                    s.color(), 0.2);
        }
        // Keyed on the entity the plate rides where there is one, so a critter that keeps its body
        // is announced once. The plate itself is the fallback key; either way the cooldown in tick()
        // is what stops a lost entity turning into a repeat every tick.
        int key = body != null ? body.getId() : stand.getId();
        boolean fresh = announced.putIfAbsent(key, System.currentTimeMillis()) == null;
        // The particle route reaches further and will have shouted already, so this stays quiet
        // while it is marking something. If the particle ids turn out to be wrong that is false, and
        // the plate becomes the announcement again - which is the fallback worth having.
        if (fresh && !SparkleParticles.marking()) {
            s.notifyFound(mc, critter == null ? null : critter.name());
        }
    }

    /** Drops the announcement history, e.g. on leaving a world. */
    public static void clear() {
        announced.clear();
    }
}
