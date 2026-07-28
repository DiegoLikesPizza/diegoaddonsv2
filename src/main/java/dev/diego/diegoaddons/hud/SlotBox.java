package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ItemModelComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiPositionType;
import dev.diego.diegoaddons.gui.GuiColors;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import net.minecraft.world.item.ItemStack;

/**
 * One inventory slot: a box, a real item model centred in it, and the stack count in its corner.
 *
 * <p>Shared by the inventory and player HUDs, which draw the same slot for different reasons - one
 * for the grid, one for the armour and equipment columns beside the body wearing them.
 *
 * <p>The item is a centred <em>child</em> of the box rather than absolutely placed inside it: an
 * absolute child of an unpositioned box does not land where the box is. RenderLib's item component
 * carries no count or durability overlay, so the count is its own label.
 */
public record SlotBox(ContainerComponent box, ItemModelComponent item, TextComponent count) {

    /** Builds a slot of the given size. Bigger boxes mean genuinely bigger items, not stretched ones. */
    public static SlotBox build(float size, boolean background) {
        Theme t = Themes.current();
        ContainerComponent box = HudElement.row(size, 0f);
        box.height(size).cornerRadius(3f)
                .position(GuiPositionType.RELATIVE)     // anchors the count label
                .justifyContent(GuiAlignment.CENTER);
        if (background) {
            box.backgroundColor(GuiColors.of(Theme.withAlpha(t.textFaint(), 0.16f)));
        }

        ItemModelComponent item = new ItemModelComponent();
        item.size(size, size).visible(false);
        box.add(item);

        TextComponent count = new TextComponent().font(HudElement.MEDIUM).textScalePixels(6f)
                .color(GuiColors.of(0xFFFFFFFF)).width(size)
                .position(GuiPositionType.ABSOLUTE).x(size * 0.4f).y(size * 0.55f)
                .visible(false);
        box.add(count);
        return new SlotBox(box, item, count);
    }

    /** Puts a stack in the slot, or empties it. Only touches the components when it actually changed. */
    public void show(ItemStack stack, ItemStack previous) {
        ItemStack next = stack == null ? ItemStack.EMPTY : stack;
        boolean empty = next.isEmpty();
        item.visible(!empty);
        if (!empty) {
            item.item(next);
        }
        boolean stacked = !empty && next.getCount() > 1;
        count.visible(stacked);
        if (stacked) {
            count.text(String.valueOf(next.getCount()));
        }
    }
}
