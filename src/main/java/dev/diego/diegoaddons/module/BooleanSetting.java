package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.config.ConfigManager;

/** An on/off setting, persisted in the owner module's {@code options} map. */
public class BooleanSetting extends Setting {
    public final boolean def;

    public BooleanSetting(Module owner, String key, String name, boolean def) {
        super(owner, key, name);
        this.def = def;
    }

    public boolean get() {
        return ConfigManager.moduleConfig(owner.id).options.getOrDefault(key, def);
    }

    public void set(boolean value) {
        ConfigManager.moduleConfig(owner.id).options.put(key, value);
        ConfigManager.save();
    }

    public void toggle() {
        set(!get());
    }
}
