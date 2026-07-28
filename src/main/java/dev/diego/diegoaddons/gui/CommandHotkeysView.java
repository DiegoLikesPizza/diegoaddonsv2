package dev.diego.diegoaddons.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.config.CommandHotkey;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.util.CommandHotkeys;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The command hotkeys: a command, and the key that runs it.
 *
 * <p>Binding is done by clicking the key chip and then pressing a key; escape clears the binding
 * rather than setting it to escape, which is the one key nobody means.
 */
public class CommandHotkeysView extends DiegoView {
    private static final float PANEL_W = 900f;
    private static final float PANEL_H = 640f;
    private static final float ROW_H = 40f;

    private ScrollContainerComponent list;
    private String newCommand = "";
    /** The hotkey waiting for a key, if any. */
    private CommandHotkey capturing;

    public CommandHotkeysView() {
        super("Command hotkeys", PANEL_W, PANEL_H);
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 12f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        ContainerComponent add = row(inner, 12f).height(36f);
        add.add(field(newCommand, "party warp", inner - 118f - 12f, s -> newCommand = s));
        ButtonComponent addBtn = clickable(t.accent(), () -> {
            String cmd = newCommand.trim();
            if (!cmd.isEmpty()) {
                CommandHotkeys.all().add(new CommandHotkey(cmd, InputConstants.UNKNOWN.getValue()));
                ConfigManager.save();
                newCommand = "";
                rebuildView();
            }
        });
        asRow(addBtn, 118f, 0f).height(36f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER);
        addBtn.add(GuiText.label("Add", t.accentText(), 14f));
        add.add(addBtn);
        body.add(add);

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 36f - 12f);
        asColumn(list, inner, 8f);
        body.add(list);
        panel.add(body);
        fill(inner);
    }

    private void rebuildView() {
        panel.clearChildren();
        build();
    }

    private void fill(float inner) {
        list.clearChildren();
        List<CommandHotkey> all = CommandHotkeys.all();
        if (all.isEmpty()) {
            list.add(textBox(GuiText.label("No hotkeys yet.", t.textFaint(), 13f), inner, 24f));
            return;
        }
        for (CommandHotkey h : List.copyOf(all)) {
            ContainerComponent r = row(inner - 24f, 10f).height(ROW_H).padding(0f, 12f)
                    .cornerRadius(10f).backgroundColor(GuiColors.of(t.surfaceAlt()))
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()))
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            r.add(textBox(GuiText.label("/" + h.command, h.enabled ? t.text() : t.textFaint(), 14f),
                    0f, ROW_H).flexGrow(1f));

            boolean bound = h.key != InputConstants.UNKNOWN.getValue();
            boolean listening = h == capturing;
            ButtonComponent key = clickable(listening ? t.elevated() : t.surface(), () -> {
                capturing = capturing == h ? null : h;
                rebuildView();
            });
            asRow(key, 150f, 0f).height(28f).cornerRadius(8f)
                    .justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(listening ? t.accent() : t.border()));
            key.add(GuiText.label(
                    listening ? "Press a key" : (bound ? CommandHotkeys.keyName(h.key) : "Unbound"),
                    listening ? t.accent() : (bound ? t.text() : t.textFaint()), 13f));
            r.add(key);

            ButtonComponent onOff = clickable(h.enabled ? t.accent() : t.surface(), () -> {
                h.enabled = !h.enabled;
                ConfigManager.save();
                rebuildView();
            });
            asRow(onOff, 70f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));
            onOff.add(GuiText.label(h.enabled ? "On" : "Off",
                    h.enabled ? t.accentText() : t.textMuted(), 13f));
            r.add(onOff);

            ButtonComponent remove = clickable(t.surface(), () -> {
                CommandHotkeys.all().remove(h);
                ConfigManager.save();
                rebuildView();
            });
            asRow(remove, 90f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));
            remove.add(GuiText.label("Remove", t.textMuted(), 13f));
            r.add(remove);
            list.add(r);
        }
    }

    /**
     * Watches for the key while one is being bound.
     *
     * <p>Binding is the one thing a list of buttons cannot express - the answer is not a click, it
     * is whatever is pressed next - and RenderLib keeps its key events to itself. So the window is
     * asked directly, which is the same trick the Ctrl+F combo uses, and only while something is
     * actually waiting for a key.
     */
    @Override
    public void tick() {
        super.tick();
        if (capturing == null) {
            return;
        }
        var window = Minecraft.getInstance().getWindow();
        for (int code = FIRST_KEY; code <= LAST_KEY; code++) {
            if (!InputConstants.isKeyDown(window, code)) {
                continue;
            }
            capturing.key = code == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN.getValue() : code;
            capturing = null;
            ConfigManager.save();
            rebuildView();
            return;
        }
    }

    /** The printable-and-function range; below this are only modifiers GLFW reports oddly. */
    private static final int FIRST_KEY = GLFW.GLFW_KEY_SPACE;
    private static final int LAST_KEY = GLFW.GLFW_KEY_LAST;
}
