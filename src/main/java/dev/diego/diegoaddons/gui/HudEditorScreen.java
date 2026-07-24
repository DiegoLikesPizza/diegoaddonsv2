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
 * selected element. Positions persist per-module in the config.
 */
public class HudEditorScreen extends Screen {
    private static final int GRID = 8;
    private static final int TOOLBAR_H = 46;

    private final List<UiButton> toolbar = new ArrayList<>();
    private UiButton snapButton;
    private boolean snap = true;

    private HudModule dragging;
    private HudModule selected;
    private int dragDX, dragDY;

    private int toolbarX, toolbarY, toolbarW;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    private String snapLabel() {
        return snap ? "Grid snap: On" : "Grid snap: Off";
    }

    @Override
    protected void init() {
        toolbar.clear();
        toolbarW = Math.min(440, width - 20);
        toolbarX = (width - toolbarW) / 2;
        toolbarY = height - 24 - TOOLBAR_H;
        int bh = 26;
        int by = toolbarY + (TOOLBAR_H - bh) / 2;
        int rightEdge = toolbarX + toolbarW - 12;

        UiButton done = new UiButton(rightEdge - 62, by, 62, bh, "Done", UiButton.Kind.PRIMARY, this::onClose);
        UiButton reset = new UiButton(done.x - 8 - 62, by, 62, bh, "Reset", UiButton.Kind.SECONDARY,
                ModuleManager::resetHudPositions);
        snapButton = new UiButton(reset.x - 8 - 100, by, 100, bh, snapLabel(), UiButton.Kind.SECONDARY, () -> {
            snap = !snap;
            snapButton.label = snapLabel();
        });
        toolbar.add(snapButton);
        toolbar.add(reset);
        toolbar.add(done);
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

        List<HudModule> huds = ModuleManager.enabledHudModules();
        if (huds.isEmpty()) {
            UiRender.textCentered(g, font, "No HUD modules enabled - turn some on in Modules.",
                    Fonts.BODY, width / 2, height / 2 - 30, t.textMuted());
        }
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
                UiRender.strokeRounded(g, x - 1, y - 1, sw + 2, sh + 2, 8, t.accent(), sm);
            }
            if (hud == dragging) {
                String tag = x + ", " + y + "  ·  " + Math.round(s * 100) + "%";
                UiRender.text(g, font, tag, Fonts.SMALL, x, y - Fonts.SMALL_H - 2, t.accent());
            }
        }

        // Toolbar.
        UiRender.dropShadow(g, toolbarX, toolbarY, toolbarW, TOOLBAR_H, 14, t.shadow(), 6, 3);
        UiRender.fillRounded(g, toolbarX, toolbarY, toolbarW, TOOLBAR_H, 14, t.surface(), sm);
        UiRender.strokeRounded(g, toolbarX, toolbarY, toolbarW, TOOLBAR_H, 14, t.border(), sm);
        UiRender.text(g, font, "HUD Editor", Fonts.MEDIUM, toolbarX + 14, toolbarY + 9, t.text());
        UiRender.text(g, font, "Drag to move • arrows nudge", Fonts.SMALL, toolbarX + 14,
                toolbarY + 9 + Fonts.BODY_H, t.textMuted());
        snapButton.label = snapLabel();
        for (UiButton b : toolbar) {
            b.render(g, mouseX, mouseY, t, font, sm);
        }
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
            for (UiButton b : toolbar) {
                if (b.mouseClicked(mx, my, 0)) {
                    return true;
                }
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
