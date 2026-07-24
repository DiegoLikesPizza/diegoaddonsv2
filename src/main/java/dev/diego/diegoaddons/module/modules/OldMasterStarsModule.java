package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Restores the old Hypixel SkyBlock master-star look. Instead of five gold stars plus a red master
 * glyph (➊–➎), the five stars themselves turn red from the left as master levels are gained - so a
 * fully upgraded item shows five red stars again.
 *
 * <p>Stateless: the actual rewrite runs from an {@code ItemTooltipCallback} in {@code ModuleManager},
 * which reads {@link #INSTANCE} to see whether this module is enabled. See {@code OldMasterStars}.
 */
public class OldMasterStarsModule extends Module {
    /** Set on construction so the tooltip hook can read the live enabled state statically. */
    public static OldMasterStarsModule INSTANCE;

    public OldMasterStarsModule() {
        super("oldmasterstars", Category.MISC, "Old Master Stars",
                "Show master stars the old way: the stars turn red instead of adding a red number.");
        INSTANCE = this;
    }
}
