package dev.diego.diegoaddons.gui;

/**
 * A modern, elevation-based colour palette. All colours are packed {@code 0xAARRGGBB} ints.
 *
 * <p>Surfaces go background → surface (cards) → surfaceAlt (insets) → elevated (hover); accents carry
 * a two-stop gradient ({@code accent} → {@code accentTo}) for buttons and highlights.
 */
public record Theme(
        String name,
        int overlay,     // full-screen scrim behind the window
        int surface,     // card / window background
        int surfaceAlt,  // inset rows / secondary surfaces
        int elevated,    // hovered surface
        int accent,      // primary accent (gradient start)
        int accentTo,    // accent gradient end (lighter)
        int accentText,  // text on top of the accent
        int text,        // primary text
        int textMuted,   // secondary text
        int textFaint,   // tertiary text / captions
        int border,      // hairline separators + outlines
        int shadow       // drop-shadow colour
) {
    public static int lighten(int argb, float t) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * factor);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * This theme with a different accent.
     *
     * <p>The gradient end and the text drawn on top are derived rather than asked for. They are not
     * independent choices - one is "the same colour, lighter" and the other is "whatever stays
     * readable on it" - and letting them be picked separately is how an accent ends up with text on
     * it that cannot be read.
     */
    public Theme withAccent(int argb) {
        int opaque = 0xFF000000 | (argb & 0x00FFFFFF);
        return new Theme(name, overlay, surface, surfaceAlt, elevated,
                opaque, lighten(opaque, 0.22f), readableOn(opaque),
                text, textMuted, textFaint, border, shadow);
    }

    /**
     * Near-black or near-white, whichever stays readable on {@code background}.
     *
     * <p>Rec. 601 luma rather than an average of the channels: the eye is far more sensitive to
     * green than to blue, so a plain average calls a saturated blue bright and puts black on it.
     */
    public static int readableOn(int background) {
        int r = (background >> 16) & 0xFF;
        int g = (background >> 8) & 0xFF;
        int b = background & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.55 ? 0xFF0A0A0A : 0xFFF2F6FA;
    }
}
