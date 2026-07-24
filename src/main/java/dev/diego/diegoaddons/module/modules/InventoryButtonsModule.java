package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.gui.InventoryButtonsScreen;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Shortcut buttons beside container GUIs, each running a command. The editor opens from this
 * feature's own settings rather than a key binding, since it is something you set up once. See
 * {@code InventoryButtons} for the drawing and {@link InventoryButtonsScreen} for the editor.
 */
public class InventoryButtonsModule extends Module {
    public static InventoryButtonsModule INSTANCE;

    private final BooleanSetting tooltips =
            new BooleanSetting(this, "tooltips", "Show tooltips", true);
    private final BooleanSetting hideInCreative =
            new BooleanSetting(this, "hideInCreative", "Hide in creative", false);
    private final ActionSetting editor =
            new ActionSetting(this, "editor", "Button editor", "Open", InventoryButtonsModule::openEditor);

    public InventoryButtonsModule() {
        super("inventorybuttons", Category.MISC, "Inventory Buttons",
                "Command shortcut buttons beside container menus.");
        settings.add(tooltips);
        settings.add(hideInCreative);
        settings.add(editor);
        INSTANCE = this;
    }

    private static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        Screen previous = mc.screen;
        mc.setScreen(new InventoryButtonsScreen(previous));
    }

    public boolean showTooltips() {
        return tooltips.get();
    }

    public boolean hideInCreative() {
        return hideInCreative.get();
    }
}
