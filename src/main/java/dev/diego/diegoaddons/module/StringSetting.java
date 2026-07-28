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
        net.minecraft.client.Minecraft.getInstance().execute(() ->
                new dev.diego.diegoaddons.gui.TextEntryView(name, get(), "Text", this::set).open());
    }
}
