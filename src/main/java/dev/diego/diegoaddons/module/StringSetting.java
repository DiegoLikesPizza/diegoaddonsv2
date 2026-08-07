package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.config.ConfigManager;

/**
 * A setting whose value is text - a sound id, a name, anything a list of options could not cover.
 *
 * <p>Shown in the ClickGUI as a row with the current value on the right, which opens whatever
 * chooser the setting was given. A sound is picked from a browser of every sound the game knows; a
 * list of items has its own editor. Free typing into the settings card is deliberately not a thing:
 * the value usually has to exist somewhere else to mean anything, and a picker can say so.
 */
public class StringSetting extends Setting {
    private final String def;
    private final Runnable chooser;

    public StringSetting(Module owner, String key, String name, String def, Runnable chooser) {
        super(owner, key, name);
        this.def = def;
        this.chooser = chooser;
    }

    /**
     * Whether this setting picks from somewhere rather than being typed.
     *
     * <p>The two want different controls: a picker is a button that opens the thing it picks from,
     * free text is a box you type in. Asking here lets the config layer choose correctly instead of
     * treating every string the same.
     */
    public boolean hasChooser() {
        return chooser != null;
    }

    public String get() {
        String v = ConfigManager.moduleConfig(owner.id).texts.get(key);
        return v == null || v.isBlank() ? def : v;
    }

    public void set(String value) {
        if (value == null || value.isBlank()) {
            ConfigManager.moduleConfig(owner.id).texts.remove(key);
        } else {
            ConfigManager.moduleConfig(owner.id).texts.put(key, value.trim());
        }
        ConfigManager.save();
        // No menu refresh needed: configlib's rows read through this setting's own getter every
        // frame, so a new value is on screen the moment it is written.
    }

    /**
     * Opens this setting's chooser - the one it was given, or the plain typing screen when it has
     * none. A row that does nothing when clicked is not a setting, it is a label.
     */
    public void choose() {
        if (chooser != null) {
            chooser.run();
            return;
        }
        // Nothing to open. A setting with no chooser is free text, and configlib draws it as a box
        // in its own row - there is no screen to go to any more.
    }
}
