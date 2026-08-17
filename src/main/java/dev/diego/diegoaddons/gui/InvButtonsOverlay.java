/*
 * Ported from Inventory Buttons (https://github.com/afranz29/Inventory-Buttons),
 * Copyright (C) 2026 Panda/afranz29, licensed under the LGPLv3 - itself a port of the inventory
 * buttons from NotEnoughUpdates (Moulberry and contributors). This file stays under the LGPLv3.
 */

package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.InvButton;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.InventoryButtonsModule;
import dev.diego.diegoaddons.util.InvButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * The buttons as they appear on a real menu: drawn beside it, and clicked to run their command.
 *
 * <p>Drawn and hit-tested from the screen events rather than from a mixin, which is how everything
 * else that decorates a container menu here works - the draw goes in after the menu's own pass and
 * before its tooltips, and the click is vetoed so it never reaches the slot underneath.
 *
 * <p><b>The geometry is the upstream mod's, kept exactly.</b> A button is placed against the player
 * inventory, which is 176x166, and a taller menu is a menu with more rows on top - so a button
 * parked below the midline is pushed down by the difference and one parked above it stays put.
 * A button sitting <i>inside</i> the 176-wide rectangle is drawn on the player inventory only,
 * because on any other menu that is where the menu's own slots are.
 */
public final class InvButtonsOverlay {

    private static final Identifier BUTTONS =
            Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "textures/invbuttons/buttons.png");

    /** The buttons sheet is five 18x18 styles on two rows: hovered on top, normal below. */
    private static final int SHEET_W = 90;
    private static final int SHEET_H = 36;

    public static final int SIZE = 18;

    /** The player inventory's own size; every offset is measured against it. */
    public static final int INV_W = 176;
    public static final int INV_H = 166;

    private InvButtonsOverlay() {
    }

    private static boolean active() {
        InventoryButtonsModule module = InventoryButtonsModule.INSTANCE;
        if (module == null || !module.isEnabled()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (module.hideInCreative() && mc.gameMode != null
                && mc.gameMode.getPlayerMode().isCreative()) {
            return false;
        }
        return true;
    }

    /**
     * Where a button lands on this screen, or null if it is not drawn here at all.
     *
     * <p>One method for both the drawing and the clicking, so a button can never be shown in one
     * place and pressed in another.
     */
    private static int[] place(AbstractContainerScreen<?> screen, InvButton button) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        int left = accessor.diego$leftPos();
        int top = accessor.diego$topPos();
        int width = accessor.diego$imageWidth();
        int height = accessor.diego$imageHeight();

        boolean playerInventory = screen instanceof InventoryScreen;
        int offsetY = 0;

        if (!playerInventory && !button.anchorBottom) {
            if (button.y < 80) {
                boolean insideX = button.x >= -1 && button.x <= INV_W;
                boolean insideY = button.y >= 0;
                if (!button.anchorRight && insideX && insideY) {
                    return null;
                }
            } else {
                // Below the midline is measured from the bottom of the inventory, which a taller
                // menu pushes down by exactly the rows it added.
                offsetY = height - INV_H;
            }
        }

        int x = left + button.x + (button.anchorRight ? width : 0);
        int y = top + button.y + offsetY + (button.anchorBottom ? height : 0);
        return new int[]{x, y};
    }

    // ------------------------------------------------------------------ drawing

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g,
                              int mouseX, int mouseY) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        InvButton hovered = null;
        int hoveredX = 0;
        int hoveredY = 0;

        for (InvButton button : InvButtons.buttons()) {
            int[] at = place(screen, button);
            if (at == null) {
                continue;
            }
            drawButton(g, mc, button, at[0], at[1]);
            if (mouseX >= at[0] && mouseX <= at[0] + SIZE && mouseY >= at[1] && mouseY <= at[1] + SIZE) {
                hovered = button;
                hoveredX = at[0];
                hoveredY = at[1];
            }
        }

        if (hovered != null) {
            border(g, hoveredX, hoveredY, SIZE, SIZE, 0xFFFFFFFF);
            if (InventoryButtonsModule.INSTANCE.showTooltips()) {
                g.setTooltipForNextFrame(mc.font, Component.literal(hovered.command), mouseX, mouseY);
            }
        }
    }

    /** One button - its plate, then whichever of the three kinds of icon it wears. */
    public static void drawButton(GuiGraphicsExtractor g, Minecraft mc, InvButton button, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, BUTTONS, x, y,
                button.backgroundIndex * 18f, 18f, SIZE, SIZE, SIZE, SIZE, SHEET_W, SHEET_H, 0xFFFFFFFF);
        drawIcon(g, mc, button, x, y);
    }

    /** The icon alone, at the same offset the plate puts it - a bundled texture, an item, or a "?". */
    public static void drawIcon(GuiGraphicsExtractor g, Minecraft mc, InvButton button, int x, int y) {
        Identifier custom = InvButtons.customTexture(button.itemId);
        if (custom != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, custom, x + 1, y + 1,
                    0f, 0f, 16, 16, 16, 16, 16, 16, 0xFFFFFFFF);
            return;
        }
        ItemStack stack = InvButtons.stackFor(button);
        if (!stack.isEmpty()) {
            g.fakeItem(stack, x + 1, y + 1);
        } else {
            g.centeredText(mc.font, "?", x + 9, y + 5, 0xFFFFFFFF);
        }
    }

    /** A one-pixel frame, which is how a hovered or selected button is marked. */
    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    // ------------------------------------------------------------------ input

    /** Runs the button under the cursor. True denies the click to the menu underneath. */
    public static boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX,
                                       double mouseY, int mouseButton) {
        if (mouseButton != 0 || !active()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        for (InvButton button : InvButtons.buttons()) {
            int[] at = place(screen, button);
            if (at == null) {
                continue;
            }
            if (mouseX >= at[0] && mouseX <= at[0] + SIZE && mouseY >= at[1] && mouseY <= at[1] + SIZE) {
                String command = button.command == null ? "" : button.command.trim();
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                if (!command.isEmpty()) {
                    mc.player.connection.sendCommand(command);
                }
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
        }
        return false;
    }
}
