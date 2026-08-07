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
