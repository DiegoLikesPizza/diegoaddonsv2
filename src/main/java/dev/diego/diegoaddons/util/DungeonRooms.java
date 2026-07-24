package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Works out which dungeon room the player is standing in, and what kind of room it is.
 *
 * <p>Two separate pieces of information are combined. <b>Which</b> room you are in comes from your
 * coordinates: dungeon rooms sit on a fixed 6x6 grid of 32-block tiles, so the tile index is pure
 * arithmetic. <b>What</b> the room is comes from the dungeon map in your inventory, whose pixels are
 * coloured per room type.
 *
 * <p>The map's own layout is not fixed - room squares are 16 or 18 pixels depending on the floor,
 * with a varying offset - so it is measured at runtime by finding the entrance room's colour run,
 * rather than assumed.
 *
 * <p>The grid constants and the colour table follow Odin (BSD-3-Clause, © odtheking and
 * contributors), which documents both; see the credits in the README.
 */
public final class DungeonRooms {
    private static final int MAP_SIZE = 128;
    private static final int GRID = 6;
    /** Rooms are 32 blocks; the world origin sits at -201 so the shift lands tile 0 at index 0. */
    private static final int WORLD_OFFSET = 201;
    private static final int ROOM_SHIFT = 5;
    /** Gap between two room squares on the map, on top of the square's own size. */
    private static final int ROOM_SPACING = 4;

    /** The kinds of room a dungeon map distinguishes, by the map colour Hypixel paints them with. */
    public enum RoomType {
        ENTRANCE((byte) 30, "Entrance"),
        FAIRY((byte) 82, "Fairy"),
        NORMAL((byte) 63, "Normal"),
        BLOOD((byte) 18, "Blood"),
        CHAMPION((byte) 74, "Miniboss"),
        UNKNOWN((byte) 85, "Unknown"),
        PUZZLE((byte) 66, "Puzzle"),
        TRAP((byte) 62, "Trap");

        public final byte color;
        public final String display;

        RoomType(byte color, String display) {
            this.color = color;
            this.display = display;
        }

        static RoomType byColor(byte c) {
            for (RoomType t : values()) {
                if (t.color == c) {
                    return t;
                }
            }
            return null;
        }
    }

    // Measured from the map each time a dungeon is entered.
    private static int roomSize = -1;
    private static int roomGap = -1;
    private static int startX = -1;
    private static int startY = -1;

    private static RoomType current;
    private static int tileX = -1;
    private static int tileZ = -1;

    private DungeonRooms() {
    }

    public static RoomType currentRoom() {
        return current;
    }

    /** Grid position 0..5, or -1 when outside the dungeon grid. */
    public static int tileX() {
        return tileX;
    }

    public static int tileZ() {
        return tileZ;
    }

    /** Clears the measured layout, e.g. on leaving a world. */
    public static void reset() {
        roomSize = -1;
        roomGap = -1;
        startX = -1;
        startY = -1;
        current = null;
        tileX = -1;
        tileZ = -1;
    }

    /** Called every client tick. Cheap when there is no dungeon map to read. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        byte[] colors = mapColors(mc);
        if (colors == null) {
            current = null;
            return;
        }
        if (roomSize < 0 && !measureLayout(colors)) {
            return;
        }

        tileX = (mc.player.getBlockX() + WORLD_OFFSET) >> ROOM_SHIFT;
        tileZ = (mc.player.getBlockZ() + WORLD_OFFSET) >> ROOM_SHIFT;
        if (tileX < 0 || tileX >= GRID || tileZ < 0 || tileZ >= GRID) {
            current = null;   // boss room or outside the grid
            return;
        }
        current = typeAt(colors, tileX, tileZ);
    }

    /** The dungeon map's pixels, or null when the player is not holding one. */
    private static byte[] mapColors(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            MapItemSavedData data = MapItem.getSavedData(stack, mc.level);
            if (data != null && data.colors != null && data.colors.length >= MAP_SIZE * MAP_SIZE) {
                return data.colors;
            }
        }
        return null;
    }

    /**
     * Measures the map's room square size and offset from the entrance room, which is the one colour
     * guaranteed to be present. The run of identical pixels across a room is its size; everything
     * else follows from that.
     */
    private static boolean measureLayout(byte[] colors) {
        byte entrance = RoomType.ENTRANCE.color;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] != entrance) {
                continue;
            }
            int end = i;
            while (end < colors.length && colors[end] == entrance) {
                end++;
            }
            int length = end - i;
            if (length != 16 && length != 18) {
                continue;
            }
            roomSize = length;
            roomGap = length + ROOM_SPACING;
            startX = (i % MAP_SIZE) % roomGap;
            startY = (i / MAP_SIZE) % roomGap;
            // A zero offset means the run started at the very edge, which the real layout never
            // does - the known good value for those floors is 22.
            if (startX == 0) {
                startX = 22;
            }
            if (startY == 0) {
                startY = 22;
            }
            return true;
        }
        return false;
    }

    /** The room type drawn at a grid position, sampled from the middle of its square. */
    private static RoomType typeAt(byte[] colors, int gx, int gz) {
        int px = startX + gx * roomGap + roomSize / 2;
        int py = startY + gz * roomGap + roomSize / 2;
        if (px < 0 || px >= MAP_SIZE || py < 0 || py >= MAP_SIZE) {
            return null;
        }
        return RoomType.byColor(colors[py * MAP_SIZE + px]);
    }
}
