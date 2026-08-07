package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The vertex work behind everything this mod draws in the world: boxes, graduated boxes, the twelve
 * edges of a box, and line segments.
 *
 * <p>It lives in one place because there are two callers with the same needs - {@link WorldRender}
 * for the solvers and {@link EspWorld} for the ESP - and the alternative is each of them growing its
 * own copy of the same six-quad winding and the same camera-relative arithmetic.
 *
 * <p>Two conventions run through all of it. Vertices go in <b>camera-relative</b>: the caller works
 * in plain world coordinates and the camera position is subtracted here. And a vertical
 * <b>gradient</b> is a property of a vertex colour rather than a stack of slices - the colour of a
 * vertex is interpolated by its height between two bounds, so the GPU draws one continuous fade.
 */
public final class WorldGeometry {
    private WorldGeometry() {
    }

    /**
     * The twelve edges of a cuboid, as thin boxes of the given thickness.
     *
     * <p>Boxes rather than line primitives: a line renders one pixel wide however far away it is, so
     * its weight cannot be set and does not fall off with distance. A thin box is an edge whose
     * thickness means blocks, and it sits in perspective like everything else out there.
     *
     * @param thickness edge width in blocks
     */
    public static List<AABB> edges(AABB b, double thickness) {
        double t = thickness / 2.0;
        List<AABB> out = new ArrayList<>(12);
        // Four along X, four along Z, four uprights along Y.
        for (double[] e : new double[][]{
                {b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ},
                {b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ},
                {b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ},
                {b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ},
                {b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ},
                {b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ},
                {b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ},
                {b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ},
                {b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ},
                {b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ},
        }) {
            out.add(new AABB(
                    Math.min(e[0], e[3]) - t, Math.min(e[1], e[4]) - t, Math.min(e[2], e[5]) - t,
                    Math.max(e[0], e[3]) + t, Math.max(e[1], e[4]) + t, Math.max(e[2], e[5]) + t));
        }
        return out;
    }

    /** A solid box in one colour. */
    public static void fillBox(PoseStack.Pose p, VertexConsumer vc, AABB b, Vec3 cam, int argb) {
        fillBox(p, vc, b, cam, argb, argb, b.minY, b.maxY);
    }

    /**
     * A solid box graduated up its own height, from {@code argbBottom} to {@code argbTop}.
     *
     * <p>{@code gradMinY}/{@code gradMaxY} are the heights those two colours belong to, which are
     * <em>not</em> always the box's own. An outline's twelve edges each want their colour taken from
     * where they sit in the whole box: pass the box's range and the bottom ring comes out at the
     * bottom colour, the uprights fade along their length, and the four edges read as one shape
     * rather than twelve separately-graduated sticks.
     */
    public static void fillBox(PoseStack.Pose p, VertexConsumer vc, AABB b, Vec3 cam,
                               int argbBottom, int argbTop, double gradMinY, double gradMaxY) {
        float x1 = (float) (b.minX - cam.x), y1 = (float) (b.minY - cam.y), z1 = (float) (b.minZ - cam.z);
        float x2 = (float) (b.maxX - cam.x), y2 = (float) (b.maxY - cam.y), z2 = (float) (b.maxZ - cam.z);
        int cLo = colourAt(b.minY, gradMinY, gradMaxY, argbBottom, argbTop);
        int cHi = colourAt(b.maxY, gradMinY, gradMaxY, argbBottom, argbTop);

        // Wound so the box is solid seen from any side.
        quad(vc, p, x1, y1, z1, cLo, x1, y2, z1, cHi, x2, y2, z1, cHi, x2, y1, z1, cLo);   // north
        quad(vc, p, x2, y1, z2, cLo, x2, y2, z2, cHi, x1, y2, z2, cHi, x1, y1, z2, cLo);   // south
        quad(vc, p, x1, y1, z2, cLo, x1, y2, z2, cHi, x1, y2, z1, cHi, x1, y1, z1, cLo);   // west
        quad(vc, p, x2, y1, z1, cLo, x2, y2, z1, cHi, x2, y2, z2, cHi, x2, y1, z2, cLo);   // east
        quad(vc, p, x1, y1, z1, cLo, x2, y1, z1, cLo, x2, y1, z2, cLo, x1, y1, z2, cLo);   // bottom
        quad(vc, p, x1, y2, z2, cHi, x2, y2, z2, cHi, x2, y2, z1, cHi, x1, y2, z1, cHi);   // top
    }

    /** One line segment, camera-relative, with a normal along its direction. */
    public static void line(PoseStack.Pose p, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam,
                            int argb, float width) {
        float ax = (float) (a.x - cam.x), ay = (float) (a.y - cam.y), az = (float) (a.z - cam.z);
        float bx = (float) (b.x - cam.x), by = (float) (b.y - cam.y), bz = (float) (b.z - cam.z);
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f) {
            return;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        // Each line vertex must carry a line width, or the buffer fails to flush and the game crashes.
        vc.addVertex(p, ax, ay, az).setColor(argb).setNormal(p, nx, ny, nz).setLineWidth(width);
        vc.addVertex(p, bx, by, bz).setColor(argb).setNormal(p, nx, ny, nz).setLineWidth(width);
    }

    /** The colour at height {@code y}, interpolated between the two bounds. */
    private static int colourAt(double y, double minY, double maxY, int bottom, int top) {
        if (bottom == top) {
            return bottom;
        }
        double span = maxY - minY;
        if (span <= 1.0e-9) {
            return bottom;
        }
        return lerp(bottom, top, (float) Math.clamp((y - minY) / span, 0.0, 1.0));
    }

    private static int lerp(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int ar = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int ag = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int ab = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p,
                             float ax, float ay, float az, int ac,
                             float bx, float by, float bz, int bc,
                             float cx, float cy, float cz, int cc,
                             float dx, float dy, float dz, int dc) {
        vc.addVertex(p, ax, ay, az).setColor(ac);
        vc.addVertex(p, bx, by, bz).setColor(bc);
        vc.addVertex(p, cx, cy, cz).setColor(cc);
        vc.addVertex(p, dx, dy, dz).setColor(dc);
    }
}
