package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.BlockedPlayer;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.util.IgnoreList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the block list: who is blocked, why, and a row to add someone.
 *
 * <p>Editing a reason in place would need a text field per row, so a row is instead loaded into the
 * add fields when clicked - saving over the same name updates it rather than duplicating.
 */
public class BlockedPlayersScreen extends Screen {
    private static final int ROW_H = 16;
    private static final int PAD = 10;

    private final Screen parent;

    private EditBox nameBox;
    private EditBox reasonBox;
    private final List<UiButton> buttons = new ArrayList<>();

    private int scroll;
    private int panelX, panelY, panelW, panelH, listTop, rows;

    public BlockedPlayersScreen(Screen parent) {
        super(Component.literal("Blocked Players"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buttons.clear();
        panelW = Math.min(400, width - 40);
        panelH = Math.min(240, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 52;
        rows = Math.max(1, (panelY + panelH - 34 - listTop) / ROW_H);

        nameBox = new EditBox(font, panelX + PAD, panelY + 24, 100, 16, Component.literal("Name"));
        nameBox.setHint(Component.literal("Player"));
        nameBox.setMaxLength(16);

        reasonBox = new EditBox(font, panelX + PAD + 106, panelY + 24, panelW - PAD * 2 - 106 - 56, 16,
                Component.literal("Reason"));
        reasonBox.setHint(Component.literal("Reason"));
        reasonBox.setMaxLength(80);
        addRenderableWidget(nameBox);
        addRenderableWidget(reasonBox);

        UiButton add = new UiButton(panelX + panelW - PAD - 50, panelY + 24, 50, 16,
                "Block", UiButton.Kind.PRIMARY, this::addEntry);
        UiButton done = new UiButton(panelX + panelW - PAD - 50, panelY + panelH - 24, 50, 20,
                "Done", UiButton.Kind.SECONDARY, this::onClose);
        buttons.add(add);
        buttons.add(done);
    }

    private void addEntry() {
        if (IgnoreList.block(nameBox.getValue().trim(), reasonBox.getValue().trim())) {
            nameBox.setValue("");
            reasonBox.setValue("");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        g.fill(0, 0, width, height, t.overlay());

        UiRender.dropShadow(g, panelX, panelY, panelW, panelH, 10, t.shadow(), 10, 5);
        UiRender.fillRounded(g, panelX, panelY, panelW, panelH, 10, t.surface(), sm);
        UiRender.strokeRounded(g, panelX, panelY, panelW, panelH, 10, t.border(), sm);
        UiRender.text(g, font, "BLOCKED PLAYERS", Fonts.SMALL, panelX + PAD, panelY + 8, t.textFaint());

        List<BlockedPlayer> list = IgnoreList.all();
        if (list.isEmpty()) {
            UiRender.text(g, font, "Nobody blocked yet.", Fonts.SMALL,
                    panelX + PAD, listTop + 4, t.textFaint());
        }

        int end = Math.min(list.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            BlockedPlayer b = list.get(i);
            int ry = listTop + (i - scroll) * ROW_H;
            boolean hover = UiRender.inside(mouseX, mouseY, panelX + PAD, ry, panelW - PAD * 2, ROW_H);
            if (hover) {
                UiRender.fillRounded(g, panelX + PAD - 3, ry - 1, panelW - PAD * 2 + 6, ROW_H, 3,
                        t.surfaceAlt(), sm);
            }
            UiRender.text(g, font, b.name, Fonts.MEDIUM, panelX + PAD, ry + 3, t.text());
            if (!b.reason.isBlank()) {
                UiRender.text(g, font, b.reason, Fonts.SMALL, panelX + PAD + 96, ry + 4, t.textMuted());
            }
            UiRender.textRight(g, font, hover ? "remove" : "", Fonts.SMALL,
                    panelX + panelW - PAD, ry + 4, t.accent());
        }

        UiRender.text(g, font, "Click a row to edit it, or the remove label to delete.", Fonts.SMALL,
                panelX + PAD, panelY + panelH - 20, t.textFaint());

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        for (UiButton b : buttons) {
            b.render(g, mouseX, mouseY, t, font, sm);
        }
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
            List<BlockedPlayer> list = IgnoreList.all();
            int end = Math.min(list.size(), scroll + rows);
            for (int i = scroll; i < end; i++) {
                int ry = listTop + (i - scroll) * ROW_H;
                if (!UiRender.inside(mx, my, panelX + PAD, ry, panelW - PAD * 2, ROW_H)) {
                    continue;
                }
                BlockedPlayer b = list.get(i);
                // The right-hand strip removes; anywhere else loads the row for editing.
                if (mx > panelX + panelW - PAD - 40) {
                    IgnoreList.unblock(b.name);
                } else {
                    nameBox.setValue(b.name);
                    reasonBox.setValue(b.reason);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, IgnoreList.all().size() - rows);
        if (max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
