package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiPositionType;
import dev.diego.diegoaddons.gui.GuiColors;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.module.modules.DungeonMapModule;
import dev.diego.diegoaddons.util.DungeonMapData;
import dev.diego.diegoaddons.util.DungeonRooms;
import dev.diego.diegoaddons.util.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The dungeon map: the room grid with its seam fills and doors, checkmarks and room labels, live
 * player markers, and the stats block underneath.
 *
 * <p>Rooms are drawn square - they butt up against their seams on the real map - and the diagonal
 * gap inside a room that continues both right and down is filled, otherwise a 2x2 room comes out
 * with a hole in the middle.
 *
 * <p>The grid is rebuilt only when the layout signature changes; markers and stats are moved and
 * retexted every tick. Players are round dots with a facing tick: the old renderer drew arrowheads
 * as triangles and RenderLib's component set has no triangle.
 */
public class DungeonMapElement extends HudElement {
    private static final float STAT_GAP = 5f;
    private static final float STAT_LINE = 9f;
    private static final float LABEL_PX = 7f;
    /**
     * How far a seam fill reaches into the two room tiles it joins.
     *
     * <p>The grid's numbers are whole pixels, but the element is drawn at whatever scale it is
     * placed at, so a tile edge and the fill that continues it can land either side of the same
     * device pixel and leave a hairline across the room - the lines that showed up on rooms spanning
     * several tiles. Overlapping cannot open a gap, and the fill is the room's own colour, so the
     * overlap is invisible.
     */
    private static final float BLEED = 0.5f;

    private final DungeonMapModule map;

    private ContainerComponent field;
    private final List<TextComponent> statLabels = new ArrayList<>();
    private final List<ContainerComponent> dots = new ArrayList<>();
    private final List<ContainerComponent> ticks = new ArrayList<>();

    private String lastLayout = "";
    private int lastStats = -1;

    public DungeonMapElement(DungeonMapModule module, ContainerComponent root) {
        super(module, root);
        this.map = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (!DungeonState.inDungeons() || mc.level == null) {
            return false;
        }
        String layout = map.layoutSignature(mc);
        List<DungeonMapModule.StatLine> stats = map.statLines();
        if (themeChanged() || !layout.equals(lastLayout) || stats.size() != lastStats) {
            lastLayout = layout;
            lastStats = stats.size();
            rebuild(mc, stats.size());
        }
        for (int i = 0; i < statLabels.size() && i < stats.size(); i++) {
            DungeonMapModule.StatLine line = stats.get(i);
            statLabels.get(i).text(line.label() + ": " + line.value()).color(GuiColors.of(line.color()));
        }
        refreshPlayers(mc);
        return true;
    }

    private void rebuild(Minecraft mc, int statCount) {
        root.clearChildren();
        statLabels.clear();
        dots.clear();
        ticks.clear();

        float size = DungeonMapModule.MAP_SIZE;
        float pad = DungeonMapModule.MAP_PAD;
        asColumn(root, size + pad * 2f, STAT_GAP).padding(pad);
        applyBackground(root, 8f);

        field = new ContainerComponent();
        field.size(size, size).position(GuiPositionType.RELATIVE);
        root.add(field);

        buildSeams(mc);
        buildRooms(mc);

        for (int i = 0; i < statCount; i++) {
            TextComponent label = small("", Themes.current().text(), LABEL_PX).width(size);
            statLabels.add(label);
            root.add(textRow(label, size, STAT_LINE));
        }
    }

