package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for DiegoAddons menu screens. Renders a modern "window" - a full-screen scrim, a soft
 * drop shadow, and a rounded surface card with a custom-font title/subtitle, a hairline divider, and
 * a close button - then lets subclasses fill the body and register widgets.
 *
 * <p>Like the ClickGUI, the whole window is drawn <b>supersampled</b>: a pose scaled by
 * {@code 1/}{@link UiRender#SS} with all layout in "units" (1 unit = 1/SS screen pixel), so the UI
 * is high-res rather than snapping to chunky GUI-scale pixels. Subclasses work entirely in units;
 * {@link #desiredWidth()}/{@link #desiredHeight()} are given in visual (screen) pixels for convenience.
 */
public abstract class DiegoScreen extends Screen {
    protected static final int S = UiRender.SS;
    protected static final int PAD = 34;
    protected final int radius = 26;

    protected final List<Widget> widgets = new ArrayList<>();
    protected int panelX, panelY, panelW, panelH;

    protected DiegoScreen(Component title) {
        super(title);
    }

    protected Theme theme() {
        return Themes.current();
    }

    protected boolean smooth() {
        return ConfigManager.get().smoothCorners;
    }

    /** Desired width in visual (screen) pixels. */
    protected abstract int desiredWidth();

    /** Desired height in visual (screen) pixels. */
    protected abstract int desiredHeight();

    protected abstract void layout();

    /** Optional one-line subtitle under the title. */
    protected String subtitle() {
        return null;
    }

    protected void renderBody(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }

    @Override
    protected void init() {
        widgets.clear();
        panelW = Math.min(desiredWidth() * S, width * S - 40);
        panelH = Math.min(desiredHeight() * S, height * S - 40);
        panelX = (width * S - panelW) / 2;
        panelY = (height * S - panelH) / 2;
        layout();
    }

    private int headerBottom() {
        int h = panelY + PAD + Fonts.UI_TITLE_SZ;
        if (subtitle() != null) {
            h += Fonts.UI_SMALL_SZ;
        }
        return h + 20;
    }

    protected int contentTop() {
        return headerBottom() + 8;
    }

    private int closeSz() {
        return 40;
    }

    private int closeX() {
        return panelX + panelW - PAD - closeSz();
    }

    private int closeY() {
        return panelY + PAD - 6;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        boolean sm = smooth();
        int mx = mouseX * S, my = mouseY * S;

        g.fill(0, 0, width, height, t.overlay());
        UiRender.beginHiRes(g);

        UiRender.dropShadow(g, panelX, panelY, panelW, panelH, radius, t.shadow(), 18, 10);
        UiRender.fillRounded(g, panelX, panelY, panelW, panelH, radius, t.surface(), sm);
        UiRender.strokeRoundedThick(g, panelX, panelY, panelW, panelH, radius, t.border(), S, sm);
        UiRender.fillRounded(g, panelX + radius, panelY + S, panelW - radius * 2, S, S,
                Theme.withAlpha(t.accent(), 0.5f), sm);

        // Title + optional subtitle.
        UiRender.text(g, font, getTitle().getString(), Fonts.UI_TITLE, panelX + PAD, panelY + PAD, t.text());
        if (subtitle() != null) {
            UiRender.text(g, font, subtitle(), Fonts.UI_SMALL, panelX + PAD,
                    panelY + PAD + Fonts.UI_TITLE_SZ + 2, t.accent());
        }

        // Hairline divider under the header.
        int dividerY = headerBottom() - 12;
        g.fill(panelX + PAD, dividerY, panelX + panelW - PAD, dividerY + S, Theme.withAlpha(t.border(), 0.9f));

        // Close button.
        int cs = closeSz();
        boolean closeHover = UiRender.inside(mx, my, closeX(), closeY(), cs, cs);
        if (closeHover) {
            UiRender.fillRounded(g, closeX(), closeY(), cs, cs, 12, t.elevated(), sm);
            UiRender.strokeRoundedThick(g, closeX(), closeY(), cs, cs, 12, t.border(), S, sm);
        }
        UiRender.textCenteredVC(g, font, "×", Fonts.UI_TITLE, Fonts.UI_TITLE_SZ, closeX() + cs / 2, closeY(), cs,
                closeHover ? t.text() : t.textMuted());

        renderBody(g, mx, my);

        for (Widget widget : widgets) {
            widget.render(g, mx, my, t, font, sm);
        }

        UiRender.endHiRes(g);
    }

    /** Draw a small uppercase section label (web-style eyebrow). */
    protected void sectionLabel(GuiGraphicsExtractor g, String label, int x, int y) {
        UiRender.text(g, font, label.toUpperCase(), Fonts.UI_EYEBROW, x, y, theme().textFaint());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) Math.round(event.x() * S);
        int my = (int) Math.round(event.y() * S);
        int cs = closeSz();
        if (event.button() == 0 && UiRender.inside(mx, my, closeX(), closeY(), cs, cs)) {
            onClose();
            return true;
        }
        for (Widget widget : widgets) {
            if (widget.mouseClicked(mx, my, event.button())) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
