package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A HUD element that mirrors the player's inventory on screen as a grid of item slots, so you can
 * see your inventory without opening it. Optional sections - laid out left to right - add an armour
 * column and a live 3-D player model, plus toggles for the panel background, slot cells and the
 * hotbar row (with the selected slot highlighted).
 *
 * <p>It is a custom-drawn {@link HudModule} (overrides the footprint and drawing instead of using
 * the default text chip) and works with the HUD editor's drag + scroll-to-scale.
 */
public class InventoryHudModule extends HudModule {
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT = 18;       // cell stride
    private static final int CELL = 16;       // drawn cell / item size
    private static final int PAD = 5;
    private static final int GAP = 6;         // gap between main storage and the hotbar row
    private static final int SECTION_GAP = 6; // gap between armour / model / grid sections
    private static final int MODEL_W = 44;
    private static final int ARMOR_H = (4 - 1) * SLOT + CELL; // 4 stacked armour slots
    private static final int GRID_W = (COLS - 1) * SLOT + CELL;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final BooleanSetting background = new BooleanSetting(this, "background", "Background", true);
    private final BooleanSetting slotBoxes = new BooleanSetting(this, "slots", "Slot boxes", true);
    private final BooleanSetting hotbar = new BooleanSetting(this, "hotbar", "Show hotbar", false);
    private final BooleanSetting armor = new BooleanSetting(this, "armor", "Armor", false);
    private final BooleanSetting playerModel = new BooleanSetting(this, "player", "Player model", false);
    private final BooleanSetting pet = new BooleanSetting(this, "pet", "Pet (SkyBlock)", false);
    private final BooleanSetting equipment = new BooleanSetting(this, "equipment", "Equipment (SkyBlock)", false);
    private final BooleanSetting debugScan = new BooleanSetting(this, "debug", "Debug scan (log)", false);

    public InventoryHudModule() {
        super("inventory", "Inventory HUD", "Shows your inventory as a grid of item slots.", false);
        settings.add(background);
        settings.add(slotBoxes);
        settings.add(hotbar);
        settings.add(armor);
        settings.add(playerModel);
        settings.add(pet);
        settings.add(equipment);
        settings.add(debugScan);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        SkyblockHud.debug = debugScan.get();

        // One-time hint: the SkyBlock pet/equipment can only be read from their (paged) menus, so
        // ask the user to open every page once. Shown only when those toggles are actually in use.
        if (mc.player == null || ConfigManager.get().sbHintShown) {
            return;
        }
        if (!pet.get() && !equipment.get()) {
            return;
        }
        mc.gui.getChat().addClientSystemMessage(Component.literal(
                "§b[DiegoAddons] §fTo show your SkyBlock pet & equipment, open §eevery page§f of your "
                        + "§e(x/y) Pets §fand §e(x/y) Equipment Sets §fmenus once - the HUD reads them from there."));
        ConfigManager.get().sbHintShown = true;
        ConfigManager.save();
    }

    @Override
    protected String label() {
        return "Inventory";
    }

    @Override
    protected String value(Minecraft mc) {
        return null; // custom-drawn; see drawLocal
    }

    private int gridColH() {
        return (ROWS - 1) * SLOT + CELL + (hotbar.get() ? GAP + CELL : 0);
    }

    /** Number of slots in the right column: pet (1) + equipment (4), whichever are enabled. */
    private int rightCount() {
        return (pet.get() ? 1 : 0) + (equipment.get() ? 4 : 0);
    }

    private int rightColH() {
        int n = rightCount();
        return n > 0 ? (n - 1) * SLOT + CELL : 0;
    }

    /** Height of the main content row (tallest of the enabled sections). */
    private int contentH() {
        int h = gridColH();
        if (armor.get()) {
            h = Math.max(h, ARMOR_H);
        }
        return Math.max(h, rightColH());
    }

    /** Width consumed by the left sections (armour + player model) before the grid. */
    private int leftW() {
        int x = 0;
        if (armor.get()) {
            x += CELL + SECTION_GAP;
        }
        if (playerModel.get()) {
            x += MODEL_W + SECTION_GAP;
        }
        return x;
    }

    /**
     * How much to scale the main storage grid so it fills the full element height. When a taller
     * section (the 4-slot armour column, or the pet + equipment column) is enabled, the 3-row grid
     * would otherwise leave empty space beneath it; scaling it up makes it fill the element.
     */
    private float gridFillScale() {
        int gh = gridColH();
        return gh > 0 ? Math.max(1f, contentH() / (float) gh) : 1f;
    }

    @Override
    public int hudWidth(Font font, Minecraft mc, boolean editor) {
        int rightW = rightCount() > 0 ? SECTION_GAP + CELL : 0;
        return PAD * 2 + leftW() + Math.round(GRID_W * gridFillScale()) + rightW;
    }

