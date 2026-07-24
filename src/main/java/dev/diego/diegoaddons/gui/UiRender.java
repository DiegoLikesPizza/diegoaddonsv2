package dev.diego.diegoaddons.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;

/**
 * Rounded-rectangle drawing built purely on {@link GuiGraphicsExtractor#fill(int, int, int, int, int)},
 * so it does not depend on any texture or shader.
 *
 * <p>A rounded rect is drawn as three straight bands (a centre column plus left/right side columns)
 * plus four quarter-circle corners. Each corner is filled one scan-line at a time; the horizontal
 * extent of the filled span for a given row comes from the circle equation. When {@code smooth} is
 * on, the single boundary pixel of each row is drawn again with fractional alpha, giving a soft
 * anti-aliased edge instead of a hard staircase.
 */
public final class UiRender {
    private UiRender() {
    }

    /**
     * Supersample factor for menu rendering. Menus push a pose scaled by {@code 1/SS} and lay
     * everything out in "units" (1 unit = 1/SS screen pixel), so geometry and text resolve at SS x
     * the GUI-scale resolution - crisp, "actually high-res" edges instead of chunky pixels.
     */
    public static final int SS = 2;

    /** Enter supersampled ("hi-res") drawing: all following draws are in units. Pair with {@link #endHiRes}. */
    public static void beginHiRes(GuiGraphicsExtractor g) {
        g.pose().pushMatrix();
        g.pose().scale(1f / SS);
    }

    public static void endHiRes(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    /** Convert a screen-space coordinate to hi-res units. */
    public static int u(double screen) {
        return (int) Math.round(screen * SS);
    }

    public static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
                                   int color, boolean smooth) {
        roundedImpl(g, x, y, w, h, radius, color, color, smooth);
    }

    /**
     * Shared solid/gradient rounded fill. Straight bands are solid (or a real vertical gradient);
     * the four corners are drawn per row with a fully-covered span plus a two-pixel anti-aliased band
     * whose coverage comes from the true distance to the corner centre - giving smooth, "actually
     * round" edges rather than a horizontal-only fringe.
     */
    private static void roundedImpl(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
                                    int top, int bottom, boolean smooth) {
        int x2 = x + w, y2 = y + h;
        int r = Math.min(radius, Math.min(w, h) / 2);
        boolean grad = top != bottom;
        if (r <= 0) {
            if (grad) {
                g.fillGradient(x, y, x2, y2, top, bottom);
            } else {
                g.fill(x, y, x2, y2, top);
            }
            return;
        }
        // Straight bands.
        if (grad) {
            g.fillGradient(x + r, y, x2 - r, y2, top, bottom);
            g.fillGradient(x, y + r, x + r, y2 - r, rowColor(top, bottom, y, h, y + r), rowColor(top, bottom, y, h, y2 - r));
            g.fillGradient(x2 - r, y + r, x2, y2 - r, rowColor(top, bottom, y, h, y + r), rowColor(top, bottom, y, h, y2 - r));
        } else {
            g.fill(x + r, y, x2 - r, y2, top);
            g.fill(x, y + r, x + r, y2 - r, top);
            g.fill(x2 - r, y + r, x2, y2 - r, top);
        }
        // Corners: (boxX, boxY) = top-left of the r×r box, (cx, cy) = arc centre.
        cornerColumn(g, x, y, r, x + r, y + r, true, top, bottom, grad, y, h, smooth);          // top-left
        cornerColumn(g, x2 - r, y, r, x2 - r, y + r, false, top, bottom, grad, y, h, smooth);   // top-right
        cornerColumn(g, x, y2 - r, r, x + r, y2 - r, true, top, bottom, grad, y, h, smooth);    // bottom-left
        cornerColumn(g, x2 - r, y2 - r, r, x2 - r, y2 - r, false, top, bottom, grad, y, h, smooth); // bottom-right
    }

    private static int rowColor(int top, int bottom, int rectY, int rectH, int py) {
        return lerp(top, bottom, (py + 0.5f - rectY) / rectH);
    }

