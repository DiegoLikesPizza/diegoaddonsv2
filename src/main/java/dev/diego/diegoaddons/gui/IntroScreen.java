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

    /** Widest the panel is allowed to get; it shrinks with the window but never past this. */
    private static final int PANEL_MAX_W = 960;
    private static final int PANEL_H = 500;
    private static final int PAD = 44;
    private static final int BTN_H = 46;
    private static final int PRIMARY_W = 240;
    private static final int SECONDARY_W = 130;
    /** Height of the brand tile, and of the title + eyebrow stack that sits beside it. */
    private static final int HEAD_H = 48;

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
    private int panelW;

    public IntroScreen(Screen parent) {
        super(Component.literal("Welcome"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(PANEL_MAX_W, Ui.u(width) - 80);
        panelX = (Ui.u(width) - panelW) / 2;
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
        return panelX + panelW - PAD - PRIMARY_W;
    }

    private int secondaryX() {
        return primaryX() - 12 - SECONDARY_W;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // The vanilla background first, at screen scale - it is not ours and is not in units.
        super.extractRenderState(g, mouseX, mouseY, partial);
        Theme t = DiegoAddonsV2Client.CONFIG.theme();
        int mx = Ui.u(mouseX);
        int my = Ui.u(mouseY);
        int inner = panelW - PAD * 2;

        // Everything below is in units, where one unit is half a screen pixel. Without this the
        // panel drew at twice its size from twice its offset, which put its top-left quarter in the
        // bottom-right of the screen and the rest of it past the edge - see ChatSearchScreen, which
        // is the same kind of screen and has always done this.
        Ui.beginHiRes(g);
        Ui.roundRect(g, panelX, panelY, panelW, PANEL_H, 20, t.surface());
        Ui.roundOutline(g, panelX, panelY, panelW, PANEL_H, 20, 1, t.stroke());

        // Brand mark: the same rounded tile with a cut-out ring the settings sidebar uses.
        int headY = panelY + PAD;
        Ui.roundRect(g, panelX + PAD, headY, HEAD_H, HEAD_H, 15, t.accent());
        Ui.roundOutline(g, panelX + PAD + 14, headY + 14, 20, 20, 10, 3, t.surfaceAlt());
        // The title and the eyebrow are two stacked bands filling the tile's height, rather than two
        // guessed offsets - guessing is what drew "VERSION 2" through the middle of the name.
        Fonts.draw(g, font, "DiegoAddons", panelX + PAD + 66,
                Fonts.centerY(headY, 30, Fonts.TITLE_SZ), Fonts.UI_TITLE, t.text());
        Fonts.draw(g, font, "VERSION 2", panelX + PAD + 66,
                Fonts.centerY(headY + 30, HEAD_H - 30, Fonts.EYEBROW_SZ), Fonts.UI_EYEBROW, t.accent());

        Fonts.drawTruncated(g, font, "Your SkyBlock tools, finally in one place.", panelX + PAD,
                Fonts.centerY(headY + HEAD_H + 30, 30, Fonts.LABEL_SZ), inner, Fonts.UI_LABEL, t.text());

        int y = headY + HEAD_H + 72;
        for (Point p : POINTS) {
            Ui.roundRect(g, panelX + PAD, y, 36, 30, 10, Ui.fade(t.accent(), 0.22f));
            Fonts.drawCentered(g, font, p.number(), panelX + PAD + 18,
                    Fonts.centerY(y, 30, Fonts.SMALL_SZ), Fonts.UI_SMALL, t.accent());
            Fonts.drawTruncated(g, font, p.title(), panelX + PAD + 52, y + 2,
                    inner - 52, Fonts.UI_BODY, t.text());
            Fonts.drawTruncated(g, font, p.detail(), panelX + PAD + 52, y + 30,
                    inner - 52, Fonts.UI_SMALL, t.textDim());
            y += 74;
        }

        // Truncated against the buttons' left edge: the note is the part that gives way, not the
        // controls it was previously drawn underneath.
        Fonts.drawTruncated(g, font, "You can change everything later.", panelX + PAD,
                Fonts.centerY(buttonY(), BTN_H, Fonts.SMALL_SZ), secondaryX() - 20 - panelX - PAD,
                Fonts.UI_SMALL, t.textFaint());

        button(g, t, primaryX(), buttonY(), PRIMARY_W, "Open settings", mx, my, true);
        button(g, t, secondaryX(), buttonY(), SECONDARY_W, "Not now", mx, my, false);
        Ui.endHiRes(g);
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
        if (Ui.hovered(mx, my, primaryX(), buttonY(), PRIMARY_W, BTN_H)) {
            markSeen();
            DiegoAddonsV2Client.CONFIG.open();
            return true;
        }
        if (Ui.hovered(mx, my, secondaryX(), buttonY(), SECONDARY_W, BTN_H)) {
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
