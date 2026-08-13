package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.InvisibugEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds Invisibugs, which have nothing to find.
 *
 * <p>An Invisibug has no model, no name plate and no hit box worth speaking of - it is a marker
 * armour stand that Hypixel makes visible by spraying crit particles around it, and the mob is
 * "nearly invisible" by design. So neither of the mod's usual two ways in works: there is no entity
 * type to match and no plate to read.
 *
 * <p><b>The particles are therefore the signal.</b> Every crit particle the server sends is checked
 * against the armour stands near where it landed, and the nearest <i>plain</i> stand - no name, no
 * equipment, so not somebody's pet plate or a floating label - is taken to be the bug. Once found it
 * is remembered by entity id and drawn from the stand itself, which keeps the box steady between
 * particles rather than making it flicker with them.
 *
 * <p>Two things follow from that and are worth stating plainly. A bug is only found <b>after</b> it
 * has thrown a particle, so there is a moment on approach where it is not yet boxed. And the whole
 * thing rests on the assumption that a marker stand is there at all - if nothing is ever boxed while
 * standing in a cloud of crit particles, that assumption is the first thing to check.
 */
public final class Invisibug {
    /** How far from a particle to look for the stand it belongs to. */
    private static final double SEARCH = 5.0;
    /** Beyond this a remembered bug is dropped rather than kept as a stale box. */
    private static final double RANGE = 64.0;

    /**
     * The armour stands taken to be bugs, by entity id.
     *
     * <p>Ids rather than the entities themselves: an entity that leaves the world has to stop being
     * drawn, and asking the level for the id every tick is what makes that automatic - the same
     * reasoning as {@code EntityEsp}'s miniboss tracking.
     */
    private static final Set<Integer> found = new LinkedHashSet<>();

    private Invisibug() {
    }

    /**
     * A particle the server just sent. Called from the packet handler on the client thread.
     *
     * <p>Cheap on purpose: crit particles arrive by the hundred from every player hitting anything,
     * so the type check and the module check come before any search of the world.
     */
    public static void onParticle(ParticleOptions options, double x, double y, double z) {
        InvisibugEspModule m = InvisibugEspModule.INSTANCE;
        if (m == null || options.getType() != ParticleTypes.CRIT) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !m.shouldDraw(mc)) {
            return;
        }
        // A bug throws particles continuously, so most of these are about one already known. Checking
        // that first is what keeps this from searching the world several times a second per bug.
        for (int id : found) {
            Entity known = mc.level.getEntity(id);
            if (known != null && known.position().distanceToSqr(x, y, z) < SEARCH * SEARCH) {
                return;
            }
        }

        AABB area = new AABB(x - SEARCH, y - SEARCH, z - SEARCH, x + SEARCH, y + SEARCH, z + SEARCH);
        List<ArmorStand> stands =
                mc.level.getEntities(EntityType.ARMOR_STAND, area, Invisibug::isPlain);
        ArmorStand best = null;
        double bestDist = Double.MAX_VALUE;
        for (ArmorStand stand : stands) {
            double d = stand.position().distanceToSqr(x, y, z);
            if (d < bestDist) {
                bestDist = d;
                best = stand;
            }
        }
        if (best != null) {
            found.add(best.getId());
        }
    }

    /**
     * A stand carrying nothing at all.
     *
     * <p>SkyBlock hangs armour stands everywhere - name plates, held-item props, the floating heads
     * on half the islands - and every one of those carries either a custom name or a piece of
     * equipment. A stand with neither is the shape an invisible marker takes, so this is what
     * separates the bug's stand from the scenery.
     */
    private static boolean isPlain(ArmorStand stand) {
        if (stand.hasCustomName()) {
            return false;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!stand.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Draws every remembered bug, dropping the ones that have gone. Called once per client tick. */
    public static void tick(Minecraft mc) {
        InvisibugEspModule m = InvisibugEspModule.INSTANCE;
        if (m == null || !m.isEnabled() || mc.level == null || mc.player == null) {
            found.clear();
            return;
        }
        if (!m.shouldDraw(mc) || found.isEmpty()) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        double half = m.size() / 2.0;
        var it = found.iterator();
        while (it.hasNext()) {
            Entity e = mc.level.getEntity(it.next());
            if (e == null || !e.isAlive() || e.distanceToSqr(mc.player) > RANGE * RANGE) {
                it.remove();
                continue;
            }
            Vec3 centre = e.position().add(0, m.lift(), 0);
            AABB box = new AABB(
                    centre.x - half, centre.y - half, centre.z - half,
                    centre.x + half, centre.y + half, centre.z + half);
            // No entity handed over: a marker has no model, so the "Player outline" style has
            // nothing to outline and should fall back to the box - which is what a null does.
            EspRender.draw(null, box, m);
            if (m.tracer()) {
                WorldRender.line(eye, centre, m.tracerColor());
            }
        }
    }

    /** Drops everything. The stands do not survive a world change and neither should their ids. */
    public static void clear() {
        found.clear();
    }
}
