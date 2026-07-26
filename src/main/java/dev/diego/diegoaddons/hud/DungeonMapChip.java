package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import com.render.api.gui.layout.GuiPositionType;
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
 * The dungeon map element: the room grid with its seam fills and doors, per-room checkmarks and
 * labels, live player markers, and the stats block underneath.
 *
 * <p>The grid is the expensive part and it barely changes, so it is rebuilt only when the layout
 * signature does - a room being discovered, a door opening, a label setting being toggled. Player
 * markers and the stats text are mutated in place every tick.
 *
 * <p>One deliberate difference from the old renderer: players were arrowheads drawn as triangles,
 * and RenderLib's component set has no triangle, so a marker here is a round dot with a short tick
 * pointing the way the player faces.
 */
public class DungeonMapChip extends HudChip {
    private static final float STAT_GAP = 5f;
    private static final float LINE_H = 9f;
    private static final float TEXT_PX = 7f;

    private final DungeonMapModule map;

    private ContainerComponent field;
    private ContainerComponent statsBox;
    private final List<TextComponent> statRows = new ArrayList<>();
    private final List<ContainerComponent> markers = new ArrayList<>();
    private final List<ContainerComponent> ticks = new ArrayList<>();

    private String lastLayout = "";
    private String lastTheme = "";
    private int lastStatCount = -1;

    public DungeonMapChip(DungeonMapModule module, ContainerComponent root) {
        super(module, root);
        this.map = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (!DungeonState.inDungeons() || mc.level == null) {
            return false;
        }
        String layout = map.layoutSignature(mc);
        String theme = Themes.current().name();
        List<DungeonMapModule.StatLine> stats = map.statLines();
        if (!layout.equals(lastLayout) || !theme.equals(lastTheme) || stats.size() != lastStatCount) {
            lastLayout = layout;
            lastTheme = theme;
            lastStatCount = stats.size();
            rebuild(mc, stats.size());
        }
        refreshStats(stats);
        refreshPlayers(mc);
        return true;
    }

    // --- construction -----------------------------------------------------------------------------

    private void rebuild(Minecraft mc, int statCount) {
        root.clearChildren();
        statRows.clear();
        markers.clear();
        ticks.clear();

        float size = DungeonMapModule.MAP_SIZE;
        root.display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.COLUMN)
                .alignItems(GuiAlignment.START)
                .rowGap(GuiLength.pixels(STAT_GAP))
                .gap(STAT_GAP)
                .padding(DungeonMapModule.MAP_PAD)
                .cornerRadius(8f)
                .width(size + DungeonMapModule.MAP_PAD * 2f);
        applyTheme();

        field = new ContainerComponent();
        field.size(size, size);
        root.add(field);

        buildSeams(mc);
        buildRooms(mc);

