package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Higher/Lower blaze puzzle: highlights the blaze to shoot next.
 *
 * <p>The blazes carry their health in the name plate above them, so the order is read from those
 * rather than from anything about the room. Which end of the order to start at is the one thing the
 * name plates cannot say - the dungeon tab list only ever calls the puzzle "Higher Or Lower" - so it
 * is read from the sign hanging in the room, with a manual override for the case where that fails.
 */
public final class BlazeSolver {
    /** "[Lv15] Blaze 1,234/5,678❤" - the health plate above each blaze. */
    private static final Pattern HEALTH = Pattern.compile("Blaze\\s+[\\d,]+/([\\d,]+)");
    /** How far to look for the blazes and the sign. */
    private static final double RANGE = 40.0;

    private static final int NEXT = 0xFF00FF00;
    private static final int SECOND = 0xFFFFFF00;
    private static final int REST = 0x60FFFFFF;

    /** True when the room wants the highest health shot first. Sticky once detected. */
    private static Boolean highestFirst;

    private BlazeSolver() {
    }

    public static void reset() {
        highestFirst = null;
    }

    /** Called every client tick while the solver is on. */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.blaze() || mc.level == null || mc.player == null) {
            return;
        }
        List<Blaze> blazes = findBlazes(mc);
        if (blazes.size() < 2) {
            return;   // not the puzzle, or it is already done
        }
        if (highestFirst == null) {
            highestFirst = detectOrder(mc);
        }
        boolean highFirst = highestFirst != null ? highestFirst : mod.blazeHighestFirst();

        blazes.sort(highFirst
                ? Comparator.comparingInt((Blaze b) -> b.health).reversed()
                : Comparator.comparingInt(b -> b.health));

        for (int i = 0; i < blazes.size(); i++) {
            int color = i == 0 ? NEXT : (i == 1 ? SECOND : REST);
            if (i > 1 && !mod.blazeShowAll()) {
                break;
            }
            WorldRender.box(blazes.get(i).box, color, true);
        }
    }

    private record Blaze(int health, AABB box) {
    }

    /**
     * The blazes around the player, with the health from their name plate. The plate is an armour
     * stand riding the blaze, so its position is used and lifted back down to the blaze's body.
     */
    private static List<Blaze> findBlazes(Minecraft mc) {
        List<Blaze> out = new ArrayList<>();
        AABB area = mc.player.getBoundingBox().inflate(RANGE);
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (!(e instanceof ArmorStand stand) || !stand.hasCustomName()) {
                continue;
            }
            String name = LegacyText.strip(stand.getCustomName().getString());
            Matcher m = HEALTH.matcher(name);
            if (!m.find()) {
                continue;
            }
            int max;
            try {
                max = Integer.parseInt(m.group(1).replace(",", ""));
            } catch (NumberFormatException ex) {
                continue;
            }
            // The plate floats above the blaze; drop it back onto the body.
            AABB box = new AABB(stand.getX() - 0.5, stand.getY() - 2.2, stand.getZ() - 0.5,
                    stand.getX() + 0.5, stand.getY() - 0.4, stand.getZ() + 0.5);
            out.add(new Blaze(max, box));
        }
        return out;
    }

    /**
     * Reads the room's sign to decide which end of the health order to start at. Returns null when
     * no sign is in range, leaving the caller to fall back to the manual setting.
     */
    private static Boolean detectOrder(Minecraft mc) {
        var level = mc.level;
        var origin = mc.player.blockPosition();
        int r = 24;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    var pos = origin.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) {
                        continue;
                    }
                    String text = signText(sign).toLowerCase(Locale.ROOT);
                    if (text.contains("highest")) {
                        return Boolean.TRUE;
                    }
                    if (text.contains("lowest")) {
                        return Boolean.FALSE;
                    }
                }
            }
        }
        return null;
    }

    private static String signText(net.minecraft.world.level.block.entity.SignBlockEntity sign) {
        StringBuilder sb = new StringBuilder();
        for (var line : sign.getFrontText().getMessages(false)) {
            sb.append(LegacyText.strip(line.getString())).append(' ');
        }
        return sb.toString();
    }
}
