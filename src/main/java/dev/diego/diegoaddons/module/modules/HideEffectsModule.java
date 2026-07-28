package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Hides the potion-effect panel shown beside the inventory. On servers that keep long buff lists
 * running it covers half the screen and pushes the menu off-centre; hiding it gives the inventory
 * back its space.
 *
 * <p>See {@code EffectsInInventoryMixin} - it answers "can these be seen" with no, which also
 * removes the sideways shift vanilla applies to make room for them - and {@code HudEffectsMixin}
 * for the row of icons in the top-right corner, which the HUD draws separately.
 */
public class HideEffectsModule extends Module {
    public static HideEffectsModule INSTANCE;

    private final BooleanSetting hudIcons =
            new BooleanSetting(this, "hudIcons", "Also hide the corner icons", true);

    public HideEffectsModule() {
        super("hideeffects", Category.RENDER, "Hide Effects",
                "Hide the potion effect panel, and the icons in the corner of the screen.");
        settings.add(hudIcons);
        INSTANCE = this;
    }

    /** Whether the top-right row of effect icons goes as well as the inventory panel. */
    public boolean hideHudIcons() {
        return hudIcons.get();
    }
}
