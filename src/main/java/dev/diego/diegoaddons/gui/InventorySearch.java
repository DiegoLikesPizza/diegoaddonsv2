package dev.diego.diegoaddons.gui;

import dev.diego.configlib.gui.widget.SearchBox;
import dev.diego.configlib.render.Fonts;
import dev.diego.configlib.render.Theme;
import dev.diego.configlib.render.Ui;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.InventorySearchModule;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.util.Calc;
import dev.diego.diegoaddons.util.LegacyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * A search box over any container menu, and a calculator in the same box.
 *
 * <p>The storage sheet has had one of these since 2.5.3, and it only ever worked there - which is
 * the wrong way round, because the sheet is the one place your items are already laid out for you.
 * The menus where finding something is actually hard are the ones nobody built a search for: a
 * bazaar page, a sack, an auction browser, somebody's 54-slot trade window.
 *
 * <p><b>Matches are highlighted rather than non-matches veiled</b>, which is the opposite of the
 * storage sheet and deliberate. The sheet is a map of everything you own, so dimming the rest keeps
 * the shape of it; a server menu is somebody else's layout that you are hunting through, and there
 * the useful thing is one slot lighting up.
 *
 * <p>Nothing here steals a key by default. The box is focused by clicking it, and the optional
 * keybind on the card is unbound until you set it - a container menu already spends its keys on
 * closing, dropping and hotbar swaps, and Ctrl+F belongs to the chat search.
 */
public final class InventorySearch {
    private static final SearchBox SEARCH = new SearchBox("Search this menu...");
    /**
     * Height of the box in configlib units, and it has to be the 34 {@code SearchBox} is built
     * around. The widget squashes its frame to whatever height it is handed
     * ({@code Math.min(height, BOX_H)}) but scales nothing inside to match: the magnifier column is
     * a fixed 34 wide and the field's font is a pre-baked size. At the 22 this used to pass, the
     * frame came out half the height of its own contents - a tiny box with text that looked like it
     * had missed the scale.
     */
    private static final int BOX_H = 34;
    /** Matches {@code SearchBox.preferredWidth()}, for the same reason. */
    private static final int BOX_W = 260;

    /** The screen the box currently belongs to, so a new menu starts a new search. */
    private static Object lastScreen;
    /** The evaluated query, or null when it is not arithmetic. Recomputed only when the text moves. */
    private static Double result;
    private static String lastQuery = "";

    private InventorySearch() {
    }

