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
    /** Gap between the bottom of the field and the coordinates line. */
    private static final int COORD_GAP = 3;
    /** Radius of a waypoint / player dot, before its 1px outline. */
    private static final int DOT_R = 2;
    /** How far the facing tick reaches out of the player dot, px. */
    private static final int FACING = 5;
    /** The field itself - darker than any theme surface, so the region tints stay readable on it. */
    private static final int FIELD = 0xFF101318;

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

    // --- read by the HUD element -----------------------------------------------------------

    public boolean showRegions() {
        return showRegions.get();
    }

    public boolean showWaypoints() {
        return showWaypoints.get();
    }

    public boolean showCoords() {
        return showCoords.get();
    }





    // --- the HUD element ------------------------------------------------------------------------

    /**
     * The minimap as a configlib element.
     *
     * <p>The Hollows are a fixed square in world space, so this needs no scan and no cached geometry:
     * every position on it is {@link CrystalHollows#fracX}/{@link CrystalHollows#fracZ} of the way
     * across a 104px field. Everything is drawn straight from live state each frame, which is cheap
     * enough at this size and means there is no staleness to manage.
     */
    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                return MAP + PAD * 2;
            }

            @Override
            public int height() {
                return MAP + PAD * 2 + (showCoords.get() ? COORD_GAP + LINE_H : 0);
            }

            /** The map means nothing anywhere else, and the player dot would be off the field. */
            @Override
            public boolean shouldRender() {
                return CrystalHollows.inHollows();
            }

            @Override
            public void render(GuiGraphicsExtractor g) {
                paint(g);
            }

            /**
             * The editor is opened from wherever the user happens to be. The field, its dividers and
             * any regions already learned still draw, so the element stays visible and draggable
             * outside the Hollows - only the player dot is missing, and only because there is no
             * position on this map to put it at.
             */
            @Override
            public void renderPreview(GuiGraphicsExtractor g) {
                paint(g);
            }
        };
    }

    /** Draws the whole element from its own top-left. */
    private void paint(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        Theme t = Themes.current();
        boolean smooth = ConfigManager.get().smoothCorners;

        int w = MAP + PAD * 2;
        int h = MAP + PAD * 2 + (showCoords.get() ? COORD_GAP + LINE_H : 0);
        dev.diego.diegoaddons.hud.HudElements.panel(g, this, w, h, 8, smooth);
        UiRender.fillRounded(g, PAD, PAD, MAP, MAP, 4, Theme.withAlpha(FIELD, 0.85f), smooth);

        int half = MAP / 2;
        // A region is only tinted once it has actually been found: the four regions are not in a
        // fixed arrangement, so guessing where they are would be wrong about as often as it is right.
        if (showRegions.get()) {
            for (int q = 0; q < 4; q++) {
                String region = CrystalHollows.quadrantRegion(q);
                if (region == null) {
                    continue;
                }
                int qx = PAD + (q % 2 == 0 ? 0 : half);
                int qy = PAD + (q < 2 ? 0 : half);
                g.fill(qx, qy, qx + half, qy + half,
                        Theme.withAlpha(CrystalHollows.regionColor(region), 0.35f));
            }
        }

        int divider = Theme.withAlpha(t.textFaint(), 0.25f);
        g.fill(PAD + half, PAD, PAD + half + 1, PAD + MAP, divider);
        g.fill(PAD, PAD + half, PAD + MAP, PAD + half + 1, divider);

        if (showWaypoints.get()) {
            for (CrystalHollows.Waypoint wp : CrystalHollows.waypoints()) {
                int[] p = toMap(PAD, PAD, wp.pos().x, wp.pos().z);
                dot(g, p[0], p[1], DOT_R, wp.type().color);
            }
        }

        if (mc.player != null) {
            int[] p = toMap(PAD, PAD, mc.player.getX(), mc.player.getZ());
            // Yaw 0 looks south (+Z) and turns clockwise; on the map +X is right and +Z is down, so
            // the facing unit is (-sin, cos) - the same convention the dungeon map's arrows use.
            double yaw = Math.toRadians(mc.player.getYRot());
            line(g, p[0], p[1],
                    (int) Math.round(p[0] - Math.sin(yaw) * FACING),
                    (int) Math.round(p[1] + Math.cos(yaw) * FACING), t.accent());
            dot(g, p[0], p[1], DOT_R, t.accent());
        }

        if (showCoords.get()) {
            String area = CrystalHollows.area();
            String label = area == null || area.isEmpty() ? "Crystal Hollows" : CrystalHollows.pretty(area);
            String line = mc.player == null ? label
                    : label + String.format(Locale.ROOT, " %d,%d",
                            (int) mc.player.getX(), (int) mc.player.getZ());
            UiRender.text(g, font, line, Fonts.SMALL, PAD, PAD + MAP + COORD_GAP, style().textColor());
        }
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

    /** A 1px line between two points. The facing tick is a few pixels long, so stepping it is fine. */
    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
