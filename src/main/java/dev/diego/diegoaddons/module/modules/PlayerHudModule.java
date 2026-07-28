package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
import net.minecraft.client.Minecraft;

/**
 * Your character on the HUD, on its own rather than as a column of the inventory.
 *
 * <p>Split out because the two are looked at differently: an inventory is read, a model is glanced
 * at. Tying them together meant the model could only be where the inventory was, and only as tall as
 * the grid beside it.
 */
public class PlayerHudModule extends HudModule {
    public static PlayerHudModule INSTANCE;

    private final NumberSetting height =
            new NumberSetting(this, "height", "Height", 70, 40, 160, 5);
    private final BooleanSetting background =
            new BooleanSetting(this, "background", "Background", true);

    public PlayerHudModule() {
        super("playerhud", "Player HUD", "Show your character on the HUD.", false);
        settings.add(height);
        settings.add(background);
        INSTANCE = this;
    }

    public float height() {
        return (float) height.get();
    }

    public boolean showBackground() {
        return background.get();
    }

    @Override
    protected String label() {
        return "Player";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // drawn by its own element
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(
            com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.PlayerElement(this, root);
    }
}
