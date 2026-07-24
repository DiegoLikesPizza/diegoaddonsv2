package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.CustomEsp;

/**
 * Boxes any mob whose name plate contains a term you added, so you can mark whatever matters for
 * what you are doing without a setting per mob.
 *
 * <p>Terms are managed with {@code /da esp add|list|remove}; matching is a loose contains, so a
 * partial name is enough.
 */
public class CustomEspModule extends Module {
    public static CustomEspModule INSTANCE;

    public CustomEspModule() {
        super("customesp", Category.RENDER, "Custom ESP",
                "Box mobs whose name contains a term you chose (/da esp).");
        INSTANCE = this;
    }

    public int color() {
        return 0xFF00FFFF;
    }

    /** How many terms are configured, for the command feedback. */
    public int termCount() {
        return CustomEsp.all().size();
    }
}
