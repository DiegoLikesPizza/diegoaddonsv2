package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;

/**
 * Draws each SkyBlock item's rarity colour behind it in inventories, the way Skytils and NoammAddons
 * do. See {@link dev.diego.diegoaddons.util.ItemRarity} for how the rarity is read.
 *
 * <p><b>Where</b> it draws is deliberately not "everywhere". A SkyBlock menu is mostly filler - grey
 * panes, close buttons, a hundred cosmetic heads - and colouring all of that turns a menu into
 * confetti. Your own items are never filler, so they are always coloured; a server menu is only
 * coloured where its contents are things you own, which so far means the accessory bag.
 */
public class ItemRarityModule extends Module {
    public static ItemRarityModule INSTANCE;

    /** How the colour is put on the slot. Indices match {@link #OUTLINE}, {@link #FILLED}, {@link #CIRCLE}. */
    public static final int OUTLINE = 0;
    public static final int FILLED = 1;
    public static final int CIRCLE = 2;

    private final CycleSetting display =
            new CycleSetting(this, "display", "Display", FILLED, "Outline", "Filled", "Circle");
    private final BooleanSetting everywhere =
            new BooleanSetting(this, "everywhere", "Your inventory in every menu", true);
    private final BooleanSetting accessoryBag =
            new BooleanSetting(this, "accessoryBag", "Accessory bag", true);

    public ItemRarityModule() {
        super("itemrarity", Category.MISC, "Item Rarity",
                "Show an item's rarity colour behind it in inventories.");
        settings.add(display);
        settings.add(everywhere);
        settings.add(accessoryBag);
        INSTANCE = this;
    }

    public int display() {
        return display.get();
    }

    /** Whether your own 36 slots are coloured in any open menu, not only in the inventory screen. */
    public boolean everywhere() {
        return everywhere.get();
    }

    /** Whether the accessory bag's own slots are coloured - the one server menu that holds your gear. */
    public boolean accessoryBag() {
        return accessoryBag.get();
    }
}
