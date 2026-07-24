package dev.diego.diegoaddons.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Minimal self-contained widget: it draws itself and handles its own left-click hit-testing. */
public interface Widget {
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme theme, Font font, boolean smooth);

    /** @return true if this widget consumed the click. */
    boolean mouseClicked(double mx, double my, int button);
}
