package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Keeps player name plates visible even while the player is invisible or sneaking - handy in
 * SkyBlock, where many NPCs are drawn as players and hide their tag that way. See
 * {@link dev.diego.diegoaddons.mixin.ForceNametagMixin}.
 */
public class ForceNametagModule extends Module {
    public static ForceNametagModule INSTANCE;

    private final BooleanSetting showOwn =
            new BooleanSetting(this, "showOwn", "Show your own tag in F5", false);

    public ForceNametagModule() {
        super("forcenametag", Category.RENDER, "Force Nametag",
                "Show player tags even when invisible or sneaking.");
        settings.add(showOwn);
        INSTANCE = this;
    }

    /** Whether your own name plate is drawn while the camera is in third person. */
    public boolean showOwn() {
        return showOwn.get();
    }
}
