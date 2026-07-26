package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Teleport Maze: highlights the single correct pad to step on next.
 *
 * <p>The maze is a fixed set of pads with a route that changes each run, and nothing in the world
 * labels the route. The one tell is the teleport itself: when a pad throws you, Hypixel snaps your
 * view to face the <b>correct next pad</b> so you know where to go. So the solver watches for the
 * position jump a teleport makes, waits a tick for that forced rotation to land, then picks the pad
 * your look vector is pointing at and marks only it. Nothing is drawn until the first teleport, since
 * that is the first moment the game reveals a direction.
 *
 * <p>The pad positions are recorded relative to the room and turned to its rotation like every other
 * solver here; only <i>which</i> of them is correct comes from the look angle.
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
    private static final int CORRECT = 0xFF00FF00;

    /** A one-tick position jump larger than this is a teleport, not walking or a sprint-jump. */
    private static final double TP_JUMP = 1.5;
    /** Skip pads nearer than this (the one under your feet) or farther than this when aiming. */
    private static final double MIN_DIST = 1.5;
    private static final double MAX_DIST = 16.0;
    /** Minimum look-to-pad alignment (cosine) to accept a pad as the one being faced (~0.85 = 32deg). */
    private static final double MIN_ALIGN = 0.85;
    /** Ticks to wait after a teleport before reading the rotation, so the forced turn has applied. */
    private static final int SETTLE_TICKS = 2;

    private static final List<BlockPos> WORLD_PADS = new ArrayList<>();
    private static String lastRoom;
    private static Vec3 lastPos;
    private static int computeIn;
    private static BlockPos correctPad;

    private TpMazeSolver() {
    }

    public static void reset() {
        WORLD_PADS.clear();
        lastRoom = null;
        lastPos = null;
        computeIn = 0;
        correctPad = null;
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
            correctPad = null;
            lastPos = null;
            computeIn = 0;
            place();
        }
        if (WORLD_PADS.isEmpty()) {
            place();
            return;
        }

        // A teleport moves you several blocks in one tick; walking never does. When one happens, the
        // game has just turned you to face the correct pad - read it a couple ticks later once applied.
        Vec3 pos = mc.player.position();
        if (lastPos != null && pos.distanceTo(lastPos) > TP_JUMP) {
            computeIn = SETTLE_TICKS;
        }
        lastPos = pos;
        if (computeIn > 0 && --computeIn == 0) {
            correctPad = padInFront(mc);
        }

        if (correctPad != null) {
            WorldRender.thickBox(new AABB(correctPad), CORRECT, LINE, true);
        }
    }

    /** The pad your look vector points at, or the previous one if nothing lines up well enough. */
    private static BlockPos padInFront(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        double lookLen = Math.sqrt(look.x * look.x + look.z * look.z);
        if (lookLen < 1e-6) {
            return correctPad;   // staring straight up or down tells us nothing; keep the last pad
        }
        BlockPos best = null;
        double bestDot = MIN_ALIGN;
        for (BlockPos pad : WORLD_PADS) {
            double dx = (pad.getX() + 0.5) - eye.x;
            double dz = (pad.getZ() + 0.5) - eye.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < MIN_DIST || d > MAX_DIST) {
                continue;
            }
            double dot = (dx * look.x + dz * look.z) / (d * lookLen);
            if (dot > bestDot) {
                bestDot = dot;
                best = pad;
            }
        }
        return best != null ? best : correctPad;
    }

    /** Turns the recorded pads into world positions once the room's rotation is known. */
    private static void place() {
        WORLD_PADS.clear();
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
