package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Leaves the mouse where it is when one menu closes and the next one opens.
 *
 * <p>SkyBlock is played through chest menus, and moving between them goes through a moment with no
 * screen at all: the game grabs the mouse for gameplay, then releases it again when the next menu
 * arrives - and a release puts the cursor back in the middle of the window. So every click on a
 * menu that opens another one throws your hand back to the centre, and a run through a few pages of
 * a shop is spent chasing the cursor rather than clicking.
 *
 * <p>With this on, the position from before the grab is put back instead of the centre, so the
 * cursor is still over the button you just pressed - which on a paged menu is usually the next thing
 * you want to press. The work is in {@link dev.diego.diegoaddons.mixin.NoCursorResetMixin}.
 */
public class NoCursorResetModule extends Module {
    public static NoCursorResetModule INSTANCE;

    public NoCursorResetModule() {
        super("nocursorreset", Category.MISC, "No Cursor Reset",
                "Keep the mouse where it was when a menu closes and another opens.");
        INSTANCE = this;
    }

    /** Whether the cursor should be put back rather than centred. Read from the mixin. */
    public static boolean on() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }
}
