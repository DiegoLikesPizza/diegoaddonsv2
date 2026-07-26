package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Rebuilds the dungeon's room grid from the colours of Hypixel's own hotbar map, the way NoammAddons
 * does it. The map is an 11x11 grid - rooms on the even cells, doorways on the odd ones - and each
 * cell's colour tells its kind: a room's fill colour is its type and its centre colour carries the
 * clear-mark, while a doorway's colour tells wither/blood/normal. Reading the finished map is far
 * more reliable than trying to infer all of this from the world, so the drawn map matches the game's.
 *
 * <p>Everything is a plain lookup of Hypixel's map palette; the one fiddly part is lining the grid up
 * with the pixels, which is calibrated from the entrance room's coloured strip and the floor number.
 */
public final class DungeonMapData {
    public static final int GRID = 11;

    /** Room clear state, from the centre pixel. */
    public enum State { GREEN, CLEARED, DISCOVERED, FAILED, UNOPENED, UNDISCOVERED }

    /** Room kind, from the fill pixel. */
    public enum Type { BLOOD, CHAMPION, ENTRANCE, FAIRY, NORMAL, PUZZLE, RARE, TRAP, UNKNOWN }

    /** Doorway kind, from the doorway pixel. */
    public enum Door { BLOOD, ENTRANCE, NORMAL, WITHER, NONE }

    private static final Type[] type = new Type[GRID * GRID];
    private static final State[] state = new State[GRID * GRID];
    private static final Door[] door = new Door[GRID * GRID];
    private static boolean valid;
    private static MapId cachedId;

    // Calibration + the map itself, exposed so markers can be placed on the same grid the rooms use.
    private static MapItemSavedData lastMap;
    private static int pxStartX;
    private static int pxStartY;
    private static int pxStride;

    public static MapItemSavedData map() {
        return lastMap;
    }

    /** Map pixel of grid cell (0,0). */
    public static int pixelStartX() {
        return pxStartX;
    }

    public static int pixelStartY() {
        return pxStartY;
    }

    /** Map pixels between adjacent grid cells. */
    public static int pixelStride() {
        return pxStride;
    }

    private DungeonMapData() {
    }

    public static boolean valid() {
        return valid;
    }

    public static Type type(int gx, int gy) {
        return type[gy * GRID + gx];
    }

    public static State state(int gx, int gy) {
        return state[gy * GRID + gx];
    }

    public static Door door(int gx, int gy) {
        return door[gy * GRID + gx];
    }

    public static void reset() {
        valid = false;
        cachedId = null;
    }

    /** Re-reads the map. Cheap enough to run every few ticks; the colours change as rooms clear. */
    public static void update(Minecraft mc) {
        MapItemSavedData md = findMap(mc);
        if (md == null) {
            valid = false;
            return;
        }
        int[] cal = calibrate(md);
        if (cal == null) {
            valid = false;
            return;
        }
        int startX = cal[0];
        int startY = cal[1];
        int halfTile = cal[2];
        int halfRoom = cal[3];
        lastMap = md;
        pxStartX = startX;
        pxStartY = startY;
        pxStride = halfTile;

        for (int gy = 0; gy < GRID; gy++) {
            for (int gx = 0; gx < GRID; gx++) {
                int i = gy * GRID + gx;
                type[i] = Type.UNKNOWN;
                state[i] = State.UNDISCOVERED;
                door[i] = Door.NONE;

                int mapX = startX + gx * halfTile;
                int mapY = startY + gy * halfTile;
                if (mapX < 0 || mapY < 0 || mapX >= 128 || mapY >= 128) {
                    continue;
                }
                int centre = color(md, mapX, mapY);
                if (centre == 0) {
                    continue;   // background: nothing here
                }
                boolean room = (gx % 2 == 0) && (gy % 2 == 0);
                if (room) {
                    int fill = color(md, mapX - halfRoom, mapY - halfRoom);
                    type[i] = typeOf(fill != 0 ? fill : centre);
                    state[i] = stateOf(centre, type[i]);
                } else {
                    door[i] = doorOf(centre);
                }
            }
        }
        valid = true;
    }

