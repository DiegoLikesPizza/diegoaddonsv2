package dev.diego.diegoaddons.config;

/**
 * One inventory button: where it sits, what it runs, and what it looks like.
 *
 * <p>A mutable class rather than a record because the editor drags it around - the button being
 * edited is the same object the list holds, so a move is a field write and nothing has to be put
 * back. The field names are the ones the upstream mod wrote, so a layout exported from Inventory
 * Buttons pastes straight in (see {@link dev.diego.diegoaddons.util.InvButtons#importFromClipboard}).
 *
 * <p>{@code x} and {@code y} are relative to the menu's top-left corner, or to the corner the anchor
 * flags name - so a button parked off the right edge stays off the right edge when a menu is wider
 * than the player inventory.
 */
public class InvButton {
    public int x;
    public int y;
    public String command = "/";
    public String itemId = "";
    public int backgroundIndex = 0;
    public boolean anchorRight = false;
    public boolean anchorBottom = false;

    /** For Gson, and for the list editor's "add a row". */
    public InvButton() {
    }

    public InvButton(int x, int y, String command, String itemId) {
        this.x = x;
        this.y = y;
        this.command = command;
        this.itemId = itemId;
    }

    public InvButton copy() {
        InvButton b = new InvButton(x, y, command, itemId);
        b.backgroundIndex = backgroundIndex;
        b.anchorRight = anchorRight;
        b.anchorBottom = anchorBottom;
        return b;
    }
}
