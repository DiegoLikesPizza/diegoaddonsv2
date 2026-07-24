package dev.diego.diegoaddons.module;

/** Base type for a feature-specific setting shown in the ClickGUI's third column. */
public abstract class Setting {
    public final Module owner;
    public final String key;
    public final String name;

    protected Setting(Module owner, String key, String name) {
        this.owner = owner;
        this.key = key;
        this.name = name;
    }
}
