package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Marks up the SkyBlock Equipment Sets menu: numbered sets, the worn set highlighted, and a hover
 * card naming each set's four pieces. The drawing runs from a screen render hook in
 * {@code ModuleManager}; see {@code EquipmentOverlay} for the actual rendering.
 */
public class EquipmentOverlayModule extends Module {
    /** Set on construction so the screen render hook can read the live enabled state statically. */
    public static EquipmentOverlayModule INSTANCE;

    public EquipmentOverlayModule() {
        super("equipmentoverlay", Category.RENDER, "Equipment Overlay",
                "Number and label the sets in the SkyBlock equipment menu.");
        INSTANCE = this;
    }
}
