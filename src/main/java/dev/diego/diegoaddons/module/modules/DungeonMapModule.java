package dev.diego.diegoaddons.module.modules;

import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.DungeonMapData;
import dev.diego.diegoaddons.util.DungeonRooms;
import dev.diego.diegoaddons.util.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A drawn dungeon map, built the way NoammAddons builds it: the dungeon is an <b>11x11 grid</b> where
 * the even cells are the 32-block rooms and the odd cells are the seams between them. A seam is only a
 * real connection when there is actually a structure spanning it - the game floats each room platform
 * over the void, so an unconnected boundary is open air. Probing the roof height at a seam tells which
 * it is: a door arch (roof 73/74/81/82) is a door, any other roof is a large room filling across the
 * seam, and no roof at all means the two rooms are not connected and nothing is drawn. That is what
 * stops the map drawing a door between every neighbour, and filling the odd-odd centre cell is what
 * closes the hole in the middle of a 2x2 room.
 *
 * <p>Room identity and type come from the room scan ({@link DungeonRooms}), so a room's type shows as
 * soon as its chunk loads. Clear-marks are read from Hypixel's own hotbar map ({@link DungeonMapData})
 * as an overlay and never affect the layout. Players come from their world positions.
 */
public class DungeonMapModule extends HudModule {
    /** So a run change can drop the seams without going through the module list. */
    public static DungeonMapModule INSTANCE;

    private static final int ROOMS = 6;      // rooms per axis (even cells of the 11x11 grid)
    private static final int ROOM = 15;      // room square, px
    private static final int GAP = 4;        // seam width, px
    private static final int STRIDE = ROOM + GAP;
    private static final int SIZE = ROOMS * ROOM + (ROOMS - 1) * GAP;
    private static final int PAD = 6;
    private static final int STAT_GAP = 5;
    private static final int LINE_H = Fonts.SMALL_H;
    private static final int RESCAN_TICKS = 4;
    private static final int DOOR_Y = 69;
    private static final int DOOR_BAR = 6;   // length of a door bar along the wall, px
    /** Corner radius of a room. Big enough to read as a rounded card, not as a blob. */
    private static final int CORNER = 4;

    // Seam kinds, cached from the roof probe so the render does no world lookups.
    private static final byte NONE = 0;
    private static final byte SEP = 1;         // large room fills across the seam
    private static final byte DOOR_NORMAL = 2;
    private static final byte DOOR_WITHER = 3;
    private static final byte DOOR_BLOOD = 4;
    private static final byte DOOR_ENTRANCE = 5;

    private final BooleanSetting showChecks =
            new BooleanSetting(this, "checks", "Room checkmarks", true);
    private final BooleanSetting showPlayers =
            new BooleanSetting(this, "players", "Player markers", true);
    private final BooleanSetting showNames =
            new BooleanSetting(this, "roomNames", "Room names", false);
    private final BooleanSetting showSecrets =
            new BooleanSetting(this, "roomSecrets", "Room secret counts", true);
    private final BooleanSetting statSecrets =
            new BooleanSetting(this, "statSecrets", "Stats: total secrets", true);
    private final BooleanSetting statScore =
            new BooleanSetting(this, "statScore", "Stats: score", true);
    private final BooleanSetting statCrypts =
            new BooleanSetting(this, "statCrypts", "Stats: crypts", false);
    private final BooleanSetting statMimic =
            new BooleanSetting(this, "statMimic", "Stats: mimic", false);
    private final BooleanSetting statPrince =
            new BooleanSetting(this, "statPrince", "Stats: prince", false);

    /** Seam kind between room (rx,rz) and its right neighbour. */
    private final byte[] rightSeam = new byte[ROOMS * ROOMS];
    /** Seam kind between room (rx,rz) and its lower neighbour. */
    private final byte[] downSeam = new byte[ROOMS * ROOMS];
    /** Whether the odd-odd centre of the 2x2 with top-left (rx,rz) is filled. */
    private final boolean[] centreFill = new boolean[ROOMS * ROOMS];

    private int rescanIn;

