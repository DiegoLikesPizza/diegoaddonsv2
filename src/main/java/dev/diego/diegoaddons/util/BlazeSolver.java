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
 * <p>The blazes carry their health in the name plate above them, so the order itself is read from
 * those. Which <i>end</i> of the order to start at cannot be read anywhere in the room: the tab list
 * only ever calls the puzzle "Higher Or Lower", and there is no sign or hologram naming the variant.
 * It comes from identifying the <b>room</b> instead - "Higher Blaze" or "Lower Blaze" - which is how
 * every established dungeon mod solves this. See {@link DungeonRooms}.
 *
 * <p>Until the room is identified the solver highlights nothing, rather than guessing an order and
 * being confidently wrong.
 */
public final class BlazeSolver {
    /** "[Lv15] Blaze 1,234/5,678❤" - the health plate above each blaze. */
    private static final Pattern HEALTH = Pattern.compile("Blaze\\s+[\\d,]+/([\\d,]+)");
    /** How far to look for the blazes and the instruction hologram. */
    private static final double RANGE = 40.0;

    private static final int NEXT = 0xFF00FF00;
    private static final int SECOND = 0xFFFFFF00;
    private static final int REST = 0x80FFFFFF;
    /** Edge width in blocks. A vanilla line is about an eighth of this at arm's length. */
    private static final double EDGE = 0.08;

    /** True when the room wants the highest health shot first; null until the room says so. */
    private static Boolean highestFirst;
    /** TEMP: guards the one-shot room diagnostic so it prints once per blaze room, not every tick. */
    private static boolean debugPrinted;

    private BlazeSolver() {
    }

    public static void reset() {
        highestFirst = null;
        debugPrinted = false;
    }

    /** What the solver currently believes, for the readout in chat. */
    public static Boolean detectedOrder() {
        return highestFirst;
    }

    /** Called every client tick while the solver is on. */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.blaze() || mc.level == null || mc.player == null) {
            return;
        }
        String room = DungeonRooms.currentRoomName();
        if (room != null) {
            // "Lower Blaze" = shoot from the lowest health up; "Higher Blaze" = from the highest down.
            if (room.equals("Lower Blaze")) {
                highestFirst = Boolean.FALSE;
            } else if (room.equals("Higher Blaze")) {
                highestFirst = Boolean.TRUE;
            }
        }

        List<Blaze> blazes = new ArrayList<>();
        scan(mc, blazes);
        if (blazes.size() < 2) {
            debugPrinted = false;   // re-arm the diagnostic for the next blaze room
            return;   // not the puzzle, or it is already finished
        }

        // TEMP DIAGNOSTIC: prints the detected room name + core hash once per blaze room so a
        // mislabelled variant (Higher reading as Lower) can be corrected in rooms.json. Remove once
        // the blaze rooms are confirmed correct.
        if (!debugPrinted) {
            debugPrinted = true;
            String info = DungeonRooms.debugCurrentTile(mc);
            if (info != null && mc.gui != null) {
                mc.gui.getChat().addClientSystemMessage(
                        net.minecraft.network.chat.Component.literal("§b[DiegoAddons] §eBlaze room " + info));
            }
        }

        Boolean useHighest = highestFirst != null ? highestFirst : mod.blazeFallbackOrder();
        if (useHighest == null) {
            return;
        }

        blazes.sort(useHighest
                ? Comparator.comparingInt((Blaze b) -> b.health).reversed()
                : Comparator.comparingInt(b -> b.health));

        int shown = mod.blazeShowAll() ? blazes.size() : Math.min(2, blazes.size());
        for (int i = 0; i < shown; i++) {
            int color = i == 0 ? NEXT : (i == 1 ? SECOND : REST);
            WorldRender.thickBox(blazes.get(i).box, color, EDGE, true);
        }
    }

    private record Blaze(int health, AABB box) {
    }

    /** Collects the nearby blazes from their health plates. */
    private static void scan(Minecraft mc, List<Blaze> out) {
        AABB area = mc.player.getBoundingBox().inflate(RANGE);
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (!(e instanceof ArmorStand stand) || !stand.hasCustomName()) {
                continue;
            }
            String name = LegacyText.strip(stand.getCustomName().getString());

            Matcher m = HEALTH.matcher(name);
            if (m.find()) {
                try {
                    int max = Integer.parseInt(m.group(1).replace(",", ""));
                    // The plate floats above the blaze; drop it back onto the body.
                    out.add(new Blaze(max, new AABB(
                            stand.getX() - 0.5, stand.getY() - 2.2, stand.getZ() - 0.5,
                            stand.getX() + 0.5, stand.getY() - 0.4, stand.getZ() + 0.5)));
                } catch (NumberFormatException ignored) {
                    // A plate that does not parse is simply not a blaze we can order.
                }
            }
        }
    }

}
