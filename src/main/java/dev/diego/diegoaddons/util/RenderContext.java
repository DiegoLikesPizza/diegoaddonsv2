package dev.diego.diegoaddons.util;

/**
 * Tiny cross-cutting render flags. {@link #wardrobePreview} is set while the wardrobe overlay draws
 * its own mannequins, so the Armor Hider and Skin Changer mixins can skip them (those features are
 * meant for real players, not the preview figures).
 */
public final class RenderContext {
    public static boolean wardrobePreview = false;

    private RenderContext() {
    }
}
