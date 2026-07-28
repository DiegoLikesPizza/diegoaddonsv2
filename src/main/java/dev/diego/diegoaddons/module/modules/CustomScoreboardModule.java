package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.StringSetting;

/**
 * Replaces the vanilla SkyBlock sidebar with a clean themed panel (no red score numbers). The vanilla
 * sidebar is cancelled by {@code ScoreboardSidebarMixin}; the drawing lives in
 * {@link dev.diego.diegoaddons.util.CustomScoreboard}.
 */
public class CustomScoreboardModule extends HudModule {
    public static CustomScoreboardModule INSTANCE;

    private final BooleanSetting background =
            new BooleanSetting(this, "background", "Panel background", true);
    private final BooleanSetting hideServerId =
            new BooleanSetting(this, "hideServerId", "Hide the server id", true);
    private final BooleanSetting hideUrl =
            new BooleanSetting(this, "hideUrl", "Hide the Hypixel URL", true);
    private final BooleanSetting hideDate =
            new BooleanSetting(this, "hideDate", "Hide the date line", false);
    private final BooleanSetting showBank =
            new BooleanSetting(this, "showBank", "Show bank balance", false);
    private final StringSetting title =
            new StringSetting(this, "title", "Custom title", "", null);
    private final StringSetting top =
            new StringSetting(this, "top", "Text at the top", "", null);
    private final StringSetting bottom =
            new StringSetting(this, "bottom", "Text at the bottom", "", null);

    public CustomScoreboardModule() {
        super("customscoreboard", Category.RENDER, "Custom Scoreboard",
                "Re-style the sidebar: themed panel, no red numbers, and only the lines you want.",
                false);
        settings.add(background);
        settings.add(hideServerId);
        settings.add(hideUrl);
        settings.add(hideDate);
        settings.add(showBank);
        settings.add(title);
        settings.add(top);
        settings.add(bottom);
        INSTANCE = this;
    }

    public boolean background() {
        return background.get();
    }

    public boolean hideServerId() {
        return hideServerId.get();
    }

    public boolean hideUrl() {
        return hideUrl.get();
    }

    public boolean hideDate() {
        return hideDate.get();
    }

    public boolean showBank() {
        return showBank.get();
    }

    /** A title of your own, or blank to keep the server's. */
    public String customTitle() {
        return title.get();
    }

    public String topText() {
        return top.get();
    }

    public String bottomText() {
        return bottom.get();
    }

    /** Whether the line at {@code index} of {@code total} is one of yours rather than the server's. */
    public boolean isCustomLine(int index, int total) {
        if (!topText().isBlank() && index == 0) {
            return true;
        }
        return !bottomText().isBlank() && index == total - 1;
    }

    @Override
    protected String label() {
        return "Scoreboard";
    }

    @Override
    protected String value(net.minecraft.client.Minecraft mc) {
        return null;   // drawn by its own element, not as a text chip
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(
            com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.ScoreboardElement(this, root);
    }
}
