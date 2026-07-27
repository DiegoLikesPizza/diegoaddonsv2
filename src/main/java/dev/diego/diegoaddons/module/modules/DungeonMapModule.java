package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
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
        if (rescanIn > 0) {
            rescanIn--;
            return;
        }
        rescanIn = RESCAN_TICKS;
        DungeonMapData.update(mc);   // map colours, for the clear-marks only
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

                // A seam that has already been resolved must not be reset to NONE just because its
                // roof column has since unloaded out of view - that is what collapsed multi-tile
                // rooms back to 1x1 when walking away. Only overwrite a seam once its column is loaded.
                if (rx + 1 < ROOMS) {
                    int wx = rx * 32 - 169, wz = rz * 32 - 185;
                    if (loaded(mc, wx, wz)) {
                        rightSeam[i] = seamKind(mc, wx, wz, rx, rz);
                    }
                } else {
                    rightSeam[i] = NONE;
                }

                if (rz + 1 < ROOMS) {
                    int wx = rx * 32 - 185, wz = rz * 32 - 169;
                    if (loaded(mc, wx, wz)) {
                        downSeam[i] = seamKind(mc, wx, wz, rx, rz);
                    }
                } else {
                    downSeam[i] = NONE;
                }

                if (rx + 1 < ROOMS && rz + 1 < ROOMS) {
                    int wx = rx * 32 - 169, wz = rz * 32 - 169;
                    if (loaded(mc, wx, wz)) {
                        centreFill[i] = roofHeight(mc, wx, wz) > 0;
                    }
                } else {
                    centreFill[i] = false;
                }
            }
        }
    }

    /** True when the chunk holding this column is loaded, so a roof probe there is meaningful. */
    private boolean loaded(Minecraft mc, int wx, int wz) {
        return mc.level.hasChunkAt(new BlockPos(wx, 70, wz));
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

    // --- view model for the RenderLib element ------------------------------------------------------

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
                sb.append((char) ('a' + rightSeamAt(rx, rz)));
                sb.append((char) ('a' + downSeamAt(rx, rz)));
                sb.append(centreFillAt(rx, rz) ? '#' : '.');
            }
        }
        sb.append(showChecks()).append(showNames()).append(showSecrets());
        return sb.toString();
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.DungeonMapElement(this, root);
    }

    @Override
    public int hudWidth(Font font, Minecraft mc, boolean editor) {
        return PAD * 2 + SIZE;
    }

    @Override
    public int hudHeight(Minecraft mc, boolean editor) {
        int n = statCount();
        return PAD * 2 + SIZE + (n > 0 ? STAT_GAP + n * LINE_H : 0);
    }

    private static int roomX(int rx) {
        return PAD + rx * STRIDE;
    }

    private static int roomY(int rz) {
        return PAD + rz * STRIDE;
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    public boolean drawLocal(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth, Minecraft mc, boolean editor) {
        boolean live = DungeonState.inDungeons() && mc.level != null;
        if (!live && !editor) {
            return false;
        }
        int w = hudWidth(font, mc, editor);
        int h = hudHeight(mc, editor);
        int bg = (0xCC << 24) | (t.surface() & 0x00FFFFFF);
        UiRender.fillRounded(g, 0, 0, w, h, 8, bg, smooth);
        UiRender.strokeRounded(g, 0, 0, w, h, 8, Theme.withAlpha(t.border(), 0.9f), smooth);

        if (!live) {
            UiRender.text(g, font, "Dungeon Map", Fonts.SMALL, PAD + 4, PAD + SIZE / 2 - 4, t.textMuted());
            drawStats(g, font, t, true);
            return true;
        }

        // Seams first (fills + doors) so the room squares sit on top of them.
        drawSeams(g, mc);
        drawRooms(g, font, mc, smooth);
        drawLabels(g, font, mc);
        if (showPlayers.get()) {
            drawPlayers(g, t, mc);
        }
        drawStats(g, font, t, false);
        return true;
    }

    private void drawSeams(GuiGraphicsExtractor g, Minecraft mc) {
        for (int rz = 0; rz < ROOMS; rz++) {
            for (int rx = 0; rx < ROOMS; rx++) {
                int i = rx + rz * ROOMS;
                DungeonRooms.RoomData room = DungeonRooms.roomAt(mc, rx, rz);

                byte right = rightSeam[i];
                if (right == SEP) {
                    int col = typeColor(colorRoom(mc, room, rx + 1, rz));
                    g.fill(roomX(rx) + ROOM, roomY(rz), roomX(rx + 1), roomY(rz) + ROOM, col);
                } else if (right >= DOOR_NORMAL) {
                    int x = roomX(rx) + ROOM;
                    int y = roomY(rz) + (ROOM - DOOR_BAR) / 2;
                    g.fill(x, y, x + GAP, y + DOOR_BAR, doorColor(right));
                }

                byte down = downSeam[i];
                if (down == SEP) {
                    int col = typeColor(colorRoom(mc, room, rx, rz + 1));
                    g.fill(roomX(rx), roomY(rz) + ROOM, roomX(rx) + ROOM, roomY(rz + 1), col);
                } else if (down >= DOOR_NORMAL) {
                    int x = roomX(rx) + (ROOM - DOOR_BAR) / 2;
                    int y = roomY(rz) + ROOM;
                    g.fill(x, y, x + DOOR_BAR, y + GAP, doorColor(down));
                }

                if (centreFill[i]) {
                    int col = typeColor(colorRoom(mc, room, rx + 1, rz + 1));
                    g.fill(roomX(rx) + ROOM, roomY(rz) + ROOM, roomX(rx + 1), roomY(rz + 1), col);
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
            case DOOR_WITHER -> 0xFF202020;
            case DOOR_BLOOD -> 0xFFD03030;
            case DOOR_ENTRANCE -> 0xFF3CA83C;
            default -> 0xFF6E5535;
        };
    }

    private void drawRooms(GuiGraphicsExtractor g, Font font, Minecraft mc, boolean smooth) {
        boolean states = DungeonMapData.valid();
        for (int rz = 0; rz < ROOMS; rz++) {
            for (int rx = 0; rx < ROOMS; rx++) {
                DungeonRooms.RoomData room = DungeonRooms.roomAt(mc, rx, rz);
                if (room == null) {
                    continue;
                }
                int x = roomX(rx);
                int y = roomY(rz);
                UiRender.fillRounded(g, x, y, ROOM, ROOM, 2, typeColor(room.type()), smooth);

                // Checkmark once per room, on its top-left tile.
                if (showChecks.get() && states && isRoomCorner(rx, rz)) {
                    drawCheck(g, font, DungeonMapData.state(rx * 2, rz * 2), x, y);
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
     * is full of them. Two things separate them from real players: NPC entities carry a version-2
     * UUID (real accounts are version 4), and only genuine players hold a tab-list entry.
     */
    /**
     * Dungeon mobs are spawned as player entities, and some of them carry a v4 UUID with a tab-list
     * entry, so presence in the tab list alone let them onto the map. A real player's tab entry also
     * carries their name; a mob's does not match the entity it is attached to.
     */
    private boolean isRealPlayer(Minecraft mc, Player p) {
        if (p.getUUID().version() != 4 || mc.getConnection() == null) {
            return false;
        }
        var info = mc.getConnection().getPlayerInfo(p.getUUID());
        if (info == null || info.getProfile() == null) {
            return false;
        }
        String tabName = info.getProfile().name();
        return tabName != null && !tabName.isEmpty()
                && tabName.equalsIgnoreCase(p.getGameProfile().name());
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
