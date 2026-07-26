package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Replaces the vanilla SkyBlock sidebar with a clean themed panel (no red score numbers). The vanilla
 * sidebar is cancelled by {@code ScoreboardSidebarMixin}; the drawing lives in
 * {@link dev.diego.diegoaddons.util.CustomScoreboard}.
 */
public class CustomScoreboardModule extends Module {
    public static CustomScoreboardModule INSTANCE;

    private final BooleanSetting background =
            new BooleanSetting(this, "background", "Panel background", true);

    public CustomScoreboardModule() {
        super("customscoreboard", Category.RENDER, "Custom Scoreboard",
                "Re-style the sidebar: themed panel, no red numbers.");
        settings.add(background);
        INSTANCE = this;
    }

    public boolean background() {
        return background.get();
    }
}
