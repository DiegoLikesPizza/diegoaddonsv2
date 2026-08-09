package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.AbilityCooldownModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows an item's ability cooldown as a number on its hotbar slot. The cooldown length is read from
 * the item's lore ("Cooldown: 5s"); the timer starts when the use key is pressed while holding the
 * item - which covers the right-click abilities that make up almost all of them.
 *
 * <p>Keyed by the item's display name rather than its NBT id, so it needs only the lore reading that
 * {@link ItemRarity} already relies on. It is therefore a best-effort aid: it cannot know whether the
 * ability actually fired (mana, etc.), only that it was triggered.
 */
public final class AbilityCooldown {
    private static final Pattern COOLDOWN = Pattern.compile("Cooldown: ?(\\d+)s");

    /** item display name -> wall-clock time the cooldown ends. */
    private static final Map<String, Long> ends = new HashMap<>();
    private static boolean wasUse;

    private AbilityCooldown() {
    }

    public static void reset() {
        ends.clear();
        wasUse = false;
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        AbilityCooldownModule mod = AbilityCooldownModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null) {
            return;
        }
        boolean down = mc.screen == null && mc.options.keyUse.isDown();
        if (down && !wasUse) {
            ItemStack held = mc.player.getMainHandItem();
            int secs = cooldownSeconds(held);
            if (secs > 0) {
                ends.put(held.getHoverName().getString(), System.currentTimeMillis() + secs * 1000L);
            }
        }
        wasUse = down;
    }

    /** Draws the remaining seconds on any hotbar item that has a running cooldown. */
    public static void renderHotbar(GuiGraphicsExtractor g, Minecraft mc) {
        AbilityCooldownModule mod = AbilityCooldownModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null || mc.options.hideGui || ends.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        // Drop finished timers so the map does not grow without bound.
        for (Iterator<Map.Entry<String, Long>> it = ends.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() - now <= 0) {
                it.remove();
            }
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int hotbarX = sw / 2 - 91;
        int y = sh - 19;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Long end = ends.get(stack.getHoverName().getString());
            if (end == null) {
                continue;
            }
            double left = (end - now) / 1000.0;
            if (left <= 0) {
                continue;
            }
            String text = left >= 10 || !mod.showDecimals()
                    ? String.valueOf((int) Math.ceil(left))
                    : String.format(java.util.Locale.ROOT, "%.1f", left);
            int color = mod.colorFor(left);
            int x = hotbarX + 3 + i * 20;
            if (mod.dimSlot()) {
                // Behind the number and over the item, so the slot reads as unavailable at a glance
                // rather than only when you look at the digits.
                g.fill(x - 1, y - 2, x + 17, y + 16, 0x80000000);
            }
            int tx = x + 16 - mc.font.width(Component.literal(text));
            g.text(mc.font, Component.literal(text), tx, y + 9, color, true);
        }
    }

    /** The ability cooldown in whole seconds parsed from an item's lore, or -1 if it has none. */
    private static int cooldownSeconds(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return -1;
        }
        List<Component> lines = lore.lines();
        for (Component c : lines) {
            Matcher m = COOLDOWN.matcher(LegacyText.strip(c.getString()));
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
