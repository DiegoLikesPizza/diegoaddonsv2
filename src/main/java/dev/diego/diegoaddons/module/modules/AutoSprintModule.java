package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Keeps you sprinting whenever you are walking forward, so you never have to hold the sprint key.
 * It only forces sprint on - it never stops you - and stays out of the way while a menu is open,
 * while you are eating/using an item, or when you are too hungry to sprint anyway.
 */
public class AutoSprintModule extends Module {
    public AutoSprintModule() {
        super("autosprint", Category.MISC, "Auto Sprint", "Always sprint while moving forward.");
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (mc.options.keyUp.isDown()
                && !mc.player.isUsingItem()
                && mc.player.getFoodData().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}
