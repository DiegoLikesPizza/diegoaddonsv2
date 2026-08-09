package dev.diego.diegoaddons.module.modules;

import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.hud.HudSlots;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Your character on the HUD, on its own rather than as a column of the inventory.
 *
 * <p>Split out because the two are looked at differently: an inventory is read, a model is glanced
 * at. Tying them together meant the model could only be where the inventory was, and only as tall as
 * the grid beside it.
 */
public class PlayerHudModule extends HudModule {
    public static PlayerHudModule INSTANCE;

    private final BooleanSetting armor =
            new BooleanSetting(this, "armor", "Armor", true);
    private final BooleanSetting model =
            new BooleanSetting(this, "model", "Player model", true);
    private final BooleanSetting equipment =
            new BooleanSetting(this, "equipment", "Equipment (SkyBlock)", true);
    private final BooleanSetting slotBoxes =
            new BooleanSetting(this, "slots", "Slot boxes", true);
    // Section order is not a Setting: it is declared straight to configlib as an OrderOption,
    // which owns the screen that arranges it. See ListSpecs.

    public PlayerHudModule() {
        super("playerhud", "Player HUD", "Your armour, your character and your equipment.", false);
        settings.add(armor);
        settings.add(model);
        settings.add(equipment);
        settings.add(slotBoxes);
        INSTANCE = this;
    }

    public boolean showSlotBoxes() {
        return slotBoxes.get();
    }

    // --- sections ---------------------------------------------------------------------------------

    /** Left to right, unless you rearrange them. */
    public static final List<String> SECTIONS = List.of("armor", "player", "equipment");

    public static String sectionName(String section) {
        return switch (section) {
            case "armor" -> "Armor";
            case "player" -> "Player model";
            case "equipment" -> "Equipment";
            default -> section;
        };
    }

    public boolean sectionShown(String section) {
        return switch (section) {
            case "armor" -> armor.get();
            case "player" -> model.get();
            case "equipment" -> equipment.get();
            default -> false;
        };
    }

    /**
     * The saved order, as one comma-separated string.
     *
     * <p>Kept as a string rather than a list because that is what it has always been on disk, and
     * it is what {@link #sectionOrder()} already parsed. It is declared to configlib as a hidden
     * value in {@code ModuleSpec}; the order screen edits it through the two methods below.
     */
    private String savedOrder = "";

    /** The raw stored order, for the config layer. */
    public String savedOrder() {
        return savedOrder;
    }

    public void setSavedOrder(String value) {
        savedOrder = value == null ? "" : value;
    }

    /** Read back defensively: anything unknown is dropped, anything missing is appended. */
    public List<String> sectionOrder() {
        List<String> out = new ArrayList<>();
        for (String part : savedOrder.split(",")) {
            String v = part.trim();
            if (SECTIONS.contains(v) && !out.contains(v)) {
                out.add(v);
            }
        }
        for (String v : SECTIONS) {
            if (!out.contains(v)) {
                out.add(v);
            }
        }
        return out;
    }

    public void setSectionOrder(List<String> order) {
        savedOrder = String.join(",", order);
        ConfigManager.save();
    }

    /** What the element rebuilds on. */
    public String sectionSignature() {
        return "" + armor.get() + model.get() + equipment.get()
                + slotBoxes.get() + showBackground() + String.join(",", sectionOrder());
    }

    /**
     * How tall the element draws before the HUD editor's own scale is applied.
     *
     * <p>Fixed rather than a setting. It was one, and it was a second size control: the editor
     * already scales every element with the scroll wheel, so two knobs multiplied into each other
     * and neither on its own said how big the thing would be.
     */
    public float height() {
        return 70f;
    }

    /** The plate is the shared "Background plate" appearance row now, not a toggle of its own. */
    public boolean showBackground() {
        return style().plate();
    }

    /** Two columns of item models either side of a player model; there is no text in it. */
    @Override
    public boolean hasStyledText() {
        return false;
    }

    @Override
    protected String label() {
        return "Player";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // drawn by its own element
    }

    // --- the HUD element ------------------------------------------------------------------------

    private static final int PAD_X = 8;
    private static final int PAD_Y = 5;
    /** Between the armour, the model and the equipment. */
    private static final int SECTION_GAP = 6;
    /** Vanilla's inventory preview is 49x70, and the model is framed to match. */
    private static final float MODEL_ASPECT = 0.7f;
    /** Vanilla draws a 70px-tall preview at scale 30; the model follows that ratio at any height. */
    private static final float MODEL_SCALE = 30f / 70f;
    /** The same nudge vanilla's inventory preview uses to sit the body in its frame. */
    private static final float MODEL_PIVOT = 0.0625f;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** The slot size that makes a column of four exactly as tall as the model beside it. */
    private int cell() {
        return Math.max(8, (Math.round(height()) - 3 * HudSlots.GAP) / 4);
    }

    private int modelWidth() {
        return Math.round(height() * MODEL_ASPECT);
    }

    /** How wide a section draws, or 0 when it is switched off. */
    private int sectionWidth(String section) {
        if (!sectionShown(section)) {
            return 0;
        }
        return "player".equals(section) ? modelWidth() : cell();
    }

