package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.InventoryButton;
import dev.diego.diegoaddons.util.InventoryButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor for the inventory buttons: a stand-in for the container GUI sits in the middle, and the
 * buttons are dragged around it exactly where they will appear in game. The selected button's
 * command and icon are edited in the side panel.
 *
 * <p>Positions are relative to the stand-in's corner, which is what gets saved - so what you arrange
 * here is what you get beside any real menu, at any GUI scale.
 */
public class InventoryButtonsScreen extends Screen {
    /** Vanilla container GUI size, used for the stand-in so the layout matches the real thing. */
    private static final int GUI_W = 176;
    private static final int GUI_H = 166;
    private static final int GRID = 20;   // 18px button + 2px gap
    private static final int PANEL_W = 190;

    private final Screen parent;

    private EditBox commandBox;
    private EditBox iconBox;
    private final List<UiButton> buttons = new ArrayList<>();

    private InventoryButton selected;
    private InventoryButton dragging;
    private int dragDX, dragDY;
    private boolean snap = true;

    private int guiX, guiY, panelX, panelY, panelH;

    public InventoryButtonsScreen(Screen parent) {
        super(Component.literal("Inventory Buttons"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buttons.clear();
        // The stand-in sits slightly left of centre so the side panel has room.
        guiX = (width - GUI_W - PANEL_W - 24) / 2;
        guiY = (height - GUI_H) / 2;
        panelX = guiX + GUI_W + 24;
        panelY = guiY;
        panelH = GUI_H;

        commandBox = new EditBox(font, panelX + 12, panelY + 46, PANEL_W - 24, 18,
                Component.literal("Command"));
        commandBox.setMaxLength(128);
        commandBox.setHint(Component.literal("ah"));
        commandBox.setResponder(v -> {
            if (selected != null) {
                selected.command = v;
            }
        });

        iconBox = new EditBox(font, panelX + 12, panelY + 96, PANEL_W - 24, 18,
                Component.literal("Icon"));
        iconBox.setMaxLength(128);
        iconBox.setHint(Component.literal("minecraft:chest"));
        iconBox.setResponder(v -> {
            if (selected != null) {
                selected.icon = v;
                InventoryButtons.invalidateIcons();
            }
        });
        addRenderableWidget(commandBox);
        addRenderableWidget(iconBox);

        int by = panelY + panelH - 30;
        UiButton add = new UiButton(panelX + 12, by, 54, 20, "Add", UiButton.Kind.PRIMARY, this::addButton);
        UiButton del = new UiButton(add.x + 60, by, 60, 20, "Delete", UiButton.Kind.SECONDARY, this::deleteSelected);
        UiButton done = new UiButton(panelX + PANEL_W - 12 - 54, by, 54, 20, "Done",
                UiButton.Kind.SECONDARY, this::onClose);
        buttons.add(add);
        buttons.add(del);
        buttons.add(done);

        syncBoxes();
    }

    private void addButton() {
        // New buttons land in the first free cell of the column left of the GUI.
        int y = 0;
        while (occupied(-22, y) && y < GUI_H) {
            y += GRID;
        }
        InventoryButton b = new InventoryButton(-22, y, "", "minecraft:chest");
        InventoryButtons.all().add(b);
        selected = b;
        syncBoxes();
        ConfigManager.save();
    }

    private boolean occupied(int x, int y) {
        for (InventoryButton b : InventoryButtons.all()) {
            if (b.x == x && b.y == y) {
                return true;
            }
        }
        return false;
    }

    private void deleteSelected() {
        if (selected != null) {
            InventoryButtons.all().remove(selected);
            selected = null;
            syncBoxes();
            ConfigManager.save();
        }
    }

    /** Push the selected button's values into the text boxes (and disable them when nothing is selected). */
    private void syncBoxes() {
        boolean has = selected != null;
        commandBox.setValue(has ? selected.command : "");
        iconBox.setValue(has ? selected.icon : "");
        commandBox.active = has;
        iconBox.active = has;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        g.fill(0, 0, width, height, t.overlay());

        // Stand-in for the container GUI.
        UiRender.dropShadow(g, guiX, guiY, GUI_W, GUI_H, 8, t.shadow(), 8, 4);
        UiRender.fillRounded(g, guiX, guiY, GUI_W, GUI_H, 8, t.surface(), sm);
        UiRender.strokeRounded(g, guiX, guiY, GUI_W, GUI_H, 8, t.border(), sm);
        UiRender.textCentered(g, font, "Your menu goes here", Fonts.SMALL,
                guiX + GUI_W / 2, guiY + GUI_H / 2 - 4, t.textFaint());

        // The buttons, positioned exactly as they will be in game.
        for (InventoryButton b : InventoryButtons.all()) {
            int x = guiX + b.x;
            int y = guiY + b.y;
            boolean hover = UiRender.inside(mouseX, mouseY, x, y, InventoryButtons.SIZE, InventoryButtons.SIZE);
            InventoryButtons.draw(g, minecraft, t, b, x, y, hover, false);
            if (b == selected) {
                UiRender.strokeRounded(g, x - 2, y - 2, InventoryButtons.SIZE + 4,
                        InventoryButtons.SIZE + 4, 6, t.accent(), sm);
            }
        }

        // Side panel.
        UiRender.dropShadow(g, panelX, panelY, PANEL_W, panelH, 10, t.shadow(), 8, 4);
        UiRender.fillRounded(g, panelX, panelY, PANEL_W, panelH, 10, t.surface(), sm);
        UiRender.strokeRounded(g, panelX, panelY, PANEL_W, panelH, 10, t.border(), sm);
        UiRender.text(g, font, "INVENTORY BUTTONS", Fonts.SMALL, panelX + 12, panelY + 12, t.textFaint());
        if (selected == null) {
            UiRender.text(g, font, "Select or add a button.", Fonts.MEDIUM,
                    panelX + 12, panelY + 30, t.textMuted());
        } else {
            UiRender.text(g, font, "Command", Fonts.SMALL, panelX + 12, panelY + 34, t.textMuted());
            UiRender.text(g, font, "Icon (item id)", Fonts.SMALL, panelX + 12, panelY + 84, t.textMuted());
        }
        UiRender.text(g, font, "Drag buttons around the menu.", Fonts.SMALL,
                panelX + 12, panelY + 128, t.textFaint());
        UiRender.text(g, font, snap ? "Grid snap: on (G)" : "Grid snap: off (G)", Fonts.SMALL,
                panelX + 12, panelY + 142, t.textFaint());

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
            for (InventoryButton b : InventoryButtons.all()) {
                if (UiRender.inside(mx, my, guiX + b.x, guiY + b.y,
                        InventoryButtons.SIZE, InventoryButtons.SIZE)) {
                    selected = b;
                    dragging = b;
                    dragDX = (int) (mx - (guiX + b.x));
                    dragDY = (int) (my - (guiY + b.y));
                    syncBoxes();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            int nx = (int) Math.round(event.x() - dragDX) - guiX;
            int ny = (int) Math.round(event.y() - dragDY) - guiY;
            if (snap) {
                nx = Math.round(nx / (float) GRID) * GRID;
                ny = Math.round(ny / (float) GRID) * GRID;
            }
            dragging.x = nx;
            dragging.y = ny;
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
    public boolean keyPressed(KeyEvent event) {
        // Only when no text box has focus, so typing "g" into a command still works.
        boolean typing = commandBox.isFocused() || iconBox.isFocused();
        if (!typing && event.key() == GLFW.GLFW_KEY_G) {
            snap = !snap;
            return true;
        }
        if (!typing && event.key() == GLFW.GLFW_KEY_DELETE) {
            deleteSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        InventoryButtons.invalidateIcons();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
