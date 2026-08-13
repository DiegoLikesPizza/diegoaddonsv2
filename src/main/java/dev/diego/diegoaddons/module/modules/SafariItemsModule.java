package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Says what a Safari item is for, on the item.
 *
 * <p>Several critters are gated behind using an object rather than catching a mob, and the object
 * never says so - a Shining Coin describes itself as a shiny coin, not as the only way a Gimmiegold
 * ever appears. See {@link dev.diego.diegoaddons.util.SafariItems} for the list.
 *
 * <p>Not gated to the island, deliberately: the moment you want to know what a quest item does is
 * usually while sorting your inventory somewhere else, wondering whether it is safe to sell.
 */
public class SafariItemsModule extends Module {
    public static SafariItemsModule INSTANCE;

    /**
     * Off by default. Bamboo and lily pads are ordinary items you hold for ordinary reasons, and a
     * Safari note on every stack of bamboo you ever pick up is noise rather than help.
     */
    private final BooleanSetting includeFood =
            new BooleanSetting(this, "includeFood", "Also note plain foods (bamboo, lily pads)", false);

    public SafariItemsModule() {
        super("safariitems", Category.SAFARI, "Safari Item Tooltips",
                "Adds what a Safari quest item is actually for to its tooltip.");
        settings.add(includeFood);
        INSTANCE = this;
    }

    public boolean includeFood() {
        return includeFood.get();
    }
}
