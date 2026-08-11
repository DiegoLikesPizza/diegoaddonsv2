package dev.diego.diegoaddons.config;

/** One saved loadout bound to a key. Plain Gson data object; see the Loadout Keybinds feature. */
public class LoadoutKey {
    /** The loadout's name exactly as the Loadouts menu shows it. */
    public String name = "";
    /** GLFW key code, or -1 when unbound. */
    public int key = -1;
    public boolean enabled = true;

    public LoadoutKey() {
    }

    public LoadoutKey(String name, int key) {
        this.name = name;
        this.key = key;
    }
}
