package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Overlays a small player model wearing each stored armour set on top of the SkyBlock wardrobe menu,
 * so every set is recognisable at a glance. The drawing runs from a screen render hook in
 * {@code ModuleManager}; see {@code WardrobeOverlay} for the actual rendering.
 */
public class WardrobeOverlayModule extends Module {
    /** Set on construction so the screen render hook can read the live enabled state statically. */
    public static WardrobeOverlayModule INSTANCE;

    public WardrobeOverlayModule() {
        super("wardrobeoverlay", Category.RENDER, "Wardrobe Overlay",
                "Show a player model wearing each set in the SkyBlock wardrobe.");
        INSTANCE = this;
    }
}
