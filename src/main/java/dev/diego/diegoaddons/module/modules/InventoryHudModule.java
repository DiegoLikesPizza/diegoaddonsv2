package dev.diego.diegoaddons.module.modules;

import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.hud.HudSlots;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;


/**
 * A HUD element that mirrors the player's inventory on screen as a grid of item slots, so you can
 * see your inventory without opening it.
 *
 * <p>Just the storage grid, with the hotbar as an option. Armour, your character and your equipment
 * moved to the Player HUD, where they belong - they are all "what am I wearing", which a grid of
 * everything you are carrying is not.
 *
 * <p>The drawing lives in {@link dev.diego.diegoaddons.hud.InventoryElement}, which builds it as a
 * HUD element; this class is only the settings and what they mean.
 */
public class InventoryHudModule extends HudModule {
    private final BooleanSetting slotBoxes = new BooleanSetting(this, "slots", "Slot boxes", true);
    private final BooleanSetting hotbar = new BooleanSetting(this, "hotbar", "Show hotbar", false);
    private final BooleanSetting debugScan = new BooleanSetting(this, "debug", "Debug scan (log)", false);

    public InventoryHudModule() {
        super("inventory", "Inventory HUD", "Shows your inventory as a grid of item slots.", false);
        settings.add(slotBoxes);
        settings.add(hotbar);
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
        if (!equipmentWanted() && !petWanted()) {
            return;
        }
        mc.gui.getChat().addClientSystemMessage(Component.literal(
                "§b[DiegoAddons] §fTo show your SkyBlock pet & equipment, open §eevery page§f of your "
                        + "§e(x/y) Pets §fand §e(x/y) Equipment Sets §fmenus once - the HUD reads them from there."));
        ConfigManager.get().sbHintShown = true;
        ConfigManager.save();
    }

    /** Item models and slot plates - nothing a text colour could reach. */
    @Override
    public boolean hasStyledText() {
        return false;
    }

    @Override
    protected String label() {
        return "Inventory";
    }

    @Override
    protected String value(Minecraft mc) {
        return null; // custom-drawn; see drawLocal
    }

    // --- read by the HUD element -----------------------------------------------------------

    /** The plate is the shared "Background plate" appearance row now, not a toggle of its own. */
    public boolean showBackground() {
        return style().plate();
    }

    public boolean showSlotBoxes() {
        return slotBoxes.get();
    }

    public boolean showHotbar() {
        return hotbar.get();
    }

    /** Whether anything is asking for the equipment cache - the player HUD owns it now. */
    private static boolean equipmentWanted() {
        PlayerHudModule player = PlayerHudModule.INSTANCE;
        return player != null && player.isEnabled() && player.sectionShown("equipment");
    }

    /** Whether anything is asking for the pet cache - the pet HUD is its own module now. */
    private static boolean petWanted() {
        PetHudModule pet = PetHudModule.INSTANCE;
        return pet != null && pet.isEnabled();
    }

    /**
     * Which sections are on and in what order, as a string - the element rebuilds its tree when this
     * changes and leaves it alone otherwise.
     */
    public String sectionSignature() {
        return "" + showBackground() + slotBoxes.get() + hotbar.get();
    }

    // --- the HUD element ------------------------------------------------------------------------

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int CELL = 16;
    private static final int PAD = 5;
    /** A wider gap before the hotbar row, so it reads as its own thing rather than a fourth row. */
    private static final int HOTBAR_GAP = 6;

    private static int gridWidth() {
        return COLS * CELL + (COLS - 1) * HudSlots.GAP;
    }

    private static int gridHeight(boolean hotbar) {
        int h = ROWS * CELL + (ROWS - 1) * HudSlots.GAP;
        return hotbar ? h + HOTBAR_GAP + CELL : h;
    }

    /**
     * The storage grid, and the hotbar under it when asked for.
     *
     * <p>Fixed size: nine columns of 16px slots is what an inventory is, and unlike the old element
     * there is no neighbouring column of armour for it to stretch to match - those moved to the
     * Player HUD. Slots are laid out straight rather than kept as objects, since nothing here has
     * state between frames.
     */
    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                return PAD * 2 + gridWidth();
            }

            @Override
            public int height() {
                return PAD * 2 + gridHeight(showHotbar());
            }

            @Override
            public boolean shouldRender() {
                return Minecraft.getInstance().player != null;
            }

            @Override
            public void render(GuiGraphicsExtractor g) {
                paint(g);
            }

            /** With no player the plates still draw, so the grid can be placed from anywhere. */
            @Override
            public void renderPreview(GuiGraphicsExtractor g) {
                paint(g);
            }
        };
    }

    private void paint(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        boolean smooth = ConfigManager.get().smoothCorners;
        boolean hotbar = showHotbar();

        dev.diego.diegoaddons.hud.HudElements.panel(g, this,
                PAD * 2 + gridWidth(), PAD * 2 + gridHeight(hotbar), 8, smooth);

        Inventory bag = mc.player == null ? null : mc.player.getInventory();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = PAD + c * (CELL + HudSlots.GAP);
                int y = PAD + r * (CELL + HudSlots.GAP);
                if (showSlotBoxes()) {
                    HudSlots.plate(g, x, y, CELL, smooth);
                }
                if (bag != null) {
                    // The storage rows are slots 9..35; 0..8 are the hotbar, drawn below.
                    HudSlots.item(g, font, bag.getItem(9 + r * COLS + c), x, y, CELL);
                }
            }
        }

        if (!hotbar) {
            return;
        }
        int y = PAD + ROWS * (CELL + HudSlots.GAP) - HudSlots.GAP + HOTBAR_GAP;
        int selected = bag == null ? -1 : bag.getSelectedSlot();
        for (int c = 0; c < COLS; c++) {
            int x = PAD + c * (CELL + HudSlots.GAP);
            if (showSlotBoxes()) {
                HudSlots.plate(g, x, y, CELL, smooth);
            }
            if (bag != null) {
                HudSlots.item(g, font, bag.getItem(c), x, y, CELL);
            }
            if (c == selected) {
                UiRender.strokeRounded(g, x, y, CELL, CELL, 3, Themes.current().accent(), smooth);
            }
        }
    }
}
