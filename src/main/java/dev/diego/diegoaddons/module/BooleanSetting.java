package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.config.ConfigManager;

/**
 * An on/off setting.
 *
 * <p>The value lives here rather than in a map keyed by module and setting name. configlib reads and
 * writes it through this pair of methods and stores it under {@code <module>.<key>}, so the setting
 * object is the single source of truth and there is no second copy to keep in step.
 */
public class BooleanSetting extends Setting {
    public final boolean def;

    private boolean value;

    public BooleanSetting(Module owner, String key, String name, boolean def) {
        super(owner, key, name);
        this.def = def;
        this.value = def;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean value) {
        if (value == this.value) {
            return;
        }
        this.value = value;
        ConfigManager.save();
    }

    public void toggle() {
        set(!get());
    }
}
