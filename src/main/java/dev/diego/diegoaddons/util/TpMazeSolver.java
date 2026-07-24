package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Teleport Maze: marks the pads you have not stepped on yet.
 *
 * <p>The maze is a fixed set of pads and the puzzle is purely about not going in circles, so the
 * useful thing to show is not a route but <b>which pads are still unexplored</b>. Pads are recorded
 * relative to the room and turned to its rotation like every other solver here.
 *
 * <p>A pad counts as visited when you stand on it, which is also the only moment the game gives you:
 * the teleport is what moves you, so there is nothing to intercept beforehand.
 */
public final class TpMazeSolver {
    /** The pads, relative to the room. */
    private static final int[][] PADS = {
            {4, 69, 12}, {4, 69, 6}, {10, 69, 12}, {10, 69, 6},
            {4, 69, 20}, {4, 69, 14}, {10, 69, 20}, {10, 69, 14},
            {4, 69, 28}, {4, 69, 22}, {10, 69, 28}, {10, 69, 22},
            {12, 69, 28}, {12, 69, 22}, {18, 69, 28}, {18, 69, 22},
            {20, 69, 28}, {20, 69, 22}, {26, 69, 28}, {26, 69, 22},
            {26, 69, 20}, {26, 69, 14}, {20, 69, 20}, {20, 69, 14},
            {26, 69, 12}, {26, 69, 6}, {20, 69, 12}, {20, 69, 6},
            {15, 69, 14}, {15, 69, 12},
    };

    private static final double LINE = 0.06;
    private static final int UNVISITED = 0xFF00FF00;
    private static final int VISITED = 0x50FFFFFF;
    /** How close you have to be to a pad to count as standing on it. */
    private static final double REACH = 1.2;

    private static final List<BlockPos> WORLD_PADS = new ArrayList<>();
    private static final Set<BlockPos> VISITED_PADS = new HashSet<>();
    private static String lastRoom;

    private TpMazeSolver() {
    }

    public static void reset() {
        WORLD_PADS.clear();
        VISITED_PADS.clear();
        lastRoom = null;
    }

    /** Called every client tick while the solver is on. */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.tpMaze() || mc.player == null || mc.level == null) {
            return;
        }
        String room = DungeonRooms.currentRoomName();
        if (!"Teleport Maze".equals(room)) {
            if (lastRoom != null) {
                reset();
            }
            return;
        }
        if (!room.equals(lastRoom)) {
            lastRoom = room;
            place();
        }
        if (WORLD_PADS.isEmpty()) {
            place();
            return;
        }

        BlockPos standing = mc.player.blockPosition();
        for (BlockPos pad : WORLD_PADS) {
            if (Math.abs(pad.getX() - standing.getX()) <= REACH
                    && Math.abs(pad.getZ() - standing.getZ()) <= REACH
                    && Math.abs(pad.getY() - standing.getY()) <= 2) {
                VISITED_PADS.add(pad);
            }
        }
        for (BlockPos pad : WORLD_PADS) {
            boolean seen = VISITED_PADS.contains(pad);
            if (seen && !mod.tpMazeShowVisited()) {
                continue;
            }
            WorldRender.thickBox(new AABB(pad), seen ? VISITED : UNVISITED, LINE, true);
        }
    }

    /** Turns the recorded pads into world positions once the room's rotation is known. */
    private static void place() {
        WORLD_PADS.clear();
        VISITED_PADS.clear();
        for (int[] p : PADS) {
            BlockPos pos = DungeonRooms.toWorld(new BlockPos(p[0], p[1], p[2]));
            if (pos == null) {
                WORLD_PADS.clear();
                return;   // rotation not known yet; try again next tick
            }
            WORLD_PADS.add(pos);
        }
    }
}
