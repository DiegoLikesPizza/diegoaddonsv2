package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Live Slayer quest state, read once per tick from the same places Hypixel exposes it: the
 * <b>sidebar scoreboard</b> names the active boss and its tier while a quest is running, and the
 * <b>world</b> tells whether that boss is currently spawned. Mirrors {@link DungeonState}: every
 * value is a parse of text or entities the server already shows the player, nothing is guessed.
 *
 * <p>The player's own boss is found from the tell every Slayer boss carries - a floating
 * "Spawned by: &lt;name&gt;" armour stand above it - so a lobby full of other players' bosses never
 * confuses it. {@link #bossEntity()} is the mob under that stand, ready for a highlight or tracer.
 */
public final class SlayerState {
    /** The six Slayer bosses, matched by the display name Hypixel puts on the sidebar quest line. */
    public enum Type {
        REVENANT("Revenant Horror"),
        TARANTULA("Tarantula Broodfather"),
        SVEN("Sven Packmaster"),
        VOIDGLOOM("Voidgloom Seraph"),
        INFERNO("Inferno Demonlord"),
        BLOODFIEND("Riftstalker Bloodfiend");

        /** How the boss is named on the sidebar (and, tier aside, on its own health plate). */
        public final String display;

        Type(String display) {
            this.display = display;
        }
    }

    /** How far to look for the boss's "Spawned by" stand. */
    private static final double SEARCH = 32.0;

    private static boolean inSlayer;
    private static Type type;
    private static int tier;             // 1..5, 0 if not read
    private static boolean bossAlive;
    private static Entity bossEntity;

    private SlayerState() {
    }

    public static boolean inSlayer() {
        return inSlayer;
    }

    /** The active Slayer boss type, or null when no quest is running. */
    public static Type activeType() {
        return type;
    }

    /** The active quest's tier (1..5), or 0 if it could not be read. */
    public static int tier() {
        return tier;
    }

    /** True while the player's own boss is spawned in the world. */
    public static boolean bossAlive() {
        return bossAlive;
    }

    /** The player's own boss mob, or null when none is spawned. */
    public static Entity bossEntity() {
        return bossEntity;
    }

    public static void reset() {
        inSlayer = false;
        type = null;
        tier = 0;
        bossAlive = false;
        bossEntity = null;
    }

    /** Called every client tick. Cheap when no quest is active (bails after the sidebar parse). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        Type found = null;
        int foundTier = 0;
        for (String line : sidebarLines(mc)) {
            for (Type ty : Type.values()) {
                if (line.contains(ty.display)) {
                    found = ty;
                    foundTier = roman(line);
                    break;
                }
            }
            if (found != null) {
                break;
            }
        }
        inSlayer = found != null;
        type = found;
        tier = foundTier;
        if (!inSlayer) {
            bossAlive = false;
            bossEntity = null;
            return;
        }
        locateBoss(mc);
    }

    /**
     * Finds the player's own boss: the mob under the "Spawned by: &lt;name&gt;" armour stand. The
     * stand rides the boss on most Hypixel bosses, so its vehicle is preferred; otherwise the nearest
     * non-stand living entity below the stand is taken.
     */
    private static void locateBoss(Minecraft mc) {
        String me = mc.player.getName().getString();
        ArmorStand owner = null;
        AABB area = mc.player.getBoundingBox().inflate(SEARCH);
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (e instanceof ArmorStand as && as.hasCustomName()) {
                String n = LegacyText.strip(as.getCustomName().getString());
                if (n.contains("Spawned by") && n.contains(me)) {
                    owner = as;
                    break;
                }
            }
        }
        if (owner == null) {
            bossAlive = false;
            bossEntity = null;
            return;
        }
        bossAlive = true;

        if (owner.getVehicle() instanceof LivingEntity ride && !(owner.getVehicle() instanceof ArmorStand)) {
            bossEntity = ride;
            return;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB near = owner.getBoundingBox().inflate(3, 8, 3);
        for (Entity e : mc.level.getEntities(owner, near)) {
            if (e instanceof LivingEntity le && !(e instanceof ArmorStand) && !(e instanceof Player)) {
                double dx = e.getX() - owner.getX();
                double dz = e.getZ() - owner.getZ();
                double d = dx * dx + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    best = le;
                }
            }
        }
        bossEntity = best;
    }

    /** Trailing roman numeral I..V on a line ("Revenant Horror IV" -> 4), or 0 if none. */
    private static int roman(String line) {
        String last = line.substring(line.lastIndexOf(' ') + 1).trim();
        return switch (last) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> 0;
        };
    }

    private static List<String> sidebarLines(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (mc.player == null || mc.player.connection == null) {
            return out;
        }
        Scoreboard sb = mc.player.connection.scoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            return out;
        }
        for (ScoreHolder holder : sb.getTrackedPlayers()) {
            if (!sb.listPlayerScores(holder).containsKey(obj)) {
                continue;
            }
            PlayerTeam team = sb.getPlayersTeam(holder.getScoreboardName());
            if (team == null) {
                continue;
            }
            String line = LegacyText.strip(
                    team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString()).trim();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }
}
