package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.EntityModelComponent;
import com.render.api.gui.GuiTextAlignment;
import com.render.api.gui.ItemModelComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiPositionType;
import dev.diego.diegoaddons.gui.GuiColors;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.module.modules.InventoryHudModule;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory HUD: armour, player model, equipment, the storage grid and the pet card, left to right.
 *
 * <p>Every slot is a fixed-size box holding a real {@link ItemModelComponent}, so items go through
 * Minecraft's own model pipeline - custom model data, player heads and pack textures included. The
 * item is a centred child of its slot rather than an absolutely-placed one, which is what broke the
 * first attempt: an absolute child of an unpositioned box does not land where the box is.
 *
 * <p>RenderLib's item component carries no count or durability overlay, so the stack count is drawn
 * as its own label in the corner.
 */
public class InventoryElement extends HudElement {
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final float CELL = 16f;
    private static final float SLOT_GAP = 2f;
    private static final float PAD = 5f;
    private static final float SECTION_GAP = 6f;
    private static final float HOTBAR_GAP = 6f;
    /**
     * Player-model framing, which is two separate numbers RenderLib expects in its own terms.
     *
     * <p><b>Size.</b> It renders a preview entity at {@code min(boxW, boxH) * 0.625 * zoom} pixels
     * <em>per block</em>, so the body comes out {@code that * bbHeight} tall - the height of the
     * entity is a factor, not a divisor. Solving for "the body fills {@link #MODEL_FILL} of the box"
     * therefore has to divide by {@link #PLAYER_BB_H}; leaving it out asked for a model most of two
     * box-heights tall, which is what pushed the head out of the top of the frame.
     *
     * <p><b>Position.</b> It offsets by {@code bbHeight / 2 + yPivot}, so {@code yPivot} is the
     * nudge on top of a centred entity, not the centring itself - the same {@code 0.0625} vanilla's
     * inventory preview uses. Cancelling the centring instead (a negative half-height) dropped the
     * model a full block in its box.
     */
    private static final float MODEL_ASPECT = 0.7f;      // vanilla's 49x70 inventory preview
    private static final float MODEL_FILL = 0.8f;        // share of the box height the body takes
    private static final float RENDERLIB_FIT = 0.625f;   // the constant in RenderLib's scale
    private static final float PLAYER_BB_H = 1.8f;
    private static final float PLAYER_PIVOT = 0.0625f;
    /**
     * Half a turn, so the preview faces you.
     *
     * <p>RenderLib builds the preview from a mannequin wearing your profile and aims it with
     * {@code setYRot}, which is the <em>head</em> yaw - a living entity is drawn facing its
     * {@code yBodyRot}, and a mannequin spawns at zero, so the preview stood with its back to the
     * camera (a cape being the giveaway). The whole model is rotated instead, which does not care
     * what the body rotation happens to be.
     */
    private static final float MODEL_FACING = 180f;

    // Pet card: a double-size item over the name and level. At slot size under two 7px rows it read
    // as a stray icon filling about half the column next to four rows of armour.
    private static final float PET_ITEM = 32f;
    private static final float PET_LINE = 7f;
    private static final float PET_NAME_PX = 10f;
    /**
     * The card's floor width. It grows to whatever the pet is actually called: a name too wide for
     * its label wraps onto a second line, and since the rows below it are only as tall as one line,
     * that second line landed on top of the level row - "Ender Dragon" over its own "Lvl 100".
     */
    private static final float PET_MIN_W = 64f;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private record Slot(ContainerComponent box, ItemModelComponent item, TextComponent count) {
    }

    private final InventoryHudModule inv;

    private final List<Slot> armor = new ArrayList<>();
    private final List<Slot> equipment = new ArrayList<>();
    private final List<Slot> grid = new ArrayList<>();
    private final List<Slot> hotbar = new ArrayList<>();
    private final List<ItemStack> shown = new ArrayList<>();

    private String lastShape = "";
    private int lastSelected = -1;

    public InventoryElement(InventoryHudModule module, ContainerComponent root) {
        super(module, root);
        this.inv = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        String shape = inv.sectionSignature();
        if (themeChanged() || !shape.equals(lastShape)) {
            lastShape = shape;
            rebuild();
        }
        refresh(mc);
        return true;
    }

    private void rebuild() {
        root.clearChildren();
        armor.clear();
        equipment.clear();
        grid.clear();
        hotbar.clear();
        shown.clear();
        lastSelected = -1;

        asRow(root, 0f, SECTION_GAP).autoWidth().alignItems(GuiAlignment.CENTER).padding(PAD);
        if (inv.showBackground()) {
            applyBackground(root, 8f);
        } else {
            root.backgroundColor(GuiColors.of(0x00000000)).borderWidth(0f);
        }

        // Left to right in whatever order the module was arranged in.
        for (String section : inv.sectionOrder()) {
            if (!inv.sectionShown(section)) {
                continue;
            }
            switch (section) {
                case "armor" -> root.add(slotColumn(armor, 4));
                case "equipment" -> root.add(slotColumn(equipment, 4));
                case "storage" -> root.add(storage());
                default -> {
                }
            }
        }
    }

    private ContainerComponent slotColumn(List<Slot> into, int count) {
        ContainerComponent col = column(CELL, SLOT_GAP);
        for (int i = 0; i < count; i++) {
            Slot slot = slot();
            into.add(slot);
            col.add(slot.box());
        }
        return col;
    }

