package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.gui.InventoryButtonsScreen;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Shortcut buttons beside container GUIs, each running a command. Press the editor key to add, move
 * and configure them - the key is bound in this feature's own settings panel. See
 * {@code InventoryButtons} for the drawing and {@link InventoryButtonsScreen} for the editor.
 */
public class InventoryButtonsModule extends Module {
    public static InventoryButtonsModule INSTANCE;

    private final BooleanSetting tooltips =
            new BooleanSetting(this, "tooltips", "Show tooltips", true);
    private final BooleanSetting hideInCreative =
            new BooleanSetting(this, "hideInCreative", "Hide in creative", false);
    private final KeybindSetting editorKey =
            new KeybindSetting(this, "editorKey", "Editor key");

    public InventoryButtonsModule() {
        super("inventorybuttons", Category.MISC, "Inventory Buttons",
                "Command shortcut buttons beside container menus.");
        settings.add(tooltips);
        settings.add(hideInCreative);
        settings.add(editorKey);
        INSTANCE = this;
    }

    public boolean showTooltips() {
        return tooltips.get();
    }

    public boolean hideInCreative() {
        return hideInCreative.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (editorKey.consumePress() && !(mc.screen instanceof InventoryButtonsScreen)) {
            mc.setScreen(new InventoryButtonsScreen(mc.screen));
        }
    }
}
