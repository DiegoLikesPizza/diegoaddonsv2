package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.config.ConfigManager;

/**
 * A "pick one of N" setting shown in the ClickGUI as a single row that advances to the next option
 * when clicked. The chosen option is stored as its <b>index</b> in the owner module's {@code numbers}
 * map, so it reuses the existing config schema - no new persisted map is needed.
 *
 * <p>The index is always clamped to a valid option on the way out, so a config edited by hand (or an
 * options list that shrank between versions) can never select past the end.
 */
public class CycleSetting extends Setting {
    public final String[] options;
    public final int def;

    public CycleSetting(Module owner, String key, String name, int def, String... options) {
        super(owner, key, name);
        this.options = options.length > 0 ? options : new String[]{""};
        this.def = clamp(def);
    }

    /** The current option's index, 0..options.length-1. */
    public int get() {
        return clamp((int) Math.round(ConfigManager.moduleConfig(owner.id).numbers.getOrDefault(key, (double) def)));
    }

    /** The current option's display text. */
    public String label() {
        return options[get()];
    }

    public void set(int index) {
        ConfigManager.moduleConfig(owner.id).numbers.put(key, (double) clamp(index));
        ConfigManager.save();
    }

    /** Advance to the next option, wrapping around. */
    public void cycle() {
        set((get() + 1) % options.length);
    }

    public void reset() {
        set(def);
    }

    private int clamp(int i) {
        int n = options == null ? 1 : options.length;
        return ((i % n) + n) % n;
    }
}
