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

    /**
     * Draws the rarity backing for the slots of the open container that are worth colouring.
     *
     * <p>Which those are depends on whose items they hold. The <b>player inventory</b> - the last 36
     * slots of any menu, and in the inventory screen every slot including the armour - is yours, so
     * it is coloured wherever it appears rather than only when the inventory screen is the thing on
     * screen. That was the old behaviour, and it meant your own gear lost its colours the moment any
     * SkyBlock menu was open on top of it.
     *
     * <p>A <b>server menu's</b> own slots are left alone, because most of a SkyBlock menu is filler -
     * panes, close buttons, cosmetic heads - and colouring that is confetti rather than information.
     * The accessory bag is the exception worth making: every slot in it is an accessory you own.
     */
    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        ItemRarityModule mod = ItemRarityModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        boolean inventoryScreen =
                screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        boolean accessories = mod.accessoryBag() && isAccessoryBag(screen);
        if (!inventoryScreen && !accessories && !mod.everywhere()) {
            return;
        }

        int left = ((AbstractContainerScreenAccessor) screen).diego$leftPos();
        int top = ((AbstractContainerScreenAccessor) screen).diego$topPos();
        List<Slot> slots = screen.getMenu().slots;
        // Your own slots are the last 36 of any menu. In the inventory screen the armour and the
        // offhand are yours too, so there the line is drawn at the start.
        int mine = inventoryScreen ? 0 : Math.max(0, slots.size() - PLAYER_SLOTS);

        for (int i = 0; i < slots.size(); i++) {
            boolean yours = i >= mine;
            if (!(yours ? inventoryScreen || mod.everywhere() : accessories)) {
                continue;
            }
            Slot slot = slots.get(i);
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

    /** The last 36 slots of any container menu are the player's own inventory. */
    private static final int PLAYER_SLOTS = 36;

    /**
     * Whether this is the accessory bag.
     *
     * <p>Matched on the title, which is how every SkyBlock menu has to be told apart. The paged
     * form carries a page number ("Accessory Bag (1/3)"), so this looks for the words rather than
     * for the whole title.
     */
    private static boolean isAccessoryBag(AbstractContainerScreen<?> screen) {
        return LegacyText.strip(screen.getTitle().getString())
                .toLowerCase(java.util.Locale.ROOT).contains("accessory bag");
    }

    /**
     * Draws the backing for one hotbar slot, called from {@link
     * dev.diego.diegoaddons.mixin.HotbarSlotRarityMixin} just before vanilla draws the item into it.
     *
     * <p>Position comes from vanilla rather than being worked out from the window size, so the
     * offhand slot and any future layout change are covered without this knowing the hotbar's shape.
     */
    public static void renderHotbarSlot(GuiGraphicsExtractor g, int x, int y, ItemStack stack) {
        ItemRarityModule mod = ItemRarityModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || stack == null || stack.isEmpty()) {
            return;
        }
        int color = color(stack);
        if (color == 0) {
            return;
        }
        paint(g, x, y, color, mod.display());
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
