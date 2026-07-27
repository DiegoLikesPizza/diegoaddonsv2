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
    private static final int MODEL_W = 44;
    private static final int COL_H = (4 - 1) * SLOT + CELL;   // 4 stacked slots (armour / equipment)
    private static final int GRID_W = (COLS - 1) * SLOT + CELL;

    // Pet card: item on top, then the name row and the level row.
    private static final int PET_LINE_H = Fonts.SMALL_H;
    private static final int PET_GAP = 3;
    private static final int PET_H = CELL + PET_GAP + PET_LINE_H * 2 + 1;
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
        int w = Math.max(Fonts.width(font, info.name(), Fonts.SMALL), Fonts.width(font, levelLine(info), Fonts.SMALL));
        return Math.max(PET_MIN_W, w);
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

    // --- read by the RenderLib element -----------------------------------------------------------

    public boolean showBackground() {
        return background.get();
    }

    public boolean showSlotBoxes() {
        return slotBoxes.get();
    }

    public boolean showHotbar() {
        return hotbar.get();
    }

    public boolean showArmor() {
        return armor.get();
    }

    public boolean showPlayerModel() {
        return playerModel.get();
    }

    public boolean showEquipment() {
        return equipment.get();
    }

    public boolean showPet() {
        return pet.get();
    }

    /** The pet's level line, shared with the RenderLib element. */
    public String levelText(SkyblockHud.PetInfo info) {
        return levelLine(info);
    }

    /**
     * Which sections are on, as a string - the element rebuilds its tree when this changes and
     * leaves it alone otherwise.
     */
    public String sectionSignature() {
        return "" + background.get() + slotBoxes.get() + hotbar.get() + armor.get()
                + playerModel.get() + equipment.get() + pet.get();
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.InventoryElement(this, root);
    }




    /** The pet section: item centred on top, then the rarity-coloured name and the level row. */
    private void drawPet(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth, int x) {
        float s = fill(PET_H);
        int cardW = petW(font);
        g.pose().pushMatrix();
        g.pose().translate(x, PAD);
        g.pose().scale(s);

        SkyblockHud.PetInfo info = SkyblockHud.petInfo();
        slot(g, font, t, smooth, SkyblockHud.pet(), (cardW - CELL) / 2, 0, false);
        if (info == null) {
            UiRender.text(g, font, "No pet", Fonts.SMALL, 0, CELL + PET_GAP, t.textFaint());
        } else {
            UiRender.text(g, font, info.name(), Fonts.SMALL, 0, CELL + PET_GAP, info.colour());
            UiRender.text(g, font, levelLine(info), Fonts.SMALL, 0, CELL + PET_GAP + PET_LINE_H + 1, t.textMuted());
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
