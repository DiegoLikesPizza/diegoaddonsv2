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
}
