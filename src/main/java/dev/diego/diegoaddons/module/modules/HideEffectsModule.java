package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Hides the potion-effect panel shown beside the inventory. On servers that keep long buff lists
 * running it covers half the screen and pushes the menu off-centre; hiding it gives the inventory
 * back its space.
 *
 * <p>See {@code EffectsInInventoryMixin} - it answers "can these be seen" with no, which also
 * removes the sideways shift vanilla applies to make room for them.
 */
public class HideEffectsModule extends Module {
    public static HideEffectsModule INSTANCE;

    public HideEffectsModule() {
        super("hideeffects", Category.RENDER, "Hide Effects",
                "Hide the potion effect panel next to the inventory.");
        INSTANCE = this;
    }
}
