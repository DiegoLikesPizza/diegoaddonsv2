package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiTextAlignment;
import com.render.api.gui.ItemModelComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiPositionType;
import dev.diego.diegoaddons.gui.GuiColors;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.module.modules.PetHudModule;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * The active pet: its icon, its name in its rarity colour, and its level.
 *
 * <p>Its own element rather than a column of the inventory HUD, because a pet is something you
 * glance at and the inventory is something you read - they do not want to be in the same place, and
 * one of them being off should not move the other.
 *
 * <p>The card widens to whatever the pet is actually called. A name too wide for its label wraps,
 * and the rows below are one line tall, so a wrapped name lands on top of the level.
 */
public class PetElement extends HudElement {
    private static final float ITEM = 32f;
    private static final float NAME_PX = 10f;
    private static final float LEVEL_PX = 7f;
    private static final float MIN_W = 64f;

    private final PetHudModule pet;

    private ContainerComponent iconRow;
    private ContainerComponent nameRow;
    private ContainerComponent levelRow;
    private ItemModelComponent icon;
    private TextComponent nameLabel;
    private TextComponent levelLabel;
    private float width;
    private ItemStack shown = ItemStack.EMPTY;
    private boolean built;

    public PetElement(PetHudModule module, ContainerComponent root) {
        super(module, root);
        this.pet = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        SkyblockHud.PetInfo info = SkyblockHud.petInfo();
        if (info == null && pet.hideWithoutPet()) {
            return false;   // nothing equipped, and you asked not to be told so
        }
        if (themeChanged() || !built) {
            build();
        }
        String name = info == null ? "No pet" : info.name();
        String level = info == null ? "" : pet.levelText(info);
        fit(name, level);

        Theme t = Themes.current();
        nameLabel.text(name).color(GuiColors.of(info == null ? t.textFaint() : info.colour()));
        levelLabel.text(level).color(GuiColors.of(t.textMuted()));

        ItemStack stack = SkyblockHud.pet();
        if (!ItemStack.matches(shown, stack)) {
            shown = stack.copy();
            icon.visible(!stack.isEmpty());
            if (!stack.isEmpty()) {
                icon.item(stack);
            }
        }
        return true;
    }

    private void build() {
        built = true;
        root.clearChildren();
        width = MIN_W;
        Theme t = Themes.current();

        asColumn(root, width, 2f).padding(PAD_Y, PAD_X).alignItems(GuiAlignment.CENTER);
        if (pet.showBackground()) {
            applyBackground(root, 8f);
        } else {
            root.backgroundColor(GuiColors.of(0x00000000)).borderWidth(0f);
        }

        ContainerComponent box = row(ITEM, 0f);
        box.height(ITEM).cornerRadius(3f).position(GuiPositionType.RELATIVE)
                .justifyContent(GuiAlignment.CENTER);
        icon = new ItemModelComponent();
        icon.size(ITEM, ITEM).visible(false);
        box.add(icon);

        iconRow = row(width, 0f);
        iconRow.height(ITEM);
        iconRow.add(box);
        root.add(iconRow);

        nameLabel = text("", t.text(), NAME_PX)
                .textAlignment(GuiTextAlignment.CENTER).lineHeight(LINE_HEIGHT).width(width);
        levelLabel = small("", t.textMuted(), LEVEL_PX)
                .textAlignment(GuiTextAlignment.CENTER).lineHeight(LINE_HEIGHT).width(width);
        nameRow = textRow(nameLabel, width, NAME_PX + 2f);
        levelRow = textRow(levelLabel, width, LEVEL_PX + 2f);
        root.add(nameRow);
        root.add(levelRow);
        centreIcon();
        sizeRoot(width + PAD_X * 2f, PAD_Y * 2f + ITEM + NAME_PX + LEVEL_PX + 8f);
    }

    /** Widens the card so the name and the level each stay on one line. */
    private void fit(String name, String level) {
        float wanted = Math.max(MIN_W, Math.max(width(name, NAME_PX), width(level, LEVEL_PX)));
        if (wanted == width) {
            return;
        }
        width = wanted;
        root.width(wanted + PAD_X * 2f);
        iconRow.width(wanted);
        nameRow.width(wanted);
        levelRow.width(wanted);
        nameLabel.width(wanted);
        levelLabel.width(wanted);
        centreIcon();
        sizeRoot(wanted + PAD_X * 2f, PAD_Y * 2f + ITEM + NAME_PX + LEVEL_PX + 8f);
    }

    /**
     * Centres the icon by padding the row it sits in rather than by asking the layout to centre it -
     * two goes at alignment left it off to one side, and a width is a width.
     */
    private void centreIcon() {
        float side = Math.max(0f, (width - ITEM) / 2f);
        iconRow.padding(0f, side);
    }
}
