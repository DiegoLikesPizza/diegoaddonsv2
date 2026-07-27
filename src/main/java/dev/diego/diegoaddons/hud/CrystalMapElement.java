package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.TextComponent;
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
 * Crystal Hollows minimap: a square field, the four quadrants tinted by whichever region has been
 * found in each, waypoint dots, and the player's dot with a tick showing which way they face.
 *
 * <p>The field is built once. Dots are a pool of small boxes that are moved and recoloured, so a
 * tick that only changes the player's position allocates nothing.
 */
public class CrystalMapElement extends HudElement {
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

    public CrystalMapElement(CrystalHollowsMapModule module, ContainerComponent root) {
        super(module, root);
        this.map = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (!CrystalHollows.inHollows() || mc.player == null) {
            return false;
        }
        String shape = "" + map.showCoords() + map.showRegions() + map.showWaypoints();
        if (themeChanged() || !shape.equals(lastShape)) {
            lastShape = shape;
            rebuild();
        }

        Theme t = Themes.current();
        if (map.showRegions()) {
            for (int q = 0; q < quadrants.size(); q++) {
                String region = CrystalHollows.quadrantRegion(q);
                quadrants.get(q).visible(region != null);
                if (region != null) {
                    quadrants.get(q)
                            .backgroundColor(Theme.withAlpha(CrystalHollows.regionColor(region), 0.35f));
                }
            }
        }

        int used = 0;
        if (map.showWaypoints()) {
            for (CrystalHollows.Waypoint wp : CrystalHollows.waypoints()) {
                ContainerComponent dot = dot(used++);
                dot.x(fx(wp.pos().x) - DOT / 2f).y(fz(wp.pos().z) - DOT / 2f)
                        .backgroundColor(wp.type().color).visible(true);
            }
        }
        for (int i = used; i < dots.size(); i++) {
            dots.get(i).visible(false);
        }

        float px = fx(mc.player.getX());
        float pz = fz(mc.player.getZ());
        playerDot.x(px - DOT / 2f).y(pz - DOT / 2f).backgroundColor(t.accent()).visible(true);

        double yaw = Math.toRadians(mc.player.getYRot());
        float tx = (float) (px - Math.sin(yaw) * 5f);
        float tz = (float) (pz + Math.cos(yaw) * 5f);
        facing.x(Math.min(px, tx)).y(Math.min(pz, tz))
                .size(Math.max(1f, Math.abs(tx - px)), Math.max(1f, Math.abs(tz - pz)))
                .backgroundColor(t.accent()).visible(true);

        if (coords != null) {
            String area = CrystalHollows.area();
            coords.text((area.isEmpty() ? "Crystal Hollows" : CrystalHollows.pretty(area))
                    + String.format(Locale.ROOT, " %d,%d",
                    (int) mc.player.getX(), (int) mc.player.getZ()));
        }
        return true;
    }

    private void rebuild() {
        root.clearChildren();
        quadrants.clear();
        dots.clear();
        Theme t = Themes.current();

        asColumn(root, MAP + PAD * 2f, 2f).padding(PAD);
        applyBackground(root, 8f);

        field = new ContainerComponent();
        field.size(MAP, MAP).cornerRadius(4f)
                .position(GuiPositionType.RELATIVE)
                .backgroundColor(Theme.withAlpha(0xFF101318, 0.85f));
        root.add(field);

        float half = MAP / 2f;
        for (int q = 0; q < 4; q++) {
            ContainerComponent quad = block(q % 2 == 0 ? 0f : half, q < 2 ? 0f : half,
                    half, half, 0x00000000);
            quad.visible(false);
            quadrants.add(quad);
            field.add(quad);
        }
        field.add(block(half, 0f, 1f, MAP, Theme.withAlpha(t.textFaint(), 0.25f)));
        field.add(block(0f, half, MAP, 1f, Theme.withAlpha(t.textFaint(), 0.25f)));

        facing = block(0f, 0f, 1f, 1f, t.accent());
        facing.visible(false);
        field.add(facing);

        playerDot = block(0f, 0f, DOT, DOT, t.accent());
        playerDot.cornerRadius(DOT / 2f).visible(false);
        field.add(playerDot);

        coords = null;
        if (map.showCoords()) {
            coords = small("", t.text(), 7f).width(MAP);
            root.add(textRow(coords, MAP, 9f));
        }
    }

    private ContainerComponent dot(int index) {
        while (dots.size() <= index) {
            ContainerComponent dot = block(0f, 0f, DOT, DOT, 0xFFFFFFFF);
            dot.cornerRadius(DOT / 2f).visible(false);
            dots.add(dot);
            field.add(dot);
        }
        return dots.get(index);
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
