package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Overlays teammates' dungeon classes on their heads in the Spirit Leap menu. Rendering lives in
 * {@link dev.diego.diegoaddons.util.LeapOverlay}.
 */
public class LeapOverlayModule extends Module {
    public static LeapOverlayModule INSTANCE;

    public LeapOverlayModule() {
        super("leapoverlay", Category.DUNGEONS, "Leap Overlay",
                "Show each teammate's class on their head in the Spirit Leap menu.");
        INSTANCE = this;
    }
}
