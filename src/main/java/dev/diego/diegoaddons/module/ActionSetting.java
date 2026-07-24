package dev.diego.diegoaddons.module;

/**
 * A setting that is a button rather than a value: clicking its row runs an action. Used for things
 * a feature can only offer by opening something, such as the inventory-button editor.
 *
 * <p>Nothing is persisted - there is no state to keep.
 */
public class ActionSetting extends Setting {
    /** Text on the button itself, e.g. "Open". */
    public final String action;
    private final Runnable onClick;

    public ActionSetting(Module owner, String key, String name, String action, Runnable onClick) {
        super(owner, key, name);
        this.action = action;
        this.onClick = onClick;
    }

    public void run() {
        if (onClick != null) {
            onClick.run();
        }
    }
}