    public DungeonMapModule() {
        super("dungeonmap", Category.DUNGEONS, "Dungeon Map",
                "A drawn dungeon map with room types, states and run stats.", false);
        settings.add(showChecks);
        settings.add(showPlayers);
        settings.add(showNames);
        settings.add(showSecrets);
        settings.add(statSecrets);
        settings.add(statScore);
        settings.add(statCrypts);
        settings.add(statMimic);
        settings.add(statPrince);
        INSTANCE = this;
    }

    @Override
    protected String label() {
        return "Dungeon Map";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!DungeonState.inDungeons()) {
            clearSeams();
            return;
        }
        // The map item is re-read every tick: it is a pixel lookup, and it is what carries a room
        // being discovered or cleared. Tying it to the seam pass meant the map only noticed the
        // room you had just walked into a second later.
        DungeonMapData.update(mc);
        if (rescanIn > 0) {
            rescanIn--;
            return;
        }
        rescanIn = RESCAN_TICKS;
        rescanSeams(mc);             // roof probe -> which seams are doors / fills / open
    }

    // --- seam scan ------------------------------------------------------------------------------

    /**
     * Probes every seam of the grid once and caches its kind, so the per-frame render never touches
     * the world. A seam counts only when there is a roof over it; a door arch sits at 73/74/81/82 and
     * anything else with a roof is a room spanning the seam.
     */
    private void rescanSeams(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        for (int rz = 0; rz < ROOMS; rz++) {
            for (int rx = 0; rx < ROOMS; rx++) {
                int i = rx + rz * ROOMS;

                // A seam is read once and kept. Guarding on the chunk being loaded was not enough:
                // past the server's own view distance the client holds chunks that are loaded and
                // empty, so the roof probe found air and happily reported "no seam", which is what
                // split multi-tile rooms back into single ones once you walked a few rooms away.
                // The layout does not change during a run, so a seam that has resolved to anything
                // is never asked again.
                if (rx + 1 < ROOMS) {
                    int wx = rx * 32 - 169, wz = rz * 32 - 185;
                    if (rightSeam[i] == NONE && hasWorldHere(mc, wx, wz)) {
                        rightSeam[i] = seamKind(mc, wx, wz, rx, rz);
                    }
                } else {
                    rightSeam[i] = NONE;
                }

                if (rz + 1 < ROOMS) {
                    int wx = rx * 32 - 185, wz = rz * 32 - 169;
                    if (downSeam[i] == NONE && hasWorldHere(mc, wx, wz)) {
                        downSeam[i] = seamKind(mc, wx, wz, rx, rz);
                    }
                } else {
                    downSeam[i] = NONE;
                }

                if (rx + 1 < ROOMS && rz + 1 < ROOMS) {
                    int wx = rx * 32 - 169, wz = rz * 32 - 169;
                    if (!centreFill[i] && hasWorldHere(mc, wx, wz)) {
                        centreFill[i] = roofHeight(mc, wx, wz) > 0;
                    }
                } else {
                    centreFill[i] = false;
                }
            }
        }
    }

    /**
     * Whether there is real world at this column, so a roof probe there means something.
     *
     * <p>Asking whether the chunk is loaded is not the same question. Past the server's view distance
     * the client still holds a chunk there - an empty one - and a probe into it reads air and reports
     * "no roof, no seam" with total confidence. So the column has to actually contain something: a
     * dungeon always has floor under it, at any point that is inside the dungeon at all.
     */
    private boolean hasWorldHere(Minecraft mc, int wx, int wz) {
        if (!mc.level.hasChunkAt(new BlockPos(wx, 70, wz))) {
            return false;
        }
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int y = 60; y < 140; y++) {   // the band a dungeon is built in
            if (!mc.level.getBlockState(probe.set(wx, y, wz)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Drops every seam, so the next run reads its own. Called when the run changes. */
    public static void forgetSeams() {
        if (INSTANCE != null) {
            INSTANCE.clearSeams();
        }
    }

    private void clearSeams() {
        java.util.Arrays.fill(rightSeam, NONE);
        java.util.Arrays.fill(downSeam, NONE);
        java.util.Arrays.fill(centreFill, false);
    }

    private byte seamKind(Minecraft mc, int wx, int wz, int rx, int rz) {
        int roof = roofHeight(mc, wx, wz);
        if (roof <= 0) {
            return NONE;                     // open void between the rooms - not connected
        }
        if (roof == 73 || roof == 74 || roof == 81 || roof == 82) {
            var block = mc.level.getBlockState(new BlockPos(wx, DOOR_Y, wz)).getBlock();
            if (block == Blocks.COAL_BLOCK) {
                return DOOR_WITHER;
            }
            if (block == Blocks.RED_TERRACOTTA) {
                return DOOR_BLOOD;
            }
            if (block == Blocks.INFESTED_CHISELED_STONE_BRICKS) {
                return DOOR_ENTRANCE;
            }
            return DOOR_NORMAL;
        }
        return SEP;                          // a large room fills across the seam
    }

    /** Highest non-air, non-gold block over a column in the door/roof band, or 0 if open to void. */
    private int roofHeight(Minecraft mc, int wx, int wz) {
        for (int y = 100; y >= 66; y--) {
            var state = mc.level.getBlockState(new BlockPos(wx, y, wz));
            if (!state.isAir() && state.getBlock() != Blocks.GOLD_BLOCK) {
                return y;
            }
        }
        return 0;
    }

    // --- geometry -------------------------------------------------------------------------------

    private int statCount() {
        int n = 0;
        if (statSecrets.get()) {
            n++;
        }
        if (statScore.get()) {
            n++;
        }
        if (statCrypts.get()) {
            n++;
        }
        if (statMimic.get()) {
            n++;
        }
        if (statPrince.get()) {
            n++;
        }
        return n;
    }

    // --- view model for the HUD element ------------------------------------------------------

    /** Grid geometry, shared with the element that draws it. */
    public static final int MAP_ROOMS = ROOMS;
    public static final int MAP_ROOM = ROOM;
    public static final int MAP_GAP = GAP;
    public static final int MAP_SIZE = SIZE;
    public static final int MAP_PAD = PAD;
    public static final int MAP_DOOR_BAR = DOOR_BAR;
    public static final byte MAP_SEP = SEP;
    public static final byte MAP_DOOR_NORMAL = DOOR_NORMAL;

    /** One line of the stats block under the map. */
    public record StatLine(String label, String value, int color) {
    }

    public boolean showChecks() {
        return showChecks.get();
    }

    public boolean showPlayers() {
        return showPlayers.get();
    }

    public boolean showNames() {
        return showNames.get();
    }

    public boolean showSecrets() {
        return showSecrets.get();
    }

    public byte rightSeamAt(int rx, int rz) {
        return rightSeam[rx + rz * ROOMS];
    }

    public byte downSeamAt(int rx, int rz) {
        return downSeam[rx + rz * ROOMS];
    }

    public boolean centreFillAt(int rx, int rz) {
        return centreFill[rx + rz * ROOMS];
    }

    public boolean roomCorner(int rx, int rz) {
        return isRoomCorner(rx, rz);
    }

    public static int colorOfType(String type) {
        return typeColor(type);
    }

    public static int colorOfDoor(byte kind) {
        return doorColor(kind);
    }

    /** The room type to paint a seam fill with, preferring the primary tile. */
    public String seamType(Minecraft mc, DungeonRooms.RoomData primary, int nx, int nz) {
        return colorRoom(mc, primary, nx, nz);
    }

    /**
     * The colour of a tile, taken from Hypixel's own map rather than from the world.
     *
     * <p>The world scan only knows a room once you have been near enough to identify it, so tiles it
     * had no answer for were left unpainted - while the seam between them was drawn anyway. That is
     * what the stray lines across big rooms were: not the seam being wrong, the room around it being
     * missing. The map item carries every room's type from the moment it is discovered, at any
     * distance, so it decides the colour and the world scan is left to do what only it can - names
     * and secret counts.
     */
    public static int tileColor(int rx, int rz, DungeonRooms.RoomData data) {
        // The world scan answers first. It reads the room's own marker, so when it knows a room it
        // is right; the map is a palette lookup of a picture, and letting it overrule the scan had
        // rooms changing type as you walked up to them. The map is here to fill the tiles the scan
        // has not reached yet, which is what stopped the stray lines across big rooms.
        if (data != null && data.type() != null) {
            return typeColor(data.type());
        }
        if (DungeonMapData.valid()) {
            DungeonMapData.Type t = DungeonMapData.type(rx * 2, rz * 2);
            if (t != null && t != DungeonMapData.Type.UNKNOWN) {
                return typeColor(t.name());
            }
        }
        return 0;
    }

    /** Whether the map knows of a room on this tile at all. */
    public static boolean tileIsRoom(int rx, int rz) {
        if (!DungeonMapData.valid()) {
            return false;
        }
        DungeonMapData.Type t = DungeonMapData.type(rx * 2, rz * 2);
        return t != null && t != DungeonMapData.Type.UNKNOWN;
    }

    /** The stats block as data, so the element can lay it out as text components. */
    public List<StatLine> statLines() {
        List<StatLine> out = new ArrayList<>();
        dev.diego.diegoaddons.gui.Theme t = dev.diego.diegoaddons.gui.Themes.current();
        if (statSecrets.get()) {
            out.add(new StatLine("Secrets", String.valueOf(DungeonState.secretsFound()), t.text()));
        }
        if (statScore.get()) {
            out.add(new StatLine("Score", DungeonState.score() + " (" + DungeonState.scoreLabel() + ")",
                    DungeonState.scoreColor()));
        }
        if (statCrypts.get()) {
            out.add(new StatLine("Crypts", String.valueOf(DungeonState.crypts()), t.text()));
        }
        if (statMimic.get()) {
            boolean dead = DungeonState.mimicKilled();
            out.add(new StatLine("Mimic", dead ? "✔" : "✖", dead ? 0xFF55FF55 : 0xFFFF5555));
        }
        if (statPrince.get()) {
            boolean dead = DungeonState.princeKilled();
            out.add(new StatLine("Prince", dead ? "✔" : "✖", dead ? 0xFF55FF55 : 0xFFFF5555));
        }
        return out;
    }

    /**
     * A short description of the room layout: the element rebuilds its tiles when this changes and
     * leaves them alone on every other tick.
     */
    public String layoutSignature(Minecraft mc) {
        StringBuilder sb = new StringBuilder(ROOMS * ROOMS * 3);
        for (int rz = 0; rz < ROOMS; rz++) {
            for (int rx = 0; rx < ROOMS; rx++) {
                DungeonRooms.RoomData room = DungeonRooms.roomAt(mc, rx, rz);
                sb.append(room == null ? '-' : room.type() == null ? '?' : room.type().charAt(0));
                // The map's own answer is part of the layout too: a room it learns about while the
                // world scan still has nothing has to redraw the grid.
                sb.append(tileIsRoom(rx, rz) ? '+' : '.');
                sb.append((char) ('a' + rightSeamAt(rx, rz)));
                sb.append((char) ('a' + downSeamAt(rx, rz)));
                sb.append(centreFillAt(rx, rz) ? '#' : '.');
            }
        }
        sb.append(showChecks()).append(showNames()).append(showSecrets());
        return sb.toString();
    }




    private static int roomX(int rx) {
        return PAD + rx * STRIDE;
    }

    private static int roomY(int rz) {
        return PAD + rz * STRIDE;
    }

    // --- the HUD element ------------------------------------------------------------------------

    /**
     * The map as a configlib element.
     *
     * <p>The drawing below is the pre-RenderLib immediate-mode code, unchanged - it always drew from
     * a local origin with plain {@code fill} and {@code text} calls, which is exactly the contract
     * {@link HudWidget} asks for. Only the entry point is new.
     *
     * <p>The size is fixed rather than measured: the grid is a constant 6x6 of 15px rooms, and the
     * stats block is however many rows are switched on. Reporting it honestly is what lets the editor
     * outline the real map instead of a text-sized box.
     */
    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                return PAD * 2 + SIZE;
            }

            @Override
            public int height() {
                int n = statCount();
                return PAD * 2 + SIZE + (n == 0 ? 0 : STAT_GAP + n * LINE_H);
            }

            /** Outside a dungeon there is no map to draw - and the grid would be blank anyway. */
            @Override
            public boolean shouldRender() {
                return DungeonState.inDungeons();
            }

            @Override
            public void render(GuiGraphicsExtractor g) {
                paint(g, false);
            }

            /**
             * The editor is usually opened from a lobby, where the seam scan has nothing to read. The
             * panel and a filled-in stats block are drawn anyway so the element is visible and
             * draggable rather than an invisible box the user has to hunt for.
             */
            @Override
            public void renderPreview(GuiGraphicsExtractor g) {
                paint(g, true);
            }
        };
    }

    /** Draws the whole element from its own top-left. {@code sample} fills the stats with stand-ins. */
    private void paint(GuiGraphicsExtractor g, boolean sample) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        Theme t = Themes.current();
        boolean smooth = ConfigManager.get().smoothCorners;

        int n = statCount();
        int w = PAD * 2 + SIZE;
        int h = PAD * 2 + SIZE + (n == 0 ? 0 : STAT_GAP + n * LINE_H);
        dev.diego.diegoaddons.hud.HudElements.panel(g, this, w, h, 8, smooth);

        // Every one of these reads the world, so they only run when there is one.
        if (mc.level != null) {
            drawRooms(g, font, mc, smooth);
            drawDoors(g);
            drawLabels(g, font, mc);
            if (showPlayers.get()) {
                drawPlayers(g, t, mc);
            }
        }
        drawStats(g, font, t, sample);
    }

    // --- drawing --------------------------------------------------------------------------------


    /**
     * The doors between rooms.
     *
     * <p>Only doors. The seams <i>within</i> a room used to be filled here too, which is how a
     * multi-tile room was held together; {@link #drawRooms} measures the room and draws it as one
     * shape now, so filling the seams again would put square corners back over the rounded ones.
     */
    private void drawDoors(GuiGraphicsExtractor g) {
        for (int rz = 0; rz < ROOMS; rz++) {
            for (int rx = 0; rx < ROOMS; rx++) {
                int i = rx + rz * ROOMS;

                byte right = rightSeam[i];
                if (right >= DOOR_NORMAL) {
                    int x = roomX(rx) + ROOM;
                    int y = roomY(rz) + (ROOM - DOOR_BAR) / 2;
                    g.fill(x, y, x + GAP, y + DOOR_BAR, doorColor(right));
                }

                byte down = downSeam[i];
                if (down >= DOOR_NORMAL) {
                    int x = roomX(rx) + (ROOM - DOOR_BAR) / 2;
                    int y = roomY(rz) + ROOM;
                    g.fill(x, y, x + DOOR_BAR, y + GAP, doorColor(down));
                }
            }
        }
    }

    /** The type of a room, preferring the primary tile but falling back to a neighbour of the fill. */
    private String colorRoom(Minecraft mc, DungeonRooms.RoomData primary, int nx, int nz) {
        if (primary != null) {
            return primary.type();
        }
        DungeonRooms.RoomData n = DungeonRooms.roomAt(mc, nx, nz);
        return n == null ? null : n.type();
    }

    private static int doorColor(byte kind) {
        return switch (kind) {
            // Hypixel draws these black, which on a dark panel is indistinguishable from
            // no door at all - they were simply invisible. Dark enough to still read as a
            // wither door, light enough to exist.
            case DOOR_WITHER -> 0xFF5A5A5A;
            case DOOR_BLOOD -> 0xFFD03030;
            case DOOR_ENTRANCE -> 0xFF3CA83C;
            default -> 0xFF6E5535;
        };
    }

    /** How far a room reaches right from its top-left tile, in tiles. */
    private int extentX(int rx, int rz) {
        int w = 1;
        while (rx + w < ROOMS && rightSeam[(rx + w - 1) + rz * ROOMS] == SEP) {
            w++;
        }
        return w;
    }

    /** How far a room reaches down from its top-left tile, in tiles. */
    private int extentZ(int rx, int rz) {
        int h = 1;
        while (rz + h < ROOMS && downSeam[rx + (rz + h - 1) * ROOMS] == SEP) {
            h++;
        }
        return h;
    }

    /**
     * Whether a room fills its whole bounding box rather than being an L or a T.
     *
     * <p>Decides how it is drawn. A rectangular room is one rounded shape, which is what makes the
     * map read as rooms rather than as a grid of tiles - but the dungeon also has L-shaped rooms,
     * and drawing one of those as its bounding box would colour in a tile that is not part of it.
     * Those keep the tile-by-tile fill, which cannot be wrong about its own shape.
     */
    private boolean solidRect(int rx, int rz, int w, int h) {
        for (int dz = 0; dz < h; dz++) {
            for (int dx = 0; dx < w; dx++) {
                if (dx + 1 < w && rightSeam[(rx + dx) + (rz + dz) * ROOMS] != SEP) {
                    return false;
                }
                if (dz + 1 < h && downSeam[(rx + dx) + (rz + dz) * ROOMS] != SEP) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The rooms, each drawn as one shape.
     *
     * <p>Every tile used to be its own rounded square with the seams between them filled in
     * afterwards, so a 2x2 room came out as four lumps joined by strips - rounded corners appearing
     * in the middle of what is supposed to be one room. A room is measured first and drawn once.
     */
    private void drawRooms(GuiGraphicsExtractor g, Font font, Minecraft mc, boolean smooth) {
        boolean states = DungeonMapData.valid();
        for (int rz = 0; rz < ROOMS; rz++) {
            for (int rx = 0; rx < ROOMS; rx++) {
                if (!isRoomCorner(rx, rz)) {
                    continue;
                }
                DungeonRooms.RoomData room = DungeonRooms.roomAt(mc, rx, rz);
                int colour = tileColor(rx, rz, room);
                if (colour == 0) {
                    continue;   // neither the scan nor the map knows of a room here yet
                }
                int w = extentX(rx, rz);
                int h = extentZ(rx, rz);
                int x = roomX(rx);
                int y = roomY(rz);
                if (solidRect(rx, rz, w, h)) {
                    UiRender.fillRounded(g, x, y,
                            w * ROOM + (w - 1) * GAP, h * ROOM + (h - 1) * GAP,
                            CORNER, colour, smooth);
                } else {
                    drawRagged(g, rx, rz, w, h, colour, smooth);
                }

                if (showChecks.get() && states) {
                    drawCheck(g, font, DungeonMapData.state(rx * 2, rz * 2), x, y);
                }
            }
        }
    }

    /**
     * A room that is not a rectangle: its tiles, plus the seams that join them.
     *
     * <p>Square corners throughout. Rounding each tile is what the whole change was to get away
     * from, and rounding only the outer corners of an arbitrary polyomino is a great deal of work
     * for the handful of rooms this applies to.
     */
    private void drawRagged(GuiGraphicsExtractor g, int rx, int rz, int w, int h, int colour,
                            boolean smooth) {
        for (int dz = 0; dz < h; dz++) {
            for (int dx = 0; dx < w; dx++) {
                int i = (rx + dx) + (rz + dz) * ROOMS;
                int x = roomX(rx + dx);
                int y = roomY(rz + dz);
                if (dx > 0 && rightSeam[i - 1] != SEP && dz > 0 && downSeam[i - ROOMS] != SEP) {
                    continue;   // not actually part of this room
                }
                g.fill(x, y, x + ROOM, y + ROOM, colour);
                if (dx + 1 < w && rightSeam[i] == SEP) {
                    g.fill(x + ROOM, y, x + ROOM + GAP, y + ROOM, colour);
                }
                if (dz + 1 < h && downSeam[i] == SEP) {
                    g.fill(x, y + ROOM, x + ROOM, y + ROOM + GAP, colour);
                }
            }
        }
    }

    /** True when this room tile is the top-left of its (possibly multi-tile) room. */
    private boolean isRoomCorner(int rx, int rz) {
        boolean fromLeft = rx > 0 && rightSeam[(rx - 1) + rz * ROOMS] == SEP;
        boolean fromTop = rz > 0 && downSeam[rx + (rz - 1) * ROOMS] == SEP;
        return !fromLeft && !fromTop;
    }

    private void drawCheck(GuiGraphicsExtractor g, Font font, DungeonMapData.State st, int x, int y) {
        String mark;
        int color;
        switch (st) {
            case GREEN -> {
                mark = "✔";
                color = 0xFF55FF55;
            }
            case CLEARED -> {
                mark = "✔";
                color = 0xFFFFFFFF;
            }
            case FAILED -> {
                mark = "✖";
                color = 0xFFFF5555;
            }
            default -> {
                return;
            }
        }
        int tw = font.width(Component.literal(mark));
        g.text(font, Component.literal(mark), x + (ROOM - tw) / 2, y + ROOM / 2 - 4, color, false);
    }

    /** Room names / secret counts, one per room, centred over the room's full extent. */
    private void drawLabels(GuiGraphicsExtractor g, Font font, Minecraft mc) {
        if (!showNames.get() && !showSecrets.get()) {
            return;
        }
        for (int rz = 0; rz < ROOMS; rz++) {
            for (int rx = 0; rx < ROOMS; rx++) {
                DungeonRooms.RoomData room = DungeonRooms.roomAt(mc, rx, rz);
                if (room == null || !isRoomCorner(rx, rz)) {
                    continue;
                }
                int wTiles = 1;
                while (rx + wTiles < ROOMS && rightSeam[(rx + wTiles - 1) + rz * ROOMS] == SEP) {
                    wTiles++;
                }
                int hTiles = 1;
                while (rz + hTiles < ROOMS && downSeam[rx + (rz + hTiles - 1) * ROOMS] == SEP) {
                    hTiles++;
                }
                int cx = roomX(rx) + (wTiles * ROOM + (wTiles - 1) * GAP) / 2;
                int cy = roomY(rz) + (hTiles * ROOM + (hTiles - 1) * GAP) / 2;
                drawLabel(g, font, room, cx, cy);
            }
        }
    }

    private void drawLabel(GuiGraphicsExtractor g, Font font, DungeonRooms.RoomData room, int cx, int cy) {
        List<String> lines = new ArrayList<>();
        if (showNames.get() && room.name() != null && !"Unknown".equals(room.name())) {
            for (String word : room.name().split(" ")) {
                lines.add(word);
            }
        }
        if (showSecrets.get() && room.secrets() > 0) {
            lines.add(String.valueOf(room.secrets()));
        }
        if (lines.isEmpty()) {
            return;
        }
        int total = lines.size() * LINE_H;
        int y = cy - total / 2;
        for (String line : lines) {
            int tw = font.width(Fonts.t(line, Fonts.SMALL));
            UiRender.text(g, font, line, Fonts.SMALL, cx - tw / 2, y, 0xFFFFFFFF);
            y += LINE_H;
        }
    }

    private void drawPlayers(GuiGraphicsExtractor g, Theme t, Minecraft mc) {
        for (Player p : mc.level.players()) {
            boolean self = p == mc.player;
            if (!self && !isRealPlayer(mc, p)) {
                continue;   // dungeon mobs are spawned as fake player entities - skip them
            }
            int ex = PAD + ROOM / 2 + (int) Math.round(((p.getX() + 185.0) / 32.0) * STRIDE);
            int ez = PAD + ROOM / 2 + (int) Math.round(((p.getZ() + 185.0) / 32.0) * STRIDE);
            if (ex < PAD || ex > PAD + SIZE || ez < PAD || ez > PAD + SIZE) {
                continue;
            }
            marker(g, ex, ez, p.getYRot(), self ? 4.0 : 3.0, self ? t.accent() : 0xFFFFFFFF);
        }
    }

    /**
     * A player marker drawn as an arrowhead pointing where the player faces. Minecraft yaw 0 looks
     * south (+Z) and turns clockwise; on the map +X is right and +Z is down, so the facing unit is
     * (-sin, cos). The head is a triangle - a long tip forward and two swept-back corners - and it is
     * drawn twice, a fatter black arrow first for a 1px outline, then the colour on top.
     */
    private static void marker(GuiGraphicsExtractor g, int ex, int ez, float yawDeg, double size, int color) {
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double px = -fz, pz = fx;                          // perpendicular
        arrow(g, ex, ez, fx, fz, px, pz, size + 1.0, 0xFF000000);
        arrow(g, ex, ez, fx, fz, px, pz, size, color);
    }

    private static void arrow(GuiGraphicsExtractor g, int ex, int ez, double fx, double fz,
                              double px, double pz, double size, int color) {
        double tip = size * 0.9, back = size * 0.5, half = size * 0.55;
        double ax = ex + fx * tip, ay = ez + fz * tip;                  // tip
        double bcx = ex - fx * back, bcy = ez - fz * back;             // base centre
        double bx = bcx + px * half, by = bcy + pz * half;             // left corner
        double cx = bcx - px * half, cy = bcy - pz * half;             // right corner
        fillTriangle(g, ax, ay, bx, by, cx, cy, color);
    }

    /** Scanline fill of a small triangle, one 1px row at a time via {@link GuiGraphicsExtractor#fill}. */
    private static void fillTriangle(GuiGraphicsExtractor g, double ax, double ay,
                                     double bx, double by, double cx, double cy, int color) {
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        double[][] edges = {{ax, ay, bx, by}, {bx, by, cx, cy}, {cx, cy, ax, ay}};
        for (int y = minY; y < maxY; y++) {
            double yc = y + 0.5;
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (double[] e : edges) {
                double y1 = e[1], y2 = e[3];
                if ((yc >= y1 && yc < y2) || (yc >= y2 && yc < y1)) {
                    double xk = e[0] + (yc - y1) / (y2 - y1) * (e[2] - e[0]);
                    lo = Math.min(lo, xk);
                    hi = Math.max(hi, xk);
                }
            }
            int xs = (int) Math.round(lo), xe = (int) Math.round(hi);
            if (xe > xs) {
                g.fill(xs, y, xe, y + 1, color);
            }
        }
    }

    /**
     * A real party member versus a dungeon NPC. Hypixel spawns dungeon mobs (Shadow Assassins,
     * Diamond Guy, Lost Adventurers, ...) as client-side player entities, so {@code level.players()}
     * is full of them - which is why they were showing up as markers.
     *
     * <p>The UUID-version and tab-list-entry tests this used to apply do not actually separate them:
     * those NPCs carry a version-4 UUID and are listed just like a real account. The party roster
     * does separate them - the five dungeon team slots are the only tab entries formatted with a
     * class ("Name (Archer LvL 33)"), and no NPC ever holds one. See {@link DungeonState#isTeamMember}.
     *
     * <p>Until the roster has been read (the first ticks of a run) nothing but the player is drawn,
     * rather than falling back to a test that lets every mob through.
     */
    private boolean isRealPlayer(Minecraft mc, Player p) {
        return DungeonState.isTeamMember(p.getName().getString());
    }

    private void drawStats(GuiGraphicsExtractor g, Font font, Theme t, boolean sample) {
        int n = statCount();
        if (n == 0) {
            return;
        }
        int y = PAD + SIZE + STAT_GAP;
        if (statSecrets.get()) {
            statLine(g, font, t, "Secrets", sample ? "12" : String.valueOf(DungeonState.secretsFound()), t.text(), y);
            y += LINE_H;
        }
        if (statScore.get()) {
            int sc = sample ? 305 : DungeonState.score();
            String label = sample ? "S+" : DungeonState.scoreLabel();
            int col = sample ? 0xFFFFCE1A : DungeonState.scoreColor();
            statLine(g, font, t, "Score", sc + " (" + label + ")", col, y);
            y += LINE_H;
        }
        if (statCrypts.get()) {
            statLine(g, font, t, "Crypts", sample ? "3" : String.valueOf(DungeonState.crypts()), t.text(), y);
            y += LINE_H;
        }
        if (statMimic.get()) {
            boolean dead = sample || DungeonState.mimicKilled();
            statLine(g, font, t, "Mimic", dead ? "✔" : "✖", dead ? 0xFF55FF55 : 0xFFFF5555, y);
            y += LINE_H;
        }
        if (statPrince.get()) {
            boolean dead = sample || DungeonState.princeKilled();
            statLine(g, font, t, "Prince", dead ? "✔" : "✖", dead ? 0xFF55FF55 : 0xFFFF5555, y);
            y += LINE_H;
        }
    }

    private void statLine(GuiGraphicsExtractor g, Font font, Theme t, String label, String value, int valueColor, int y) {
        UiRender.text(g, font, label + ":", Fonts.SMALL, PAD, y, t.textMuted());
        int lx = PAD + font.width(Fonts.t(label + ": ", Fonts.SMALL));
        if (value.length() == 1 && (value.charAt(0) == '✔' || value.charAt(0) == '✖')) {
            g.text(font, Component.literal(value), lx, y, valueColor, false);
        } else {
            UiRender.text(g, font, value, Fonts.SMALL, lx, y, valueColor);
        }
    }

    private static int typeColor(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "BLOOD" -> 0xFFB03030;
            case "ENTRANCE" -> 0xFF3CA83C;
            case "FAIRY" -> 0xFFE060C0;
            case "PUZZLE" -> 0xFFB050D0;
            case "TRAP" -> 0xFFD08020;
            case "CHAMPION" -> 0xFFD0A020;
            case "RARE" -> 0xFFD0B020;
            default -> 0xFF9A7B52;
        };
    }
}
