package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.EntityModelComponent;
import com.render.api.gui.ItemModelComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import com.render.api.gui.layout.GuiPositionType;
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
 * The Inventory HUD element: armour, player model, equipment, the storage grid and the pet card,
 * laid out left to right and each scaled to the tallest section's height.
 *
 * <p>Items are real {@link ItemModelComponent}s, so they go through Minecraft's own model pipeline -
 * custom model data, player heads and pack textures all render as they do in a container. The stock
 * count/durability overlays are not part of that component, so the stack count is drawn as its own
 * corner label.
 *
 * <p>Slots are built once per layout change and only their stacks are swapped afterwards, compared
 * with {@link ItemStack#matches} so an untouched inventory costs nothing.
 */
public class InventoryChip extends HudChip {
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final float SLOT = 18f;
    private static final float CELL = 16f;
    private static final float PAD = 5f;
    private static final float GAP = 6f;
    private static final float SECTION_GAP = 6f;
    private static final float MODEL_W = 44f;
    private static final float COL_H = 3 * SLOT + CELL;
    private static final float GRID_W = (COLS - 1) * SLOT + CELL;
    private static final float PET_LINE = 7f;
    private static final float PET_GAP = 3f;
    private static final float PET_H = CELL + PET_GAP + PET_LINE * 2f + 1f;
    private static final float PET_W = 54f;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** One built slot: the box, the item inside it and the count label. */
    private record Slot(ContainerComponent box, ItemModelComponent model, TextComponent count) {
    }

    private final InventoryHudModule inv;

    private final List<Slot> armorSlots = new ArrayList<>();
    private final List<Slot> equipmentSlots = new ArrayList<>();
    private final List<Slot> gridSlots = new ArrayList<>();
    private final List<Slot> hotbarSlots = new ArrayList<>();
    private final List<ItemStack> lastStacks = new ArrayList<>();
    private Slot petSlot;
    private TextComponent petName;
    private TextComponent petLevel;

    private String lastShape = "";
    private String lastTheme = "";
    private int lastSelected = -1;

    public InventoryChip(InventoryHudModule module, ContainerComponent root) {
        super(module, root);
        this.inv = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        String shape = inv.sectionSignature();
        String theme = Themes.current().name();
        if (!shape.equals(lastShape) || !theme.equals(lastTheme)) {
            lastShape = shape;
            lastTheme = theme;
            rebuild();
        }
        refresh(mc);
        return true;
    }

    // --- construction -----------------------------------------------------------------------------

    private void rebuild() {
        root.clearChildren();
        armorSlots.clear();
        equipmentSlots.clear();
        gridSlots.clear();
        hotbarSlots.clear();
        lastStacks.clear();
        petSlot = null;
        petName = null;
        petLevel = null;
        lastSelected = -1;

        float contentH = contentH();
        root.display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.ROW)
                .alignItems(GuiAlignment.START)
                .columnGap(GuiLength.pixels(SECTION_GAP))
                .gap(SECTION_GAP)
                .padding(PAD)
                .cornerRadius(8f);
        if (inv.showBackground()) {
            applyTheme();
        } else {
            root.backgroundColor(0x00000000).borderWidth(0f);
        }

        if (inv.showArmor()) {
            root.add(column(armorSlots, 4, contentH / COL_H));
        }
        if (inv.showPlayerModel()) {
            root.add(playerModel(contentH));
        }
        if (inv.showEquipment()) {
            root.add(column(equipmentSlots, 4, contentH / COL_H));
        }
        root.add(grid(contentH / gridH()));
        if (inv.showPet()) {
            root.add(petCard(contentH / PET_H));
        }
    }

    /** A vertical run of slots (armour or SkyBlock equipment), scaled to fill the element. */
    private ContainerComponent column(List<Slot> into, int count, float scale) {
        ContainerComponent col = flow(GuiFlexDirection.COLUMN, (SLOT - CELL) * scale);
        col.width(CELL * scale);
        for (int i = 0; i < count; i++) {
            Slot slot = slot(CELL * scale);
            into.add(slot);
            col.add(slot.box());
        }
        return col;
    }

    private ContainerComponent grid(float scale) {
        ContainerComponent wrap = flow(GuiFlexDirection.COLUMN, GAP * scale);
        wrap.width(GRID_W * scale);

        ContainerComponent storage = flow(GuiFlexDirection.COLUMN, (SLOT - CELL) * scale);
        for (int r = 0; r < ROWS; r++) {
            ContainerComponent row = flow(GuiFlexDirection.ROW, (SLOT - CELL) * scale);
            for (int c = 0; c < COLS; c++) {
                Slot slot = slot(CELL * scale);
                gridSlots.add(slot);
                row.add(slot.box());
            }
            storage.add(row);
        }
        wrap.add(storage);

        if (inv.showHotbar()) {
            ContainerComponent row = flow(GuiFlexDirection.ROW, (SLOT - CELL) * scale);
            for (int c = 0; c < COLS; c++) {
                Slot slot = slot(CELL * scale);
                hotbarSlots.add(slot);
                row.add(slot.box());
            }
            wrap.add(row);
        }
        return wrap;
    }

    private ContainerComponent playerModel(float contentH) {
        ContainerComponent holder = new ContainerComponent();
        holder.size(MODEL_W, contentH);
        EntityModelComponent model = new EntityModelComponent();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            model.playerUuid(mc.player.getUUID());
        }
        model.size(MODEL_W, contentH);
        holder.add(model);
        return holder;
    }

    private ContainerComponent petCard(float scale) {
        ContainerComponent card = flow(GuiFlexDirection.COLUMN, PET_GAP * scale);
        card.width(PET_W * scale).alignItems(GuiAlignment.CENTER);

        petSlot = slot(CELL * scale);
        card.add(petSlot.box());

        petName = new TextComponent().font(HudText.SMALL).textScalePixels(PET_LINE * scale)
                .width(PET_W * scale);
        petLevel = new TextComponent().font(HudText.SMALL).textScalePixels(PET_LINE * scale)
                .width(PET_W * scale);
        card.add(petName);
        card.add(petLevel);
        return card;
    }

    private Slot slot(float size) {
        Theme t = Themes.current();
        ContainerComponent box = new ContainerComponent();
        box.size(size, size).cornerRadius(3f)
                .position(GuiPositionType.RELATIVE)   // anchors the absolute count label below
                .display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.ROW)
                .alignItems(GuiAlignment.CENTER)
                .justifyContent(GuiAlignment.CENTER);
        if (inv.showSlotBoxes()) {
            box.backgroundColor(Theme.withAlpha(t.textFaint(), 0.16f));
        }

        ItemModelComponent model = new ItemModelComponent();
        model.size(size, size).visible(false);
        box.add(model);

        TextComponent count = new TextComponent().font(HudText.MEDIUM)
                .textScalePixels(Math.max(5f, size * 0.5f))
                .color(0xFFFFFFFF)
                .position(GuiPositionType.ABSOLUTE)
                .x(size * 0.35f).y(size * 0.5f)
                .width(size)
                .visible(false);
        box.add(count);
        return new Slot(box, model, count);
    }

    private static ContainerComponent flow(GuiFlexDirection direction, float gap) {
        ContainerComponent c = new ContainerComponent();
        c.display(GuiDisplay.FLEX).flexDirection(direction).alignItems(GuiAlignment.START);
        if (direction == GuiFlexDirection.ROW) {
            c.columnGap(GuiLength.pixels(gap));
        } else {
            c.rowGap(GuiLength.pixels(gap));
        }
        c.gap(gap);
        return c;
    }

    // --- per-tick refresh -------------------------------------------------------------------------

    private void refresh(Minecraft mc) {
        Inventory bag = mc.player.getInventory();
        int index = 0;

        for (int i = 0; i < armorSlots.size(); i++) {
            index = put(armorSlots.get(i), mc.player.getItemBySlot(ARMOR[i]), index);
        }
        for (int i = 0; i < equipmentSlots.size(); i++) {
            index = put(equipmentSlots.get(i), SkyblockHud.equipment(i), index);
        }
        for (int i = 0; i < gridSlots.size(); i++) {
            index = put(gridSlots.get(i), bag.getItem(9 + i), index);
        }
        for (int i = 0; i < hotbarSlots.size(); i++) {
            index = put(hotbarSlots.get(i), bag.getItem(i), index);
        }
        if (petSlot != null) {
            index = put(petSlot, SkyblockHud.pet(), index);
            SkyblockHud.PetInfo info = SkyblockHud.petInfo();
            Theme t = Themes.current();
            petName.text(info == null ? "No pet" : info.name())
                    .color(info == null ? t.textFaint() : info.colour());
            petLevel.text(info == null ? "" : inv.levelText(info)).color(t.textMuted());
        }

        if (inv.showHotbar() && !hotbarSlots.isEmpty()) {
            int selected = bag.getSelectedSlot();
            if (selected != lastSelected) {
                Theme t = Themes.current();
                for (int i = 0; i < hotbarSlots.size(); i++) {
                    ContainerComponent box = hotbarSlots.get(i).box();
                    boolean on = i == selected;
                    box.borderWidth(on ? 1f : 0f).borderColor(on ? t.accent() : 0x00000000);
                }
                lastSelected = selected;
            }
        }
    }

    /** Swaps a slot's stack only when it actually changed; returns the next change-tracking index. */
    private int put(Slot slot, ItemStack stack, int index) {
        ItemStack shown = stack == null ? ItemStack.EMPTY : stack;
        while (lastStacks.size() <= index) {
            lastStacks.add(ItemStack.EMPTY);
        }
        if (ItemStack.matches(lastStacks.get(index), shown)) {
            return index + 1;
        }
        lastStacks.set(index, shown.copy());

        boolean empty = shown.isEmpty();
        slot.model().visible(!empty);
        if (!empty) {
            slot.model().item(shown);
        }
        boolean stacked = !empty && shown.getCount() > 1;
        slot.count().visible(stacked);
        if (stacked) {
            slot.count().text(String.valueOf(shown.getCount()));
        }
        return index + 1;
    }

    // --- geometry ---------------------------------------------------------------------------------

    private float gridH() {
        return (ROWS - 1) * SLOT + CELL + (inv.showHotbar() ? GAP + CELL : 0f);
    }

    private float contentH() {
        float h = gridH();
        if (inv.showArmor() || inv.showEquipment()) {
            h = Math.max(h, COL_H);
        }
        if (inv.showPet()) {
            h = Math.max(h, PET_H);
        }
        return h;
    }
}
