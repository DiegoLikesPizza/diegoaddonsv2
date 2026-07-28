package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.module.modules.PlayerHudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Arranges the player HUD's sections - armour, your character, equipment - one card per section in a
 * strip, dragged left or right into the order you want them drawn in.
 *
 * <p>The cards reorder <b>while</b> you drag rather than on release, so the strip always reads as the
 * layout you are about to get. Sections that are switched off are still shown - greyed, and labelled
 * as such - because their place in the order still matters for when you turn them back on.
 */
public class SectionOrderScreen extends Screen {
    private static final int CARD_W = 76;
    private static final int CARD_H = 58;
    private static final int GAP = 8;
    private static final int STRIDE = CARD_W + GAP;
    private static final int PAD = 12;

    private final Screen parent;
    private final PlayerHudModule module;
    private final List<String> order = new ArrayList<>();
    private final List<UiButton> buttons = new ArrayList<>();

    /** Index of the card being dragged, its live x, and the grab offset inside it. */
    private int dragging = -1;
    private int dragX;
    private int dragDX;

    private int panelX, panelY, panelW, panelH, stripX, stripY;

    public SectionOrderScreen(Screen parent, PlayerHudModule module) {
        super(Component.literal("Inventory HUD layout"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        buttons.clear();
        order.clear();
        order.addAll(module.sectionOrder());

        panelW = Math.min(width - 40, PAD * 2 + order.size() * STRIDE - GAP);
        panelH = 52 + CARD_H + 44;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        stripX = panelX + PAD;
        stripY = panelY + 46;

        buttons.add(new UiButton(panelX + panelW - PAD - 54, panelY + panelH - 30, 54, 20,
                "Done", UiButton.Kind.PRIMARY, this::onClose));
        buttons.add(new UiButton(panelX + panelW - PAD - 54 - 8 - 54, panelY + panelH - 30, 54, 20,
                "Reset", UiButton.Kind.SECONDARY, this::reset));
    }

    private void reset() {
        order.clear();
        order.addAll(PlayerHudModule.SECTIONS);
        module.setSectionOrder(order);
    }

    /** Where card {@code i} sits when it is not the one being dragged. */
    private int slotX(int i) {
        return stripX + i * STRIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        g.fill(0, 0, width, height, t.overlay());

        UiRender.dropShadow(g, panelX, panelY, panelW, panelH, 10, t.shadow(), 10, 5);
        UiRender.fillRounded(g, panelX, panelY, panelW, panelH, 10, t.surface(), sm);
        UiRender.strokeRounded(g, panelX, panelY, panelW, panelH, 10, t.border(), sm);
        UiRender.text(g, font, "SECTION ORDER", Fonts.SMALL, panelX + PAD, panelY + 10, t.textFaint());
        UiRender.text(g, font, "Drag a card to move it - left is drawn first.", Fonts.SMALL,
                panelX + PAD, panelY + 24, t.textMuted());

        for (int i = 0; i < order.size(); i++) {
            if (i == dragging) {
                continue;   // drawn last, on top of the rest
            }
            card(g, t, sm, order.get(i), slotX(i), stripY, i + 1, false,
                    UiRender.inside(mouseX, mouseY, slotX(i), stripY, CARD_W, CARD_H));
        }
        if (dragging >= 0) {
            card(g, t, sm, order.get(dragging), dragX, stripY, dragging + 1, true, true);
        }

        for (UiButton b : buttons) {
            b.render(g, mouseX, mouseY, t, font, sm);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    /** One section card: its position in the order, its name, and whether it is switched on. */
    private void card(GuiGraphicsExtractor g, Theme t, boolean sm, String section, int x, int y,
                      int position, boolean held, boolean hover) {
        boolean on = module.sectionShown(section);
        if (held) {
            UiRender.dropShadow(g, x, y, CARD_W, CARD_H, 8, t.shadow(), 8, 4);
        }
        UiRender.fillRounded(g, x, y, CARD_W, CARD_H, 8, held || hover ? t.elevated() : t.surfaceAlt(), sm);
        UiRender.strokeRounded(g, x, y, CARD_W, CARD_H, 8,
                held ? t.accent() : Theme.withAlpha(t.border(), 0.9f), sm);

        UiRender.text(g, font, String.valueOf(position), Fonts.SMALL, x + 8, y + 8,
                held ? t.accent() : t.textFaint());
        UiRender.textCentered(g, font, PlayerHudModule.sectionName(section), Fonts.MEDIUM,
                x + CARD_W / 2, y + 24, on ? t.text() : t.textFaint());
        UiRender.textCentered(g, font, on ? "on" : "off", Fonts.SMALL,
                x + CARD_W / 2, y + 40, on ? t.accent() : t.textFaint());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            for (UiButton b : buttons) {
                if (b.mouseClicked(mx, my, 0)) {
                    return true;
                }
            }
            for (int i = 0; i < order.size(); i++) {
                if (UiRender.inside(mx, my, slotX(i), stripY, CARD_W, CARD_H)) {
                    dragging = i;
                    dragX = slotX(i);
                    dragDX = (int) (mx - slotX(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging < 0) {
            return super.mouseDragged(event, dx, dy);
        }
        dragX = (int) Math.round(event.x()) - dragDX;
        // Which slot the card's own centre now sits over; moving it there keeps the strip readable.
        int target = Math.round((dragX - stripX) / (float) STRIDE);
        target = Math.max(0, Math.min(order.size() - 1, target));
        if (target != dragging) {
            order.add(target, order.remove(dragging));
            dragging = target;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging >= 0) {
            dragging = -1;
            module.setSectionOrder(order);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        module.setSectionOrder(order);
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
