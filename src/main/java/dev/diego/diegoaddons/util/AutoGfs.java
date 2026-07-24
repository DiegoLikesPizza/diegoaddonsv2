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
    /** One refillable item: what to look for in an item's name, and what to ask the sacks for. */
    public record Item(String match, String command) {
    }

    public static final Item PEARLS = new Item("ender pearl", "gfs ENDER_PEARL 16");
    public static final Item SUPERBOOM = new Item("superboom", "gfs SUPERBOOM_TNT 16");
    public static final Item LEAPS = new Item("spirit leap", "gfs SPIRIT_LEAP 16");

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

        int threshold = mod.threshold();
        for (Item item : mod.enabledItems()) {
            int have = count(mc, item.match());
            // Zero means "not carried at all" rather than "ran out", so it is left alone.
            if (have > 0 && have < threshold) {
                lastRefill = System.currentTimeMillis();
                mc.player.connection.sendCommand(item.command());
                return;   // one refill at a time; the next check picks up the rest
            }
        }
    }

    /** How many of an item are in the inventory, matched loosely on the display name. */
    private static int count(Minecraft mc, String match) {
        int total = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) {
                continue;
            }
            String name = LegacyText.strip(st.getHoverName().getString()).toLowerCase(Locale.ROOT);
            if (name.contains(match)) {
                total += st.getCount();
            }
        }
        return total;
    }
}
