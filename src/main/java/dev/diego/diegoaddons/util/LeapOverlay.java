package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.LeapOverlayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Overlays each teammate's dungeon class on their head in the Spirit Leap menu, in the class colour,
 * so the right person can be picked at a glance. The class is read from the tab list (the one place
 * the dungeon shows it), matched to the head by player name; if it cannot be read the head is simply
 * left plain rather than guessed at.
 */
public final class LeapOverlay {
    /** Short tags + colours, indexed alongside {@link PartyFinder#CLASSES}. */
    private static final String[] TAGS = {"HEA", "MAG", "BER", "ARC", "TAN"};
    private static final int[] COLORS = {
            0xFF55FF55,   // healer  - green
            0xFF55FFFF,   // mage    - aqua
            0xFFFF5555,   // berserk - red
            0xFFFFAA00,   // archer  - gold
            0xFFFF55FF,   // tank    - light purple
    };

    private LeapOverlay() {
    }

    /** Draws the overlay, after the menu's own items so the tag sits on top of each head. */
    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        LeapOverlayModule mod = LeapOverlayModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (!screen.getTitle().getString().contains("Spirit Leap")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int leftPos = acc.diego$leftPos();
        int topPos = acc.diego$topPos();

        for (Slot s : screen.getMenu().slots) {
            if (s.container == mc.player.getInventory()) {
                continue;
            }
            ItemStack stack = s.getItem();
            if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) {
                continue;
            }
            String name = LegacyText.strip(stack.getHoverName().getString()).trim();
            int cls = classOf(mc, name);
            if (cls < 0) {
                continue;
            }
            String tag = TAGS[cls];
            int color = COLORS[cls];
            int x = leftPos + s.x + 8 - mc.font.width(tag) / 2;
            int y = topPos + s.y + 4;
            g.text(mc.font, Component.literal(tag), x, y, color, true);
        }
    }

    /** The class index for a player from the tab list, or -1 if it is not shown there. */
    private static int classOf(Minecraft mc, String name) {
        if (name.isEmpty()) {
            return -1;
        }
        for (String line : SkyblockLocation.tabLines(mc)) {
            if (!line.contains(name)) {
                continue;
            }
            String low = line.toLowerCase(Locale.ROOT);
            for (int i = 0; i < PartyFinder.CLASSES.length; i++) {
                if (low.contains(PartyFinder.CLASSES[i])) {
                    return i;
                }
            }
        }
        return -1;
    }
}
