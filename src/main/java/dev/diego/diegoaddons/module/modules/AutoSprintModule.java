package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import net.minecraft.client.Minecraft;

/**
 * Keeps you sprinting whenever you are walking forward, so you never have to hold the sprint key.
 * It only forces sprint on - it never stops you - and stays out of the way while a menu is open,
 * while you are eating/using an item, or when you are too hungry to sprint anyway.
 */
public class AutoSprintModule extends Module {
    private final BooleanSetting whileUsing =
            new BooleanSetting(this, "whileUsing", "Sprint while using an item", false);
    private final BooleanSetting whileSneaking =
            new BooleanSetting(this, "whileSneaking", "Sprint while sneaking", false);
    private final BooleanSetting anyDirection =
            new BooleanSetting(this, "anyDirection", "Any direction, not just forward", false);
    private final NumberSetting minHunger =
            new NumberSetting(this, "minHunger", "Minimum hunger", 6, 0, 20, 1);

    public AutoSprintModule() {
        super("autosprint", Category.MISC, "Auto Sprint", "Always sprint while moving forward.");
        settings.add(whileUsing);
        settings.add(whileSneaking);
        settings.add(anyDirection);
        settings.add(minHunger);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (!moving(mc)) {
            return;
        }
        // Vanilla stops sprinting below the hunger threshold anyway; forcing it back on every tick
        // under that would fight the game rather than override it.
        if (mc.player.getFoodData().getFoodLevel() <= minHunger.get()) {
            return;
        }
        if (mc.player.isUsingItem() && !whileUsing.get()) {
            return;
        }
        if (mc.player.isShiftKeyDown() && !whileSneaking.get()) {
            return;
        }
        mc.player.setSprinting(true);
    }

    /** Whether the player is asking to move at all - forward only, or any of the four. */
    private boolean moving(Minecraft mc) {
        if (!anyDirection.get()) {
            return mc.options.keyUp.isDown();
        }
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }
}
