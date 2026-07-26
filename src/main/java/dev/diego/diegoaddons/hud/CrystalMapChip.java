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
import dev.diego.diegoaddons.module.modules.CrystalHollowsMapModule;
import dev.diego.diegoaddons.util.CrystalHollows;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Crystal Hollows minimap: a fixed square field with the four quadrants tinted by the region
 * found in each, waypoint dots, and the player's dot with a facing tick.
 *
 * <p>The field and its dividers are built once; the dots are a pool of small absolutely-positioned
 * boxes that are moved and recoloured as waypoints are discovered and the player walks, so nothing
 * is allocated on a normal tick.
 */
public class CrystalMapChip extends HudChip {
    private static final float MAP = 104f;
    private static final float PAD = 6f;
    private static final float DOT = 4f;

    private final CrystalHollowsMapModule map;

    private ContainerComponent field;
    private final List<ContainerComponent> quadrants = new ArrayList<>();
    private final List<ContainerComponent> dots = new ArrayList<>();
    private ContainerComponent playerDot;
    private ContainerComponent facing;
    private TextComponent coords;

    private String lastShape = "";
    private String lastTheme = "";

    public CrystalMapChip(CrystalHollowsMapModule module, ContainerComponent root) {
        super(module, root);
        this.map = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (!CrystalHollows.inHollows() || mc.player == null) {
            return false;   // only drawn while you are actually down there
        }
        String shape = "" + map.showCoords() + map.showRegions() + map.showWaypoints();
        String theme = Themes.current().name();
        if (!shape.equals(lastShape) || !theme.equals(lastTheme)) {
            lastShape = shape;
            lastTheme = theme;
            rebuild();
        }

        Theme t = Themes.current();
        if (map.showRegions()) {
            for (int q = 0; q < quadrants.size(); q++) {
                String region = CrystalHollows.quadrantRegion(q);
                quadrants.get(q).visible(region != null);
                if (region != null) {
                    quadrants.get(q).backgroundColor(
                            Theme.withAlpha(CrystalHollows.regionColor(region), 0.35f));
                }
            }
        }

        int used = 0;
        if (map.showWaypoints()) {
            for (CrystalHollows.Waypoint wp : CrystalHollows.waypoints()) {
                ContainerComponent dot = dot(used++);
                place(dot, wp.pos().x, wp.pos().z, DOT);
                dot.backgroundColor(wp.type().color).visible(true);
            }
        }
        for (int i = used; i < dots.size(); i++) {
            dots.get(i).visible(false);
        }

        place(playerDot, mc.player.getX(), mc.player.getZ(), DOT);
        playerDot.backgroundColor(t.accent()).visible(true);

        // The facing tick is a thin box reaching from the player dot towards where they are looking.
        double yaw = Math.toRadians(mc.player.getYRot());
        float px = fx(mc.player.getX());
        float pz = fz(mc.player.getZ());
        float tx = (float) (px - Math.sin(yaw) * 5f);
        float tz = (float) (pz + Math.cos(yaw) * 5f);
        facing.x(Math.min(px, tx)).y(Math.min(pz, tz))
                .size(Math.max(1f, Math.abs(tx - px)), Math.max(1f, Math.abs(tz - pz)))
                .backgroundColor(t.accent()).visible(true);

        if (coords != null) {
            String area = CrystalHollows.area();
            coords.text((area.isEmpty() ? "Crystal Hollows" : CrystalHollows.pretty(area))
                            + String.format(Locale.ROOT, " %d,%d",
                            (int) mc.player.getX(), (int) mc.player.getZ()))
                    .color(t.text());
        }
        return true;
    }

    private void rebuild() {
        root.clearChildren();
        quadrants.clear();
        dots.clear();
        Theme t = Themes.current();

        root.display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.COLUMN)
                .alignItems(GuiAlignment.START)
                .rowGap(GuiLength.pixels(2f))
                .gap(2f)
                .padding(PAD)
                .cornerRadius(8f)
                .width(MAP + PAD * 2f);
        applyTheme();

        field = new ContainerComponent();
        field.size(MAP, MAP).cornerRadius(4f)
                .backgroundColor(Theme.withAlpha(0xFF101318, 0.85f));
        root.add(field);

        float half = MAP / 2f;
        for (int q = 0; q < 4; q++) {
            ContainerComponent quad = new ContainerComponent();
            quad.size(half, half).position(GuiPositionType.ABSOLUTE)
                    .x(q % 2 == 0 ? 0f : half)
                    .y(q < 2 ? 0f : half)
                    .visible(false);
            quadrants.add(quad);
            field.add(quad);
        }

        ContainerComponent vertical = new ContainerComponent();
        vertical.size(1f, MAP).position(GuiPositionType.ABSOLUTE).x(half).y(0f)
                .backgroundColor(Theme.withAlpha(t.textFaint(), 0.25f));
        field.add(vertical);
        ContainerComponent horizontal = new ContainerComponent();
        horizontal.size(MAP, 1f).position(GuiPositionType.ABSOLUTE).x(0f).y(half)
                .backgroundColor(Theme.withAlpha(t.textFaint(), 0.25f));
        field.add(horizontal);

        facing = new ContainerComponent();
        facing.position(GuiPositionType.ABSOLUTE).size(1f, 1f).visible(false);
        field.add(facing);

        playerDot = new ContainerComponent();
        playerDot.size(DOT, DOT).cornerRadius(DOT / 2f)
                .position(GuiPositionType.ABSOLUTE).visible(false);
        field.add(playerDot);

        coords = null;
        if (map.showCoords()) {
            coords = new TextComponent().font(HudText.SMALL).textScalePixels(7f)
                    .color(t.text()).width(MAP);
            root.add(coords);
        }
    }

    /** A pooled waypoint dot, created on first use and reused from then on. */
    private ContainerComponent dot(int index) {
        while (dots.size() <= index) {
            ContainerComponent dot = new ContainerComponent();
            dot.size(DOT, DOT).cornerRadius(DOT / 2f)
                    .position(GuiPositionType.ABSOLUTE).visible(false);
            dots.add(dot);
            field.add(dot);
        }
        return dots.get(index);
    }

    /** Puts a marker on the field at a world position, centred on its own size. */
    private void place(ContainerComponent marker, double worldX, double worldZ, float size) {
        marker.x(fx(worldX) - size / 2f).y(fz(worldZ) - size / 2f);
    }

    private static float fx(double worldX) {
        return clamp((float) (CrystalHollows.fracX(worldX) * MAP));
    }

    private static float fz(double worldZ) {
        return clamp((float) (CrystalHollows.fracZ(worldZ) * MAP));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(MAP, v));
    }
}
