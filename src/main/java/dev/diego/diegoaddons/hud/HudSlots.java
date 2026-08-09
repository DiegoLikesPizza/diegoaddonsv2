package dev.diego.diegoaddons.hud;

import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * One inventory slot, drawn.
 *
 * <p>Shared because the Inventory HUD and the Player HUD draw the same thing at different sizes - a
 * plate, an item on it, and the game's own count and durability overlays. They were split into two
 * modules precisely so they could be placed apart, which is a reason to share the drawing rather
 * than to write it twice.
 */
public final class HudSlots {

    /** The space between two slots in a row or column. */
    public static final int GAP = 2;

    /** An item's natural drawn size; anything larger is reached by scaling the pose. */
    private static final int NATURAL = 16;

    private HudSlots() {
    }

    /** The empty plate a slot sits on, when the user has those switched on. */
    public static void plate(GuiGraphicsExtractor g, int x, int y, int size, boolean smooth) {
        UiRender.fillRounded(g, x, y, size, size, 3,
                Theme.withAlpha(Themes.current().textFaint(), 0.16f), smooth);
    }

    /**
     * An item filling a slot of the given size.
     *
     * <p>There is no size argument to draw an item with, so a slot larger than {@link #NATURAL}
     * carries the difference in the pose - which scales the model rather than stretching a sprite,
     * so a big slot is genuinely a bigger item.
     *
     * <p>{@code fakeItem} rather than {@code item}: it needs no holding entity, which is what lets
     * the grid draw in the HUD editor when it is opened from the title screen.
     */
    public static void item(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        float scale = size / (float) NATURAL;
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        if (scale != 1f) {
            g.pose().scale(scale, scale);
        }
        g.fakeItem(stack, 0, 0);
        // The game's own overlays, so a stack count here reads exactly as it does in the inventory.
        g.itemDecorations(font, stack, 0, 0);
        g.pose().popMatrix();
    }
}
