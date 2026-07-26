package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Draws each SkyBlock item's rarity colour behind it in inventories, the way Skytils and NoammAddons
 * do. See {@link dev.diego.diegoaddons.util.ItemRarity} for how the rarity is read.
 */
public class ItemRarityModule extends Module {
    public static ItemRarityModule INSTANCE;

    public ItemRarityModule() {
        super("itemrarity", Category.MISC, "Item Rarity",
                "Show an item's rarity colour behind it in inventories.");
        INSTANCE = this;
    }
}
