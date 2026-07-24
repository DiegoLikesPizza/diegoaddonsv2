package dev.diego.diegoaddons.config;

/**
 * One user-defined button drawn beside a container GUI. Persisted in {@link AddonConfig}, so this is
 * a plain Gson data object with a no-arg constructor and public fields.
 *
 * <p>The position is an offset from <b>one corner of the menu</b>, chosen by {@link #anchorRight}
 * and {@link #anchorBottom}. Anchoring to the nearest corner rather than always the top-left is what
 * keeps a button in the right place across menus of different sizes: a button parked under a
 * six-row chest would otherwise float in the middle of a three-row one.
 */
public class InventoryButton {
    /** Offset from the anchored corner, in GUI pixels. */
    public int x = -22;
    public int y = 0;

    /** Which corner {@link #x} / {@link #y} are measured from. */
    public boolean anchorRight = false;
    public boolean anchorBottom = false;

    /** Double-size button (2x2 slots), for the ones you want to hit without looking. */
    public boolean gigantic = false;

    /** The command to run, without a leading slash. */
    public String command = "";

    /** Item id used as the icon, or {@code skull:<texture>} for a custom head. */
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
