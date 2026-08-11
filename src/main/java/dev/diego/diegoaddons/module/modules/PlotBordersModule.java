package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.Garden;
import dev.diego.diegoaddons.util.Pests;
import dev.diego.diegoaddons.util.WorldRender;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the plot grid in the world: a band around the plot you are standing in, and a marker on the
 * plots that have pests in them.
 *
 * <p>This is the other half of {@link PestEspModule}, and the half that gets you there. The ESP can
 * only show a pest once you are in its plot; what the pest widget knows and the ESP cannot show is
 * <i>which</i> plot to go to. Plot borders answer that from the fixed grid - see {@link Garden} -
 * so a plot can be outlined from across the Garden without ever having been walked into.
 *
 * <p>The borders are bands at your own height rather than full-height walls. A plot is 96×96×256
 * blocks, and drawn as it really is it is a wall you cannot see the Garden through.
 */
public class PlotBordersModule extends Module {
    public static PlotBordersModule INSTANCE;

    /** How far above and below the player the band is drawn. */
    private static final double BAND_BELOW = 2;
    private static final double BAND_ABOVE = 4;
    /** How tall the marker on an infested plot is - tall enough to clear the crops from a distance. */
    private static final double MARKER_HEIGHT = 30;

    private final BooleanSetting showCurrent =
            new BooleanSetting(this, "current", "Border around your plot", true);
    private final BooleanSetting showInfested =
            new BooleanSetting(this, "infested", "Mark infested plots", true);
    private final BooleanSetting beacon =
            new BooleanSetting(this, "beacon", "Beam to the nearest infested plot", true);
    private final BooleanSetting labels =
            new BooleanSetting(this, "labels", "Name the plots", true);
    private final ColorSetting color =
            new ColorSetting(this, "color", "Border colour", 0xFF55FFFF);
    private final ColorSetting infestedColor =
            new ColorSetting(this, "infestedColor", "Infested colour", 0xFFFF5555);

    public PlotBordersModule() {
        super("plotborders", Category.GARDEN, "Plot Borders",
                "Outline the plot you are in, and mark the plots that have pests.");
        settings.add(showCurrent);
        settings.add(showInfested);
        settings.add(beacon);
        settings.add(labels);
        settings.add(color);
        settings.add(infestedColor);
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || !Pests.inGarden()) {
            return;
        }
        double y = mc.player.getY();

        if (showCurrent.get()) {
            int here = Garden.currentPlot(mc);
            AABB box = here < 0 ? null : Garden.plotBox(here, y - BAND_BELOW, y + BAND_ABOVE);
            if (box != null) {
                WorldRender.box(box, color.argb(), false);
            }
        }

        if (!showInfested.get()) {
            return;
        }
        int nearest = beacon.get() ? Garden.nearestInfested(mc) : -1;
        for (int id : Garden.infestedPlots()) {
            AABB box = Garden.plotBox(id, y - BAND_BELOW, y + MARKER_HEIGHT);
            if (box == null) {
                continue;
            }
            // Through walls: the point of this one is to be seen from the other side of the Garden,
            // where there is a barn and a hedge and twenty rows of crops in the way.
            WorldRender.box(box, infestedColor.argb(), true);
            Vec3 mid = Garden.plotMiddle(id);
            if (mid == null) {
                continue;
            }
            if (labels.get()) {
                WorldRender.text(Garden.plotName(id), new Vec3(mid.x, y + MARKER_HEIGHT + 1, mid.z), 1.2f);
            }
            if (id == nearest) {
                WorldRender.line(new Vec3(mid.x, y - BAND_BELOW, mid.z),
                        new Vec3(mid.x, y + MARKER_HEIGHT * 3, mid.z), infestedColor.argb());
            }
        }
    }
}
