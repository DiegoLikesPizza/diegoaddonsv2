package dev.diego.diegoaddons.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A themed, rounded, clickable button with three visual kinds. */
public class UiButton implements Widget {
    public enum Kind {PRIMARY, SECONDARY, GHOST}

    public int x, y, w, h;
    public String label;
    public Kind kind;
    public Runnable onClick;
    public boolean selected;
    public boolean enabled = true;
    /** When true, draw with the supersampled menu font family (for hi-res screens). */
    public boolean hiRes = false;

    public UiButton(int x, int y, int w, int h, String label, Kind kind, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.kind = kind;
        this.onClick = onClick;
    }

    private boolean hovered(int mx, int my) {
        return enabled && UiRender.inside(mx, my, x, y, w, h);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t, Font font, boolean smooth) {
        boolean hover = hovered(mouseX, mouseY);
        int radius = Math.min(hiRes ? 22 : 11, h / 2);
        int strokeW = hiRes ? UiRender.SS : 1;
        int textColor;

        if (kind == Kind.PRIMARY || (kind == Kind.SECONDARY && selected)) {
            if (hiRes) {
                UiRender.glow(g, x, y, w, h, radius, t.accent(), 10 * UiRender.SS, hover ? 0.10f : 0.07f);
            } else {
                UiRender.dropShadow(g, x, y, w, h, radius, t.shadow(), 3, 1);
            }
            int a = hover ? Theme.lighten(t.accent(), 0.08f) : t.accent();
            int b = hover ? Theme.lighten(t.accentTo(), 0.08f) : t.accentTo();
            UiRender.fillRoundedGradient(g, x, y, w, h, radius, a, b, smooth);
            textColor = t.accentText();
        } else if (kind == Kind.SECONDARY) {
            int bg = hover ? t.elevated() : t.surfaceAlt();
            UiRender.fillRounded(g, x, y, w, h, radius, bg, smooth);
            UiRender.strokeRoundedThick(g, x, y, w, h, radius, t.border(), strokeW, smooth);
            textColor = enabled ? t.text() : t.textFaint();
        } else { // GHOST
            if (hover) {
                UiRender.fillRounded(g, x, y, w, h, radius, t.surfaceAlt(), smooth);
            }
            textColor = hover ? t.text() : t.textMuted();
        }

        if (hiRes) {
            UiRender.textCenteredVC(g, font, label, Fonts.UI_LABEL, Fonts.UI_LABEL_SZ, x + w / 2, y, h, textColor);
        } else {
            UiRender.textCentered(g, font, label, Fonts.MEDIUM, x + w / 2, y + (h - Fonts.BODY_VH) / 2, textColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && enabled && UiRender.inside(mx, my, x, y, w, h)) {
            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }
}
