package dev.diego.diegoaddons.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.config.CommandHotkey;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.util.CommandHotkeys;
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
 * Manages the command hotkeys: a command per row with the key beside it.
 *
 * <p>Clicking the key listens for the next press, so a binding is set the way you would expect from
 * a controls screen rather than by typing a key name.
 */
public class CommandHotkeysScreen extends Screen {
    private static final int ROW_H = 18;
    private static final int PAD = 10;
    private static final int KEY_W = 90;

    private final Screen parent;

    private EditBox commandBox;
    private final List<UiButton> buttons = new ArrayList<>();

    private int scroll;
    /** Index of the row waiting for a key press, or -1. */
    private int binding = -1;
    private int panelX, panelY, panelW, panelH, listTop, rows;

    public CommandHotkeysScreen(Screen parent) {
        super(Component.literal("Command Hotkeys"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buttons.clear();
        binding = -1;
        panelW = Math.min(400, width - 40);
        panelH = Math.min(240, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 52;
        rows = Math.max(1, (panelY + panelH - 34 - listTop) / ROW_H);

        commandBox = new EditBox(font, panelX + PAD, panelY + 24, panelW - PAD * 2 - 56, 16,
                Component.literal("Command"));
        commandBox.setHint(Component.literal("party warp"));
        commandBox.setMaxLength(128);
        addRenderableWidget(commandBox);

        UiButton add = new UiButton(panelX + panelW - PAD - 50, panelY + 24, 50, 16,
                "Add", UiButton.Kind.PRIMARY, this::addEntry);
        UiButton done = new UiButton(panelX + panelW - PAD - 50, panelY + panelH - 24, 50, 20,
                "Done", UiButton.Kind.SECONDARY, this::onClose);
        buttons.add(add);
        buttons.add(done);
    }

    private void addEntry() {
        String cmd = commandBox.getValue().trim();
        if (cmd.isEmpty()) {
            return;
        }
        CommandHotkeys.all().add(new CommandHotkey(cmd, InputConstants.UNKNOWN.getValue()));
        commandBox.setValue("");
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
        UiRender.text(g, font, "COMMAND HOTKEYS", Fonts.SMALL, panelX + PAD, panelY + 8, t.textFaint());

        List<CommandHotkey> list = CommandHotkeys.all();
        if (list.isEmpty()) {
            UiRender.text(g, font, "No hotkeys yet.", Fonts.SMALL,
                    panelX + PAD, listTop + 4, t.textFaint());
        }

        int end = Math.min(list.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            CommandHotkey h = list.get(i);
            int ry = listTop + (i - scroll) * ROW_H;
            boolean hover = UiRender.inside(mouseX, mouseY, panelX + PAD, ry, panelW - PAD * 2, ROW_H);
            if (hover) {
                UiRender.fillRounded(g, panelX + PAD - 3, ry - 1, panelW - PAD * 2 + 6, ROW_H, 3,
                        t.surfaceAlt(), sm);
            }
            int col = h.enabled ? t.text() : t.textFaint();
            UiRender.text(g, font, "/" + h.command, Fonts.MEDIUM, panelX + PAD, ry + 5, col);

            // Key chip on the right.
            int kx = panelX + panelW - PAD - KEY_W - 46;
            boolean listening = i == binding;
            String label = listening ? "Press a key…" : CommandHotkeys.keyName(h.key);
            UiRender.fillRounded(g, kx, ry + 1, KEY_W, ROW_H - 3, 4,
                    listening ? Theme.withAlpha(t.accent(), 0.25f) : t.surfaceAlt(), sm);
            UiRender.textCentered(g, font, label, Fonts.SMALL, kx + KEY_W / 2, ry + 5,
                    listening ? t.accent() : (h.key == InputConstants.UNKNOWN.getValue() ? t.textFaint() : t.text()));

            if (hover) {
                UiRender.textRight(g, font, "remove", Fonts.SMALL,
                        panelX + panelW - PAD, ry + 5, t.accent());
            }
        }

        UiRender.text(g, font, "Click the key to rebind, the row to toggle, remove to delete.",
                Fonts.SMALL, panelX + PAD, panelY + panelH - 20, t.textFaint());

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        for (UiButton b : buttons) {
            b.render(g, mouseX, mouseY, t, font, sm);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        for (UiButton b : buttons) {
            if (b.mouseClicked(mx, my, 0)) {
                return true;
            }
        }
        List<CommandHotkey> list = CommandHotkeys.all();
        int end = Math.min(list.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            int ry = listTop + (i - scroll) * ROW_H;
            if (!UiRender.inside(mx, my, panelX + PAD, ry, panelW - PAD * 2, ROW_H)) {
                continue;
            }
            int kx = panelX + panelW - PAD - KEY_W - 46;
            if (mx > panelX + panelW - PAD - 40) {
                list.remove(i);
                binding = -1;
            } else if (UiRender.inside(mx, my, kx, ry + 1, KEY_W, ROW_H - 3)) {
                binding = (binding == i) ? -1 : i;   // click again to cancel
            } else {
                list.get(i).enabled = !list.get(i).enabled;
            }
            ConfigManager.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        List<CommandHotkey> list = CommandHotkeys.all();
        if (binding >= 0 && binding < list.size()) {
            list.get(binding).key = event.key() == GLFW.GLFW_KEY_ESCAPE
                    ? InputConstants.UNKNOWN.getValue()
                    : event.key();
            binding = -1;
            ConfigManager.save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, CommandHotkeys.all().size() - rows);
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
