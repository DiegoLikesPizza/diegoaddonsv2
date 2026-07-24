package dev.diego.diegoaddons.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Identifies which dungeon room the player is standing in, by name.
 *
 * <p>Nothing in the world says what a room is called - the tab list names puzzles but not their
 * variant, and rooms carry no sign or hologram. What does identify a room is its <b>build</b>: the
 * column of blocks through its centre is unique per room, so hashing that column and looking the
 * hash up in a table of known rooms names it. This is how the established dungeon mods do it, and
 * the hashes here come from the same shared table.
 *
 * <p>The hash is order-sensitive and skips the blocks that differ between runs of the same room -
 * chests and the planks around them - so a looted room still matches an unlooted one.
 */
public final class DungeonRooms {
    /** 6x6 tiles of 32 blocks; the world origin sits at -201 so tile 0 lands at index 0. */
    private static final int GRID = 6;
    private static final int WORLD_OFFSET = 201;
    private static final int ROOM_SHIFT = 5;

    /** One entry of the room table. */
    public record RoomData(String name, String type, String shape) {
    }

    private static Map<Integer, RoomData> byCore;
    /** Rooms already identified this dungeon, keyed by tile index. */
    private static final Map<Integer, RoomData> IDENTIFIED = new HashMap<>();
    /** Ticks left before retrying a tile that did not resolve, so an unknown room is not rescanned
     * every tick - the chunk may simply not have arrived yet, but hashing a full column is not free. */
    private static final int RETRY_TICKS = 20;
    private static int retryIn;

    private static int tileX = -1;
    private static int tileZ = -1;
    private static RoomData current;

    private DungeonRooms() {
    }

    public static RoomData currentRoom() {
        return current;
    }

    /** The current room's name, or null when it is unknown or outside the grid. */
    public static String currentRoomName() {
        return current == null ? null : current.name();
    }

    public static void reset() {
        IDENTIFIED.clear();
        retryIn = 0;
        current = null;
        tileX = -1;
        tileZ = -1;
    }

    /** Called every client tick by whichever feature needs a room. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            current = null;
            return;
        }
        int tx = (mc.player.getBlockX() + WORLD_OFFSET) >> ROOM_SHIFT;
        int tz = (mc.player.getBlockZ() + WORLD_OFFSET) >> ROOM_SHIFT;
        if (tx < 0 || tx >= GRID || tz < 0 || tz >= GRID) {
            current = null;   // boss room or outside the dungeon grid
            return;
        }
        tileX = tx;
        tileZ = tz;
        int key = tx + tz * GRID;

        RoomData known = IDENTIFIED.get(key);
        if (known != null) {
            current = known;
            return;
        }
        if (retryIn > 0) {
            retryIn--;
            return;
        }
        RoomData found = identify(mc, tx, tz);
        if (found != null) {
            IDENTIFIED.put(key, found);
        } else {
            retryIn = RETRY_TICKS;
        }
        current = found;
    }

    /** Scans the room's core column and looks the resulting hash up in the table. */
    private static RoomData identify(Minecraft mc, int tx, int tz) {
        // Tiles map back to chunks as chunk = (tile - 6) * 2; the core column sits at block 7 of it.
        int chunkX = (tx - GRID) * 2;
        int chunkZ = (tz - GRID) * 2;
        LevelChunk chunk = mc.level.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        int x = chunkX * 16 + 7;
        int z = chunkZ * 16 + 7;
        return table().get(coreHash(chunk, x, z));
    }

    /**
     * Hashes the column of blocks through the room's centre.
     *
     * <p>Everything above the room is skipped until the first real block, and everything below its
     * floor once bedrock has been passed, so only the room's own build contributes. Chests and the
     * planks beside them are left out because they change as a room is looted.
     */
    private static int coreHash(LevelChunk chunk, int x, int z) {
        StringBuilder sb = new StringBuilder(1024);
        boolean foundHighest = false;
        int bedrock = 0;

        for (int y = 140; y >= 12; y--) {
            BlockState state = chunk.getBlockState(new BlockPos(x, y, z));

            if (!foundHighest) {
                if (!state.isAir() && state.getBlock() != Blocks.GOLD_BLOCK) {
                    foundHighest = true;
                } else {
                    sb.append('0');
                }
            }
            if (!foundHighest) {
                continue;
            }

            if (state.isAir() && bedrock >= 2 && y < 69) {
                sb.append("0".repeat(Math.max(0, y - 11)));
                break;
            }
            if (state.getBlock() == Blocks.BEDROCK) {
                bedrock++;
            } else {
                bedrock = 0;
                if (state.getBlock() == Blocks.OAK_PLANKS
                        || state.getBlock() == Blocks.TRAPPED_CHEST
                        || state.getBlock() == Blocks.CHEST) {
                    continue;
                }
            }
            sb.append(state.getBlock());
        }
        return sb.toString().hashCode();
    }

    /** The room table, loaded once. */
    private static Map<Integer, RoomData> table() {
        if (byCore != null) {
            return byCore;
        }
        byCore = new HashMap<>();
        Identifier id = Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "puzzles/rooms.json");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open();
                     InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonArray rooms = JsonParser.parseReader(r).getAsJsonArray();
                    for (JsonElement e : rooms) {
                        JsonObject o = e.getAsJsonObject();
                        RoomData data = new RoomData(
                                o.get("name").getAsString(),
                                o.has("type") ? o.get("type").getAsString() : "NORMAL",
                                o.has("shape") ? o.get("shape").getAsString() : "1x1");
                        for (JsonElement core : o.getAsJsonArray("cores")) {
                            byCore.put(core.getAsInt(), data);
                        }
                    }
                }
            }
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Could not load room table: {}", e.toString());
        }
        return byCore;
    }
}
