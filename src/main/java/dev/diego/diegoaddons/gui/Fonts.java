package dev.diego.diegoaddons.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The mod's custom typeface (<b>Poppins</b>, bundled under the SIL Open Font License) exposed as
 * ready-to-use {@link Style}s. Text is drawn by wrapping a string in a component carrying one of
 * these styles, so the game's text pipeline rasterises our TTF instead of the pixelated default font.
 *
 * <p>Two families exist:
 * <ul>
 *   <li><b>HUD family</b> ({@link #BODY}, {@link #MEDIUM}, {@link #TITLE}, {@link #SMALL}) - small
 *       sizes drawn in the normal HUD/editor coordinate space.</li>
 *   <li><b>Menu family</b> ({@code UI_*}) - large sizes drawn <i>supersampled</i>: the ClickGUI and
 *       intro render inside a pose scaled by {@code 1/}{@link UiRender#SS}, working in "units" (one
 *       unit = 1/{@code SS} screen pixel). Because glyphs are rasterised at {@code SS}x their visual
 *       size and shrunk by the pose, text stays razor-sharp instead of snapping to GUI-scale pixels.</li>
 * </ul>
 */
public final class Fonts {
    private static FontDescription face(String path) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath("diegoaddonsv2", path));
    }

    private static Style font(String path) {
        return Style.EMPTY.withFont(face(path));
    }

    /** {@link #MEDIUM}'s typeface alone, for {@link #reface}. */
    public static final FontDescription MEDIUM_FACE = face("ui_medium");

    /**
     * Puts one of the mod's typefaces on a component that already carries its own colours.
     *
     * <p>{@link #t} is for text the mod wrote, where a whole {@link Style} can simply be set. Text
     * that came from the server is different: a scoreboard line or a chat message says a great deal
     * with colour, and setting a Style over it would flatten all of that to one shade. Setting only
     * the font leaves every other field alone, and because an unset field is inherited from the
     * parent, the face reaches the child runs while their colours stay theirs.
     */
    public static Component reface(Component text, FontDescription face) {
        return text.copy().withStyle(s -> s.withFont(face));
    }

    // --- HUD family (normal space) -------------------------------------------------------------
    /** Regular body text (~10px). */
    public static final Style BODY = font("ui");
    /** Semibold, for labels and buttons (~10px). */
    public static final Style MEDIUM = font("ui_medium");
    /** Semibold, for titles/headings (~15px). */
    public static final Style TITLE = font("ui_title");
    /** Small captions (~7px). */
    public static final Style SMALL = font("ui_small");

    // Approximate visual line heights for vertical layout (the default font.lineHeight is a fixed 9).
    public static final int TITLE_H = 16;
    public static final int BODY_H = 12;
    public static final int SMALL_H = 9;

    // Approximate drawn glyph heights, used to vertically centre text inside a box.
    public static final int TITLE_VH = 14;
    public static final int BODY_VH = 9;
    public static final int SMALL_VH = 6;

    // --- Menu family (supersampled space, sizes in units = visual * SS) ------------------------
    /** Big brand/hero title. */
    public static final Style UI_HERO = font("uih_hero");
    /** Section/window titles. */
    public static final Style UI_TITLE = font("uih_title");
    /** Medium-weight labels, buttons, row names. */
    public static final Style UI_LABEL = font("uih_label");
    /** Regular body copy. */
    public static final Style UI_BODY = font("uih_body");
    /** Captions / secondary lines. */
    public static final Style UI_SMALL = font("uih_small");
    /** Uppercase eyebrow / group labels. */
    public static final Style UI_EYEBROW = font("uih_eyebrow");

    // The unit point-size of each menu style (matches the size in its .json provider). Used to
    // vertically centre text: the visible glyph height is roughly GLYPH_RATIO * size.
    public static final int UI_HERO_SZ = 38;
    public static final int UI_TITLE_SZ = 30;
    public static final int UI_LABEL_SZ = 24;
    public static final int UI_BODY_SZ = 22;
    public static final int UI_SMALL_SZ = 18;
    public static final int UI_EYEBROW_SZ = 16;

    /** Fraction of a style's point-size occupied by the visible cap height - one knob to tune. */
    public static final float GLYPH_RATIO = 0.72f;

    /**
     * Minecraft renders text with the <b>baseline fixed at {@code y + 7}</b> (in the text's local
     * coordinate space - which, for the supersampled menus, is units) regardless of the font's point
     * size. Vertical centering has to be computed from that baseline, not from the box top; see
     * {@link #centerTop}. Tune by ±1 if a line sits a hair high or low.
     */
    public static final float BASELINE = 7f;

    private Fonts() {
    }

    public static MutableComponent t(String text, Style style) {
        return Component.literal(text).setStyle(style);
    }

    public static int width(Font font, String text, Style style) {
        return font.width(t(text, style));
    }

    /**
     * Top Y to pass to {@code g.text(...)} so a line of the given unit point-size is vertically
     * centred inside {@code [top, top+h)}.
     *
     * <p>The visible cap (height {@code cap = GLYPH_RATIO*size}) has its bottom on the baseline,
     * which sits at {@code returnedY + BASELINE}. So the cap centre is {@code returnedY + BASELINE -
     * cap/2}; setting that equal to {@code top + h/2} gives the offset below.
     */
    public static int centerTop(int top, int h, int sizeUnits) {
        float cap = GLYPH_RATIO * sizeUnits;
        return top + Math.round(h / 2f - BASELINE + cap / 2f);
    }
}
