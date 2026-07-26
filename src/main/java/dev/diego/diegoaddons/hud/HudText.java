package dev.diego.diegoaddons.hud;

import com.render.api.gui.GuiFont;
import com.render.api.gui.TextComponent;
import dev.diego.diegoaddons.gui.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Text helpers for RenderLib-drawn HUD elements, in the mod's own typeface.
 *
 * <p>RenderLib shapes text itself, and a {@link TextComponent} with no width wraps at whatever the
 * layout hands it - so every label here is given a measured width. The measurement comes from
 * Minecraft's metrics for the same resource font, rescaled: the font's natural size in the HUD is
 * about {@link Fonts#BODY_H} px, so a label drawn at {@code scale} px is that much wider, plus a
 * little slack to stay off the wrapping point.
 */
public final class HudText {
    /** The mod's semibold UI face, the one the old chip renderer used. */
    public static final GuiFont MEDIUM =
            GuiFont.of(Identifier.fromNamespaceAndPath("diegoaddonsv2", "ui_medium"));
    /** The mod's small face, for captions inside an element. */
    public static final GuiFont SMALL =
            GuiFont.of(Identifier.fromNamespaceAndPath("diegoaddonsv2", "ui_small"));

    /** Natural pixel size of the resource fonts above, as Minecraft rasterises them. */
    private static final float NATURAL_PX = 10f;

    private HudText() {
    }

    /** A single-line label wide enough that it never wraps. */
    public static TextComponent label(String s, int color, float scale) {
        return new TextComponent().text(s).color(color).font(MEDIUM).textScalePixels(scale)
                .width(width(s, scale));
    }

    /** How wide {@code s} renders at {@code scale}, in the same units the layout uses. */
    public static float width(String s, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return s.length() * scale * 0.6f;
        }
        float natural = mc.font.width(Fonts.t(s, Fonts.MEDIUM));
        return natural * (scale / NATURAL_PX) * 1.06f + 4f;
    }

    /**
     * The width of {@code s} with every digit swapped for the widest one, so a numeric readout keeps
     * a steady width instead of the chip twitching as the value changes - the same trick the old
     * chip renderer used.
     */
    public static float steadyWidth(String s, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return width(s, scale);
        }
        char widest = '0';
        int max = -1;
        for (char c = '0'; c <= '9'; c++) {
            int cw = mc.font.width(Fonts.t(String.valueOf(c), Fonts.MEDIUM));
            if (cw > max) {
                max = cw;
                widest = c;
            }
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= '0' && c <= '9' ? widest : c);
        }
        return width(sb.toString(), scale);
    }
}
