package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.WordReplacement;
import dev.diego.diegoaddons.util.WordReplacer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the find/replace pairs. Each row can be toggled off without deleting it, so a rename can
 * be parked rather than retyped.
 */
public class ReplaceWordsScreen extends Screen {
    private static final int ROW_H = 16;
    private static final int PAD = 10;

    private final Screen parent;

    private EditBox fromBox;
    private EditBox toBox;
    private final List<UiButton> buttons = new ArrayList<>();

    private int scroll;
    private int panelX, panelY, panelW, panelH, listTop, rows;

    public ReplaceWordsScreen(Screen parent) {
        super(Component.literal("Replace Words"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buttons.clear();
        panelW = Math.min(420, width - 40);
        panelH = Math.min(240, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 52;
        rows = Math.max(1, (panelY + panelH - 34 - listTop) / ROW_H);

        int boxW = (panelW - PAD * 2 - 56 - 8) / 2;
        fromBox = new EditBox(font, panelX + PAD, panelY + 24, boxW, 16, Component.literal("From"));
        fromBox.setHint(Component.literal("Aspect of the Void"));
        fromBox.setMaxLength(80);
        toBox = new EditBox(font, panelX + PAD + boxW + 8, panelY + 24, boxW, 16, Component.literal("To"));
        toBox.setHint(Component.literal("AOTV"));
        toBox.setMaxLength(80);
        addRenderableWidget(fromBox);
        addRenderableWidget(toBox);

        UiButton add = new UiButton(panelX + panelW - PAD - 50, panelY + 24, 50, 16,
                "Add", UiButton.Kind.PRIMARY, this::addEntry);
        UiButton done = new UiButton(panelX + panelW - PAD - 50, panelY + panelH - 24, 50, 20,
                "Done", UiButton.Kind.SECONDARY, this::onClose);
        buttons.add(add);
        buttons.add(done);
    }

    private void addEntry() {
        String from = fromBox.getValue().trim();
        if (from.isEmpty()) {
            return;
        }
        WordReplacer.all().add(new WordReplacement(from, toBox.getValue()));
        fromBox.setValue("");
        toBox.setValue("");
        ConfigManager.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        g.fill(0, 0, width, height, t.overlay());

        UiRender.dropShadow(g, panelX, panelY, panelW, panelH, 10, t.shadow(), 10, 5);
        UiRender.fillRounded(g, panelX, panelY, panelW, panelH, 10, t.surface(), sm);
        UiRender.strokeRounded(g, panelX, panelY, panelW, panelH, 10, t.border(), sm);
        UiRender.text(g, font, "REPLACE WORDS", Fonts.SMALL, panelX + PAD, panelY + 8, t.textFaint());

        List<WordReplacement> list = WordReplacer.all();
        if (list.isEmpty()) {
            UiRender.text(g, font, "No replacements yet.", Fonts.SMALL,
                    panelX + PAD, listTop + 4, t.textFaint());
        }

        int end = Math.min(list.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            WordReplacement r = list.get(i);
            int ry = listTop + (i - scroll) * ROW_H;
            boolean hover = UiRender.inside(mouseX, mouseY, panelX + PAD, ry, panelW - PAD * 2, ROW_H);
            if (hover) {
                UiRender.fillRounded(g, panelX + PAD - 3, ry - 1, panelW - PAD * 2 + 6, ROW_H, 3,
                        t.surfaceAlt(), sm);
            }
            int col = r.enabled ? t.text() : t.textFaint();
            UiRender.text(g, font, r.from, Fonts.MEDIUM, panelX + PAD, ry + 3, col);
            UiRender.text(g, font, "→", Fonts.SMALL, panelX + panelW / 2 - 20, ry + 4, t.textFaint());
            UiRender.text(g, font, r.to, Fonts.MEDIUM, panelX + panelW / 2, ry + 3,
                    r.enabled ? t.accent() : t.textFaint());
            if (hover) {
                UiRender.textRight(g, font, "remove", Fonts.SMALL,
                        panelX + panelW - PAD, ry + 4, t.accent());
            }
        }

        UiRender.text(g, font, "Click a row to toggle it, the remove label to delete.", Fonts.SMALL,
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
            List<WordReplacement> list = WordReplacer.all();
            int end = Math.min(list.size(), scroll + rows);
            for (int i = scroll; i < end; i++) {
                int ry = listTop + (i - scroll) * ROW_H;
                if (!UiRender.inside(mx, my, panelX + PAD, ry, panelW - PAD * 2, ROW_H)) {
                    continue;
                }
                if (mx > panelX + panelW - PAD - 40) {
                    list.remove(i);
                } else {
                    list.get(i).enabled = !list.get(i).enabled;
                }
                ConfigManager.save();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, WordReplacer.all().size() - rows);
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
