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
 *
 * <p>Knowing the name is only half of it: the same room is placed in any of four orientations, so a
 * solution written down once has to be turned to match. The orientation is found from a marker block
 * the game builds into every room - a blue terracotta in one corner - and whichever corner it turns
 * up in names the rotation. {@link #toWorld} then turns a recorded position into the real one.
 */
public final class DungeonRooms {
    /** 6x6 tiles of 32 blocks; the world origin sits at -201 so tile 0 lands at index 0. */
    private static final int GRID = 6;
    private static final int WORLD_OFFSET = 201;
    private static final int ROOM_SHIFT = 5;

    /** One entry of the room table. */
    public record RoomData(String name, String type, String shape, int secrets) {
    }

    /**
     * How a room is turned, named after the corner the marker block sits in. The offsets are where
     * to look for that marker relative to the room's corner.
     */
    public enum Rotation {
        NORTH(15, 15),
        SOUTH(-15, -15),
        WEST(15, -15),
        EAST(-15, 15);

        final int dx;
        final int dz;

        Rotation(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    private static Map<Integer, RoomData> byCore;
    /** Rooms already identified this dungeon, keyed by tile index. */
    private static final Map<Integer, RoomData> IDENTIFIED = new HashMap<>();
    /** Ticks left before retrying a tile that did not resolve, so an unknown room is not rescanned
     * every tick - the chunk may simply not have arrived yet, but hashing a full column is not free. */
    private static final int RETRY_TICKS = 20;
    private static int retryIn;
    /** Ticks spent in a room that will not resolve, and the room already complained about. */
    private static int unresolved;
    private static int warnedTile = -1;

    private static int tileX = -1;
    private static int tileZ = -1;
    private static RoomData current;
    private static Rotation rotation;
    private static BlockPos marker;
    /** The tile the rotation and marker belong to, so they are not carried into the next room. */
    private static int rotationTile = -1;

    private DungeonRooms() {
    }

    public static RoomData currentRoom() {
        return current;
    }

    /** The current room's name, or null when it is unknown or outside the grid. */
    public static String currentRoomName() {
        return current == null ? null : current.name();
    }

    /** The current room's orientation, or null while it is unknown. */
    public static Rotation rotation() {
        return rotation;
    }

    /**
     * Turns a position recorded for this room into the real one, applying the room's rotation and
     * origin. Positions are recorded relative to the marker block, which is why it is kept.
     */
    public static BlockPos toWorld(BlockPos recorded) {
        if (marker == null || rotation == null) {
            return null;
        }
        BlockPos r = switch (rotation) {
            case NORTH -> new BlockPos(-recorded.getX(), recorded.getY(), -recorded.getZ());
            case WEST -> new BlockPos(-recorded.getZ(), recorded.getY(), recorded.getX());
            case SOUTH -> recorded;
            case EAST -> new BlockPos(recorded.getZ(), recorded.getY(), -recorded.getX());
        };
        return r.offset(marker.getX(), 0, marker.getZ());
    }

    /** The inverse of {@link #toWorld}, for recording a position seen in the world. */
    public static BlockPos toRecorded(BlockPos world) {
        if (marker == null || rotation == null) {
            return null;
        }
        BlockPos p = world.subtract(new BlockPos(marker.getX(), 0, marker.getZ()));
        return switch (rotation) {
            case NORTH -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case WEST -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            case SOUTH -> p;
            case EAST -> new BlockPos(-p.getZ(), p.getY(), p.getX());
        };
    }

    /**
     * Identify the room occupying an arbitrary tile, for the dungeon map's whole-grid scan. Uses and
     * fills the same per-dungeon cache as {@link #tick}, but touches none of the current-room or
     * rotation state, so it is safe to call for every tile each frame. Returns null while the tile's
     * chunk has not loaded or the room is unknown.
     */
    public static RoomData roomAt(Minecraft mc, int tx, int tz) {
        if (mc == null || mc.level == null || tx < 0 || tx >= GRID || tz < 0 || tz >= GRID) {
            return null;
        }
        int key = tx + tz * GRID;
        RoomData known = IDENTIFIED.get(key);
        if (known != null) {
            return known;
        }
        RoomData found = identify(mc, tx, tz);
        if (found != null) {
            IDENTIFIED.put(key, found);
        }
        return found;
    }

    public static void reset() {
        IDENTIFIED.clear();
        unresolved = 0;
        warnedTile = -1;
        retryIn = 0;
        rotation = null;
        marker = null;
        rotationTile = -1;
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
        if (rotationTile != key) {
            // Entered a different room: its orientation has to be found again.
            rotation = null;
            marker = null;
            rotationTile = -1;
        }
        if (key != warnedTile) {
            unresolved = 0;
        }

        RoomData known = IDENTIFIED.get(key);
        if (known != null) {
            current = known;
            if (rotation == null) {
                findRotation(mc, tx, tz);
            }
            return;
        }
        if (retryIn > 0) {
            retryIn--;
            return;
        }
        RoomData found = identify(mc, tx, tz);
        if (found != null) {
            IDENTIFIED.put(key, found);
            findRotation(mc, tx, tz);
        } else {
            retryIn = RETRY_TICKS;
        }
        current = found;
        complain(mc, key);
    }

    /**
     * Says once, in a room that will not resolve, that it will not resolve.
     *
     * <p>Every coordinate solver - Boulder, Creeper Beams, Ice Fill, Water Board, Teleport Maze -
     * hangs off {@link #toWorld}, which needs both the room's identity and its rotation. When either
     * is missing they all draw nothing, and they do it silently, which is indistinguishable from
     * being switched off or broken. A room that has not resolved after a few seconds of standing in
     * it is not going to, so it says so rather than leaving you wondering.
     */
    private static void complain(Minecraft mc, int key) {
        if (current != null && rotation != null) {
            unresolved = 0;
            return;
        }
        if (++unresolved < 60 || key == warnedTile || mc.gui == null) {
            return;
        }
        warnedTile = key;
        String why = current == null ? "not in the room list" : "orientation not found";
        mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(
                "§b[DiegoAddons] §7This room could not be identified (" + why
                        + ") - the coordinate solvers stay quiet here."));
    }

    /**
     * Finds the room's orientation by looking for the marker block in each of the four corners.
     *
     * <p>The Fairy room is the exception - it has no marker - so its known orientation is used
     * directly rather than leaving the room unusable.
     */
    private static void findRotation(Minecraft mc, int tx, int tz) {
        if (mc.level == null) {
            return;
        }
        int originX = tx * 32 - 185;
        int originZ = tz * 32 - 185;
        int key = tx + tz * GRID;

        if (current != null && "Fairy".equals(current.name())) {
            rotation = Rotation.SOUTH;
            marker = new BlockPos(originX + Rotation.SOUTH.dx, 70, originZ + Rotation.SOUTH.dz);
            rotationTile = key;
            return;
        }
        // Scan each corner's whole column for the marker rather than probing one guessed height. The
        // marker sits at the room's roof, but the roof height varies room to room and a single wrong
        // guess left the rotation - and every coordinate solver that depends on it - dead. Only the
        // marker's X/Z matter to {@link #toWorld}, so the exact Y found here is unimportant. Of the
        // corners with a terracotta, the one whose block is highest is the real roof marker (any
        // decorative terracotta sits lower), so that corner wins.
        Rotation best = null;
        BlockPos bestPos = null;
        for (Rotation r : Rotation.values()) {
            BlockPos found = markerInColumn(mc, originX + r.dx, originZ + r.dz);
            if (found != null && (bestPos == null || found.getY() > bestPos.getY())) {
                best = r;
                bestPos = found;
            }
        }
        if (best != null) {
            rotation = best;
            marker = bestPos;
            rotationTile = key;
        }
    }

    /** The highest blue-terracotta block down a column, or null - the room's rotation marker. */
    private static BlockPos markerInColumn(Minecraft mc, int x, int z) {
        for (int y = 150; y >= 60; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (mc.level.getBlockState(pos).getBlock() == Blocks.BLUE_TERRACOTTA) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Diagnostic: the core hash and matched name for the tile the player is standing in. Used to fix
     * up the room table when a room resolves to the wrong name (e.g. a blaze variant reading as the
     * opposite order). Returns null while the tile is outside the grid or its chunk has not loaded.
     */
    public static String debugCurrentTile(Minecraft mc) {
        if (mc == null || mc.level == null || tileX < 0 || tileZ < 0) {
            return null;
        }
        int chunkX = (tileX - GRID) * 2;
        int chunkZ = (tileZ - GRID) * 2;
        LevelChunk chunk = mc.level.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        int hash = coreHash(chunk, chunkX * 16 + 7, chunkZ * 16 + 7);
        RoomData d = table().get(hash);
        return "hash=" + hash + " name=" + (d == null ? "UNKNOWN" : d.name());
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
    /** Y of the first solid block in the last scanned column; the marker sits at that height. */
    private static int highestBlock;

    private static int coreHash(LevelChunk chunk, int x, int z) {
        StringBuilder sb = new StringBuilder(1024);
        boolean foundHighest = false;
        int bedrock = 0;

        for (int y = 140; y >= 12; y--) {
            BlockState state = chunk.getBlockState(new BlockPos(x, y, z));

            if (!foundHighest) {
                if (!state.isAir() && state.getBlock() != Blocks.GOLD_BLOCK) {
                    foundHighest = true;
                    highestBlock = y;
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
                                o.has("shape") ? o.get("shape").getAsString() : "1x1",
                                o.has("secrets") ? o.get("secrets").getAsInt() : 0);
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