    /**
     * Draws one quarter-disc. The r×r box has its top-left at (boxX, boxY); (cx, cy) is the arc
     * centre; {@code left} says the box is on the left of the centre.
     */
    private static void cornerColumn(GuiGraphicsExtractor g, int boxX, int boxY, int r,
                                     int cx, int cy, boolean left, int top, int bottom, boolean grad,
                                     int rectY, int rectH, boolean smooth) {
        for (int j = 0; j < r; j++) {
            int py = boxY + j;
            double dyc = (py + 0.5) - cy;
            double ext = Math.sqrt(Math.max(0, (double) r * r - dyc * dyc));
            int col = grad ? rowColor(top, bottom, rectY, rectH, py) : top;
            if (left) {
                int solidStart = (int) Math.round(cx - ext);
                if (solidStart < boxX) {
                    solidStart = boxX;
                }
                g.fill(solidStart, py, cx, py + 1, col);
                if (smooth) {
                    for (int px = Math.max(boxX - 1, solidStart - 2); px < solidStart; px++) {
                        aaPixel(g, px, py, cx, cy, r, col);
                    }
                }
            } else {
                int solidEnd = (int) Math.round(cx + ext);
                if (solidEnd > boxX + r) {
                    solidEnd = boxX + r;
                }
                g.fill(cx, py, solidEnd, py + 1, col);
                if (smooth) {
                    for (int px = solidEnd; px < Math.min(boxX + r, solidEnd + 2); px++) {
                        aaPixel(g, px, py, cx, cy, r, col);
                    }
                }
            }
        }
    }

    private static void aaPixel(GuiGraphicsExtractor g, int px, int py, int cx, int cy, int r, int color) {
        double dxc = (px + 0.5) - cx;
        double dyc = (py + 0.5) - cy;
        double d = Math.sqrt(dxc * dxc + dyc * dyc);
        float cov = (float) Math.max(0.0, Math.min(1.0, r + 0.5 - d));
        if (cov <= 0.02f) {
            return;
        }
        g.fill(px, py, px + 1, py + 1, cov >= 1f ? color : Theme.withAlpha(color, cov));
    }

    /** Fill with only the top two corners rounded (square bottom) - used for header strips. */
    public static void fillRoundedTop(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
                                      int color, boolean smooth) {
        int x2 = x + w;
        int r = Math.min(radius, Math.min(w, h));
        if (r <= 0) {
            g.fill(x, y, x2, y + h, color);
            return;
        }
        g.fill(x + r, y, x2 - r, y + r, color);   // top centre band
        g.fill(x, y + r, x2, y + h, color);       // everything below the corner rows (full width)
        for (int i = 0; i < r; i++) {
            double dyc = r - i - 0.5;
            double extent = Math.sqrt(r * r - dyc * dyc);
            int solid = (int) Math.floor(extent);
            int topY = y + i;
            g.fill(x + r - solid, topY, x + r, topY + 1, color);
            g.fill(x2 - r, topY, x2 - r + solid, topY + 1, color);
            if (smooth) {
                float frac = (float) (extent - solid);
                if (frac > 0.02f) {
                    int edge = Theme.withAlpha(color, frac);
                    g.fill(x + r - solid - 1, topY, x + r - solid, topY + 1, edge);
                    g.fill(x2 - r + solid, topY, x2 - r + solid + 1, topY + 1, edge);
                }
            }
        }
    }

