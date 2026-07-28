package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.AutoGfsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Refills consumables from your sacks when they run low, by sending the game's own "get from sacks"
 * command.
 *
 * <p>The refill is deliberately conservative. It only fires below a threshold, never more than once
 * every few seconds, and not at all while a menu is open - a command sent while you are clicking
 * through a GUI is the one that goes wrong. Each item is its own toggle, all off by default, because
 * this sends commands on your behalf.
 */
public final class AutoGfs {
    /** Minimum gap between two refills, so a low stack cannot spam commands. */
    private static final long COOLDOWN_MS = 5000;
    private static final int CHECK_INTERVAL = 20;

    private static long lastRefill;
    private static int tick;

    private AutoGfs() {
    }

    public static void reset() {
        lastRefill = 0;
        tick = 0;
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        AutoGfsModule mod = AutoGfsModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null) {
            return;
        }
        // Never while a screen is open: a command fired mid-menu is the one that misfires.
        if (mc.screen != null) {
            return;
        }
        if (++tick < CHECK_INTERVAL) {
            return;
        }
        tick = 0;
        if (System.currentTimeMillis() - lastRefill < COOLDOWN_MS) {
            return;
        }

        for (dev.diego.diegoaddons.config.GfsItem item : mod.enabledItems()) {
            int[] state = scan(mc, item.match());
            int have = state[0];
            int maxStack = state[1];
            // Zero means "not carried at all" rather than "ran out", so it is left alone.
            if (have <= 0 || have >= item.threshold) {
                continue;
            }
            // Top the stack back up to full rather than always pulling a whole stack - grabbing 16
            // when you are only four short just wastes them back into the sack.
            int deficit = maxStack - have;
            if (deficit <= 0) {
                continue;
            }
            lastRefill = System.currentTimeMillis();
            mc.player.connection.sendCommand("gfs " + item.sackId() + " " + deficit);
            return;   // one refill at a time; the next check picks up the rest
        }
    }

    /**
     * Scans the inventory for an item matched loosely on its display name.
     *
     * @return {@code [total count, per-slot max stack size]}; the max size is what a full stack is,
     *         so the refill can bring the count back up to it instead of over-grabbing.
     */
    private static int[] scan(Minecraft mc, String match) {
        int total = 0;
        int maxStack = 1;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) {
                continue;
            }
            String name = LegacyText.strip(st.getHoverName().getString()).toLowerCase(Locale.ROOT);
            if (name.contains(match)) {
                total += st.getCount();
                maxStack = Math.max(maxStack, st.getMaxStackSize());
            }
        }
        return new int[]{total, maxStack};
    }
}