    /** Whether the search should be drawn and listened to over this screen. */
    private static boolean active(AbstractContainerScreen<?> screen) {
        InventorySearchModule mod = InventorySearchModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return false;
        }
        // The storage sheet stands in for the whole menu and has a search of its own; two boxes on
        // one screen, both taking keys, is worse than either.
        return !StorageOverlay.active(screen);
    }

    /** Drops the query when the menu changes, so a search does not follow you into the next one. */
    private static void syncScreen(AbstractContainerScreen<?> screen) {
        if (screen != lastScreen) {
            lastScreen = screen;
            SEARCH.clear();
            SEARCH.collapse();
            result = null;
            lastQuery = "";
        }
    }

    /** The current query, lowercased and trimmed, or "" when nothing is being searched. */
    private static String query() {
        return SEARCH.value().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Highlights the slots that match. Drawn with the background, so it sits under the item rather
     * than over it - the same reasoning the rarity backing already follows.
     */
    public static void renderHighlights(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        if (!active(screen)) {
            return;
        }
        syncScreen(screen);
        String query = query();
        if (query.isEmpty()) {
            return;
        }
        InventorySearchModule mod = InventorySearchModule.INSTANCE;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int left = acc.diego$leftPos();
        int top = acc.diego$topPos();
        int colour = mod.highlightColor();
        for (Slot s : screen.getMenu().slots) {
            ItemStack stack = s.getItem();
            if (stack.isEmpty() || !matches(mod, stack, query)) {
                continue;
            }
            g.fill(left + s.x, top + s.y, left + s.x + 16, top + s.y + 16, colour);
        }
    }

    /**
     * Draws the box itself, with the background so item tooltips stay on top of it.
     *
     * <p>Positioned under the menu rather than inside it: a container's own area belongs to the
     * server's layout, and a box floating over the middle of a bazaar page would cover the thing
     * being searched for. That is also what lets it draw this early - nothing of the menu's own
     * reaches down here to paint over it.
     */
    public static void renderBox(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g,
                                 int mx, int my) {
        if (!active(screen)) {
            return;
        }
        syncScreen(screen);
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int centreX = acc.diego$leftPos() + acc.diego$imageWidth() / 2;
        int belowY = acc.diego$topPos() + acc.diego$imageHeight() + 3;

        // The sum, worked out only when the text has actually moved: this runs every frame and the
        // parse is wasted on the ninety-nine frames out of a hundred where nothing was typed.
        String q = SEARCH.value().trim();
        if (!q.equals(lastQuery)) {
            lastQuery = q;
            result = Calc.eval(q);
        }

        Theme t = DiegoAddonsV2Client.CONFIG.theme();
        Ui.beginHiRes(g);
        int x = Ui.u(centreX) - BOX_W / 2;
        int y = Ui.u(belowY);
        SEARCH.bounds(x, y, BOX_W, BOX_H);
        SEARCH.render(g, t, Ui.u(mx), Ui.u(my));
        // Beside the box, not in it. It is an annotation on what you typed: the text stays exactly
        // what it was, and the search goes on matching it - so "2x2" still finds an item called that
        // if one exists, and the sum is extra rather than instead.
        if (result != null) {
            Fonts.drawTruncated(g, Minecraft.getInstance().font, "= " + Calc.format(result),
                    x + BOX_W + 8, y + 6, 160, Fonts.UI_BODY, t.accent());
        }
        Ui.endHiRes(g);
    }

    /** Whether this stack answers the query. */
    private static boolean matches(InventorySearchModule mod, ItemStack stack, String query) {
        if (contains(stack.getHoverName(), query, mod.ignoreCase())) {
            return true;
        }
        if (!mod.searchLore()) {
            return false;
        }
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        for (Component line : lore.lines()) {
            if (contains(line, query, mod.ignoreCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(Component text, String query, boolean ignoreCase) {
        String plain = LegacyText.strip(text.getString());
        return ignoreCase
                ? plain.toLowerCase(Locale.ROOT).contains(query)
                : plain.contains(query);
    }

    // --- input ----------------------------------------------------------------------------------

    /** @return whether the click was the box's, so the menu should not also get it */
    public static boolean mouseClicked(AbstractContainerScreen<?> screen, double mx, double my,
                                       int button) {
        if (!active(screen)) {
            return false;
        }
        syncScreen(screen);
        return SEARCH.mouseClicked(Ui.u(mx), Ui.u(my), button);
    }

    /** @return whether the key was the box's */
    public static boolean keyPressed(AbstractContainerScreen<?> screen, int key, int modifiers) {
        if (!active(screen)) {
            return false;
        }
        syncScreen(screen);
        InventorySearchModule mod = InventorySearchModule.INSTANCE;
        if (mod.focusKey() != 0 && key == mod.focusKey()) {
            SEARCH.focus();
            return true;
        }
        // Only while it has focus. A container menu spends its keys on closing, dropping and hotbar
        // swaps, and an unfocused box taking any of those would be the feature reaching too far.
        return SEARCH.capturingInput() && SEARCH.keyPressed(key, modifiers);
    }

    /** @return whether the character was typed into the box */
    public static boolean charTyped(AbstractContainerScreen<?> screen, char c) {
        if (!active(screen)) {
            return false;
        }
        syncScreen(screen);
        return SEARCH.capturingInput() && SEARCH.charTyped(c);
    }

    /** Whether the box currently owns the keyboard, so nothing else claims a key from under it. */
    public static boolean capturing() {
        return SEARCH.capturingInput();
    }
}
