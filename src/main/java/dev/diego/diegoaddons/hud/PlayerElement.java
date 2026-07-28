package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.EntityModelComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.gui.GuiColors;
import dev.diego.diegoaddons.module.modules.PlayerHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * You, and what you are wearing: the armour column, your character, and the SkyBlock equipment
 * column, in whichever order you arrange them.
 *
 * <p>These three belong together - they are all "what am I wearing" - which the inventory grid is
 * not. Keeping them with the grid meant the model was stuck at the grid's height and moved whenever
 * the grid did.
 *
 * <p>The columns are sized to the model rather than the other way round: four slots stretched to the
 * body's height, so the three read as one block instead of a tall thing beside two short ones.
 *
 * <p>The model's framing numbers are RenderLib's, and both were wrong once - the preview scales in
 * pixels <em>per block</em>, so the entity's height is a factor rather than a divisor, and
 * {@code yPivot} nudges an already-centred entity rather than doing the centring.
 */
public class PlayerElement extends HudElement {
    private static final float ASPECT = 0.7f;          // vanilla's 49x70 inventory preview
    private static final float FILL = 0.8f;            // share of the box height the body takes
    private static final float RENDERLIB_FIT = 0.625f; // the constant in RenderLib's scale
    private static final float BB_HEIGHT = 1.8f;
    private static final float PIVOT = 0.0625f;
    private static final float FACING = 180f;          // the mannequin spawns facing away
    private static final float SLOT_GAP = 2f;
    private static final float SECTION_GAP = 6f;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final PlayerHudModule player;
    private final List<SlotBox> armor = new ArrayList<>();
    private final List<SlotBox> equipment = new ArrayList<>();
    private final List<ItemStack> shown = new ArrayList<>();

    private String lastShape = "";

    public PlayerElement(PlayerHudModule module, ContainerComponent root) {
        super(module, root);
        this.player = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        String shape = player.sectionSignature();
        if (themeChanged() || !shape.equals(lastShape)) {
            lastShape = shape;
            rebuild(mc);
        }
        refresh(mc);
        return true;
    }

    private void rebuild(Minecraft mc) {
        root.clearChildren();
        armor.clear();
        equipment.clear();
        shown.clear();

        float height = player.height();
        asRow(root, 0f, SECTION_GAP).autoWidth().alignItems(GuiAlignment.CENTER).padding(PAD_Y, PAD_X);
        if (player.showBackground()) {
            applyBackground(root, 8f);
        } else {
            root.backgroundColor(GuiColors.of(0x00000000)).borderWidth(0f);
        }

        for (String section : player.sectionOrder()) {
            if (!player.sectionShown(section)) {
                continue;
            }
            switch (section) {
                case "armor" -> root.add(column(armor, height));
                case "equipment" -> root.add(column(equipment, height));
                case "player" -> root.add(model(mc, height));
                default -> {
                }
            }
        }
    }

    /** Four slots, stretched so the column is exactly as tall as the body beside it. */
    private ContainerComponent column(List<SlotBox> into, float height) {
        float cell = Math.max(8f, (height - 3f * SLOT_GAP) / 4f);
        ContainerComponent col = asColumn(new ContainerComponent(), cell, SLOT_GAP);
        for (int i = 0; i < 4; i++) {
            SlotBox slot = SlotBox.build(cell, player.showSlotBoxes());
            into.add(slot);
            col.add(slot.box());
        }
        return col;
    }

    private ContainerComponent model(Minecraft mc, float height) {
        float width = height * ASPECT;
        ContainerComponent holder = row(width, 0f);
        holder.height(height).justifyContent(GuiAlignment.CENTER);

        EntityModelComponent model = new EntityModelComponent();
        model.playerUuid(mc.player.getUUID());
        model.size(width, height);
        model.zoom(FILL * height / (RENDERLIB_FIT * BB_HEIGHT * Math.min(width, height)));
        model.yPivot(PIVOT);
        model.yRotation(FACING);
        holder.add(model);
        return holder;
    }

    /** Swaps a slot's stack only when it changed, so most ticks touch nothing. */
    private void refresh(Minecraft mc) {
        int i = 0;
        for (int a = 0; a < armor.size(); a++) {
            i = put(armor.get(a), mc.player.getItemBySlot(ARMOR[a]), i);
        }
        for (int e = 0; e < equipment.size(); e++) {
            i = put(equipment.get(e), dev.diego.diegoaddons.util.SkyblockHud.equipment(e), i);
        }
    }

    private int put(SlotBox slot, ItemStack stack, int index) {
        ItemStack next = stack == null ? ItemStack.EMPTY : stack;
        while (shown.size() <= index) {
            shown.add(ItemStack.EMPTY);
        }
        if (ItemStack.matches(shown.get(index), next)) {
            return index + 1;
        }
        shown.set(index, next.copy());
        slot.show(next, null);
        return index + 1;
    }
}
