package dev.diego.diegoaddons.gui;

import com.render.api.RenderColor;

/**
 * Bridges the mod's packed ARGB colours to RenderLib's {@link RenderColor}.
 *
 * <p>{@link Theme} speaks in {@code int}s, because the mod's own drawing ({@link UiRender}) works
 * straight on {@code GuiGraphics.fill}, which takes packed colours. RenderLib takes {@code RenderColor}
 * and keeps its {@code int} overloads only as a migration path, so the conversion happens here, at
 * the one boundary between the two - rather than by calling the deprecated overloads everywhere.
 */
public final class GuiColors {
    private GuiColors() {
    }

    /** A packed ARGB colour as RenderLib wants it. */
    public static RenderColor of(int argb) {
        return RenderColor.argb(argb);
    }

    /** As {@link #of(int)}, with the colour's alpha scaled by {@code alpha}. */
    public static RenderColor of(int argb, float alpha) {
        return RenderColor.argb(Theme.withAlpha(argb, alpha));
    }
}
