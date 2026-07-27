package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A small convenience layer over {@link WorldRender} for the ESP-style features (Slayer beacon,
 * nukekebi, minibosses). It adds the three primitives those features share:
 *
 * <ul>
 *   <li>{@link #highlight} - a thick box (just {@code WorldRender.thickBox}, through walls).</li>
 *   <li>{@link #tracer} - a line from the camera to a world point, so a target reads as "over there".</li>
 *   <li>{@link #timerLabel} - a floating label, for a countdown above a target.</li>
 *   <li>{@link #arrow2d} - a <b>2D HUD arrow</b> at the screen edge pointing at an off-screen target,
 *       for when the thing to look at is not in view.</li>
 * </ul>
 *
 * <p>The world primitives forward straight to {@link WorldRender}, which already double-buffers per
 * tick. The 2D arrows are queued the same way and drawn from the HUD render pass, since a screen-space
 * projection can only be done once the camera for the frame is known.
 */
public final class EspDraw {
    private static final int OUTLINE = 0xFF000000;
    /** Keep the arrow this many GUI pixels inside the screen edge. */
    private static final int MARGIN = 26;
    /** Arrow half-size in GUI pixels. */
    private static final double ARROW = 9.0;

    private record Arrow(Vec3 target, int argb) {
    }

    private static final List<Arrow> BUILDING = new ArrayList<>();
    private static volatile List<Arrow> RENDER = new ArrayList<>();

    private EspDraw() {
    }

    // --- world primitives -----------------------------------------------------------------------

    /** A thick outlined box around a world region, drawn through walls. */
    public static void highlight(AABB box, int argb, double thickness) {
        WorldRender.thickBox(box, argb, thickness, true);
    }

    /** A line from the camera to a world point. */
    public static void tracer(Vec3 target, int argb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vec3 look = mc.player.getViewVector(1.0f);
        // Start a touch in front of the camera so the near plane does not clip the first vertex.
        WorldRender.line(cam.add(look.scale(0.5)), target, argb);
    }

    /** A label floating at a world position (e.g. a countdown), always facing the camera. */
    public static void timerLabel(Vec3 pos, String text) {
        WorldRender.text(text, pos, 1.0f);
    }

    // --- 2D off-screen arrow --------------------------------------------------------------------

    /** Queues a HUD arrow that points at {@code target} while it is off-screen. Submit each tick. */
    public static void arrow2d(Vec3 target, int argb) {
        synchronized (BUILDING) {
            BUILDING.add(new Arrow(target, argb));
        }
    }

    /** Promotes this tick's arrows to the set drawn each frame. Call right after {@code WorldRender.flip()}. */
    public static void flip() {
        synchronized (BUILDING) {
            RENDER = new ArrayList<>(BUILDING);
            BUILDING.clear();
        }
    }

    public static void clear() {
        synchronized (BUILDING) {
            BUILDING.clear();
        }
        RENDER = new ArrayList<>();
    }

    /**
     * Draws the queued off-screen arrows. Each target's bearing is taken relative to the camera's
     * yaw/pitch (so no view-matrix handedness to get wrong): an arrow is shown only while the target
     * sits outside the field of view, placed on an ellipse around the crosshair and pointing at it.
     */
    public static void renderHud(GuiGraphicsExtractor g, Minecraft mc) {
        List<Arrow> arrows = RENDER;
        if (arrows.isEmpty() || mc.player == null || mc.options.hideGui || mc.gameRenderer == null) {
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double rx = w / 2.0 - MARGIN;
        double ry = h / 2.0 - MARGIN;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        double camYaw = Math.toRadians(mc.player.getYRot());
        double camPitch = Math.toRadians(mc.player.getXRot());
        double halfV = Math.toRadians(fov(mc)) / 2.0;
        double aspect = h == 0 ? 1.0 : (double) w / h;
        double halfH = Math.atan(Math.tan(halfV) * aspect);

        for (Arrow a : arrows) {
            Vec3 d = a.target().subtract(cam);
            double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
            if (horiz < 1e-4 && Math.abs(d.y) < 1e-4) {
                continue;   // sitting on the camera; nothing to point at
            }
            double yawT = Math.atan2(-d.x, d.z);          // Minecraft yaw of the target direction
            double pitchT = Math.atan2(-d.y, horiz);      // Minecraft pitch (down positive)
            double relYaw = wrap(yawT - camYaw);
            double relPitch = pitchT - camPitch;

            boolean onScreen = Math.abs(relYaw) < halfH && Math.abs(relPitch) < halfV;
            if (onScreen) {
                continue;   // in view already; no need to point at it
            }
            // Screen-space direction to the target: x right, y down.
            double ang = Math.atan2(Math.sin(relPitch), Math.sin(relYaw));
            double ex = cx + Math.cos(ang) * rx;
            double ey = cy + Math.sin(ang) * ry;
            drawArrow(g, ex, ey, ang, a.argb());
        }
    }

    /** A filled arrowhead centred at (x,y), tip pointing along {@code ang}; black outline behind. */
    private static void drawArrow(GuiGraphicsExtractor g, double x, double y, double ang, int color) {
        double fx = Math.cos(ang), fy = Math.sin(ang);   // forward (screen space)
        double px = -fy, py = fx;                        // perpendicular
        arrowTriangle(g, x, y, fx, fy, px, py, ARROW + 1.0, OUTLINE);
        arrowTriangle(g, x, y, fx, fy, px, py, ARROW, color);
    }

    private static void arrowTriangle(GuiGraphicsExtractor g, double x, double y, double fx, double fy,
                                      double px, double py, double size, int color) {
        double tip = size, back = size * 0.6, half = size * 0.7;
        double ax = x + fx * tip, ay = y + fy * tip;
        double bcx = x - fx * back, bcy = y - fy * back;
        double bx = bcx + px * half, by = bcy + py * half;
        double cxp = bcx - px * half, cyp = bcy - py * half;
        fillTriangle(g, ax, ay, bx, by, cxp, cyp, color);
    }

    /** Scanline fill of a small triangle, one 1px row at a time via {@link GuiGraphicsExtractor#fill}. */
    private static void fillTriangle(GuiGraphicsExtractor g, double ax, double ay,
                                     double bx, double by, double cx, double cy, int color) {
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        double[][] edges = {{ax, ay, bx, by}, {bx, by, cx, cy}, {cx, cy, ax, ay}};
        for (int yy = minY; yy < maxY; yy++) {
            double yc = yy + 0.5;
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (double[] e : edges) {
                double y1 = e[1], y2 = e[3];
                if ((yc >= y1 && yc < y2) || (yc >= y2 && yc < y1)) {
                    double xk = e[0] + (yc - y1) / (y2 - y1) * (e[2] - e[0]);
                    lo = Math.min(lo, xk);
                    hi = Math.max(hi, xk);
                }
            }
            int xs = (int) Math.round(lo), xe = (int) Math.round(hi);
            if (xe > xs) {
                g.fill(xs, yy, xe, yy + 1, color);
            }
        }
    }

    private static double fov(Minecraft mc) {
        try {
            return mc.options.fov().get();
        } catch (Throwable ignored) {
            return 70.0;
        }
    }

    private static double wrap(double a) {
        while (a <= -Math.PI) {
            a += 2 * Math.PI;
        }
        while (a > Math.PI) {
            a -= 2 * Math.PI;
        }
        return a;
    }
}
