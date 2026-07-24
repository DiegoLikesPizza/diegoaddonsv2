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

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Called every client tick while enabled. */
    public void onClientTick(Minecraft mc) {
    }
}