    /** Locates the grid on the map: the pixel of cell (0,0) and the per-cell pixel stride. */
    private static int[] calibrate(MapItemSavedData md) {
        int[] entrance = findEntranceStrip(md);
        int size = entrance[1];
        if (size != 16 && size != 18) {
            size = 16;   // fall back rather than give up; most floors are 16
        }
        int halfRoom = size / 2;
        int start = entrance[0];

        int cornerX;
        int cornerZ;
        int floor = floorNumber();
        switch (floor) {
            case 0 -> {
                cornerX = 22;
                cornerZ = 22;
            }
            case 1 -> {
                cornerX = 22;
                cornerZ = 11;
            }
            case 2, 3 -> {
                cornerX = 11;
                cornerZ = 11;
            }
            default -> {
                cornerX = (start & 127) % (size + 4);
                cornerZ = (start >> 7) % (size + 4);
            }
        }
        return new int[]{cornerX + halfRoom, cornerZ + halfRoom, halfRoom + 2, halfRoom};
    }

    /** The map index and length of the entrance room's coloured strip (colour 30). */
    private static int[] findEntranceStrip(MapItemSavedData md) {
        int start = 0;
        int run = 0;
        for (int i = 0; i < md.colors.length; i++) {
            if ((md.colors[i] & 0xFF) == 30) {
                if (run == 0) {
                    start = i;
                }
                run++;
            } else {
                if (run >= 16) {
                    return new int[]{start, run};
                }
                run = 0;
            }
        }
        return new int[]{start, run};
    }

    private static int floorNumber() {
        String f = DungeonState.floor();
        if (f.isEmpty() || f.equals("E")) {
            return 0;
        }
        char last = f.charAt(f.length() - 1);
        return Character.isDigit(last) ? last - '0' : 0;
    }

    private static int color(MapItemSavedData md, int x, int y) {
        if (x < 0 || y < 0 || x >= 128 || y >= 128) {
            return 0;
        }
        return md.colors[y * 128 + x] & 0xFF;
    }

    private static Type typeOf(int c) {
        return switch (c) {
            case 18 -> Type.BLOOD;
            case 74 -> Type.CHAMPION;
            case 30 -> Type.ENTRANCE;
            case 82 -> Type.FAIRY;
            case 63, 85 -> Type.NORMAL;
            case 66 -> Type.PUZZLE;
            case 34 -> Type.RARE;
            case 62 -> Type.TRAP;
            default -> Type.NORMAL;
        };
    }

    private static State stateOf(int centre, Type t) {
        return switch (centre) {
            case 30 -> t == Type.ENTRANCE ? State.DISCOVERED : State.GREEN;
            case 34 -> State.CLEARED;
            case 85, 119 -> State.UNOPENED;
            case 18 -> t == Type.PUZZLE ? State.FAILED : State.DISCOVERED;
            default -> State.DISCOVERED;
        };
    }

    private static Door doorOf(int c) {
        return switch (c) {
            case 18 -> Door.BLOOD;
            case 30 -> Door.ENTRANCE;
            case 119 -> Door.WITHER;
            case 74, 82, 66, 62, 85, 63 -> Door.NORMAL;
            default -> Door.NONE;
        };
    }

    /** The dungeon map item's data, from any filled map the player carries. */
    private static MapItemSavedData findMap(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (cachedId != null) {
            MapItemSavedData d = mc.level.getMapData(cachedId);
            if (d != null) {
                return d;
            }
            cachedId = null;
        }
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            MapId id = st.get(DataComponents.MAP_ID);
            if (id != null) {
                MapItemSavedData d = mc.level.getMapData(id);
                if (d != null) {
                    cachedId = id;
                    return d;
                }
            }
        }
        return null;
    }
}
