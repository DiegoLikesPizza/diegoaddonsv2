package dev.diego.diegoaddons.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.gui.ChatSearchScreen;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Everything this mod does to chat, in one place: keeping the whole session's scrollback, merging
 * repeated lines, searching the backlog and copying a message out of it.
 *
 * <p>These were three modules. They are one because they are one thing to a player - "how my chat
 * behaves" - and because keeping them apart meant the search knew nothing about the history it was
 * searching, and the compactor could merge lines the history had already dropped.
 *
 * <p>The Ctrl+F combo is polled straight from the window rather than bound as a key mapping: it has
 * to work <i>while the chat screen is open</i>, which is exactly where vanilla key mappings do not
 * fire.
 */
public class ChatModule extends Module {
    public static ChatModule INSTANCE;

    /**
     * Kept messages while the history option is on. The cap is raised rather than removed: chat
     * lines stay in memory for as long as they are in the scrollback, so an unbounded list would
     * grow all session. This is unlimited in practice and still has an end.
     */
    private static final int HISTORY_LIMIT = 100_000;

    private final BooleanSetting history =
            new BooleanSetting(this, "history", "Unlimited chat history", true);
    private final BooleanSetting compact =
            new BooleanSetting(this, "compact", "Compact chat", false);
    /** 30 s to 5 min, in half-minute steps. */
    private final NumberSetting window =
            new NumberSetting(this, "window", "Compact chat time window (s)", 60, 30, 300, 30);
    private final BooleanSetting search =
            new BooleanSetting(this, "search", "Ctrl+F to search", true);
    private final BooleanSetting copy =
            new BooleanSetting(this, "copy", "Ctrl+click to copy", true);
    private final BooleanSetting caseSensitive =
            new BooleanSetting(this, "caseSensitive", "Search is case sensitive", false);

    /**
     * Hold to read the chat without opening it.
     *
     * <p><b>Unbound by default</b>, and that is the same rule Ctrl+F and the Inventory Search box
     * follow: this module is on for anybody who wants unlimited history, so shipping a default key
     * would quietly take that key off everybody who never asked for the feature. Bound is on;
     * unbound is off. There is no second switch, because a hold key with nothing to hold is already
     * the off state.
     */
    private final KeybindSetting peekKey =
            new KeybindSetting(this, "peekKey", "Hold to peek at chat");
    private final BooleanSetting peekFullHeight =
            new BooleanSetting(this, "peekFullHeight", "Peek shows the full chat height", true);

    private boolean wasDown;

    public ChatModule() {
        super("chat", Category.MISC, "Chat",
                "Unlimited history, compacting, search and copy - everything chat.");
        settings.add(history);
        settings.add(compact);
        settings.add(window);
        settings.add(search);
        settings.add(copy);
        settings.add(caseSensitive);
        settings.add(peekKey);
        settings.add(peekFullHeight);
        INSTANCE = this;
    }

    /** Whether the scrollback cap is lifted. */
    public boolean unlimitedHistory() {
        return isEnabled() && history.get();
    }

    public static int limit() {
        return HISTORY_LIMIT;
    }

    public boolean compacting() {
        return isEnabled() && compact.get();
    }

    /** How long a message stays eligible to be merged, in seconds. */
    public double windowSeconds() {
        return window.get();
    }

    public boolean copyOnCtrlClick() {
        return isEnabled() && copy.get();
    }

    public boolean caseSensitive() {
        return caseSensitive.get();
    }

    /** The hold-to-peek key. See {@link dev.diego.diegoaddons.util.ChatPeek}. */
    public KeybindSetting peekKey() {
        return peekKey;
    }

    public boolean peekFullHeight() {
        return peekFullHeight.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.getWindow() == null || !search.get()) {
            return;
        }
        boolean ctrl = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean down = ctrl && InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F);
        boolean pressed = down && !wasDown;
        wasDown = down;

        if (pressed && mc.player != null && !(mc.screen instanceof ChatSearchScreen)) {
            mc.setScreen(new ChatSearchScreen());
        }
    }
}
