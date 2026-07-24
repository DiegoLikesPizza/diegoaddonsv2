package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.ModuleConfig;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen HUD editor: draws a reference grid, renders every enabled HUD element at its saved
 * position, and lets you drag them around (with optional grid snapping). Arrow keys nudge the
 * selected element, and scrolling while dragging scales it. Positions persist per-module.
 *
 * <p>The chrome (toolbar, hint card, drag readout) is drawn <b>supersampled</b> in the same style as
 * {@link ClickGuiScreen}: a pose scaled by {@code 1/}{@link UiRender#SS} with the layout expressed in
 * "units". The HUD elements themselves are <i>not</i> - they must stay in real screen pixels, since
 * that is where they will actually appear in game.
 */
public class HudEditorScreen extends Screen {
    private static final int S = UiRender.SS;
    private static final int GRID = 8;

    // Toolbar metrics, in hi-res units (visual size = unit / SS).
    private static final int T_PAD = 24;
    private static final int T_H = 100;
    private static final int T_RAD = 28;
    private static final int T_MARGIN = 28;
    private static final int BTN_H = 52;
    private static final int BTN_W = 116;
    private static final int PILL_W = 46;
    private static final int PILL_H = 26;
    private static final String HINT = "DRAG TO MOVE  ·  SCROLL WHILE DRAGGING TO SCALE  ·  ARROWS NUDGE";

    private final List<UiButton> toolbar = new ArrayList<>();
    private boolean snap = true;

    private HudModule dragging;
    private HudModule selected;
    private int dragDX, dragDY;

    private int toolbarX, toolbarY, toolbarW;
    private int snapX, snapY, snapW, snapH;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        toolbar.clear();
        int maxW = width * S;

        // Right cluster: [Grid snap toggle] [Reset] [Done]; left cluster: mark + title + hint.
        int snapLabelW = font.width(Fonts.t("Grid snap", Fonts.UI_LABEL));
        snapW = 20 + snapLabelW + 14 + PILL_W + 20;
        int titleW = font.width(Fonts.t("HUD Editor", Fonts.UI_LABEL));
        int hintW = font.width(Fonts.t(HINT, Fonts.UI_EYEBROW));
        int leftW = 44 + 16 + Math.max(titleW, hintW);
        int rightW = snapW + 16 + BTN_W + 16 + BTN_W;

        toolbarW = Math.min(maxW - 40, T_PAD * 2 + leftW + 56 + rightW);
        toolbarX = (maxW - toolbarW) / 2;
        toolbarY = height * S - T_MARGIN - T_H;

        int by = toolbarY + (T_H - BTN_H) / 2;
        int rightEdge = toolbarX + toolbarW - T_PAD;

        UiButton done = new UiButton(rightEdge - BTN_W, by, BTN_W, BTN_H, "Done",
                UiButton.Kind.PRIMARY, this::onClose);
        UiButton reset = new UiButton(done.x - 16 - BTN_W, by, BTN_W, BTN_H, "Reset",
                UiButton.Kind.SECONDARY, ModuleManager::resetHudPositions);
        done.hiRes = true;
        reset.hiRes = true;
        toolbar.add(reset);
        toolbar.add(done);

        snapX = reset.x - 16 - snapW;
        snapY = by;
        snapH = BTN_H;
    }

    private int chipW(HudModule hud) {
        return hud.hudWidth(font, minecraft, true);
    }

    private int chipH(HudModule hud) {
        return hud.hudHeight(minecraft, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;

        g.fill(0, 0, width, height, t.overlay());
        drawGrid(g, t);

        // The elements live in real screen pixels - this is a preview of the actual HUD.
        List<HudModule> huds = ModuleManager.enabledHudModules();
        for (HudModule hud : huds) {
            float s = ModuleManager.hudScale(hud);
            int sw = Math.round(hud.hudWidth(font, minecraft, true) * s);
            int sh = Math.round(hud.hudHeight(minecraft, true) * s);
            int x = ModuleManager.hudX(hud);
            int y = ModuleManager.hudY(hud);
            ModuleManager.drawElement(g, font, t, sm, hud, minecraft, x, y, true);
            boolean active = hud == dragging || hud == selected
                    || UiRender.inside(mouseX, mouseY, x, y, sw, sh);
            if (active) {
                UiRender.strokeRounded(g, x - 2, y - 2, sw + 4, sh + 4, 9,
                        hud == dragging ? t.accent() : Theme.withAlpha(t.accent(), 0.55f), sm);
            }
        }

        // Chrome, supersampled like the ClickGUI.
        UiRender.beginHiRes(g);
        if (huds.isEmpty()) {
            emptyCard(g, t, sm);
        }
        for (HudModule hud : huds) {
            if (hud == dragging) {
                dragReadout(g, t, sm, hud);
            }
        }
        renderToolbar(g, t, sm, mouseX * S, mouseY * S);
        UiRender.endHiRes(g);
    }

    /** A small floating readout above the element being dragged: its position and scale. */
    private void dragReadout(GuiGraphicsExtractor g, Theme t, boolean sm, HudModule hud) {
        float s = ModuleManager.hudScale(hud);
        int x = ModuleManager.hudX(hud) * S;
        int y = ModuleManager.hudY(hud) * S;
        String tag = ModuleManager.hudX(hud) + ", " + ModuleManager.hudY(hud)
                + "   ·   " + Math.round(s * 100) + "%";
        int tw = font.width(Fonts.t(tag, Fonts.UI_EYEBROW));
        int w = tw + 24;
        int h = 32;
        int ty = Math.max(0, y - h - 8);
        UiRender.fillRounded(g, x, ty, w, h, 10, t.elevated(), sm);
        UiRender.strokeRoundedThick(g, x, ty, w, h, 10, Theme.withAlpha(t.accent(), 0.7f), S, sm);
        UiRender.textVC(g, font, tag, Fonts.UI_EYEBROW, Fonts.UI_EYEBROW_SZ, x + 12, ty, h, t.accent());
    }

    /** Shown when there is nothing to arrange, so the editor never looks broken. */
    private void emptyCard(GuiGraphicsExtractor g, Theme t, boolean sm) {
        String line = "No HUD elements enabled";
        String sub = "TURN SOME ON IN THE HUD GROUP";
        int w = Math.max(font.width(Fonts.t(line, Fonts.UI_LABEL)),
                font.width(Fonts.t(sub, Fonts.UI_EYEBROW))) + 72;
        int h = 108;
        int x = (width * S - w) / 2;
        int y = (height * S - h) / 2 - 60;
        UiRender.dropShadow(g, x, y, w, h, 20, t.shadow(), 12, 6);
        UiRender.fillRounded(g, x, y, w, h, 20, t.surface(), sm);
        UiRender.strokeRoundedThick(g, x, y, w, h, 20, t.border(), S, sm);
        UiRender.textCenteredVC(g, font, line, Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                x + w / 2, y + 12, 44, t.text());
        UiRender.textCentered(g, font, sub, Fonts.UI_EYEBROW, x + w / 2, y + 66, t.textFaint());
    }

    private void renderToolbar(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        // Window shell, matching the ClickGUI: shadow, surface, thick border, accent hairline.
        UiRender.dropShadow(g, toolbarX, toolbarY, toolbarW, T_H, T_RAD, t.shadow(), 18, 10);
        UiRender.fillRounded(g, toolbarX, toolbarY, toolbarW, T_H, T_RAD, t.surface(), sm);
        UiRender.strokeRoundedThick(g, toolbarX, toolbarY, toolbarW, T_H, T_RAD, t.border(), S, sm);
        UiRender.fillRounded(g, toolbarX + T_RAD, toolbarY + S, toolbarW - T_RAD * 2, S, S,
                Theme.withAlpha(t.accent(), 0.5f), sm);

        // Brand mark + title + eyebrow hint.
        int mark = 44;
        int markX = toolbarX + T_PAD;
        int markY = toolbarY + (T_H - mark) / 2;
        UiRender.glow(g, markX, markY, mark, mark, 13, t.accent(), 12, 0.12f);
        UiRender.fillRoundedGradient(g, markX, markY, mark, mark, 13, t.accent(), t.accentTo(), sm);
        UiRender.textCenteredVC(g, font, "H", Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                markX + mark / 2, markY, mark, t.accentText());

        int tx = markX + mark + 16;
        UiRender.textVC(g, font, "HUD Editor", Fonts.UI_LABEL, Fonts.UI_LABEL_SZ, tx,
                toolbarY + 14, 40, t.text());
        UiRender.text(g, font, HINT, Fonts.UI_EYEBROW, tx, toolbarY + 60, t.textFaint());

        // Grid snap, as a labelled pill toggle like the ClickGUI's setting rows.
        boolean snapHover = UiRender.inside(mx, my, snapX, snapY, snapW, snapH);
        UiRender.fillRounded(g, snapX, snapY, snapW, snapH, 22,
                snapHover ? t.elevated() : t.surfaceAlt(), sm);
        UiRender.strokeRoundedThick(g, snapX, snapY, snapW, snapH, 22, t.border(), S, sm);
        UiRender.textVC(g, font, "Grid snap", Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                snapX + 20, snapY, snapH, snap ? t.text() : t.textMuted());
        pill(g, snapX + snapW - 20 - PILL_W, snapY + (snapH - PILL_H) / 2, snap, t, sm);

        for (UiButton b : toolbar) {
            b.render(g, mx, my, t, font, sm);
        }
    }

    /** The ClickGUI's on/off pill, so both screens read the same. */
    private void pill(GuiGraphicsExtractor g, int x, int y, boolean on, Theme t, boolean sm) {
        if (on) {
            UiRender.fillRoundedGradient(g, x, y, PILL_W, PILL_H, PILL_H / 2, t.accent(), t.accentTo(), sm);
        } else {
            UiRender.fillRounded(g, x, y, PILL_W, PILL_H, PILL_H / 2,
                    Theme.withAlpha(t.textFaint(), 0.5f), sm);
        }
        int knobR = PILL_H - 8;
        int knobX = on ? (x + PILL_W - knobR - 4) : (x + 4);
        UiRender.circle(g, knobX + knobR / 2, y + 4 + knobR / 2, knobR / 2, on ? t.accentText() : t.text(), sm);
    }

    private void drawGrid(GuiGraphicsExtractor g, Theme t) {
        int line = Theme.withAlpha(t.textFaint(), 0.16f);
        int center = Theme.withAlpha(t.accent(), 0.35f);
        for (int gx = 0; gx <= width; gx += GRID) {
            g.fill(gx, 0, gx + 1, height, line);
        }
        for (int gy = 0; gy <= height; gy += GRID) {
            g.fill(0, gy, width, gy + 1, line);
        }
        g.fill(width / 2, 0, width / 2 + 1, height, center);
        g.fill(0, height / 2, width, height / 2 + 1, center);
    }

    private void applyPos(HudModule hud, int nx, int ny) {
        float s = ModuleManager.hudScale(hud);
        int w = Math.round(chipW(hud) * s);
        int h = Math.round(chipH(hud) * s);
        if (snap) {
            nx = Math.round(nx / (float) GRID) * GRID;
            ny = Math.round(ny / (float) GRID) * GRID;
        }
        nx = Math.max(0, Math.min(width - w, nx));
        ny = Math.max(0, Math.min(height - h, ny));
        ModuleConfig cfg = ConfigManager.moduleConfig(hud.id);
        cfg.hudX = nx;
        cfg.hudY = ny;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            // The toolbar is laid out in hi-res units, so test it in that space.
            int ux = (int) Math.round(mx * S);
            int uy = (int) Math.round(my * S);
            if (UiRender.inside(ux, uy, snapX, snapY, snapW, snapH)) {
                snap = !snap;
                return true;
            }
            for (UiButton b : toolbar) {
                if (b.mouseClicked(ux, uy, 0)) {
                    return true;
                }
            }
            // Clicks anywhere on the toolbar shell must not fall through onto an element beneath it.
            if (UiRender.inside(ux, uy, toolbarX, toolbarY, toolbarW, T_H)) {
                return true;
            }

            List<HudModule> huds = ModuleManager.enabledHudModules();
            for (int i = huds.size() - 1; i >= 0; i--) {
                HudModule hud = huds.get(i);
                float s = ModuleManager.hudScale(hud);
                int w = Math.round(chipW(hud) * s);
                int h = Math.round(chipH(hud) * s);
                int x = ModuleManager.hudX(hud);
                int y = ModuleManager.hudY(hud);
                if (UiRender.inside(mx, my, x, y, w, h)) {
                    dragging = hud;
                    selected = hud;
                    dragDX = (int) (mx - x);
                    dragDY = (int) (my - y);
                    return true;
                }
            }
            selected = null;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            applyPos(dragging, (int) Math.round(event.x() - dragDX), (int) Math.round(event.y() - dragDY));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // Hold left-click on an element and scroll to scale it up/down.
        if (dragging != null && dy != 0) {
            float next = ModuleManager.hudScale(dragging) + (float) dy * 0.1f;
            ModuleManager.setHudScale(dragging, next);
            // Keep it on-screen and re-anchor the drag offset to the new size.
            applyPos(dragging, ModuleManager.hudX(dragging), ModuleManager.hudY(dragging));
            ConfigManager.save();
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selected != null) {
            int step = snap ? GRID : 1;
            int x = ModuleManager.hudX(selected);
            int y = ModuleManager.hudY(selected);
            boolean moved = true;
            switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT -> applyPos(selected, x - step, y);
                case GLFW.GLFW_KEY_RIGHT -> applyPos(selected, x + step, y);
                case GLFW.GLFW_KEY_UP -> applyPos(selected, x, y - step);
                case GLFW.GLFW_KEY_DOWN -> applyPos(selected, x, y + step);
                default -> moved = false;
            }
            if (moved) {
                ConfigManager.save();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
