package dev.diego.diegoaddons.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boulder: shows which block to push next.
 *
 * <p>The room is one of a handful of fixed layouts, told apart by reading the board itself - a grid
 * of positions that either hold a boulder or do not, which spells out a 42-character pattern. That
 * pattern selects the recorded sequence of pushes.
 *
 * <p>The board is read <b>once per room</b>, not continuously: pushing a boulder changes the board,
 * and re-reading it would then match a different layout and hand out a different solution halfway
 * through. Instead the pushes are ticked off as they are made.
 */
public final class BoulderSolver {
    private static final int EDGE = 0;
    private static final double LINE = 0.06;
    private static final int NEXT = 0xFF00FF00;
    private static final int LATER = 0x60FFFFFF;

    /** One recorded push: the block to hit, and where the game registers the click. */
    private record Push(BlockPos show, BlockPos click) {
    }

    private static Map<String, List<int[]>> solutions;
    private static final List<Push> REMAINING = new ArrayList<>();
    private static boolean readThisRoom;
    private static String lastRoom;

    private BoulderSolver() {
    }

    public static void reset() {
        REMAINING.clear();
        readThisRoom = false;
        lastRoom = null;
    }

    /** Called every client tick while the solver is on. */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.boulder() || mc.level == null) {
            return;
        }
        String room = DungeonRooms.currentRoomName();
        if (!"Boulder".equals(room)) {
            if (lastRoom != null) {
                reset();
            }
            return;
        }
        if (!room.equals(lastRoom)) {
            lastRoom = room;
            readThisRoom = false;
            REMAINING.clear();
        }
        if (!readThisRoom) {
            readBoard(mc);
        }

        boolean all = mod.boulderShowAll();
        for (int i = 0; i < REMAINING.size(); i++) {
            if (i > 0 && !all) {
                break;
            }
            WorldRender.thickBox(new AABB(REMAINING.get(i).show()), i == 0 ? NEXT : LATER, LINE, true);
        }
    }

    /**
     * Reads the board into the pattern that names this layout.
     *
     * <p>The scan order matters as much as the positions - it is what makes the pattern comparable
     * with the recorded ones, so it walks the grid in the same direction they were recorded in.
     */
    private static void readBoard(Minecraft mc) {
        StringBuilder pattern = new StringBuilder(42);
        for (int z = 24; z >= 9; z -= 3) {
            for (int x = 24; x >= 6; x -= 3) {
                BlockPos pos = DungeonRooms.toWorld(new BlockPos(x, 66, z));
                if (pos == null) {
                    return;   // rotation not known yet; try again next tick
                }
                pattern.append(mc.level.getBlockState(pos).isAir() ? '0' : '1');
            }
        }
        readThisRoom = true;

        List<int[]> solution = table().get(pattern.toString());
        REMAINING.clear();
        if (solution == null) {
            return;
        }
        for (int[] s : solution) {
            BlockPos show = DungeonRooms.toWorld(new BlockPos(s[0], 65, s[1]));
            BlockPos click = DungeonRooms.toWorld(new BlockPos(s[2], 65, s[3]));
            if (show != null && click != null) {
                REMAINING.add(new Push(show, click));
            }
        }
    }

    /** Ticks off a push once the player has made it. */
    public static void onInteract(BlockPos clicked) {
        REMAINING.removeIf(p -> p.click().equals(clicked));
    }

    private static Map<String, List<int[]>> table() {
        if (solutions != null) {
            return solutions;
        }
        solutions = new HashMap<>();
        Identifier id = Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "puzzles/boulder.json");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open();
                     InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (var e : root.entrySet()) {
                        List<int[]> moves = new ArrayList<>();
                        for (JsonElement m : e.getValue().getAsJsonArray()) {
                            JsonArray a = m.getAsJsonArray();
                            moves.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(),
                                    a.get(2).getAsInt(), a.get(3).getAsInt()});
                        }
                        solutions.put(e.getKey(), moves);
                    }
                }
            }
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Could not load boulder solutions: {}", e.toString());
        }
        return solutions;
    }
}
