package dev.diego.diegoaddons.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;

/** A labelled row with a smooth on/off pill switch on the right. Clicking the row toggles it. */
public class UiToggle implements Widget {
    public int x, y, w, h;
    public final String label;
    private final BooleanSupplier state;
    private final Runnable onToggle;

    public UiToggle(int x, int y, int w, int h, String label, BooleanSupplier state, Runnable onToggle) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.state = state;
        this.onToggle = onToggle;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t, Font font, boolean smooth) {
        boolean hover = UiRender.inside(mouseX, mouseY, x, y, w, h);
        boolean on = state.getAsBoolean();

        UiRender.fillRounded(g, x, y, w, h, 10, hover ? t.elevated() : t.surfaceAlt(), smooth);
        UiRender.text(g, font, label, Fonts.BODY, x + 14, y + (h - Fonts.BODY_VH) / 2, t.text());

        int pillW = 30, pillH = 16;
        int pillX = x + w - pillW - 14;
        int pillY = y + (h - pillH) / 2;
        if (on) {
            UiRender.fillRoundedGradient(g, pillX, pillY, pillW, pillH, pillH / 2, t.accent(), t.accentTo(), smooth);
        } else {
            UiRender.fillRounded(g, pillX, pillY, pillW, pillH, pillH / 2, Theme.withAlpha(t.textFaint(), 0.55f), smooth);
        }
        int knobR = pillH - 4;
        int knobX = on ? (pillX + pillW - knobR - 2) : (pillX + 2);
        int knobCx = knobX + knobR / 2;
        int knobCy = pillY + 2 + knobR / 2;
        UiRender.circle(g, knobCx, knobCy, knobR / 2, on ? t.accentText() : t.text(), smooth);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && UiRender.inside(mx, my, x, y, w, h)) {
            onToggle.run();
            return true;
        }
        return false;
    }
}
