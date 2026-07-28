package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.EspModule;
import net.minecraft.world.phys.AABB;

/**
 * Draws an ESP box in whichever shape and colour its module is set to, so every ESP feature reaches
 * for one call and they all end up looking the same.
 *
 * <p>A gradient is drawn <b>up</b> the box: the bottom edges take the first colour, the top edges
 * the second, and the uprights are split into bands between them. There is no per-vertex colour to
 * be had here - the outline is built from thin solid boxes - so the blend is made out of the pieces
 * the shape already has.
 */
public final class EspRender {
    private static final double EDGE = 0.05;
    /** How many bands an upright is cut into for a gradient. Enough to read as a blend, cheap enough. */
    private static final int BANDS = 6;

    private EspRender() {
    }

    /** Draws {@code box} the way {@code module} is set to draw it. */
    public static void draw(AABB box, EspModule module) {
        switch (module.espStyle()) {
            case EspModule.BOX -> filled(box, module.espColor());
            case EspModule.SQUARE_2D -> EspDraw.square2d(box, module.espColor().argb());
            default -> outline(box, module.espColor());
        }
    }

    /**
     * As {@link #draw}, in a colour of the caller's choosing - for the features whose colour carries
     * meaning of its own, like a slayer boss coloured by its tier. The shape is still the module's.
     */
    public static void draw(AABB box, EspModule module, int argb) {
        switch (module.espStyle()) {
            case EspModule.BOX -> WorldRender.filledBox(box, translucent(argb), true);
            case EspModule.SQUARE_2D -> EspDraw.square2d(box, argb);
            default -> WorldRender.thickBox(box, argb, EDGE, true);
        }
    }

    /** The classic: twelve edges, drawn through walls. */
    private static void outline(AABB box, ColorSetting colour) {
        if (colour.mode() == ColorSetting.SINGLE) {
            WorldRender.thickBox(box, colour.argb(), EDGE, true);
            return;
        }
        // Bottom ring, top ring, then the uprights banded between them.
        WorldRender.thickBox(flat(box, box.minY), colour.argbAt(0f), EDGE, true);
        WorldRender.thickBox(flat(box, box.maxY), colour.argbAt(1f), EDGE, true);
        for (int i = 0; i < BANDS; i++) {
            double y1 = box.minY + (box.maxY - box.minY) * i / (double) BANDS;
            double y2 = box.minY + (box.maxY - box.minY) * (i + 1) / (double) BANDS;
            int argb = colour.argbAt((i + 0.5f) / BANDS);
            uprights(box, y1, y2, argb);
        }
    }

    /** A solid box; a gradient is stacked as slices, since a fill has one colour at a time. */
    private static void filled(AABB box, ColorSetting colour) {
        if (colour.mode() == ColorSetting.SINGLE) {
            WorldRender.filledBox(box, translucent(colour.argb()), true);
            return;
        }
        for (int i = 0; i < BANDS; i++) {
            double y1 = box.minY + (box.maxY - box.minY) * i / (double) BANDS;
            double y2 = box.minY + (box.maxY - box.minY) * (i + 1) / (double) BANDS;
            WorldRender.filledBox(new AABB(box.minX, y1, box.minZ, box.maxX, y2, box.maxZ),
                    translucent(colour.argbAt((i + 0.5f) / BANDS)), true);
        }
    }

    /** A filled ESP at full alpha hides the thing it is pointing at. */
    private static int translucent(int argb) {
        return (argb & 0x00FFFFFF) | (0x66 << 24);
    }

    /** The box flattened to a ring at one height. */
    private static AABB flat(AABB box, double y) {
        return new AABB(box.minX, y, box.minZ, box.maxX, y, box.maxZ);
    }

    /** The four vertical edges between two heights. */
    private static void uprights(AABB box, double y1, double y2, int argb) {
        double t = EDGE / 2.0;
        for (double[] xz : new double[][]{
                {box.minX, box.minZ}, {box.maxX, box.minZ},
                {box.minX, box.maxZ}, {box.maxX, box.maxZ}}) {
            WorldRender.filledBox(new AABB(
                    xz[0] - t, y1, xz[1] - t, xz[0] + t, y2, xz[1] + t), argb, true);
        }
    }
}
