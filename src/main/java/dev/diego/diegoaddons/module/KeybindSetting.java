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
    private int value = UNBOUND;

    public KeybindSetting(Module owner, String key, String name) {
        super(owner, key, name);
    }

    /**
     * A binding that starts on a key rather than unbound.
     *
     * <p>Unbound is the right default for a key that <i>adds</i> a way to do something you can
     * already do - which is most of them here, and why the plain constructor exists. It is the wrong
     * default for a key that <b>is</b> the feature: a hold-to-peek with nothing to hold is a module
     * that does nothing when you switch it on. The module is still off by default, so this takes no
     * key from anybody who has not asked for it.
     */
    public KeybindSetting(Module owner, String key, String name, int def) {
        super(owner, key, name);
        this.value = def;
    }

    public int get() {
        return value;
    }

    public void set(int keyCode) {
        if (keyCode == this.value) {
            return;
        }
        this.value = keyCode;
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

    /**
     * Whether the key is being held right now.
     *
     * <p>Unlike {@link #consumePress()} this keeps no state, so it is safe to ask from the render
     * thread as often as a frame needs - which is what a hold-to-do-something key is read by.
     */
    public boolean isDown() {
        int code = get();
        return code != UNBOUND && isDown(code);
    }

    private static boolean isDown(int code) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() != null && InputConstants.isKeyDown(mc.getWindow(), code);
    }
}