        if (statCount > 0) {
            statsBox = new ContainerComponent();
            statsBox.display(GuiDisplay.FLEX)
                    .flexDirection(GuiFlexDirection.COLUMN)
                    .alignItems(GuiAlignment.START)
                    .rowGap(GuiLength.pixels(1f))
                    .gap(1f)
                    .width(size);
            for (int i = 0; i < statCount; i++) {
                TextComponent row = new TextComponent().font(HudText.SMALL)
                        .textScalePixels(TEXT_PX).width(size);
                statRows.add(row);
                statsBox.add(row);
            }
            root.add(statsBox);
        } else {
            statsBox = null;
        }
    }

    /** Seam fills between tiles of one large room, and the door bars between separate rooms. */
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
                if (right == DungeonMapModule.MAP_SEP) {
                    add(x + room, y, gap, room, 0f,
                            DungeonMapModule.colorOfType(map.seamType(mc, primary, rx + 1, rz)));
                } else if (right >= DungeonMapModule.MAP_DOOR_NORMAL) {
                    add(x + room, y + (room - bar) / 2f, gap, bar, 0f,
                            DungeonMapModule.colorOfDoor(right));
                }

                byte down = map.downSeamAt(rx, rz);
                if (down == DungeonMapModule.MAP_SEP) {
                    add(x, y + room, room, gap, 0f,
                            DungeonMapModule.colorOfType(map.seamType(mc, primary, rx, rz + 1)));
                } else if (down >= DungeonMapModule.MAP_DOOR_NORMAL) {
                    add(x + (room - bar) / 2f, y + room, bar, gap, 0f,
                            DungeonMapModule.colorOfDoor(down));
                }

                if (map.centreFillAt(rx, rz)) {
                    add(x + room, y + room, gap, gap, 0f,
                            DungeonMapModule.colorOfType(map.seamType(mc, primary, rx + 1, rz + 1)));
                }
            }
        }
    }

    /** The room squares themselves, plus their checkmark and name/secret labels. */
    private void buildRooms(Minecraft mc) {
        int rooms = DungeonMapModule.MAP_ROOMS;
        float room = DungeonMapModule.MAP_ROOM;
        float gap = DungeonMapModule.MAP_GAP;
        boolean states = DungeonMapData.valid();

        for (int rz = 0; rz < rooms; rz++) {
            for (int rx = 0; rx < rooms; rx++) {
                DungeonRooms.RoomData data = DungeonRooms.roomAt(mc, rx, rz);
                if (data == null) {
                    continue;
                }
                float x = roomX(rx);
                float y = roomY(rz);
                add(x, y, room, room, 2f, DungeonMapModule.colorOfType(data.type()));

                if (!map.roomCorner(rx, rz)) {
                    continue;
                }
                if (map.showChecks() && states) {
                    check(DungeonMapData.state(rx * 2, rz * 2), x, y, room);
                }

                // A room can span several tiles; centre its label over the whole extent.
                int wTiles = 1;
                while (rx + wTiles < rooms
                        && map.rightSeamAt(rx + wTiles - 1, rz) == DungeonMapModule.MAP_SEP) {
                    wTiles++;
                }
                int hTiles = 1;
                while (rz + hTiles < rooms
                        && map.downSeamAt(rx, rz + hTiles - 1) == DungeonMapModule.MAP_SEP) {
                    hTiles++;
                }
                float cx = x + (wTiles * room + (wTiles - 1) * gap) / 2f;
                float cy = y + (hTiles * room + (hTiles - 1) * gap) / 2f;
                label(data, cx, cy);
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
        TextComponent text = new TextComponent().text(mark).color(color)
                .font(HudText.MEDIUM).textScalePixels(8f).width(room);
        text.position(GuiPositionType.ABSOLUTE).x(x + room / 2f - 3f).y(y + room / 2f - 4f);
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
        float y = cy - lines.size() * LINE_H / 2f;
        for (String line : lines) {
            float w = HudText.width(line, TEXT_PX);
            TextComponent text = new TextComponent().text(line).color(0xFFFFFFFF)
                    .font(HudText.SMALL).textScalePixels(TEXT_PX).width(w);
            text.position(GuiPositionType.ABSOLUTE).x(cx - w / 2f).y(y);
            field.add(text);
            y += LINE_H;
        }
    }

    /** An absolutely-placed coloured box on the field. */
    private void add(float x, float y, float w, float h, float radius, int color) {
        ContainerComponent box = new ContainerComponent();
        box.size(w, h).cornerRadius(radius).backgroundColor(color)
                .position(GuiPositionType.ABSOLUTE).x(x).y(y);
        field.add(box);
    }

    private static float roomX(int rx) {
        return rx * (DungeonMapModule.MAP_ROOM + DungeonMapModule.MAP_GAP);
    }

    private static float roomY(int rz) {
        return rz * (DungeonMapModule.MAP_ROOM + DungeonMapModule.MAP_GAP);
    }

    // --- per-tick refresh -------------------------------------------------------------------------

    private void refreshStats(List<DungeonMapModule.StatLine> stats) {
        for (int i = 0; i < statRows.size() && i < stats.size(); i++) {
            DungeonMapModule.StatLine line = stats.get(i);
            statRows.get(i).text(line.label() + ": " + line.value()).color(line.color());
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
                float ex = half + (float) ((p.getX() + 185.0) / 32.0) * stride;
                float ez = half + (float) ((p.getZ() + 185.0) / 32.0) * stride;
                if (ex < 0f || ex > DungeonMapModule.MAP_SIZE
                        || ez < 0f || ez > DungeonMapModule.MAP_SIZE) {
                    continue;
                }
                float size = self ? 5f : 4f;
                int color = self ? t.accent() : 0xFFFFFFFF;

                ContainerComponent dot = marker(used);
                dot.size(size, size).cornerRadius(size / 2f)
                        .x(ex - size / 2f).y(ez - size / 2f)
                        .backgroundColor(color).visible(true);

                double yaw = Math.toRadians(p.getYRot());
                float tx = (float) (ex - Math.sin(yaw) * 5f);
                float tz = (float) (ez + Math.cos(yaw) * 5f);
                ContainerComponent tick = ticks.get(used);
                tick.x(Math.min(ex, tx)).y(Math.min(ez, tz))
                        .size(Math.max(1f, Math.abs(tx - ex)), Math.max(1f, Math.abs(tz - ez)))
                        .backgroundColor(color).visible(true);
                used++;
            }
        }
        for (int i = used; i < markers.size(); i++) {
            markers.get(i).visible(false);
            ticks.get(i).visible(false);
        }
    }

    /** A pooled player marker (dot plus facing tick), created on first use. */
    private ContainerComponent marker(int index) {
        while (markers.size() <= index) {
            ContainerComponent tick = new ContainerComponent();
            tick.position(GuiPositionType.ABSOLUTE).size(1f, 1f).visible(false);
            field.add(tick);
            ticks.add(tick);

            ContainerComponent dot = new ContainerComponent();
            dot.position(GuiPositionType.ABSOLUTE).size(4f, 4f).visible(false);
            field.add(dot);
            markers.add(dot);
        }
        return markers.get(index);
    }
}
