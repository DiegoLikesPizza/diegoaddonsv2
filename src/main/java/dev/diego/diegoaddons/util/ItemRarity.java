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
        String title = LegacyText.strip(screen.getTitle().getString())
                .toLowerCase(java.util.Locale.ROOT);
        // The server menus whose slots hold items rather than buttons.
        boolean serverSlots = (mod.accessoryBag() && isAccessoryBag(title))
                || (mod.pets() && isPetsMenu(title))
                || (mod.chests() && isChest(title));
        if (!inventoryScreen && !serverSlots && !mod.everywhere()) {
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
            if (!(yours ? inventoryScreen || mod.everywhere() : serverSlots)) {
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
    private static boolean isAccessoryBag(String lowerTitle) {
        return lowerTitle.contains("accessory bag");
    }

    /**
     * Whether this is the pets menu - every pet you own, and the one other server menu where a
     * rarity colour is information rather than decoration.
     *
     * <p>Matched from the <b>start</b> of the title rather than anywhere in it: the menu is "Pets"
     * or, once you have more than a page of them, "Pets (1/2)". Looking for the word anywhere would
     * also catch every menu that merely mentions pets.
     *
     * <p>The buttons along the bottom of the menu need no excluding: a sort or filter button has no
     * rarity line, so {@link #color} answers 0 for it and it is left alone.
     */
    private static boolean isPetsMenu(String lowerTitle) {
        return lowerTitle.startsWith("pets");
    }

    /**
     * Whether this is a chest of some kind: a plain or large chest, an Ender Chest page, a backpack,
     * a personal vault, or a dungeon reward chest.
     *
     * <p>One check for all of them because they are one thing - a box of items - and the word is in
     * every title SkyBlock and vanilla give them: "Chest", "Large Chest", "Ender Chest (1/9)",
     * "Wood Chest" through "Bedrock Chest". Backpacks are the same idea under a different noun, so
     * they are named here too.
     *
     * <p>The reward chest's own furniture needs no excluding: the "Open Reward Chest" button and the
     * coin cost carry no rarity line, so {@link #color} answers 0 and they are left alone. That is
     * the same reason the pets menu's buttons come out uncoloured.
     */
    private static boolean isChest(String lowerTitle) {
        return lowerTitle.contains("chest")
                || lowerTitle.contains("backpack")
                || lowerTitle.contains("personal vault");
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

    /**
     * The rarity word itself, which is what a rarity line is recognised by.
     *
     * <p>Word boundaries rather than a whole-line match, because the line is not always only the
     * word: a dungeon item reads "LEGENDARY DUNGEON HELMET", and a recombobulated one wraps it in
     * obfuscated characters that strip down to stray letters either side.
     */
    private static final java.util.regex.Pattern RARITY = java.util.regex.Pattern.compile(
            "\\b(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|SUPREME|ULTIMATE|ADMIN)\\b");

    /** The ARGB rarity colour of an item, or 0 if it has no readable rarity line. */
    public static int color(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return 0;
        }
        List<Component> lines = lore.lines();
        Component bottom = null;
        // Bottom-up, because the rarity is the last thing an item's lore says about itself.
        for (int i = lines.size() - 1; i >= 0; i--) {
            Component c = lines.get(i);
            String text = LegacyText.strip(c.getString());
            if (text.isBlank()) {
                continue;
            }
            if (RARITY.matcher(text).find()) {
                return firstColor(c);
            }
            if (bottom == null) {
                bottom = c;
            }
        }
        // Nothing named a rarity, so fall back to the bottom-most line's colour - which is what
        // this read used to be in full. A menu item is where the two differ: the pets menu ends
        // every pet with "Click to summon!", and taking that line would paint the whole menu
        // yellow while the rarity sat one line above it.
        return bottom == null ? 0 : firstColor(bottom);
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
