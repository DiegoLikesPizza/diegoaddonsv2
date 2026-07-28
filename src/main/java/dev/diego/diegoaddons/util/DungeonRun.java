package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.MimicMessageModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * Notices when one dungeon run has ended and another has begun, and wipes everything the last one
 * left behind.
 *
 * <p>Everything the dungeon features know is keyed by a room's <b>position in the grid</b> - which
 * room is at tile 3,2, which way it faces, whether the seam beside it is a doorway or the middle of
 * a room. All of that is true of one run and meaningless in the next, and none of it was ever
 * cleared: the only reset hung off disconnecting, and going from one run to the next never
 * disconnects you. Two runs back to back meant the second was drawn, and solved, as the first.
 *
 * <p>The run is identified by <b>the map in your hotbar</b>. Hypixel hands out a fresh one per run,
 * so its id changing is the run changing - available the moment you load in, and it does not depend
 * on catching a chat line or a scoreboard change.
 */
public final class DungeonRun {
    /** The map this run was last seen holding, or null outside a dungeon. */
    private static MapId currentMap;
    private static boolean wasInDungeons;

    private DungeonRun() {
    }

    /** Called every client tick, before anything reads the room grid. */
    public static void tick(Minecraft mc) {
        boolean in = DungeonState.inDungeons();
        if (!in) {
            // Left the dungeon: the next run starts from nothing either way.
            if (wasInDungeons) {
                clear();
            }
            wasInDungeons = false;
            currentMap = null;
            return;
        }
        wasInDungeons = true;

        MapId id = mapId(mc);
        if (id == null) {
            return;   // no map in the hotbar yet; keep what we have until one turns up
        }
        if (!id.equals(currentMap)) {
            if (currentMap != null) {
                clear();
            }
            currentMap = id;
        }
    }

    /** The id of the dungeon map in the player's inventory, or null while there is not one. */
    private static MapId mapId(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            MapId id = stack.get(DataComponents.MAP_ID);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** Everything a run leaves behind, forgotten in one place. */
    public static void clear() {
        DungeonRooms.reset();
        dev.diego.diegoaddons.module.modules.DungeonMapModule.forgetSeams();
        DungeonMapData.reset();
        DungeonState.resetRun();
        PuzzleSolvers.reset();
        BlazeSolver.reset();
        BeamsSolver.reset();
        BoulderSolver.reset();
        IceFillSolver.reset();
        WaterSolver.reset();
        TpMazeSolver.reset();
        TicTacToeSolver.reset();
        SecretChime.reset();
        if (MimicMessageModule.INSTANCE != null) {
            MimicMessageModule.INSTANCE.resetRun();
        }
    }

    /** Forgets which run we were in, so the next tick treats whatever it finds as a new one. */
    public static void forget() {
        currentMap = null;
        wasInDungeons = false;
    }
}
