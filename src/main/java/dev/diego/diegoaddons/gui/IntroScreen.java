package dev.diego.diegoaddons.gui;

import dev.diego.configlib.render.Fonts;
import dev.diego.configlib.render.Theme;
import dev.diego.configlib.render.Ui;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The one-time welcome, shown the first time the title screen appears in an instance.
 *
 * <p>Hand-positioned on configlib's drawing layer. It was a two-column layout with a theme picker
 * down the right; the picker is gone because there is one palette now, so what is left is what the
 * screen was actually for - saying what this is, and getting you into the settings.
 *
 * <p>Marking it seen happens on <b>any</b> exit, including Escape. A welcome that reappears because
 * you dismissed it the wrong way is worse than one you never saw.
 */
public final class IntroScreen extends Screen {

    private static final int PANEL_W = 620;
    private static final int PANEL_H = 420;
    private static final int PAD = 34;
    private static final int BTN_H = 38;

    private record Point(String number, String title, String detail) {
    }

    private static final Point[] POINTS = {
            new Point("01", "Clear controls",
                    "Searchable categories, and settings that open under the module they belong to."),
            new Point("02", "A HUD you can trust",
                    "Every element placed and scaled from one screen, and it stays where you put it."),
            new Point("03", "Built for SkyBlock",
                    "Dungeon and Crystal Hollows maps, puzzle solvers, and the numbers you read."),
    };

    private final Screen parent;
    private int panelX;
    private int panelY;

    public IntroScreen(Screen parent) {
        super(Component.literal("Welcome"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (Ui.u(width) - PANEL_W) / 2;
        panelY = (Ui.u(height) - PANEL_H) / 2;
    }

    /** Records that it has been seen, so it never opens again in this instance. */
    private void markSeen() {
        if (!ConfigManager.get().introShown) {
            ConfigManager.get().introShown = true;
            ConfigManager.save();
        }
    }

    private int buttonY() {
        return panelY + PANEL_H - PAD - BTN_H;
    }

    private int primaryX() {
        return panelX + PANEL_W - PAD - 190;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        Theme t = DiegoAddonsV2Client.CONFIG.theme();
        int mx = Ui.u(mouseX);
        int my = Ui.u(mouseY);
        int inner = PANEL_W - PAD * 2;

        Ui.roundRect(g, panelX, panelY, PANEL_W, PANEL_H, 20, t.surface());
        Ui.roundOutline(g, panelX, panelY, PANEL_W, PANEL_H, 20, 1, t.stroke());

        // Brand mark: the same rounded tile with a cut-out ring the settings sidebar uses.
        Ui.roundRect(g, panelX + PAD, panelY + PAD, 34, 34, 11, t.accent());
        Ui.roundOutline(g, panelX + PAD + 10, panelY + PAD + 10, 14, 14, 7, 2, t.surfaceAlt());
        Fonts.draw(g, font, "DiegoAddons", panelX + PAD + 48,
                Fonts.centerY(panelY + PAD, 20, Fonts.TITLE_SZ), Fonts.UI_TITLE, t.text());
        Fonts.draw(g, font, "VERSION 2", panelX + PAD + 48,
                Fonts.centerY(panelY + PAD + 18, 16, Fonts.EYEBROW_SZ), Fonts.UI_EYEBROW, t.accent());

        Fonts.drawTruncated(g, font, "Your SkyBlock tools, finally in one place.",
                panelX + PAD, panelY + PAD + 62, inner, Fonts.UI_LABEL, t.text());

        int y = panelY + PAD + 100;
        for (Point p : POINTS) {
            Ui.roundRect(g, panelX + PAD, y, 26, 22, 7, Ui.fade(t.accent(), 0.22f));
            Fonts.drawCentered(g, font, p.number(), panelX + PAD + 13,
                    Fonts.centerY(y, 22, Fonts.SMALL_SZ), Fonts.UI_SMALL, t.accent());
            Fonts.drawTruncated(g, font, p.title(), panelX + PAD + 38, y + 1,
                    inner - 38, Fonts.UI_BODY, t.text());
            Fonts.drawTruncated(g, font, p.detail(), panelX + PAD + 38, y + 22,
                    inner - 38, Fonts.UI_SMALL, t.textDim());
            y += 56;
        }

        Fonts.draw(g, font, "You can change everything later.", panelX + PAD,
                Fonts.centerY(buttonY(), BTN_H, Fonts.SMALL_SZ), Fonts.UI_SMALL, t.textFaint());

        button(g, t, primaryX(), buttonY(), 190, "Open settings", mx, my, true);
        button(g, t, primaryX() - 108, buttonY(), 100, "Not now", mx, my, false);
    }

    private void button(GuiGraphicsExtractor g, Theme t, int x, int y, int w, String label,
                        int mx, int my, boolean accent) {
        boolean hot = Ui.hovered(mx, my, x, y, w, BTN_H);
        int bg = accent
                ? Ui.mix(t.accent(), 0xFFFFFFFF, hot ? 0.12f : 0f)
                : (hot ? t.cardHover() : t.controlOff());
        Ui.roundRect(g, x, y, w, BTN_H, 10, bg);
        Fonts.drawCentered(g, font, label, x + w / 2, Fonts.centerY(y, BTN_H, Fonts.BODY_SZ),
                Fonts.UI_BODY, accent ? t.accentText() : t.text());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = Ui.u(event.x());
        int my = Ui.u(event.y());
        if (Ui.hovered(mx, my, primaryX(), buttonY(), 190, BTN_H)) {
            markSeen();
            DiegoAddonsV2Client.CONFIG.open();
            return true;
        }
        if (Ui.hovered(mx, my, primaryX() - 108, buttonY(), 100, BTN_H)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        markSeen();
        minecraft.setScreen(parent);
    }
}