    @Override
    public int hudHeight(Minecraft mc, boolean editor) {
        return PAD * 2 + contentH();
    }

    @Override
    public boolean drawLocal(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth, Minecraft mc, boolean editor) {
        int w = hudWidth(font, mc, true);
        int h = hudHeight(mc, true);
        Inventory inv = mc.player != null ? mc.player.getInventory() : null;

        if (background.get()) {
            int bg = (0xCC << 24) | (t.surface() & 0x00FFFFFF);
            UiRender.fillRounded(g, 0, 0, w, h, 8, bg, smooth);
            UiRender.strokeRounded(g, 0, 0, w, h, 8, Theme.withAlpha(t.border(), 0.9f), smooth);
        }

        int x = PAD;

        // Armour column (helmet -> boots).
        if (armor.get()) {
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack st = mc.player != null ? mc.player.getItemBySlot(ARMOR[i]) : ItemStack.EMPTY;
                slot(g, font, t, smooth, st, x, PAD + i * SLOT, false);
            }
            x += CELL + SECTION_GAP;
        }

        // Live player model. Unlike the item slots (which draw through the current GUI pose), the
        // entity renderer takes literal screen-rect coordinates and renders in its own pass, so it
        // ignores the pose's translate/scale. Transform the model's local rect through the current
        // pose ourselves so it moves and scales with the rest of the HUD element.
        if (playerModel.get()) {
            if (mc.player != null) {
                org.joml.Matrix3x2f m = g.pose();
                org.joml.Vector2f p1 = m.transformPosition(new org.joml.Vector2f(x, PAD));
                org.joml.Vector2f p2 = m.transformPosition(new org.joml.Vector2f(x + MODEL_W, PAD + contentH()));
                int x1 = Math.round(p1.x), y1 = Math.round(p1.y);
                int x2 = Math.round(p2.x), y2 = Math.round(p2.y);
                float cx = (x1 + x2) / 2f;
                float cy = (y1 + y2) / 2f;
                try {
                    InventoryScreen.extractEntityInInventoryFollowsMouse(
                            g, x1, y1, x2, y2, (int) ((y2 - y1) * 0.55f), 0.0f, cx, cy, mc.player);
                } catch (Throwable ignored) {
                    // Entity rendering can be finicky; never let it break the HUD.
                }
            }
            x += MODEL_W + SECTION_GAP;
        }

        // Main storage (+ hotbar), scaled to fill the full element height. Drawn in a scaled pose
        // group so the whole grid grows uniformly around its top-left corner.
        float gs = gridFillScale();
        g.pose().pushMatrix();
        g.pose().translate(x, PAD);
        g.pose().scale(gs);

        // Main storage (inventory indices 9..35), top row first.
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                ItemStack st = inv != null ? inv.getItem(9 + r * COLS + c) : ItemStack.EMPTY;
                slot(g, font, t, smooth, st, c * SLOT, r * SLOT, false);
            }
        }

        // Hotbar (indices 0..8) with the selected slot highlighted.
        if (hotbar.get()) {
            int rowY = (ROWS - 1) * SLOT + CELL + GAP;
            int selected = inv != null ? inv.getSelectedSlot() : -1;
            for (int c = 0; c < COLS; c++) {
                ItemStack st = inv != null ? inv.getItem(c) : ItemStack.EMPTY;
                slot(g, font, t, smooth, st, c * SLOT, rowY, c == selected);
            }
        }
        g.pose().popMatrix();
        x += Math.round(GRID_W * gs);

        // Right column: SkyBlock pet + equipment (cached from the SkyBlock menus).
        if (rightCount() > 0) {
            int rx = x + SECTION_GAP;
            int row = 0;
            if (pet.get()) {
                slot(g, font, t, smooth, SkyblockHud.pet(), rx, PAD + row * SLOT, false);
                row++;
            }
            if (equipment.get()) {
                for (int i = 0; i < 4; i++) {
                    slot(g, font, t, smooth, SkyblockHud.equipment(i), rx, PAD + row * SLOT, false);
                    row++;
                }
            }
        }
        return true;
    }

    private void slot(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth,
                      ItemStack stack, int x, int y, boolean selected) {
        if (slotBoxes.get()) {
            UiRender.fillRounded(g, x, y, CELL, CELL, 3, Theme.withAlpha(t.textFaint(), 0.16f), smooth);
        }
        if (selected) {
            UiRender.strokeRounded(g, x - 1, y - 1, CELL + 2, CELL + 2, 4, t.accent(), smooth);
        }
        if (stack != null && !stack.isEmpty()) {
            g.item(stack, x, y);
            g.itemDecorations(font, stack, x, y);
        }
    }
}
