package dev.diego.diegoaddons.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.WardrobeSwapper;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Binds a key to each wardrobe set: pressing it opens the wardrobe and equips that set. Two options
 * shape what happens around the swap - whether to close the menu again afterwards, and whether to
 * refuse a swap that would only take the current set off.
 *
 * <p>The keys are registered once at startup (unbound by default, so nothing is stolen from the
 * user's existing binds) and are only acted on while this module is enabled. See
 * {@link WardrobeSwapper} for the actual swap.
 */
public class WardrobeKeybindsModule extends Module {
    /** How many sets can be bound. SkyBlock shows nine per wardrobe page. */
    public static final int SLOTS = 9;

    public static WardrobeKeybindsModule INSTANCE;

    private static final KeyMapping[] KEYS = new KeyMapping[SLOTS];

    private final BooleanSetting closeAfter =
            new BooleanSetting(this, "closeAfter", "Close after swapping", true);
    private final BooleanSetting preventUnequip =
            new BooleanSetting(this, "preventUnequip", "Prevent unequipping", true);

    public WardrobeKeybindsModule() {
        super("wardrobekeys", Category.MISC, "Wardrobe Keybinds",
                "Press a key to open the wardrobe and equip that set.");
        settings.add(closeAfter);
        settings.add(preventUnequip);
        INSTANCE = this;
    }

    /**
     * Registers the nine key mappings. Called once from the client entrypoint - key mappings must
     * exist before the options screen is built, so this cannot wait until the module is enabled.
     */
    public static void registerKeys() {
        for (int i = 0; i < SLOTS; i++) {
            KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.diegoaddonsv2.wardrobe_" + (i + 1),
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),   // unbound until the user picks a key
                    KeyMapping.Category.INVENTORY));
        }
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
        for (int i = 0; i < SLOTS; i++) {
            KeyMapping key = KEYS[i];
            if (key == null) {
                continue;
            }
            boolean pressed = false;
            while (key.consumeClick()) {
                pressed = true;
            }
            // Only from normal play or from the wardrobe itself - never while typing in a text box.
            if (pressed && (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
                WardrobeSwapper.request(mc, i + 1);
            }
        }
    }
}
