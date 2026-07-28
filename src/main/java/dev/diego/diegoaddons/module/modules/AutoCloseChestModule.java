package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * After a dungeon secret chest has been emptied, closes its screen so you can keep moving without a
 * manual close. Strictly gated to dungeons and to a chest screen, and it only fires once the chest's
 * own slots are all empty - so it never closes a chest you are still looting.
 *
 * <p>The subtlety is that "all slots empty" is also true for the first few ticks of <i>every</i>
 * chest: the screen opens before the server has sent its contents, so a naive emptiness check closes
 * every chest the instant it opens and the feature looks like it does nothing at all. So the module
 * waits until it has actually seen loot in this chest before it is willing to close it, and forgets
 * that the moment a different container is opened.
 *
 * <p>Off by default: it is an input side-effect and should be an opt-in convenience.
 */
public class AutoCloseChestModule extends Module {
    public static AutoCloseChestModule INSTANCE;

    /** How many consecutive empty ticks to require, so a mid-loot frame cannot trigger a close. */
    private static final int EMPTY_TICKS = 2;

    /** The menu we are currently watching, so state resets when a different container is opened. */
    private int watchedMenu = -1;
    /** Whether this chest has ever been seen holding loot - the guard against closing on open. */
    private boolean sawLoot;
    private int emptyTicks;

    public AutoCloseChestModule() {
        super("autoclosechest", Category.DUNGEONS, "Auto Close Chests",
                "Close a dungeon secret chest once it has been looted.");
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!DungeonState.inDungeons() || mc.player == null
                || !(mc.screen instanceof AbstractContainerScreen<?> cs) || !isChest(cs)) {
            forget();
            return;
        }

        int id = cs.getMenu().containerId;
        if (id != watchedMenu) {
            forget();
            watchedMenu = id;
        }

        if (hasLoot(mc, cs)) {
            sawLoot = true;
            emptyTicks = 0;
            return;
        }
        if (!sawLoot) {
            return;   // contents have not arrived yet - this is an opening chest, not a looted one
        }
        if (++emptyTicks >= EMPTY_TICKS) {
            forget();
            mc.player.closeContainer();
        }
    }

    /**
     * Whether any slot outside the player's own inventory still holds an item. The player's slots are
     * part of the same menu, so they have to be excluded or a chest could never read as empty.
     */
    private boolean hasLoot(Minecraft mc, AbstractContainerScreen<?> cs) {
        for (Slot s : cs.getMenu().slots) {
            if (s.container != mc.player.getInventory() && s.hasItem()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dungeon secret chests are plain chest screens; the title is how they are told from the
     * hundreds of menus SkyBlock builds out of chests.
     *
     * <p>The title is stripped of colour codes first. Comparing the raw string meant a title the
     * server had coloured never matched anything, and the feature simply never fired - which is
     * indistinguishable from it being broken. What it declined is logged once per container, so a
     * title that still does not match can be read off rather than guessed at.
     */
    private boolean isChest(AbstractContainerScreen<?> cs) {
        String title = dev.diego.diegoaddons.util.LegacyText.strip(cs.getTitle().getString()).trim();
        boolean chest = title.equals("Chest") || title.equals("Large Chest");
        if (!chest && cs.getMenu().containerId != declined) {
            declined = cs.getMenu().containerId;
            dev.diego.diegoaddons.DiegoAddonsV2Client.LOGGER.info(
                    "[DiegoAddons] Auto Close Chests ignored a container titled \"{}\"", title);
        }
        return chest;
    }

    /** The container already logged as "not a chest", so it is said once and not every tick. */
    private int declined = -1;

    private void forget() {
        watchedMenu = -1;
        sawLoot = false;
        emptyTicks = 0;
    }
}
