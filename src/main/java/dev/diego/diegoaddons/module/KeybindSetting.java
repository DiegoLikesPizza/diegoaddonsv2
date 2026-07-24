package dev.diego.diegoaddons.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.config.ConfigManager;
import net.minecraft.client.Minecraft;

/**
 * A key binding that lives in this mod's own settings GUI rather than Minecraft's controls screen.
 *
 * <p>Because it is not a vanilla {@code KeyMapping}, the key is read straight from the window and
 * edge-detected here: {@link #consumePress()} returns true once per physical press. Polling this way
 * also means the binding works while a container menu is open, which vanilla key mappings do not.
 */
public class KeybindSetting extends Setting {
    /** Stored when nothing is bound. */
    public static final int UNBOUND = InputConstants.UNKNOWN.getValue();

    private boolean wasDown;

    public KeybindSetting(Module owner, String key, String name) {
        super(owner, key, name);
    }

    public int get() {
        return ConfigManager.moduleConfig(owner.id).keys.getOrDefault(key, UNBOUND);
    }

    public void set(int keyCode) {
        ConfigManager.moduleConfig(owner.id).keys.put(key, keyCode);
        ConfigManager.save();
    }

    public void clear() {
        set(UNBOUND);
    }

    public boolean isBound() {
        return get() != UNBOUND;
    }

    /** Human-readable key name for the GUI, e.g. "K" or "Not bound". */
    public String display() {
        int code = get();
        if (code == UNBOUND) {
            return "Not bound";
        }
        return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
    }

    /** True exactly once per press of the bound key. Call every client tick. */
    public boolean consumePress() {
        int code = get();
        if (code == UNBOUND) {
            wasDown = false;
            return false;
        }
        boolean down = isDown(code);
        boolean pressed = down && !wasDown;
        wasDown = down;
        return pressed;
    }

    private static boolean isDown(int code) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() != null && InputConstants.isKeyDown(mc.getWindow(), code);
    }
}
