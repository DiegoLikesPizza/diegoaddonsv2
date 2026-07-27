package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.CrystalHollows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * A Crystal Hollows minimap. The Hollows are a fixed 202-823 square on both axes, so a player's
 * coordinates map straight onto it - no scanning needed for your own dot. The four quadrants are
 * tinted with whichever mining region you have found in each (learned live, since the layout can
 * differ per visit), the Nucleus sits at the centre, and every structure/grotto/chest waypoint is
 * plotted as a dot. Only drawn while you are actually in the Hollows.
 */
public class CrystalHollowsMapModule extends HudModule {
    private static final int MAP = 104;
    private static final int PAD = 6;
    private static final int LINE_H = Fonts.SMALL_H;

    private final BooleanSetting showRegions =
            new BooleanSetting(this, "regions", "Tint found regions", true);
    private final BooleanSetting showWaypoints =
            new BooleanSetting(this, "waypoints", "Show waypoints", true);
    private final BooleanSetting showCoords =
            new BooleanSetting(this, "coords", "Show area + coords", true);

    public CrystalHollowsMapModule() {
        super("crystalmap", Category.MINING, "Crystal Hollows Map",
                "A minimap of the Crystal Hollows.", false);
        settings.add(showRegions);
        settings.add(showWaypoints);
        settings.add(showCoords);
    }

    @Override
    protected String label() {
        return "Crystal Hollows Map";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;
    }

    // --- read by the RenderLib element -----------------------------------------------------------

    public boolean showRegions() {
        return showRegions.get();
    }

    public boolean showWaypoints() {
        return showWaypoints.get();
    }

    public boolean showCoords() {
        return showCoords.get();
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.CrystalMapElement(this, root);
    }

    @Override
    public int hudWidth(Font font, Minecraft mc, boolean editor) {
        return PAD * 2 + MAP;
    }

    @Override
    public int hudHeight(Minecraft mc, boolean editor) {
        return PAD * 2 + MAP + (showCoords.get() ? LINE_H + 2 : 0);
    }

    @Override
    public boolean drawLocal(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth, Minecraft mc, boolean editor) {
        boolean live = CrystalHollows.inHollows() && mc.player != null;
        if (!live && !editor) {
            return false;
        }
        int w = hudWidth(font, mc, editor);
        int h = hudHeight(mc, editor);
        int bg = (0xCC << 24) | (t.surface() & 0x00FFFFFF);
        UiRender.fillRounded(g, 0, 0, w, h, 8, bg, smooth);
        UiRender.strokeRounded(g, 0, 0, w, h, 8, Theme.withAlpha(t.border(), 0.9f), smooth);

        int mx = PAD, my = PAD;
        // Map field.
        UiRender.fillRounded(g, mx, my, MAP, MAP, 4, Theme.withAlpha(0xFF101318, 0.85f), smooth);

        int half = MAP / 2;
        if (showRegions.get() && live) {
            for (int q = 0; q < 4; q++) {
                String region = CrystalHollows.quadrantRegion(q);
                if (region == null) {
                    continue;
                }
                int col = q % 2 == 0 ? mx : mx + half;
                int row = q < 2 ? my : my + half;
                g.fill(col, row, col + half, row + half,
                        Theme.withAlpha(CrystalHollows.regionColor(region), 0.35f));
            }
        }
        // Quadrant divider + nucleus.
        g.fill(mx + half, my, mx + half + 1, my + MAP, Theme.withAlpha(t.textFaint(), 0.25f));
        g.fill(mx, my + half, mx + MAP, my + half + 1, Theme.withAlpha(t.textFaint(), 0.25f));

        if (editor && !live) {
            UiRender.text(g, font, "Crystal Hollows", Fonts.SMALL,
                    mx + 6, my + half - 4, t.textMuted());
            return true;
        }

        if (showWaypoints.get()) {
            for (CrystalHollows.Waypoint wp : CrystalHollows.waypoints()) {
                int[] e = toMap(mx, my, wp.pos().x, wp.pos().z);
                dot(g, e[0], e[1], 2, wp.type().color);
            }
        }

        // Player dot with a facing tick.
        int[] pe = toMap(mx, my, mc.player.getX(), mc.player.getZ());
        double yaw = Math.toRadians(mc.player.getYRot());
        int fx = (int) Math.round(pe[0] - Math.sin(yaw) * 5);
        int fz = (int) Math.round(pe[1] + Math.cos(yaw) * 5);
        g.fill(Math.min(pe[0], fx), Math.min(pe[1], fz), Math.max(pe[0], fx) + 1, Math.max(pe[1], fz) + 1, t.accent());
        dot(g, pe[0], pe[1], 2, t.accent());

        if (showCoords.get()) {
            String area = CrystalHollows.area();
            String txt = (area.isEmpty() ? "Crystal Hollows" : CrystalHollows.pretty(area))
                    + String.format(Locale.ROOT, " §7%d,%d", (int) mc.player.getX(), (int) mc.player.getZ());
            UiRender.text(g, font, txt, Fonts.SMALL, PAD, my + MAP + 2, t.text());
        }
        return true;
    }

    private int[] toMap(int mx, int my, double wx, double wz) {
        int ex = mx + (int) Math.round(CrystalHollows.fracX(wx) * MAP);
        int ez = my + (int) Math.round(CrystalHollows.fracZ(wz) * MAP);
        return new int[]{Math.max(mx, Math.min(mx + MAP, ex)), Math.max(my, Math.min(my + MAP, ez))};
    }

    private static void dot(GuiGraphicsExtractor g, int x, int y, int r, int color) {
        g.fill(x - r - 1, y - r - 1, x + r + 2, y + r + 2, 0xFF000000);
        g.fill(x - r, y - r, x + r + 1, y + r + 1, color);
    }
}
