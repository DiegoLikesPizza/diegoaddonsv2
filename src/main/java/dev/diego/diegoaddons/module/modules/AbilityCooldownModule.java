package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Shows an item's ability cooldown as a number on its hotbar slot. Logic in
 * {@link dev.diego.diegoaddons.util.AbilityCooldown}.
 */
public class AbilityCooldownModule extends Module {
    public static AbilityCooldownModule INSTANCE;

    private final BooleanSetting decimals =
            new BooleanSetting(this, "decimals", "Tenths under 10s", true);
    private final BooleanSetting dimSlot =
            new BooleanSetting(this, "dimSlot", "Darken the slot while on cooldown", false);
    private final ColorSetting readyColor =
            new ColorSetting(this, "readyColor", "Colour", 0xFFFFFF55);
    private final ColorSetting urgentColor =
            new ColorSetting(this, "urgentColor", "Colour under 1s", 0xFFFF5555);

    public AbilityCooldownModule() {
        super("abilitycooldown", Category.MISC, "Ability Cooldown",
                "Show an item's ability cooldown on its hotbar slot.");
        settings.add(decimals);
        settings.add(dimSlot);
        settings.add(readyColor);
        settings.add(urgentColor);
        INSTANCE = this;
    }

    /** Whether a cooldown under ten seconds is shown to one decimal place. */
    public boolean showDecimals() {
        return decimals.get();
    }

    public boolean dimSlot() {
        return dimSlot.get();
    }

    /** The number's colour, which turns urgent in the last second. */
    public int colorFor(double secondsLeft) {
        return secondsLeft <= 1.0 ? urgentColor.argb() : readyColor.argb();
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
