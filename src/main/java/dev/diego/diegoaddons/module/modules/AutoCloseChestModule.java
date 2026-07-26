package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * After a dungeon secret chest has been emptied, closes its screen so you can keep moving without a
 * manual close. Strictly gated to dungeons and to a plain "Chest" screen, and it only fires once the
 * chest's own slots are all empty - so it never closes a chest you are still looting.
 *
 * <p>Off by default: it is an input side-effect and should be an opt-in convenience.
 */
public class AutoCloseChestModule extends Module {
    public static AutoCloseChestModule INSTANCE;

    public AutoCloseChestModule() {
        super("autoclosechest", Category.DUNGEONS, "Auto Close Chests",
                "Close a dungeon secret chest once it has been looted.");
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!DungeonState.inDungeons() || mc.player == null
                || !(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            return;
        }
        if (!"Chest".equals(cs.getTitle().getString())) {
            return;
        }
        for (Slot s : cs.getMenu().slots) {
            if (s.container != mc.player.getInventory() && s.hasItem()) {
                return;   // still has loot in it
            }
        }
        mc.player.closeContainer();
    }
}
