package dev.diego.diegoaddons.config;

/**
 * One user-defined button drawn beside a container GUI. Persisted in {@link AddonConfig}, so this is
 * a plain Gson data object with a no-arg constructor and public fields.
 *
 * <p>The position is stored <b>relative to the container GUI's top-left corner</b>, not in absolute
 * screen coordinates, so buttons stay where you put them at any window size or GUI scale.
 */
public class InventoryButton {
    /** Offset from the GUI's top-left corner, in GUI pixels. May be negative (left of / above it). */
    public int x = -22;
    public int y = 0;

    /** The command to run, without a leading slash. */
    public String command = "";

    /** Item id used as the icon, e.g. {@code minecraft:chest}. */
    public String icon = "minecraft:chest";

    public InventoryButton() {
    }

    public InventoryButton(int x, int y, String command, String icon) {
        this.x = x;
        this.y = y;
        this.command = command;
        this.icon = icon;
    }
}
