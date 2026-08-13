package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.BatEspModule;
import dev.diego.diegoaddons.module.modules.CustomEspModule;
import dev.diego.diegoaddons.module.modules.DungeonMinibossEspModule;
import dev.diego.diegoaddons.module.modules.PestEspModule;
import dev.diego.diegoaddons.module.modules.PlayerEspModule;
import dev.diego.diegoaddons.module.modules.StarredMobEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Boxes drawn around entities: dungeon starred mobs, and anything the user has named themselves.
 *
 * <p>SkyBlock mobs are ordinary vanilla entities wearing a name plate, so both features work the
 * same way - find the plate, then box the mob it belongs to. A plate is an armour stand riding the
 * mob, which is why the box is dropped back down to where the body actually is.
 */
public final class EntityEsp {
    /** The marker SkyBlock puts on dungeon starred mobs. */
    private static final String STAR = "\u272F";
    private static final double RANGE = 64.0;
    private static final double EDGE = 0.05;

    private EntityEsp() {
    }

    /** Called every client tick while either feature is on. */
    public static void tick(Minecraft mc) {
        StarredMobEspModule starred = StarredMobEspModule.INSTANCE;
        CustomEspModule custom = CustomEspModule.INSTANCE;
        BatEspModule bats = BatEspModule.INSTANCE;
        DungeonMinibossEspModule miniboss = DungeonMinibossEspModule.INSTANCE;
        PlayerEspModule players = PlayerEspModule.INSTANCE;
        boolean doStarred = starred != null && starred.isEnabled();
        boolean doCustom = custom != null && custom.isEnabled() && !CustomEsp.all().isEmpty();
        boolean inDungeons = DungeonState.inDungeons();
        boolean doBat = bats != null && bats.isEnabled() && inDungeons;
        boolean doMiniboss = miniboss != null && miniboss.isEnabled() && inDungeons;
        boolean doPlayer = players != null && players.isEnabled();
        PestEspModule pests = PestEspModule.INSTANCE;
        boolean doPest = pests != null && pests.isEnabled() && Pests.inGarden() && pests.shouldDraw(mc);
        // The hunting ESPs decided which of them are drawing before this ran - see Hunting.tick.
        boolean doHunting = Hunting.active();
        // The Safari's critters and its sparkling variants, decided from the same plate - see
        // SafariEsp, which was told whether it is drawing just before this ran.
        boolean doSafari = SafariEsp.active();
        if (!doStarred && !doCustom && !doBat && !doMiniboss && !doPlayer && !doPest && !doHunting
                && !doSafari
                || mc.player == null || mc.level == null) {
            return;
        }

        if (!doMiniboss) {
            tracked.clear();
        }

        AABB area = mc.player.getBoundingBox().inflate(RANGE);
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            // Bats are real entities, not name plates, so they are matched by type.
            if (doBat && e instanceof Bat) {
                // Opening a door drops a crowd of bats through the doorway; boxing those buries the
                // one bat that is actually a secret. See BatEspModule.isDoorBat.
                if (!bats.isDoorBat(e.getDeltaMovement().y)) {
                    EspRender.draw(e, e.getBoundingBox().inflate(0.1), bats);
                }
                continue;
            }
            // Real players, hiding the NPCs that share the player model.
            if (doPlayer && e instanceof Player p && p != mc.player) {
                if (isRealPlayer(mc, p)) {
                    EspRender.draw(p, p.getBoundingBox().inflate(0.05), players);
                }
                continue;
            }
            // The hunting critters are real vanilla animals rather than costumed mobs, so they are
            // the one group here matched by entity type and boxed from their own bounding box.
            if (doHunting && Hunting.onEntity(e)) {
                continue;
            }
            // Safari critters by type, for the ones that carry no plate - see SafariEsp.onEntity.
            if (doSafari && SafariEsp.onEntity(e)) {
                continue;
            }
            if (!(e instanceof ArmorStand stand) || !stand.hasCustomName()) {
                continue;
            }
            String name = LegacyText.strip(stand.getCustomName().getString());
            // Matcho is the one hunting mob wearing a plate rather than being an animal, so it is
            // resolved here with the rest of them rather than in the type pass above.
            dev.diego.diegoaddons.module.HuntingEspModule hunted =
                    doHunting ? Hunting.onPlate(name) : null;

            if (doStarred && name.contains(STAR) && !starred.hideDead(name)) {
                box(stand, starred);
            } else if (doMiniboss && miniboss.matches(name)) {
                // Boxed from its body below, so it keeps its box once it turns invisible and the
                // plate goes with it. Only if the body cannot be resolved do we fall back to the plate.
                if (!remember(mc, stand)) {
                    box(stand, miniboss);
                }
            } else if (doPest && Pests.pestOnPlate(name) != null) {
                if (pests.debugPlates()) {
                    // The raw plate, colour codes and all: what a pet's plate actually says is the
                    // one fact this feature is still missing, and it cannot be guessed twice more.
                    dev.diego.diegoaddons.DiegoAddonsV2Client.LOGGER.info(
                            "[pest esp] boxing plate: {}", stand.getCustomName().getString());
                }
                boxPest(stand, pests);
            } else if (hunted != null) {
                box(stand, hunted);
            } else if (doSafari && SafariEsp.onPlate(mc, stand, name)) {
                // Boxed by SafariEsp itself: a critter's box comes from its own body and its colour
                // from its rarity, neither of which the shared box() knows about.
                continue;
            } else if (doCustom) {
                String match = CustomEsp.match(name);
                if (match != null) {
                    box(stand, custom);
                }
            }
        }

