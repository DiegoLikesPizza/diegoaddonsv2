package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.config.CommandHotkey;
import dev.diego.diegoaddons.config.ConfigManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs user-defined commands from key presses.
 *
 * <p>Keys are polled from the window and edge-detected here rather than registered as vanilla key
 * mappings, for the same reason the rest of this mod does: the list is dynamic, and vanilla mappings
 * have to exist before the controls screen is built.
 */
public final class CommandHotkeys {
    /** Keys held down last tick, so a command fires once per press rather than every tick. */
    private static final Set<Integer> DOWN = new HashSet<>();

    private CommandHotkeys() {
    }

    /** The configured hotkeys (never null). */
    public static List<CommandHotkey> all() {
        if (ConfigManager.get().commandHotkeys == null) {
            ConfigManager.get().commandHotkeys = new ArrayList<>();
        }
        return ConfigManager.get().commandHotkeys;
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        if (mc.getWindow() == null || mc.player == null) {
            return;
        }
        // Only from normal play: a bound letter must stay typeable in chat and in text fields.
        boolean allowed = mc.screen == null;

        for (CommandHotkey h : all()) {
            if (!h.enabled || h.key == InputConstants.UNKNOWN.getValue() || h.command.isBlank()) {
                continue;
            }
            boolean down = InputConstants.isKeyDown(mc.getWindow(), h.key);
            boolean was = DOWN.contains(h.key);
            if (down) {
                DOWN.add(h.key);
            } else {
                DOWN.remove(h.key);
            }
            if (down && !was && allowed) {
                String cmd = h.command.startsWith("/") ? h.command.substring(1) : h.command;
                mc.player.connection.sendCommand(cmd);
            }
        }
    }

    /** Human-readable key name for the GUI. */
    public static String keyName(int code) {
        if (code == InputConstants.UNKNOWN.getValue()) {
            return "Not bound";
        }
        return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
    }
}
