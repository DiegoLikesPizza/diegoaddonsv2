package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;

/**
 * The active SkyBlock pet on the HUD: its icon, name and level.
 *
 * <p>Its own element rather than a column of the inventory HUD - a pet is a glance and an inventory
 * is a read, and switching one off should not move the other.
 */
public class PetHudModule extends HudModule {
    public static PetHudModule INSTANCE;

    private final BooleanSetting background =
            new BooleanSetting(this, "background", "Background", true);
    private final BooleanSetting hideWithout =
            new BooleanSetting(this, "hideWithout", "Hide when no pet is out", false);

    public PetHudModule() {
        super("pethud", "Pet HUD", "Show your active pet on the HUD.", false);
        settings.add(background);
        settings.add(hideWithout);
        INSTANCE = this;
    }

    public boolean showBackground() {
        return background.get();
    }

    public boolean hideWithoutPet() {
        return hideWithout.get();
    }

    /** The pet's level line, shared with the element. */
    public String levelText(SkyblockHud.PetInfo info) {
        String lvl = info.level() >= 0 ? "Lvl " + info.level() : "Lvl ?";
        return info.xp() == null ? lvl : lvl + "  " + info.xp();
    }

    @Override
    protected String label() {
        return "Pet";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // drawn by its own element
    }

}