    private void buildSeams(Minecraft mc) {
        int rooms = DungeonMapModule.MAP_ROOMS;
        float room = DungeonMapModule.MAP_ROOM;
        float gap = DungeonMapModule.MAP_GAP;
        float bar = DungeonMapModule.MAP_DOOR_BAR;

        for (int rz = 0; rz < rooms; rz++) {
            for (int rx = 0; rx < rooms; rx++) {
                DungeonRooms.RoomData primary = DungeonRooms.roomAt(mc, rx, rz);
                float x = roomX(rx);
                float y = roomY(rz);
                byte right = map.rightSeamAt(rx, rz);
                byte down = map.downSeamAt(rx, rz);

                if (right == DungeonMapModule.MAP_SEP) {
                    field.add(block(x + room - BLEED, y, gap + BLEED * 2f, room,
                            seamColour(mc, rx, rz, rx + 1, rz, primary)));
                } else if (right >= DungeonMapModule.MAP_DOOR_NORMAL) {
                    field.add(block(x + room, y + (room - bar) / 2f, gap, bar,
                            DungeonMapModule.colorOfDoor(right)));
                }

                if (down == DungeonMapModule.MAP_SEP) {
                    field.add(block(x, y + room - BLEED, room, gap + BLEED * 2f,
                            seamColour(mc, rx, rz, rx, rz + 1, primary)));
                } else if (down >= DungeonMapModule.MAP_DOOR_NORMAL) {
                    field.add(block(x + (room - bar) / 2f, y + room, bar, gap,
                            DungeonMapModule.colorOfDoor(down)));
                }

                // The inside corner of a room that carries on both right and down. Without it the
                // room is drawn with a hole where its four tiles meet.
                boolean interior = right == DungeonMapModule.MAP_SEP
                        && down == DungeonMapModule.MAP_SEP
                        && rx + 1 < rooms && rz + 1 < rooms
                        && map.downSeamAt(rx + 1, rz) == DungeonMapModule.MAP_SEP
                        && map.rightSeamAt(rx, rz + 1) == DungeonMapModule.MAP_SEP;
                if (interior || map.centreFillAt(rx, rz)) {
                    field.add(block(x + room - BLEED, y + room - BLEED, gap + BLEED * 2f, gap + BLEED * 2f,
                            seamColour(mc, rx, rz, rx + 1, rz + 1, primary)));
                }
            }
        }
    }

    /**
     * The colour of the fill that joins two tiles of one room: the room's own, taken from whichever
     * of the two tiles the map can answer for. A seam is only ever drawn between tiles of the same
     * room, so either answer is the same colour.
     */
    private static int seamColour(Minecraft mc, int rx, int rz, int nx, int nz,
                                  DungeonRooms.RoomData primary) {
        int here = DungeonMapModule.tileColor(rx, rz, primary);
        if (here != 0) {
            return here;
        }
        return DungeonMapModule.tileColor(nx, nz, DungeonRooms.roomAt(mc, nx, nz));
    }

    private void buildRooms(Minecraft mc) {
        int rooms = DungeonMapModule.MAP_ROOMS;
        float room = DungeonMapModule.MAP_ROOM;
        float gap = DungeonMapModule.MAP_GAP;
        boolean states = DungeonMapData.valid();

        for (int rz = 0; rz < rooms; rz++) {
            for (int rx = 0; rx < rooms; rx++) {
                DungeonRooms.RoomData data = DungeonRooms.roomAt(mc, rx, rz);
                // The map item knows a room is there long before the world scan can identify it, so
                // the tile is painted whenever either of them says so. Skipping the ones the scan
                // had no answer for left the seam between them drawn against nothing - the stray
                // lines across the big rooms were the room missing, not the seam.
                int colour = DungeonMapModule.tileColor(rx, rz, data);
                if (colour == 0) {
                    continue;
                }
                float x = roomX(rx);
                float y = roomY(rz);
                field.add(block(x, y, room, room, colour));

                if (data == null || !map.roomCorner(rx, rz)) {
                    continue;
                }
                if (map.showChecks() && states) {
                    check(DungeonMapData.state(rx * 2, rz * 2), x, y, room);
                }

                int wide = 1;
                while (rx + wide < rooms
                        && map.rightSeamAt(rx + wide - 1, rz) == DungeonMapModule.MAP_SEP) {
                    wide++;
                }
                int tall = 1;
                while (rz + tall < rooms
                        && map.downSeamAt(rx, rz + tall - 1) == DungeonMapModule.MAP_SEP) {
                    tall++;
                }
                label(data, x + (wide * room + (wide - 1) * gap) / 2f,
                        y + (tall * room + (tall - 1) * gap) / 2f);
            }
        }
    }