    /**
     * You and what you are wearing: the armour column, your character, and the SkyBlock equipment
     * column, in whichever order you arranged them.
     *
     * <p>The model is the game's own inventory preview rather than a mannequin, so it is genuinely
     * you - armour, cape and skin included - and it faces forward without any of the body-versus-head
     * rotation trouble the old element had to work around.
     */
    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                int w = 0;
                int shown = 0;
                for (String section : sectionOrder()) {
                    int sw = sectionWidth(section);
                    if (sw > 0) {
                        w += sw;
                        shown++;
                    }
                }
                if (shown == 0) {
                    return 1;
                }
                return PAD_X * 2 + w + (shown - 1) * SECTION_GAP;
            }

            @Override
            public int height() {
                return PAD_Y * 2 + Math.round(PlayerHudModule.this.height());
            }

            @Override
            public boolean shouldRender() {
                return Minecraft.getInstance().player != null;
            }

            @Override
            public void render(GuiGraphicsExtractor g) {
                paint(g);
            }

            /**
             * The model needs a real player to draw, so with none the columns draw alone. That is
             * enough to place the element, and the editor is the one place it will not be missed.
             */
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
        int h = Math.round(height());
        int cell = cell();

        int inner = 0;
        int shown = 0;
        for (String section : sectionOrder()) {
            int sw = sectionWidth(section);
            if (sw > 0) {
                inner += sw;
                shown++;
            }
        }
        if (shown == 0) {
            return;
        }
        int w = PAD_X * 2 + inner + (shown - 1) * SECTION_GAP;

        dev.diego.diegoaddons.hud.HudElements.panel(g, this, w, PAD_Y * 2 + h, 8, smooth);

        int x = PAD_X;
        for (String section : sectionOrder()) {
            int sw = sectionWidth(section);
            if (sw == 0) {
                continue;
            }
            switch (section) {
                case "armor" -> column(g, font, x, cell, smooth, true);
                case "equipment" -> column(g, font, x, cell, smooth, false);
                case "player" -> model(g, mc, x, sw, h);
                default -> {
                }
            }
            x += sw + SECTION_GAP;
        }
    }

    /** Four slots down, either the worn armour or the SkyBlock equipment set. */
    private void column(GuiGraphicsExtractor g, Font font, int x, int cell, boolean smooth,
                        boolean armour) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 4; i++) {
            int y = PAD_Y + i * (cell + HudSlots.GAP);
            if (showSlotBoxes()) {
                HudSlots.plate(g, x, y, cell, smooth);
            }
            ItemStack stack = armour ? armour(mc, i) : SkyblockHud.equipment(i);
            HudSlots.item(g, font, stack, x, y, cell);
        }
    }

    /**
     * One armour slot: what you are actually wearing, or what the Loadouts menu last said.
     *
     * <p>The live inventory wins whenever it has anything at all, because it is the truth and the
     * menu's copy is a snapshot. The fallback is for the case the inventory cannot answer - every
     * slot empty while SkyBlock has you in full gear, which is what a lobby or a not-yet-synced
     * profile looks like. Falling back per empty slot instead would leave a helmet you took off
     * sitting on the HUD for the rest of the session.
     */
    private static ItemStack armour(Minecraft mc, int i) {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        for (EquipmentSlot slot : ARMOR) {
            if (!mc.player.getItemBySlot(slot).isEmpty()) {
                return mc.player.getItemBySlot(ARMOR[i]);
            }
        }
        return SkyblockHud.armour(i);
    }

    /**
     * The player preview.
     *
     * <p>Unlike everything else here, this does <b>not</b> draw in the element's local space. The
     * game's inventory preview submits the entity to its own render pass and takes the frame as
     * screen coordinates, so the pose configlib has already translated and scaled is not applied to
     * it - handing it local coordinates put the model in the top-left corner of the screen while the
     * rest of the element sat where it belonged.
     *
     * <p>So the pose is read back and applied by hand. {@code Matrix3x2f} keeps the translation in
     * {@code m20}/{@code m21} and the scale on the diagonal, which is all a HUD element's transform
     * ever is - there is no rotation to worry about.
     */
    private void model(GuiGraphicsExtractor g, Minecraft mc, int x, int w, int h) {
        if (mc.player == null) {
            return;
        }
        var pose = g.pose();
        float scale = pose.m00();
        int sx = Math.round(pose.m20() + x * scale);
        int sy = Math.round(pose.m21() + PAD_Y * scale);
        int sw = Math.round(w * scale);
        int sh = Math.round(h * scale);
        drawModel(g, mc, sx, sy, sx + sw, sy + sh, Math.round(sh * MODEL_SCALE));
    }

    private void drawModel(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int x2, int y2,
                           int size) {
        // The mouse arguments are what aim the preview: vanilla turns it by the offset from the
        // frame's centre, so handing it the centre itself is how it comes out looking straight at
        // you rather than tracking a cursor that is not there.
        float cx = (x + x2) / 2f;
        float cy = (y + y2) / 2f;
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x, y, x2, y2,
                Math.max(1, size), MODEL_PIVOT, cx, cy, mc.player);
    }
}
