package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;

/**
 * Draws each SkyBlock item's rarity colour behind it in inventories, the way Skytils and NoammAddons
 * do. See {@link dev.diego.diegoaddons.util.ItemRarity} for how the rarity is read.
 */
public class ItemRarityModule extends Module {
    public static ItemRarityModule INSTANCE;

    /** How the colour is put on the slot. Indices match {@link #OUTLINE}, {@link #FILLED}, {@link #CIRCLE}. */
    public static final int OUTLINE = 0;
    public static final int FILLED = 1;
    public static final int CIRCLE = 2;

    private final CycleSetting display =
            new CycleSetting(this, "display", "Display", FILLED, "Outline", "Filled", "Circle");

    public ItemRarityModule() {
        super("itemrarity", Category.MISC, "Item Rarity",
                "Show an item's rarity colour behind it in inventories.");
        settings.add(display);
        INSTANCE = this;
    }

    public int display() {
        return display.get();
    }
}
