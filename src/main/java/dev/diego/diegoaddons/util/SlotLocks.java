package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.SlotLockModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;

import java.util.Set;

/**
 * Locks player-inventory slots so their item cannot be moved, swapped or dropped by accident. A lock
 * is stored by the slot's index in the player inventory (0-40), so it holds in every screen that shows
 * that slot, and is shown as a small padlock in the slot's corner. The block is done entirely client-side by denying the input - the click, the hotbar-swap
 * key, the drop key - so the action never reaches the server; no server-side mixin is needed.
 *
 * <p>Toggle a lock by pointing at a slot in any container screen and pressing the module's key.
 *
 * <p>The drop key is the one input that also has to be caught outside a screen, since with the
 * inventory closed it never passes through one; see {@link dev.diego.diegoaddons.mixin.PlayerDropMixin}.
 */
public final class SlotLocks {
    /** Player-inventory index of the off-hand slot, for the swap-offhand key. */
    private static final int OFFHAND = 40;

    private SlotLocks() {
    }

    private static Set<Integer> locked() {
        return ConfigManager.get().lockedSlots;
    }

    public static boolean isLocked(int invIndex) {
        return invIndex >= 0 && locked().contains(invIndex);
    }

    public static void toggle(int invIndex) {
        if (invIndex < 0) {
            return;
        }
        if (!locked().add(invIndex)) {
            locked().remove(invIndex);
        }
        ConfigManager.save();
    }

    // --- input blocking -------------------------------------------------------------------------

    /** True when a click at (gx,gy) lands on a locked slot and should be swallowed. */
    public static boolean locksClick(AbstractContainerScreen<?> screen, double gx, double gy) {
        if (!enabled()) {
            return false;
        }
        return isLocked(invIndex(Minecraft.getInstance(), slotUnder(screen, gx, gy)));
    }

    /** True when a key press should be swallowed because it would move a locked slot's item. */
    public static boolean locksKey(AbstractContainerScreen<?> screen, KeyEvent event) {
        if (!enabled()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        double[] m = guiMouse(mc);
        int hovered = invIndex(mc, slotUnder(screen, m[0], m[1]));
        Options o = mc.options;

        if (o.keyDrop.matches(event) && isLocked(hovered)) {
            return true;   // Q-drop of a locked slot
        }
        for (int i = 0; i < o.keyHotbarSlots.length; i++) {
            if (o.keyHotbarSlots[i].matches(event) && (isLocked(hovered) || isLocked(i))) {
                return true;   // number-key swap into/out of a locked slot
            }
        }
        return o.keySwapOffhand.matches(event) && (isLocked(hovered) || isLocked(OFFHAND));
    }

    // --- rendering + toggling -------------------------------------------------------------------

    /** Handles the toggle key. {@code mx,my} are GUI-space mouse coords. */
    public static void keys(AbstractContainerScreen<?> screen, int mx, int my) {
        SlotLockModule mod = SlotLockModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (mod.lockKey().consumePress()) {
            toggle(invIndex(Minecraft.getInstance(), slotUnder(screen, mx, my)));
        }
    }

    /** Draws a padlock on every locked slot. */
    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        SlotLockModule mod = SlotLockModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.overlay()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int leftPos = acc.diego$leftPos();
        int topPos = acc.diego$topPos();
        for (Slot s : screen.getMenu().slots) {
            if (s.container != mc.player.getInventory() || !isLocked(s.getContainerSlot())) {
                continue;
            }
            padlock(g, leftPos + s.x, topPos + s.y);
        }
    }

    /**
     * A small translucent padlock in the corner of a locked slot.
     *
     * <p>It used to be a red wash over the whole slot with a border round it, which covered the
     * item's rarity colour and, being drawn last, sat on top of tooltips as well. A lock is a
     * marker, not a highlight: it belongs in a corner, faint, out of the way of everything the slot
     * is already saying.
     */
    private static void padlock(GuiGraphicsExtractor g, int x, int y) {
        int body = 0xC0FF5555;
        int shackle = 0x90FF8888;
        int px = x + 9;    // bottom-right corner of the 16x16 slot
        int py = y + 8;
        // Shackle: two uprights and a cap.
        g.fill(px + 1, py, px + 2, py + 2, shackle);
        g.fill(px + 4, py, px + 5, py + 2, shackle);
        g.fill(px + 1, py, px + 5, py + 1, shackle);
        // Body.
        g.fill(px, py + 2, px + 6, py + 7, body);
        // Keyhole.
        g.fill(px + 2, py + 3, px + 4, py + 5, 0x80000000);
    }

    // --- helpers --------------------------------------------------------------------------------

    private static boolean enabled() {
        SlotLockModule mod = SlotLockModule.INSTANCE;
        return mod != null && mod.isEnabled();
    }

    /** Whether locks are being enforced at all - read from outside, by the drop-key mixin. */
    public static boolean locksEnabled() {
        return enabled();
    }

    /** The slot under a GUI-space point, or null. */
    private static Slot slotUnder(AbstractContainerScreen<?> screen, double gx, double gy) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int leftPos = acc.diego$leftPos();
        int topPos = acc.diego$topPos();
        for (Slot s : screen.getMenu().slots) {
            int x = leftPos + s.x;
            int y = topPos + s.y;
            if (gx >= x && gx < x + 16 && gy >= y && gy < y + 16) {
                return s;
            }
        }
        return null;
    }

    /** The player-inventory index of a slot, or -1 if it is not a player-inventory slot. */
    private static int invIndex(Minecraft mc, Slot s) {
        if (s == null || mc.player == null || s.container != mc.player.getInventory()) {
            return -1;
        }
        return s.getContainerSlot();
    }

    private static double[] guiMouse(Minecraft mc) {
        var w = mc.getWindow();
        double gx = mc.mouseHandler.xpos() * w.getGuiScaledWidth() / Math.max(1, w.getScreenWidth());
        double gy = mc.mouseHandler.ypos() * w.getGuiScaledHeight() / Math.max(1, w.getScreenHeight());
        return new double[]{gx, gy};
    }
}
