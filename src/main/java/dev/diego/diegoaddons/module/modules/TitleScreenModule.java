package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * The title screen: a one-click Hypixel button on the vanilla one, and optionally configlib's own
 * menu in place of it.
 *
 * <p>The Hypixel button is deliberately not configurable. It was four settings once - whether to
 * replace Realms, what to call the button, where it points, and whether to hide the corner icons -
 * and none of them were ever changed from their defaults. Settings that only ever hold their default
 * are shelf space, so the button is simply there, and the module's own switch is the one control
 * that matters.
 *
 * @see dev.diego.diegoaddons.mixin.TitleScreenMixin
 */
public class TitleScreenModule extends Module {
    public static TitleScreenModule INSTANCE;

    /**
     * Swap Minecraft's title screen for configlib's own.
     *
     * <p>Off by default: it replaces the whole screen rather than adjusting it, which is a bigger
     * change than the Hypixel button makes. It can also be turned on and off from the DiegoAddons
     * button on the title screen itself, which is where you are standing when you want to.
     */
    private final BooleanSetting customMenu =
            new BooleanSetting(this, "custommenu", "Custom main menu", false);

    public TitleScreenModule() {
        super("titlescreen", Category.RENDER, "Title Screen",
                "A one-click Hypixel button, and optionally a menu of our own.");
        settings.add(customMenu);
        INSTANCE = this;
    }

    /** Whether configlib's menu should stand in for the vanilla one. */
    public boolean customMenu() {
        return isEnabled() && customMenu.get();
    }

    /**
     * Sets it from the other end - the switch on the title screen's own options.
     *
     * <p>Turning it on there enables the module too. The alternative is a switch that flips back a
     * tick later because the module behind it is off, which reads as broken rather than as a second
     * thing needing to be enabled somewhere else.
     */
    public void customMenu(boolean value) {
        customMenu.set(value);
        if (value && !isEnabled()) {
            dev.diego.diegoaddons.module.ModuleManager.setEnabled(this, true);
        }
    }
}
