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

    /** A world box to be drawn as a flat rectangle around wherever it lands on screen. */
    private record Square(AABB box, int argb) {
    }

    private static final List<Arrow> BUILDING = new ArrayList<>();
    private static volatile List<Arrow> RENDER = new ArrayList<>();
    private static final List<Square> BUILDING_SQUARES = new ArrayList<>();
    private static volatile List<Square> RENDER_SQUARES = new ArrayList<>();

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

    /**
     * Queues a box to be drawn as a 2D rectangle around its outline on screen - the flat sort of ESP,
     * where what you see is a box on the glass rather than a cage in the world.
     */
    public static void square2d(AABB box, int argb) {
        synchronized (BUILDING) {
            BUILDING_SQUARES.add(new Square(box, argb));
        }
    }

    /** Promotes this tick's arrows to the set drawn each frame. Call right after {@code WorldRender.flip()}. */
    public static void flip() {
        synchronized (BUILDING) {
            RENDER = new ArrayList<>(BUILDING);
            BUILDING.clear();
            RENDER_SQUARES = new ArrayList<>(BUILDING_SQUARES);
            BUILDING_SQUARES.clear();
        }
    }

    public static void clear() {
        synchronized (BUILDING) {
            BUILDING.clear();
            BUILDING_SQUARES.clear();
        }
        RENDER = new ArrayList<>();
        RENDER_SQUARES = new ArrayList<>();
    }

    /**
     * Draws the queued off-screen arrows. Each target's bearing is taken relative to the camera's
     * yaw/pitch (so no view-matrix handedness to get wrong): an arrow is shown only while the target
     * sits outside the field of view, placed on an ellipse around the crosshair and pointing at it.
     */
    public static void renderHud(GuiGraphicsExtractor g, Minecraft mc) {
        List<Arrow> arrows = RENDER;
        List<Square> squares = RENDER_SQUARES;
        if (mc.player == null || mc.options.hideGui || mc.gameRenderer == null) {
            return;
        }
        squares(g, mc, squares);
        if (arrows.isEmpty()) {
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

    /**
     * Draws each queued box as the rectangle it covers on screen.
     *
     * <p>All eight corners are projected and the rectangle is their extent, so a mob standing at an
     * angle is still framed by what it actually occupies rather than by its width from one side. A
     * corner behind the camera has no sensible place on screen, so a box with any corner behind is
     * dropped rather than smeared across it.
     */
    private static void squares(GuiGraphicsExtractor g, Minecraft mc, List<Square> squares) {
        if (squares.isEmpty()) {
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        double camYaw = Math.toRadians(mc.player.getYRot());
        double camPitch = Math.toRadians(mc.player.getXRot());
        double halfV = Math.toRadians(fov(mc)) / 2.0;
        double halfH = Math.atan(Math.tan(halfV) * (h == 0 ? 1.0 : (double) w / h));

        for (Square s : squares) {
            AABB b = s.box();
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            boolean visible = true;
            for (int i = 0; i < 8 && visible; i++) {
                Vec3 corner = new Vec3(
                        (i & 1) == 0 ? b.minX : b.maxX,
                        (i & 2) == 0 ? b.minY : b.maxY,
                        (i & 4) == 0 ? b.minZ : b.maxZ);
                double[] p = project(corner, cam, camYaw, camPitch, halfH, halfV, w, h);
                if (p == null) {
                    visible = false;
                    break;
                }
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]);
                maxY = Math.max(maxY, p[1]);
            }
            if (!visible || maxX < 0 || maxY < 0 || minX > w || minY > h) {
                continue;
            }
            rect(g, (int) minX, (int) minY, (int) maxX, (int) maxY, s.argb());
        }
    }

    /** A world point in GUI pixels, or null when it is behind the camera. */
    private static double[] project(Vec3 point, Vec3 cam, double camYaw, double camPitch,
                                    double halfH, double halfV, int w, int h) {
        Vec3 d = point.subtract(cam);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        double relYaw = wrap(Math.atan2(-d.x, d.z) - camYaw);
        double relPitch = Math.atan2(-d.y, horiz) - camPitch;
        if (Math.abs(relYaw) > Math.PI / 2.2 || Math.abs(relPitch) > Math.PI / 2.2) {
            return null;   // beside or behind the camera: the tangent below stops meaning anything
        }
        double x = w / 2.0 + Math.tan(relYaw) / Math.tan(halfH) * (w / 2.0);
        double y = h / 2.0 + Math.tan(relPitch) / Math.tan(halfV) * (h / 2.0);
        return new double[]{x, y};
    }

    /** A one-pixel rectangle outline, with a dark line behind it so it reads against anything. */
    private static void rect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1 - 1, y1 - 1, x2 + 1, y1, OUTLINE);
        g.fill(x1 - 1, y2, x2 + 1, y2 + 1, OUTLINE);
        g.fill(x1 - 1, y1, x1, y2, OUTLINE);
        g.fill(x2, y1, x2 + 1, y2, OUTLINE);
        g.fill(x1, y1, x2, y1 + 1, argb);
        g.fill(x1, y2 - 1, x2, y2, argb);
        g.fill(x1, y1, x1 + 1, y2, argb);
        g.fill(x2 - 1, y1, x2, y2, argb);
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
