package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Shows an item's ability cooldown as a number on its hotbar slot. Logic in
 * {@link dev.diego.diegoaddons.util.AbilityCooldown}.
 */
public class AbilityCooldownModule extends Module {
    public static AbilityCooldownModule INSTANCE;

    public AbilityCooldownModule() {
        super("abilitycooldown", Category.MISC, "Ability Cooldown",
                "Show an item's ability cooldown on its hotbar slot.");
        INSTANCE = this;
    }

    @Override
    protected void onDisable() {
        dev.diego.diegoaddons.util.AbilityCooldown.reset();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        dev.diego.diegoaddons.util.AbilityCooldown.tick(mc);
    }
}
