package dev.diego.diegoaddons.gui;

import com.render.api.gui.GuiFont;
import com.render.api.gui.TextComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Text for the RenderLib menu, in the mod's own typeface.
 *
 * <p>Two RenderLib facts drive everything here. A {@link TextComponent} with no width wraps at
 * whatever the layout hands it, so every label gets a measured width; and RenderLib shapes these
 * faces about a quarter wider than {@code Font.width} reports, so the measurement carries that
 * allowance. Heights are always left to the content - forcing one makes glyphs spill below the box -
 * which is why a row that needs a fixed height puts the text in a box instead.
 */
public final class GuiText {
    public static final GuiFont BODY = font("ui");
    public static final GuiFont MEDIUM = font("ui_medium");
    public static final GuiFont TITLE = font("ui_title");

    /** Natural pixel size of the bundled faces, as Minecraft rasterises them. */
    private static final float NATURAL_PX = 10f;
    /**
     * How much wider RenderLib shapes these faces than {@code Font.width} reports.
     *
     * <p>Starts as an estimate and is replaced by a real measurement the moment one is available:
     * {@link #calibrate} reads a laid-out label's own intrinsic width and works the ratio out from
     * that, so the number describes what RenderLib actually did rather than what I once measured off
     * a screenshot.
     */
    private static float slack = 1.25f;
    private static boolean calibrated;

    private GuiText() {
    }

    private static GuiFont font(String path) {
        return GuiFont.of(Identifier.fromNamespaceAndPath("diegoaddonsv2", path));
    }

    /** A label that never wraps. */
    public static TextComponent label(String s, int color, float scale) {
        return label(s, color, scale, MEDIUM);
    }

    public static TextComponent label(String s, int color, float scale, GuiFont face) {
        return new TextComponent().text(s).color(color).font(face).textScalePixels(scale)
                .width(width(s, scale));
    }

    /**
     * A symbol drawn in Minecraft's own font. The bundled Poppins faces carry no glyph for the
     * category icons or the close cross, so through {@link #label} they come out as tofu boxes.
     */
    public static TextComponent glyph(String s, int color, float scale) {
        return new TextComponent().text(s).color(color).font(GuiFont.minecraftDefault())
                .textScalePixels(scale)
                .width(Minecraft.getInstance().font.width(s) * (scale / 8f) + 4f);
    }

    /** A label that wraps inside {@code width} - descriptions and other running text. */
    public static TextComponent wrapped(String s, int color, float scale, float width) {
        return new TextComponent().text(s).color(color).font(BODY).textScalePixels(scale)
                .width(width);
    }

    /** How wide {@code s} renders at {@code scale}, in layout units. */
    public static float width(String s, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return s.length() * scale * 0.6f;
        }
        return mc.font.width(styled(s, Fonts.MEDIUM)) * (scale / NATURAL_PX) * slack + 6f;
    }

    /**
     * Learns the real shaping ratio from a label that has been laid out. Call with a label whose
     * text and scale are known; it takes the first sane reading and then leaves itself alone.
     */
    public static void calibrate(TextComponent label, String text, float scale) {
        if (calibrated || label == null || text.isEmpty()) {
            return;
        }
        float actual = label.intrinsicSize().maxWidth();
        Minecraft mc = Minecraft.getInstance();
        if (actual <= 0f || mc == null || mc.font == null) {
            return;   // not laid out yet
        }
        float base = mc.font.width(styled(text, Fonts.MEDIUM)) * (scale / NATURAL_PX);
        if (base <= 0f) {
            return;
        }
        float measured = actual / base;
        if (measured > 0.8f && measured < 2.0f) {   // ignore a nonsense reading
            slack = measured;
            calibrated = true;
        }
    }

    private static net.minecraft.network.chat.Component styled(String s, Style style) {
        return net.minecraft.network.chat.Component.literal(s).setStyle(style);
    }
}
