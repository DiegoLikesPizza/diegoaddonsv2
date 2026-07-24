package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.WardrobeSwapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Binds a key to each wardrobe set: pressing it opens the wardrobe and equips that set. Two options
 * shape what happens around the swap - whether to close the menu again afterwards, and whether to
 * refuse a swap that would only take the current set off.
 *
 * <p>The keys are bound in this mod's own settings panel, not Minecraft's controls screen, so every
 * option for the feature sits in one place. See {@link WardrobeSwapper} for the actual swap.
 */
public class WardrobeKeybindsModule extends Module {
    /** How many sets can be bound. SkyBlock shows nine per wardrobe page. */
    public static final int SLOTS = 9;

    public static WardrobeKeybindsModule INSTANCE;

    private final KeybindSetting[] keys = new KeybindSetting[SLOTS];

    private final BooleanSetting closeAfter =
            new BooleanSetting(this, "closeAfter", "Close after swapping", true);
    private final BooleanSetting preventUnequip =
            new BooleanSetting(this, "preventUnequip", "Prevent unequipping", true);

    public WardrobeKeybindsModule() {
        super("wardrobekeys", Category.MISC, "Wardrobe Keybinds",
                "Press a key to open the wardrobe and equip that set.");
        settings.add(closeAfter);
        settings.add(preventUnequip);
        for (int i = 0; i < SLOTS; i++) {
            keys[i] = new KeybindSetting(this, "key" + (i + 1), "Set " + (i + 1) + " key");
            settings.add(keys[i]);
        }
        INSTANCE = this;
    }

    public boolean closeAfter() {
        return closeAfter.get();
    }

    public boolean preventUnequip() {
        return preventUnequip.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        WardrobeSwapper.tick(mc);
        // Only from normal play or from a menu - never while a text field could be taking the key.
        boolean allowed = mc.screen == null || mc.screen instanceof AbstractContainerScreen<?>;
        for (int i = 0; i < SLOTS; i++) {
            if (keys[i].consumePress() && allowed) {
                WardrobeSwapper.request(mc, i + 1);
            }
        }
    }
}
