package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Fonts;
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
 * see your inventory without opening it.
 *
 * <p>The element is built from optional <b>sections</b>, always laid out left to right in this
 * order: <b>armour, player model, equipment, inventory, pet</b>. Every section declares its natural
 * size and is then <b>scaled to fill the element's full height</b> (see {@link #fill(int)}), so no
 * section is left as a short stub next to a taller one - whichever section is naturally tallest sets
 * the height and the rest grow to match.
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
    private static final int SECTION_GAP = 6; // gap between sections
    private static final int COL_H = (4 - 1) * SLOT + CELL;   // 4 stacked slots (armour / equipment)
    private static final int GRID_W = (COLS - 1) * SLOT + CELL;

    /**
     * Vanilla draws the inventory's player model into a 49x70 rect at entity scale 30 with a 0.0625
     * vertical nudge. Those three numbers are a set - the scale is what fits a 1.8-block body inside
     * that rect - so the model is framed by reproducing the ratios rather than by picking a scale:
     * too large a scale and the body overflows upward, leaving only the legs inside the element,
     * which is exactly what a flat 0.55 factor was doing here.
     */
    private static final float MODEL_ASPECT = 49f / 70f;
    private static final float MODEL_SCALE = 30f / 70f;
    private static final float MODEL_Y_NUDGE = 0.0625f;

    // Pet card: item on top, then the name row and the level row. The item is drawn at double slot
    // size - at a plain 16px it read as a stray icon next to four rows of armour.
    private static final int PET_ITEM = CELL * 2;
    private static final int PET_NAME_H = Fonts.BODY_H;
    private static final int PET_LINE_H = Fonts.SMALL_H;
    private static final int PET_GAP = 3;
    private static final int PET_H = PET_ITEM + PET_GAP + PET_NAME_H + PET_LINE_H + 1;
    private static final int PET_MIN_W = 54;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final BooleanSetting background = new BooleanSetting(this, "background", "Background", true);
    private final BooleanSetting slotBoxes = new BooleanSetting(this, "slots", "Slot boxes", true);
    private final BooleanSetting hotbar = new BooleanSetting(this, "hotbar", "Show hotbar", false);
    private final BooleanSetting armor = new BooleanSetting(this, "armor", "Armor", false);
    private final BooleanSetting playerModel = new BooleanSetting(this, "player", "Player model", false);
    private final BooleanSetting equipment = new BooleanSetting(this, "equipment", "Equipment (SkyBlock)", false);
    private final BooleanSetting pet = new BooleanSetting(this, "pet", "Pet (SkyBlock)", false);
    private final BooleanSetting debugScan = new BooleanSetting(this, "debug", "Debug scan (log)", false);

    public InventoryHudModule() {
        super("inventory", "Inventory HUD", "Shows your inventory as a grid of item slots.", false);
        settings.add(background);
        settings.add(slotBoxes);
        settings.add(hotbar);
        settings.add(armor);
        settings.add(playerModel);
        settings.add(equipment);
        settings.add(pet);
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

    // --- Natural (unscaled) section sizes -------------------------------------------------------

    private int gridH() {
        return (ROWS - 1) * SLOT + CELL + (hotbar.get() ? GAP + CELL : 0);
    }

    private int petW(Font font) {
        SkyblockHud.PetInfo info = SkyblockHud.petInfo();
        if (info == null) {
            return PET_MIN_W;
        }
        int w = Math.max(Fonts.width(font, info.name(), Fonts.BODY), Fonts.width(font, levelLine(info), Fonts.SMALL));
        return Math.max(Math.max(PET_MIN_W, PET_ITEM), w);
    }

    private static String levelLine(SkyblockHud.PetInfo info) {
        String lvl = info.level() >= 0 ? "Lvl " + info.level() : "Lvl ?";
        return info.xp() == null ? lvl : lvl + "  " + info.xp();
    }

    /**
     * Height of the element's content: the tallest enabled section. Every other section is scaled up
     * to this, so the element has no half-empty columns.
     */
    private int contentH() {
        int h = gridH();
        if (armor.get() || equipment.get()) {
            h = Math.max(h, COL_H);
        }
        if (pet.get()) {
            h = Math.max(h, PET_H);
        }
        return h;
    }

    /** The factor that makes a section of natural height {@code naturalH} fill the element. */
    private float fill(int naturalH) {
        return naturalH > 0 ? contentH() / (float) naturalH : 1f;
    }

    /** Width the player model needs to frame the body at the element's height. */
    private int modelW() {
        return Math.max(1, Math.round(contentH() * MODEL_ASPECT));
    }

    /**
     * Top offset that centres a section of natural height {@code naturalH} once scaled by
     * {@code scale}. Sections are meant to fill the element exactly, but rounding (and any section
     * whose scale is pinned) can leave a few pixels over - splitting them beats letting the whole
     * remainder pile up under the shortest column.
     */
    private int centreTop(int naturalH, float scale) {
        return PAD + Math.max(0, Math.round((contentH() - naturalH * scale) / 2f));
    }

    @Override
    public int hudWidth(Font font, Minecraft mc, boolean editor) {
        int w = 0;
        if (armor.get()) {
            w += Math.round(CELL * fill(COL_H)) + SECTION_GAP;
        }
        if (playerModel.get()) {
            w += modelW() + SECTION_GAP;   // the model is drawn at the element height already
        }
        if (equipment.get()) {
            w += Math.round(CELL * fill(COL_H)) + SECTION_GAP;
        }
        w += Math.round(GRID_W * fill(gridH())) + SECTION_GAP;
        if (pet.get()) {
            w += Math.round(petW(font) * fill(PET_H)) + SECTION_GAP;
        }
        return PAD * 2 + w - SECTION_GAP;   // no trailing gap after the last section
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

        // 1. Armour column (helmet -> boots).
        if (armor.get()) {
            float s = fill(COL_H);
            g.pose().pushMatrix();
            g.pose().translate(x, centreTop(COL_H, s));
            g.pose().scale(s);
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack st = mc.player != null ? mc.player.getItemBySlot(ARMOR[i]) : ItemStack.EMPTY;
                slot(g, font, t, smooth, st, 0, i * SLOT, false);
            }
            g.pose().popMatrix();
            x += Math.round(CELL * s) + SECTION_GAP;
        }

        // 2. Live player model. Unlike the item slots (which draw through the current GUI pose), the
        // entity renderer takes literal screen-rect coordinates and renders in its own pass, so it
        // ignores the pose's translate/scale. Transform the model's local rect through the current
        // pose ourselves so it moves and scales with the rest of the HUD element.
        if (playerModel.get()) {
            int mw = modelW();
            if (mc.player != null) {
                org.joml.Matrix3x2f m = g.pose();
                org.joml.Vector2f p1 = m.transformPosition(new org.joml.Vector2f(x, PAD));
                org.joml.Vector2f p2 = m.transformPosition(new org.joml.Vector2f(x + mw, PAD + contentH()));
                int x1 = Math.round(p1.x), y1 = Math.round(p1.y);
                int x2 = Math.round(p2.x), y2 = Math.round(p2.y);
                float cx = (x1 + x2) / 2f;
                float cy = (y1 + y2) / 2f;
                // Scale from the rect's height using vanilla's own ratio, so the whole body is framed
                // instead of being blown up until only the legs fall inside the rect.
                int scale = Math.max(1, Math.round((y2 - y1) * MODEL_SCALE));
                try {
                    InventoryScreen.extractEntityInInventoryFollowsMouse(
                            g, x1, y1, x2, y2, scale, MODEL_Y_NUDGE, cx, cy, mc.player);
                } catch (Throwable ignored) {
                    // Entity rendering can be finicky; never let it break the HUD.
                }
            }
            x += mw + SECTION_GAP;
        }

        // 3. SkyBlock equipment column (necklace / cloak / belt / gloves), cached from its menu.
        if (equipment.get()) {
            float s = fill(COL_H);
            g.pose().pushMatrix();
            g.pose().translate(x, centreTop(COL_H, s));
            g.pose().scale(s);
            for (int i = 0; i < 4; i++) {
                slot(g, font, t, smooth, SkyblockHud.equipment(i), 0, i * SLOT, false);
            }
            g.pose().popMatrix();
            x += Math.round(CELL * s) + SECTION_GAP;
        }

        // 4. Main storage (+ hotbar). Drawn in a scaled pose group so the whole grid grows uniformly
        // around its top-left corner.
        float gs = fill(gridH());
        g.pose().pushMatrix();
        g.pose().translate(x, centreTop(gridH(), gs));
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
        x += Math.round(GRID_W * gs) + SECTION_GAP;

        // 5. Pet card: the pet item, its name in its rarity colour, and its level + XP to the next.
        if (pet.get()) {
            drawPet(g, font, t, smooth, x);
        }
        return true;
    }

    /**
     * The pet section: the pet item on top at double slot size, then the rarity-coloured name and
     * the level row. The name is set in the body face rather than the caption face - at caption size
     * the two rows filled barely half the card, which is what made the pet read as an afterthought.
     */
    private void drawPet(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth, int x) {
        float s = fill(PET_H);
        int cardW = petW(font);
        g.pose().pushMatrix();
        g.pose().translate(x, centreTop(PET_H, s));
        g.pose().scale(s);

        SkyblockHud.PetInfo info = SkyblockHud.petInfo();

        // The item renderer always draws a 16px icon, so the pet gets its own scaled group.
        g.pose().pushMatrix();
        g.pose().translate((cardW - PET_ITEM) / 2f, 0);
        g.pose().scale(PET_ITEM / (float) CELL);
        slot(g, font, t, smooth, SkyblockHud.pet(), 0, 0, false);
        g.pose().popMatrix();

        int nameY = PET_ITEM + PET_GAP;
        if (info == null) {
            UiRender.text(g, font, "No pet", Fonts.BODY, 0, nameY, t.textFaint());
        } else {
            UiRender.text(g, font, info.name(), Fonts.BODY, 0, nameY, info.colour());
            UiRender.text(g, font, levelLine(info), Fonts.SMALL, 0, nameY + PET_NAME_H + 1, t.textMuted());
        }
        g.pose().popMatrix();
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