    private ContainerComponent storage() {
        float cell = storageCell();
        float rowW = COLS * cell + (COLS - 1) * SLOT_GAP;
        ContainerComponent wrap = column(rowW, HOTBAR_GAP);

        ContainerComponent rows = column(rowW, SLOT_GAP);
        for (int r = 0; r < ROWS; r++) {
            ContainerComponent line = row(rowW, SLOT_GAP);
            for (int c = 0; c < COLS; c++) {
                Slot slot = slot(cell);
                grid.add(slot);
                line.add(slot.box());
            }
            rows.add(line);
        }
        wrap.add(rows);

        if (inv.showHotbar()) {
            ContainerComponent line = row(rowW, SLOT_GAP);
            for (int c = 0; c < COLS; c++) {
                Slot slot = slot(cell);
                hotbar.add(slot);
                line.add(slot.box());
            }
            wrap.add(line);
        }
        return wrap;
    }

    /** Rows the storage grid stacks: the three stash rows, plus the hotbar when it is on. */
    private int storageRows() {
        return ROWS + (inv.showHotbar() ? 1 : 0);
    }

    private float storageGaps() {
        return (ROWS - 1) * SLOT_GAP + (inv.showHotbar() ? HOTBAR_GAP : 0f);
    }

    /**
     * The slot size that makes the grid exactly as tall as the tallest section beside it - three
     * rows of storage against four of armour left the grid a head short, with the element's
     * background padding out the difference. Slots only ever grow, never shrink below {@link #CELL},
     * and since RenderLib rasterises an item at its box size they come out genuinely bigger rather
     * than stretched.
     */
    private float storageCell() {
        return Math.max(CELL, (contentHeight() - storageGaps()) / storageRows());
    }

    /**
     * The tallest section, which every other one centres against. The model used to be sized to the
     * storage grid alone, so it stayed short whenever armour or the hotbar made the element taller.
     */
    private float contentHeight() {
        float storage = ROWS * CELL + (ROWS - 1) * SLOT_GAP
                + (inv.showHotbar() ? HOTBAR_GAP + CELL : 0f);
        float h = storage;
        if (inv.showArmor() || inv.showEquipment()) {
            h = Math.max(h, 4 * CELL + 3 * SLOT_GAP);
        }
        return h;
    }

    /** One slot: a box, the item centred in it, and the count label in its corner. */
    private Slot slot() {
        return slot(CELL);
    }

    /**
     * A slot of a given size. The pet uses a larger one; RenderLib's item component rasterises at the
     * box size, so a bigger box means a genuinely bigger item rather than a stretched 16px sprite.
     */
    private Slot slot(float size) {
        Theme t = Themes.current();
        ContainerComponent box = row(size, 0f);
        box.height(size).cornerRadius(3f)
                .position(GuiPositionType.RELATIVE)     // anchors the count label
                .justifyContent(GuiAlignment.CENTER);
        if (inv.showSlotBoxes()) {
            box.backgroundColor(GuiColors.of(Theme.withAlpha(t.textFaint(), 0.16f)));
        }

        ItemModelComponent item = new ItemModelComponent();
        item.size(size, size).visible(false);
        box.add(item);

        TextComponent count = new TextComponent().font(MEDIUM).textScalePixels(6f)
                .color(GuiColors.of(0xFFFFFFFF)).width(size)
                .position(GuiPositionType.ABSOLUTE).x(size * 0.4f).y(size * 0.55f)
                .visible(false);
        box.add(count);
        return new Slot(box, item, count);
    }

    // --- per tick -----------------------------------------------------------------------------------

    private void refresh(Minecraft mc) {
        Inventory bag = mc.player.getInventory();
        int i = 0;
        for (int a = 0; a < armor.size(); a++) {
            i = put(armor.get(a), mc.player.getItemBySlot(ARMOR[a]), i);
        }
        for (int e = 0; e < equipment.size(); e++) {
            i = put(equipment.get(e), SkyblockHud.equipment(e), i);
        }
        for (int g = 0; g < grid.size(); g++) {
            i = put(grid.get(g), bag.getItem(9 + g), i);
        }
        for (int h = 0; h < hotbar.size(); h++) {
            i = put(hotbar.get(h), bag.getItem(h), i);
        }
        if (!hotbar.isEmpty()) {
            int selected = bag.getSelectedSlot();
            if (selected != lastSelected) {
                Theme t = Themes.current();
                for (int h = 0; h < hotbar.size(); h++) {
                    boolean on = h == selected;
                    hotbar.get(h).box().borderWidth(on ? 1f : 0f)
                            .borderColor(GuiColors.of(on ? t.accent() : 0x00000000));
                }
                lastSelected = selected;
            }
        }
    }

    /** Swaps a slot's stack only when it changed; returns the next tracking index. */
    private int put(Slot slot, ItemStack stack, int index) {
        ItemStack next = stack == null ? ItemStack.EMPTY : stack;
        while (shown.size() <= index) {
            shown.add(ItemStack.EMPTY);
        }
        if (ItemStack.matches(shown.get(index), next)) {
            return index + 1;
        }
        shown.set(index, next.copy());

        boolean empty = next.isEmpty();
        slot.item().visible(!empty);
        if (!empty) {
            slot.item().item(next);
        }
        boolean stacked = !empty && next.getCount() > 1;
        slot.count().visible(stacked);
        if (stacked) {
            slot.count().text(String.valueOf(next.getCount()));
        }
        return index + 1;
    }
}
