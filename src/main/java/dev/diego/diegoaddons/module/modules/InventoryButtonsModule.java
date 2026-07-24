package dev.diego.diegoaddons.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.gui.InventoryButtonsScreen;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Shortcut buttons beside container GUIs, each running a command. Press the editor key to add, move
 * and configure them. See {@code InventoryButtons} for the drawing and {@link InventoryButtonsScreen}
 * for the editor.
 */
public class InventoryButtonsModule extends Module {
    public static InventoryButtonsModule INSTANCE;

    private static KeyMapping editorKey;

    private final BooleanSetting tooltips =
            new BooleanSetting(this, "tooltips", "Show tooltips", true);
    private final BooleanSetting hideInCreative =
            new BooleanSetting(this, "hideInCreative", "Hide in creative", false);

    private boolean hintShown;

    public InventoryButtonsModule() {
        super("inventorybuttons", Category.MISC, "Inventory Buttons",
                "Command shortcut buttons beside container menus.");
        settings.add(tooltips);
        settings.add(hideInCreative);
        INSTANCE = this;
    }

    /** Registers the editor key. Called once at startup, before the controls screen is built. */
    public static void registerKeys() {
        editorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.diegoaddonsv2.inventory_buttons",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),   // unbound until the user picks a key
                KeyMapping.Category.INVENTORY));
    }

    public boolean showTooltips() {
        return tooltips.get();
    }

    public boolean hideInCreative() {
        return hideInCreative.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        // The editor key is unbound by default, so say once where to find it.
        if (!hintShown) {
            hintShown = true;
            if (mc.gui != null) {
                mc.gui.getChat().addClientSystemMessage(Component.literal(
                        "§b[DiegoAddons] §fInventory Buttons: bind §eControls → Inventory → "
                                + "\"Inventory Buttons Editor\"§f to add and move your buttons."));
            }
        }
        if (editorKey == null) {
            return;
        }
        boolean pressed = false;
        while (editorKey.consumeClick()) {
            pressed = true;
        }
        if (pressed && !(mc.screen instanceof InventoryButtonsScreen)) {
            mc.setScreen(new InventoryButtonsScreen(mc.screen));
        }
    }
}
