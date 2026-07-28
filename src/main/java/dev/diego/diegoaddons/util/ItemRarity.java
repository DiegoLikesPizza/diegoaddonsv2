package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.ItemRarityModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Optional;

/**
 * Draws a SkyBlock item's rarity colour as a square behind it in any inventory, the way the 1.8.9
 * mods did. The rarity is not a data field - it is the colour of the item's bottom lore line
 * ("LEGENDARY DUNGEON HELMET" etc.) - so the colour is read straight from there.
 */
public final class ItemRarity {
    private ItemRarity() {
    }

    /** Draws the rarity backing for every filled slot of the open container. */
    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        ItemRarityModule mod = ItemRarityModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        // Only your own inventory, not chest/SkyBlock menus - the rarity backings there just clutter
        // pages of filler items.
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
            return;
        }
        int left = ((AbstractContainerScreenAccessor) screen).diego$leftPos();
        int top = ((AbstractContainerScreenAccessor) screen).diego$topPos();
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            int color = color(stack);
            if (color == 0) {
                continue;
            }
            paint(g, left + slot.x, top + slot.y, color, mod.display());
        }
    }

    /**
     * Draws a rarity-coloured frame around each hotbar slot. Runs in the HUD layer (on top of the
     * hotbar), so it frames the item rather than filling behind it - which on the hotbar would sit
     * over the item instead.
     */
    public static void renderHotbar(GuiGraphicsExtractor g, net.minecraft.client.Minecraft mc) {
        ItemRarityModule mod = ItemRarityModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null || mc.options.hideGui) {
            return;
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
            int color = color(stack);
            if (color == 0) {
                continue;
            }
            paint(g, hotbarX + 3 + i * 20, y, color, mod.display());
        }
    }

    /**
     * Puts a rarity colour on one 16x16 slot, in whichever of the three ways is chosen.
     *
     * <p>Filled sits behind the item at low alpha; outline frames it at full; the circle is a disc
     * behind it, drawn as rows of a filled circle since a rounded shape has to be built out of the
     * rectangles {@code fill} gives us.
     */
    private static void paint(GuiGraphicsExtractor g, int x, int y, int color, int display) {
        switch (display) {
            case ItemRarityModule.OUTLINE -> {
                int c = (color & 0x00FFFFFF) | 0xFF000000;
                g.fill(x - 1, y - 1, x + 17, y, c);
                g.fill(x - 1, y + 16, x + 17, y + 17, c);
                g.fill(x - 1, y, x, y + 16, c);
                g.fill(x + 16, y, x + 17, y + 16, c);
            }
            case ItemRarityModule.CIRCLE -> {
                int c = (color & 0x00FFFFFF) | (0x88 << 24);
                double r = 9.0;
                double cx = x + 8.0;
                double cy = y + 8.0;
                for (int row = -9; row < 9; row++) {
                    double dy = row + 0.5;
                    double half = r * r - dy * dy;
                    if (half <= 0) {
                        continue;
                    }
                    int w = (int) Math.round(Math.sqrt(half));
                    g.fill((int) (cx - w), (int) (cy + dy - 0.5), (int) (cx + w), (int) (cy + dy + 0.5), c);
                }
            }
            default -> g.fill(x - 1, y - 1, x + 17, y + 17, (color & 0x00FFFFFF) | (0x66 << 24));
        }
    }

    /** The ARGB rarity colour of an item, or 0 if it has no readable rarity line. */
    public static int color(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return 0;
        }
        List<Component> lines = lore.lines();
        for (int i = lines.size() - 1; i >= 0; i--) {
            Component c = lines.get(i);
            if (LegacyText.strip(c.getString()).isBlank()) {
                continue;
            }
            return firstColor(c);   // the bottom-most non-blank line carries the rarity
        }
        return 0;
    }

    /** The colour of the first coloured, non-blank run of a component. */
    private static int firstColor(Component c) {
        int[] out = {0};
        c.visit((style, text) -> {
            TextColor tc = style.getColor();
            if (tc != null && !text.isBlank()) {
                out[0] = 0xFF000000 | tc.getValue();
                return Optional.of(Boolean.TRUE);   // stop at the first coloured run
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out[0];
    }
}
