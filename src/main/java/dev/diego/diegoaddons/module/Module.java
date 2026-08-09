package dev.diego.diegoaddons.module;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained, runtime-toggleable feature. {@link #setEnabled(boolean)} flips it on/off live
 * (calling {@link #onEnable()} / {@link #onDisable()}). Each module belongs to a {@link Category}
 * and may expose feature-specific {@link Setting}s shown in the ClickGUI.
 */
public abstract class Module {
    public final String id;
    public final Category category;
    public final String name;
    public final String description;

    protected final List<Setting> settings = new ArrayList<>();

    private boolean enabled = false;

    protected Module(String id, Category category, String name, String description) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Setting> settings() {
        return settings;
    }

    /** Turn the module on or off. No-op if already in that state. */
    public final void setEnabled(boolean value) {
        if (value == enabled) {
            return;
        }
        enabled = value;
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /**
     * Sets the flag without running {@link #onEnable} or {@link #onDisable}.
     *
     * <p>For restoring a saved state: the config is read while the client is still being built, and
     * a module waking up there may touch parts of it that do not exist yet. The flag is set now and
     * the module woken later, in one pass - see {@code ModuleManager.applyEnabled}.
     */
    public final void setEnabledQuietly(boolean value) {
        enabled = value;
    }

    /** Runs {@link #onEnable} if this module is flagged on. Paired with {@link #setEnabledQuietly}. */
    final void wake() {
        if (enabled) {
            onEnable();
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Called every client tick while enabled. */
    public void onClientTick(Minecraft mc) {
    }
}
