package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.EntityModelComponent;
import com.render.api.gui.ItemModelComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
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
    private static final float MODEL_W = 44f;
    private static final float PET_LINE = 7f;

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
    private Slot petSlot;
    private TextComponent petName;
    private TextComponent petLevel;

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
        petSlot = null;
        lastSelected = -1;

        asRow(root, 0f, SECTION_GAP).autoWidth().alignItems(GuiAlignment.CENTER).padding(PAD);
        if (inv.showBackground()) {
            applyBackground(root, 8f);
        } else {
            root.backgroundColor(0x00000000).borderWidth(0f);
        }

        if (inv.showArmor()) {
            root.add(slotColumn(armor, 4));
        }
        if (inv.showPlayerModel()) {
            root.add(playerModel());
        }
        if (inv.showEquipment()) {
            root.add(slotColumn(equipment, 4));
        }
        root.add(storage());
        if (inv.showPet()) {
            root.add(petCard());
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
        float rowW = COLS * CELL + (COLS - 1) * SLOT_GAP;
        ContainerComponent wrap = column(rowW, HOTBAR_GAP);

        ContainerComponent rows = column(rowW, SLOT_GAP);
        for (int r = 0; r < ROWS; r++) {
            ContainerComponent line = row(rowW, SLOT_GAP);
            for (int c = 0; c < COLS; c++) {
                Slot slot = slot();
                grid.add(slot);
                line.add(slot.box());
            }
            rows.add(line);
        }
        wrap.add(rows);

        if (inv.showHotbar()) {
            ContainerComponent line = row(rowW, SLOT_GAP);
            for (int c = 0; c < COLS; c++) {
                Slot slot = slot();
                hotbar.add(slot);
                line.add(slot.box());
            }
            wrap.add(line);
        }
        return wrap;
    }

    private ContainerComponent playerModel() {
        float height = ROWS * CELL + (ROWS - 1) * SLOT_GAP;
        ContainerComponent holder = row(MODEL_W, 0f);
        holder.height(height).justifyContent(GuiAlignment.CENTER);
        EntityModelComponent model = new EntityModelComponent();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            model.playerUuid(mc.player.getUUID());
        }
        model.size(MODEL_W, height);
        holder.add(model);
        return holder;
    }

    private ContainerComponent petCard() {
        ContainerComponent card = column(56f, 2f);
        card.alignItems(GuiAlignment.CENTER);
        petSlot = slot();
        card.add(petSlot.box());
        petName = small("", Themes.current().text(), PET_LINE).width(56f);
        petLevel = small("", Themes.current().textMuted(), PET_LINE).width(56f);
        card.add(textRow(petName, 56f, PET_LINE + 2f));
        card.add(textRow(petLevel, 56f, PET_LINE + 2f));
        return card;
    }

    /** One slot: a box, the item centred in it, and the count label in its corner. */
    private Slot slot() {
        Theme t = Themes.current();
        ContainerComponent box = row(CELL, 0f);
        box.height(CELL).cornerRadius(3f)
                .position(GuiPositionType.RELATIVE)     // anchors the count label
                .justifyContent(GuiAlignment.CENTER);
        if (inv.showSlotBoxes()) {
            box.backgroundColor(Theme.withAlpha(t.textFaint(), 0.16f));
        }

        ItemModelComponent item = new ItemModelComponent();
        item.size(CELL, CELL).visible(false);
        box.add(item);

        TextComponent count = new TextComponent().font(MEDIUM).textScalePixels(6f)
                .color(0xFFFFFFFF).width(CELL)
                .position(GuiPositionType.ABSOLUTE).x(CELL * 0.4f).y(CELL * 0.55f)
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
        if (petSlot != null) {
            put(petSlot, SkyblockHud.pet(), i);
            SkyblockHud.PetInfo info = SkyblockHud.petInfo();
            Theme t = Themes.current();
            petName.text(info == null ? "No pet" : info.name())
                    .color(info == null ? t.textFaint() : info.colour());
            petLevel.text(info == null ? "" : inv.levelText(info)).color(t.textMuted());
        }

        if (!hotbar.isEmpty()) {
            int selected = bag.getSelectedSlot();
            if (selected != lastSelected) {
                Theme t = Themes.current();
                for (int h = 0; h < hotbar.size(); h++) {
                    boolean on = h == selected;
                    hotbar.get(h).box().borderWidth(on ? 1f : 0f)
                            .borderColor(on ? t.accent() : 0x00000000);
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