    /** A 1px rounded outline, drawn as a thin rounded frame (outer rounded fill minus inner). */
    public static void strokeRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
                                     int color, boolean smooth) {
        // Draw the four straight edges, leaving the rounded corners to the corner scan below.
        int r = Math.min(radius, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + 1, color);               // top
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);       // bottom
        g.fill(x, y + r, x + 1, y + h - r, color);               // left
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);       // right

        int x2 = x + w, y2 = y + h;
        int prevSolid = -1;
        for (int i = 0; i < r; i++) {
            double dyc = r - i - 0.5;
            double extent = Math.sqrt(r * r - dyc * dyc);
            int solid = (int) Math.floor(extent);
            int topY = y + i;
            int botY = y2 - 1 - i;
            // Only draw the outermost ring pixel(s) for each row so it reads as a 1px stroke.
            int step = (prevSolid < 0) ? 1 : Math.max(1, solid - prevSolid);
            for (int k = 0; k < step; k++) {
                int px = solid - k;
                g.fill(x + r - px - 1, topY, x + r - px, topY + 1, color);
                g.fill(x2 - r + px, topY, x2 - r + px + 1, topY + 1, color);
                g.fill(x + r - px - 1, botY, x + r - px, botY + 1, color);
                g.fill(x2 - r + px, botY, x2 - r + px + 1, botY + 1, color);
            }
            if (smooth) {
                float frac = (float) (extent - solid);
                if (frac > 0.02f) {
                    int edge = Theme.withAlpha(color, frac);
                    g.fill(x + r - solid - 1, topY, x + r - solid, topY + 1, edge);
                    g.fill(x2 - r + solid, topY, x2 - r + solid + 1, topY + 1, edge);
                    g.fill(x + r - solid - 1, botY, x + r - solid, botY + 1, edge);
                    g.fill(x2 - r + solid, botY, x2 - r + solid + 1, botY + 1, edge);
                }
            }
            prevSolid = solid;
        }
    }

    /** Convenience: filled rounded panel with a 1px border on top. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
                             int fill, int border, boolean smooth) {
        fillRounded(g, x, y, w, h, radius, fill, smooth);
        strokeRounded(g, x, y, w, h, radius, border, smooth);
    }

    /** A rounded outline of the given thickness (drawn as nested 1-unit strokes). */
    public static void strokeRoundedThick(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                          int radius, int color, int thickness, boolean smooth) {
        for (int i = 0; i < thickness; i++) {
            strokeRounded(g, x + i, y + i, w - i * 2, h - i * 2, Math.max(0, radius - i), color, smooth);
        }
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------------------------------------
    // Modern extras: gradients, shadows, circles, spinner, and custom-font text.
    // ---------------------------------------------------------------------------------------------

    private static int lerp(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int ar = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int ag = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int ab = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Rounded rectangle filled with a smooth vertical gradient from {@code top} to {@code bottom}. */
    public static void fillRoundedGradient(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                           int radius, int top, int bottom, boolean smooth) {
        roundedImpl(g, x, y, w, h, radius, top, bottom, smooth);
    }

    /** Soft drop shadow behind a rounded rect: concentric translucent rounded rects, fading out. */
    public static void dropShadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
                                  int shadowColor, int spread, int dy) {
        for (int i = spread; i >= 1; i--) {
            float a = 0.16f * (1f - (float) (i - 1) / spread);
            int c = Theme.withAlpha(shadowColor, a);
            fillRounded(g, x - i, y - i + dy, w + i * 2, h + i * 2, radius + i, c, true);
        }
    }

    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color, boolean smooth) {
        fillRounded(g, cx - r, cy - r, r * 2, r * 2, r, color, smooth);
    }

    /**
     * A soft, centred colour glow behind an element (no vertical offset) - concentric translucent
     * rounded rects fading outward. Good for the accent "bloom" under a primary button or logo.
     */
    public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
                            int color, int spread, float strength) {
        for (int i = spread; i >= 1; i--) {
            float a = strength * (1f - (float) (i - 1) / spread);
            fillRounded(g, x - i, y - i, w + i * 2, h + i * 2, radius + i, Theme.withAlpha(color, a), true);
        }
    }

    /**
     * A modern round loading spinner: {@code count} dots evenly spaced on a circle, with a bright
     * "head" that rotates over time and a trailing fade. {@code timeMs} drives the rotation.
     */
    public static void spinner(GuiGraphicsExtractor g, int cx, int cy, int radius, int dotR,
                               int accent, int track, long timeMs, boolean smooth) {
        int count = 12;
        double head = (timeMs / 70.0) % count;
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0 * i / count) - Math.PI / 2.0;
            int px = cx + (int) Math.round(Math.cos(ang) * radius);
            int py = cy + (int) Math.round(Math.sin(ang) * radius);
            double behind = (head - i + count) % count;      // 0 = at head, grows going backwards
            float t = 1f - (float) (behind / count);          // 1 at head -> 0 trailing
            t = t * t;
            int c = lerp(track, accent, t);
            circle(g, px, py, dotR, c, smooth);
        }
    }

    // --- custom-font text (no drop shadow, for a flat modern look) ---

    public static void text(GuiGraphicsExtractor g, Font font, String s, Style style, int x, int y, int color) {
        g.text(font, Fonts.t(s, style), x, y, color, false);
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, String s, Style style, int cx, int y, int color) {
        int w = font.width(Fonts.t(s, style));
        g.text(font, Fonts.t(s, style), cx - w / 2, y, color, false);
    }

    public static void textRight(GuiGraphicsExtractor g, Font font, String s, Style style, int rx, int y, int color) {
        int w = font.width(Fonts.t(s, style));
        g.text(font, Fonts.t(s, style), rx - w, y, color, false);
    }

    /** Left-aligned text, vertically centred inside {@code [boxTop, boxTop+boxH)} for a menu style. */
    public static void textVC(GuiGraphicsExtractor g, Font font, String s, Style style, int sizeUnits,
                              int x, int boxTop, int boxH, int color) {
        g.text(font, Fonts.t(s, style), x, Fonts.centerTop(boxTop, boxH, sizeUnits), color, false);
    }

    /** Centred text, vertically centred inside {@code [boxTop, boxTop+boxH)} for a menu style. */
    public static void textCenteredVC(GuiGraphicsExtractor g, Font font, String s, Style style, int sizeUnits,
                                      int cx, int boxTop, int boxH, int color) {
        int w = font.width(Fonts.t(s, style));
        g.text(font, Fonts.t(s, style), cx - w / 2, Fonts.centerTop(boxTop, boxH, sizeUnits), color, false);
    }
}