    private void check(DungeonMapData.State state, float x, float y, float room) {
        String mark;
        int color;
        switch (state) {
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
        TextComponent text = glyph(mark, color, 8f);
        text.position(GuiPositionType.ABSOLUTE).x(x + room / 2f - 4f).y(y + room / 2f - 4f);
        field.add(text);
    }

    private void label(DungeonRooms.RoomData data, float cx, float cy) {
        List<String> lines = new ArrayList<>();
        if (map.showNames() && data.name() != null && !"Unknown".equals(data.name())) {
            for (String word : data.name().split(" ")) {
                lines.add(word);
            }
        }
        if (map.showSecrets() && data.secrets() > 0) {
            lines.add(String.valueOf(data.secrets()));
        }
        if (lines.isEmpty()) {
            return;
        }
        float y = cy - lines.size() * STAT_LINE / 2f;
        for (String line : lines) {
            float w = width(line, LABEL_PX);
            TextComponent text = small(line, 0xFFFFFFFF, LABEL_PX);
            text.position(GuiPositionType.ABSOLUTE).x(cx - w / 2f).y(y);
            field.add(text);
            y += STAT_LINE;
        }
    }

    private void refreshPlayers(Minecraft mc) {
        int used = 0;
        if (map.showPlayers()) {
            Theme t = Themes.current();
            float stride = DungeonMapModule.MAP_ROOM + DungeonMapModule.MAP_GAP;
            float half = DungeonMapModule.MAP_ROOM / 2f;
            for (Player p : mc.level.players()) {
                boolean self = p == mc.player;
                if (!self && !isRealPlayer(mc, p)) {
                    continue;   // a mob wearing a player model, not somebody in your party
                }
                float ex = half + (float) ((p.getX() + 185.0) / 32.0) * stride;
                float ez = half + (float) ((p.getZ() + 185.0) / 32.0) * stride;
                if (ex < 0f || ex > DungeonMapModule.MAP_SIZE
                        || ez < 0f || ez > DungeonMapModule.MAP_SIZE) {
                    continue;
                }
                float size = self ? 5f : 4f;
                int color = self ? t.accent() : 0xFFFFFFFF;

                marker(used);
                dots.get(used).size(size, size).cornerRadius(size / 2f)
                        .x(ex - size / 2f).y(ez - size / 2f)
                        .backgroundColor(GuiColors.of(color)).visible(true);

                double yaw = Math.toRadians(p.getYRot());
                float tx = (float) (ex - Math.sin(yaw) * 5f);
                float tz = (float) (ez + Math.cos(yaw) * 5f);
                ticks.get(used).x(Math.min(ex, tx)).y(Math.min(ez, tz))
                        .size(Math.max(1f, Math.abs(tx - ex)), Math.max(1f, Math.abs(tz - ez)))
                        .backgroundColor(GuiColors.of(color)).visible(true);
                used++;
            }
        }
        for (int i = used; i < dots.size(); i++) {
            dots.get(i).visible(false);
            ticks.get(i).visible(false);
        }
    }

    /**
     * Whether a player entity is a person rather than one of SkyBlock's mobs, which are very often
     * player entities - which is why half a dungeon's mobs were turning up as party markers. A real
     * account has a version-4 UUID and an entry in the player list; the server's mobs have neither.
     */
    private static boolean isRealPlayer(Minecraft mc, Player player) {
        if (mc.getConnection() == null) {
            return false;
        }
        return player.getUUID().version() == 4
                && mc.getConnection().getPlayerInfo(player.getUUID()) != null;
    }

    private void marker(int index) {
        while (dots.size() <= index) {
            ContainerComponent tick = block(0f, 0f, 1f, 1f, 0xFFFFFFFF);
            tick.visible(false);
            field.add(tick);
            ticks.add(tick);

            ContainerComponent dot = block(0f, 0f, 4f, 4f, 0xFFFFFFFF);
            dot.visible(false);
            field.add(dot);
            dots.add(dot);
        }
    }

    private static float roomX(int rx) {
        return rx * (DungeonMapModule.MAP_ROOM + DungeonMapModule.MAP_GAP);
    }

    private static float roomY(int rz) {
        return rz * (DungeonMapModule.MAP_ROOM + DungeonMapModule.MAP_GAP);
    }
}