        if (doMiniboss) {
            drawTracked(mc);
        }
    }

    /**
     * Minibosses whose plate we have seen, kept by entity id so they stay boxed after the plate goes.
     *
     * <p>A Shadow Assassin vanishes by turning invisible, and Hypixel takes its name plate away at the
     * same moment - so a solver that only ever draws from plates loses the mob exactly when you most
     * want to see it. The body entity itself stays loaded throughout, so once a plate has identified
     * it we keep drawing from the body and stop only when the entity actually leaves the world.
     */
    private static final java.util.Set<Integer> tracked = new java.util.HashSet<>();

    /** Re-boxes every remembered miniboss from its body, dropping any that have left the world. */
    private static void drawTracked(Minecraft mc) {
        var it = tracked.iterator();
        while (it.hasNext()) {
            Entity body = mc.level.getEntity(it.next());
            if (body == null || !body.isAlive()) {
                it.remove();
                continue;
            }
            if (body.distanceToSqr(mc.player) > RANGE * RANGE) {
                it.remove();   // out of range: forget it rather than hold a stale box
                continue;
            }
            EspRender.draw(body, body.getBoundingBox().inflate(0.05), DungeonMinibossEspModule.INSTANCE);
        }
    }

    /**
     * Records the mob a miniboss plate belongs to. SkyBlock mounts the plate on the mob, so the
     * vehicle is the body; when that is unavailable, the nearest living non-plate entity just below
     * the plate is taken instead.
     */
    private static boolean remember(Minecraft mc, ArmorStand stand) {
        Entity body = stand.getVehicle();
        if (body == null) {
            AABB below = new AABB(
                    stand.getX() - 0.6, stand.getY() - 2.6, stand.getZ() - 0.6,
                    stand.getX() + 0.6, stand.getY(), stand.getZ() + 0.6);
            double best = Double.MAX_VALUE;
            for (Entity e : mc.level.getEntities(stand, below)) {
                if (!(e instanceof LivingEntity) || e instanceof ArmorStand) {
                    continue;
                }
                double d = e.distanceToSqr(stand);
                if (d < best) {
                    best = d;
                    body = e;
                }
            }
        }
        if (body == null) {
            return false;
        }
        tracked.add(body.getId());
        return true;
    }

    /**
     * A real party/lobby player versus a SkyBlock NPC. NPCs are spawned as client-side player entities
     * with a version-2 UUID and no tab-list entry; real accounts are version 4 and are listed.
     */
    private static boolean isRealPlayer(Minecraft mc, Player p) {
        if (p.getUUID().version() != 4) {
            return false;
        }
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(p.getUUID()) != null;
    }

    /**
     * A pest, boxed from its own body rather than from a guess below the plate.
     *
     * <p>The fixed drop {@link #box} uses is right for a player-sized dungeon mob and wrong for a
     * fly: pests come in every size from a mite to a field mouse, so a box sized for one of them
     * floats off every other. The body carries its own bounding box, so when it can be found that is
     * what gets drawn; the drop below the plate is only the fallback for when it cannot.
     */
    private static void boxPest(ArmorStand stand, dev.diego.diegoaddons.module.EspModule module) {
        Entity body = stand.getVehicle();
        if (body != null) {
            EspRender.draw(body, body.getBoundingBox().inflate(0.1), module);
            return;
        }
        AABB box = new AABB(
                stand.getX() - 0.4, stand.getY() - 1.2, stand.getZ() - 0.4,
                stand.getX() + 0.4, stand.getY() - 0.2, stand.getZ() + 0.4);
        EspRender.draw(null, box, module);
    }

    /** The plate floats above its mob, so the box is placed on the body below it. */
    private static void box(ArmorStand stand, dev.diego.diegoaddons.module.EspModule module) {
        AABB box = new AABB(
                stand.getX() - 0.45, stand.getY() - 2.1, stand.getZ() - 0.45,
                stand.getX() + 0.45, stand.getY() - 0.3, stand.getZ() + 0.45);
        // The mob the plate belongs to, when it can be found: the model outline needs an entity,
        // and a plate is not the thing you want outlined.
        Entity body = stand.getVehicle();
        EspRender.draw(body, box, module);
    }
}
