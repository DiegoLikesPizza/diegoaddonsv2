package dev.diego.diegoaddons.config;

/** One command bound to a key. Plain Gson data object; see the Command Hotkeys feature. */
public class CommandHotkey {
    /** The command to run, without a leading slash. */
    public String command = "";
    /** GLFW key code, or -1 when unbound. */
    public int key = -1;
    public boolean enabled = true;

    public CommandHotkey() {
    }

    public CommandHotkey(String command, int key) {
        this.command = command;
        this.key = key;
    }
}
