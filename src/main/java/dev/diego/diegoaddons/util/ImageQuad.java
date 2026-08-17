package dev.diego.diegoaddons.util;

import net.minecraft.world.phys.Vec3;

/**
 * A rectangle in the world with a picture on it: where it is, how big, and which part of the image
 * lands on it.
 *
 * <p>The rectangle is given as a corner plus the two edge vectors leaving it - {@code right} across
 * and {@code up} upwards - rather than as two opposite corners, because the surfaces this is drawn
 * on are vertical walls facing four different ways and a pair of corners says nothing about which
 * way round the picture goes.
 *
 * <p>{@code v0} is the <b>top</b> edge of the texture, matching how an image is read rather than how
 * the world is measured: {@code origin + up} is the top-left corner and carries {@code (u0, v0)}.
 *
 * <p>{@link #fit} is the whole of the stretch / fill / fit choice, and the three differ in what they
 * are willing to lose:
 * <ul>
 *   <li><b>Stretch</b> uses all of the image and all of the rectangle, and distorts whichever axis
 *       does not match.</li>
 *   <li><b>Fill</b> keeps the shape and covers the rectangle, cropping the overflow - done in the
 *       UVs, so the geometry is untouched and nothing is drawn that is not seen.</li>
 *   <li><b>Fit</b> keeps the shape and shows all of the image, shrinking the rectangle and leaving
 *       the rest of the surface bare - done in the geometry, so nothing is painted over the gap.</li>
 * </ul>
 */
public record ImageQuad(Vec3 origin, Vec3 right, Vec3 up, float u0, float v0, float u1, float v1) {
    /** Fill the rectangle with the whole image, distorting it. */
    public static final int STRETCH = 0;
    /** Fill the rectangle, keep the aspect ratio, crop what does not fit. */
    public static final int FILL = 1;
    /** Show the whole image, keep the aspect ratio, leave the rest of the rectangle bare. */
    public static final int FIT = 2;

    /** The face's outward direction, for placing the quad just off the surface it covers. */
    public Vec3 normal() {
        Vec3 n = right.cross(up);
        double len = n.length();
        return len < 1.0e-6 ? new Vec3(0, 1, 0) : n.scale(1.0 / len);
    }

    /** The same rectangle moved along its own normal - how a picture is kept off the wall it covers. */
    public ImageQuad offset(double distance) {
        Vec3 d = normal().scale(distance);
        return new ImageQuad(origin.add(d), right, up, u0, v0, u1, v1);
    }

    /**
     * The same picture seen from behind: the rectangle read the other way across, so the image is
     * the right way round rather than mirrored, and pushed to the far side of the surface.
     */
    public ImageQuad flipped(double distance) {
        Vec3 d = normal().scale(-distance);
        return new ImageQuad(origin.add(right).add(d), right.scale(-1), up, u0, v0, u1, v1);
    }

    /**
     * Places an image on a rectangle in one of the three modes.
     *
     * @param origin the bottom-left corner of the surface
     * @param right  the surface's width edge, in world units
     * @param up     the surface's height edge, in world units
     * @param aspect the image's width over its height
     * @param mode   {@link #STRETCH}, {@link #FILL} or {@link #FIT}
     */
    public static ImageQuad fit(Vec3 origin, Vec3 right, Vec3 up, double aspect, int mode) {
        double w = right.length();
        double h = up.length();
        if (w <= 0 || h <= 0 || aspect <= 0 || mode == STRETCH) {
            return new ImageQuad(origin, right, up, 0f, 0f, 1f, 1f);
        }
        double surface = w / h;
        if (mode == FILL) {
            // Crop the axis with the surplus. The image is wider than the surface -> the sides go;
            // taller -> the top and bottom go. Centred either way, since a crop biased to one edge
            // would cut the subject out of half the pictures anyone actually hangs up.
            if (aspect > surface) {
                float keep = (float) (surface / aspect);
                float edge = (1f - keep) / 2f;
                return new ImageQuad(origin, right, up, edge, 0f, 1f - edge, 1f);
            }
            float keep = (float) (aspect / surface);
            float edge = (1f - keep) / 2f;
            return new ImageQuad(origin, right, up, 0f, edge, 1f, 1f - edge);
        }
        // FIT: shrink the rectangle to the image's shape and centre it on the surface.
        if (aspect > surface) {
            double scale = surface / aspect;                 // full width, less height
            Vec3 shorter = up.scale(scale);
            Vec3 centred = origin.add(up.scale((1 - scale) / 2));
            return new ImageQuad(centred, right, shorter, 0f, 0f, 1f, 1f);
        }
        double scale = aspect / surface;                     // full height, less width
        Vec3 narrower = right.scale(scale);
        Vec3 centred = origin.add(right.scale((1 - scale) / 2));
        return new ImageQuad(centred, narrower, up, 0f, 0f, 1f, 1f);
    }
}
