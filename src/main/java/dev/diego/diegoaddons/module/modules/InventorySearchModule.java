package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;

/**
 * A search box over every container menu, with a calculator in the same box.
 *
 * <p>The storage sheet has had one since 2.5.3 and it only ever worked there, which is the wrong way
 * round: the sheet is the one place your items are already laid out for you. The menus where finding
 * something is genuinely hard are the ones with no search at all - a bazaar page, a sack, an auction
 * browser. See {@link dev.diego.diegoaddons.gui.InventorySearch}.
 */
public class InventorySearchModule extends Module {
    public static InventorySearchModule INSTANCE;

    private final BooleanSetting searchLore =
            new BooleanSetting(this, "searchLore", "Search item lore too", true);
    private final BooleanSetting ignoreCase =
            new BooleanSetting(this, "ignoreCase", "Ignore capitals", true);
    private final ColorSetting highlightColor =
            new ColorSetting(this, "highlightColor", "Highlight colour", 0x80FF5555);
    /**
     * Unbound by default, and that is deliberate rather than lazy.
     *
     * <p>A container menu already spends its keys: the inventory key closes it, 1-9 swap to the
     * hotbar, Q drops. Ctrl+F is the chat search. Claiming any of those by default would break
     * something people already use, so the box is focused by clicking it and this is here for
     * whoever wants a key and knows which one is free for them.
     */
    private final KeybindSetting focusKey =
            new KeybindSetting(this, "focusKey", "Key to focus the box");

    public InventorySearchModule() {
        super("inventorysearch", Category.MISC, "Inventory Search",
                "Search any menu, and do sums in the same box (2x2 shows = 4).");
        settings.add(searchLore);
        settings.add(ignoreCase);
        settings.add(highlightColor);
        settings.add(focusKey);
        INSTANCE = this;
    }

    public boolean searchLore() {
        return searchLore.get();
    }

    public boolean ignoreCase() {
        return ignoreCase.get();
    }

    public int highlightColor() {
        return highlightColor.argb();
    }

    public int focusKey() {
        return focusKey.get();
    }
}
