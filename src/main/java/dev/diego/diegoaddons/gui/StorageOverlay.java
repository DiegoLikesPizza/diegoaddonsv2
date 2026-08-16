package dev.diego.diegoaddons.gui;

import dev.diego.configlib.gui.widget.SearchBox;
import dev.diego.configlib.render.Fonts;
import dev.diego.configlib.render.Theme;
import dev.diego.configlib.render.Ui;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.StorageOverlayModule;
import dev.diego.diegoaddons.util.ItemRarity;
import dev.diego.diegoaddons.util.LegacyText;
import dev.diego.diegoaddons.util.StorageData;
import dev.diego.diegoaddons.util.StorageScanner;
import dev.diego.diegoaddons.util.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every storage page on one sheet, over SkyBlock's own storage menu - the NEU overlay, in this mod's
 * shape.
 *
 * <p>It takes over the storage menu rather than being a screen of its own: the menu stays open
 * underneath, which is the whole point. A page you are actually standing in is <b>live</b> - clicks
 * on it are the menu's own clicks, so items move exactly as they would have - and every other page
 * is drawn from the cache ({@link StorageData}) until you go there.
 *
 * <h2>Getting to a page</h2>
 * Clicking a cached page's slot sends the command that opens it and waits for the menu to actually
 * be that page before doing anything else; shift-clicking additionally quick-moves that slot into
 * your inventory once it arrives. Verified by title rather than by a timer, because "click blind
 * after 300ms" is how an overlay ends up clicking something else entirely.
 *
 * <p><b>Never while carrying.</b> Changing container returns whatever is on the cursor to your
 * inventory - so a click that would navigate is refused while you are holding something, with a
 * toast saying why. Silently dropping the held item and opening the page anyway looks exactly like
 * the overlay having eaten it.
 */
public final class StorageOverlay {

    // ---------------------------------------------------------------- layout constants (units)

    private static final int PAD = 20;
    private static final int BLOCK_GAP = 16;
    private static final int BLOCK_PAD = 10;
    /** The caption band above each page's grid. */
    private static final int CAPTION_H = 22;
    private static final int HEADER_H = 46;
    private static final int SEARCH_H = 34;
    private static final int SLOT_GAP = 4;
    /** Between the sheet and the inventory, and between the inventory and the search box. */
    private static final int SPLIT = 14;

    /** How long to wait for a page to open before giving up on it. */
    private static final long NAV_TIMEOUT_MS = 6_000L;

    private static final SearchBox SEARCH = new SearchBox("Search every page...");

    private static boolean active;
    private static float scroll;

    /** The page we asked the server for, or null when nothing is in flight. */
    private static StorageData.Page navTarget;
    private static long navSince;
    /** The slot to quick-move once {@link #navTarget} arrives, or -1 for "just take me there". */
    private static int navThenMove = -1;

    private StorageOverlay() {
    }

    // ---------------------------------------------------------------- state

    /**
     * Whether the overlay should be drawing over this screen.
     *
     * <p>Two conditions, and the second is what makes navigation possible: the overlay is switched
     * on when the storage menu opens, and stays on for as long as the open menu is <i>some</i> part
     * of storage - the overview or any page. Anything else on screen means you have gone somewhere
     * unrelated, and the sheet gets out of the way rather than covering it.
     */
    public static boolean active(Screen screen) {
        StorageOverlayModule module = StorageOverlayModule.INSTANCE;
        if (!active || module == null || !module.isEnabled()
                || !(screen instanceof AbstractContainerScreen<?> container)) {
            return false;
        }
        if (!StorageScanner.isStorageFamily(title(container))) {
            active = false;
            return false;
        }
        return true;
    }

    /** Called when a container opens: the storage overview is what raises the sheet. */
    public static void onStorageMenu() {
        if (!active) {
            active = true;
            scroll = 0f;
            SEARCH.clear();
            // Focused on arrival: the sheet is a thing you search, and having to click the box
            // first is the difference between typing a name and hunting for one. The cost is that
            // the number keys type rather than swap hotbar slots while it is up.
            SEARCH.focus();
        }
    }

    /** Dismissed by hand, or by the screen going away. */
    public static void close() {
        active = false;
        navTarget = null;
        navThenMove = -1;
    }

    private static String title(AbstractContainerScreen<?> screen) {
        return LegacyText.strip(screen.getTitle().getString()).trim();
    }

    // ---------------------------------------------------------------- navigation

    /**
     * Watches for the page we asked for to actually arrive, then does what the click asked for.
     *
     * <p>Called every client tick. The arrival test is the menu's own title, not a delay: the page
     * is open when the server says it is open.
     */
    public static void tick(Minecraft mc) {
        if (navTarget == null) {
            return;
        }
        if (System.currentTimeMillis() - navSince > NAV_TIMEOUT_MS) {
            Toasts.show("Storage", "Gave up waiting for " + navTarget.name);
            navTarget = null;
            navThenMove = -1;
            return;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        StorageData.Page live = livePage(screen);
        if (live == null || live.kind != navTarget.kind || live.index != navTarget.index) {
            return;
        }
        int move = navThenMove;
        navTarget = null;
        navThenMove = -1;
        if (move >= 0) {
            AbstractContainerMenu menu = screen.getMenu();
            int menuSlot = live.menuSlot(move);
            if (menuSlot < containerSlots(menu) && !menu.slots.get(menuSlot).getItem().isEmpty()) {
                click(mc, menu, menuSlot, 0, ContainerInput.QUICK_MOVE);
            }
        }
    }

    /** The page the open menu <i>is</i>, or null when it is the overview or something else. */
    private static StorageData.Page livePage(AbstractContainerScreen<?> screen) {
        return StorageScanner.identify(title(screen));
    }

    private static void navigateTo(Minecraft mc, StorageData.Page page, int thenMove) {
        StorageOverlayModule module = StorageOverlayModule.INSTANCE;
        if (module == null || mc.player == null) {
            return;
        }
        String command = module.commandFor(page);
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isBlank()) {
            return;
        }
        navTarget = page;
        navThenMove = thenMove;
        navSince = System.currentTimeMillis();
        mc.player.connection.sendCommand(command);
    }

    private static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button,
                              ContainerInput type) {
        if (mc.gameMode == null || mc.player == null) {
            return;
        }
        mc.gameMode.handleContainerInput(menu.containerId, slot, button, type, mc.player);
    }

    /** A menu's own slots - everything before the 36 that are your inventory. */
    private static int containerSlots(AbstractContainerMenu menu) {
        return Math.max(0, menu.slots.size() - 36);
    }

    // ---------------------------------------------------------------- geometry

    /** One page's grid, positioned in sheet space (y counted from the top of the scrolling area). */
    private record Block(StorageData.Page page, int x, int y, int rows) {
    }

    /**
     * Everything the sheet needs to draw or to be clicked, worked out from the window and the
     * settings. Recomputed rather than cached: drawing and hit testing disagreeing about where a
     * slot is would be exactly the bug that is impossible to see.
     */
    private static final class Layout {
        int slot;
        int cell;
        int panelX;
        int panelY;
        int panelW;
        int panelH;
        int sheetTop;
        int sheetH;
        int sheetTotal;
        int blockW;
        int invX;
        int invY;
        int invH;
        int searchY;
        List<Block> blocks = new ArrayList<>();
    }

    private static Layout layout(Minecraft mc, List<StorageData.Page> pages) {
        StorageOverlayModule module = StorageOverlayModule.INSTANCE;
        Layout l = new Layout();
        l.slot = (int) Math.round((module == null ? 18 : module.slotSize()) * Ui.SS);
        l.cell = l.slot + SLOT_GAP;

        int cols = Math.max(1, module == null ? 3 : module.pagesAcross());
        int gridW = StorageData.COLUMNS * l.cell - SLOT_GAP;
        l.blockW = gridW + BLOCK_PAD * 2;
        l.panelW = cols * l.blockW + (cols - 1) * BLOCK_GAP + PAD * 2;

        // Rows of blocks, each as tall as the tallest page in it - a page is 1 to 6 rows and a
        // ragged bottom edge reads as a broken grid rather than as pages of different sizes.
        int y = 0;
        for (int i = 0; i < pages.size(); i += cols) {
            int tallest = 1;
            for (int j = i; j < Math.min(i + cols, pages.size()); j++) {
                tallest = Math.max(tallest, rowsOf(pages.get(j)));
            }
            for (int j = i; j < Math.min(i + cols, pages.size()); j++) {
                int col = j - i;
                l.blocks.add(new Block(pages.get(j),
                        col * (l.blockW + BLOCK_GAP), y, tallest));
            }
            y += blockHeight(l, tallest) + BLOCK_GAP;
        }
        l.sheetTotal = Math.max(0, y - BLOCK_GAP);

        // Three rows of storage, a gap, then the hotbar - the shape everyone already reads as
        // "your inventory".
        l.invH = 3 * l.cell - SLOT_GAP + 8 + l.slot;
        int fixed = HEADER_H + SPLIT + l.invH + SPLIT + SEARCH_H + PAD;
        int maxH = Ui.u(mc.getWindow().getGuiScaledHeight()) - 40;
        l.panelH = Math.min(maxH, fixed + l.sheetTotal);
        l.sheetH = Math.max(l.cell, l.panelH - fixed);

        l.panelX = (Ui.u(mc.getWindow().getGuiScaledWidth()) - l.panelW) / 2;
        l.panelY = (Ui.u(mc.getWindow().getGuiScaledHeight()) - l.panelH) / 2;
        l.sheetTop = l.panelY + HEADER_H;
        l.invX = l.panelX + (l.panelW - gridW) / 2;
        l.invY = l.sheetTop + l.sheetH + SPLIT;
        l.searchY = l.invY + l.invH + SPLIT;
        return l;
    }

    private static int rowsOf(StorageData.Page page) {
        if (page.size() > 0) {
            return page.rows();
        }
        // Never opened, so its size is unknown: an ender chest page is always five rows, and a
        // backpack gets one row as a placeholder rather than a block the size of a guess.
        return page.kind == StorageData.Kind.ENDER_CHEST ? 5 : 1;
    }

    private static int blockHeight(Layout l, int rows) {
        return CAPTION_H + rows * l.cell - SLOT_GAP + BLOCK_PAD * 2;
    }

    private static int maxScroll(Layout l) {
        return Math.max(0, l.sheetTotal - l.sheetH);
    }

    // ---------------------------------------------------------------- drawing

    /** Draws the whole overlay. Returns true when it did, which is the mixin's cue to cancel. */
    public static boolean render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g,
                                 int mouseX, int mouseY) {
        if (!active(screen)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Theme t = DiegoAddonsV2Client.CONFIG.theme();
        List<StorageData.Page> pages = StorageData.pages();
        Layout l = layout(mc, pages);
        StorageData.Page live = livePage(screen);
        AbstractContainerMenu menu = screen.getMenu();
        String query = SEARCH.value().trim().toLowerCase(Locale.ROOT);

        int mx = Ui.u(mouseX);
        int my = Ui.u(mouseY);
        ItemStack hovered = ItemStack.EMPTY;

        Ui.beginHiRes(g);
        Ui.rect(g, 0, 0, Ui.u(mc.getWindow().getGuiScaledWidth()),
                Ui.u(mc.getWindow().getGuiScaledHeight()), t.scrim());
        Ui.roundRect(g, l.panelX, l.panelY, l.panelW, l.panelH, t.radius(), t.surface());
        Ui.roundOutline(g, l.panelX, l.panelY, l.panelW, l.panelH, t.radius(), 1, t.stroke());

        header(g, t, l, pages, live);

        Ui.pushClip(g, l.panelX, l.sheetTop, l.panelW, l.sheetH);
        int offset = Math.round(scroll);
        for (Block block : l.blocks) {
            int by = l.sheetTop + block.y() - offset;
            if (by + blockHeight(l, block.rows()) < l.sheetTop || by > l.sheetTop + l.sheetH) {
                continue;
            }
            ItemStack under = drawBlock(g, t, l, block, l.panelX + PAD + block.x(), by,
                    live, query, mx, my);
            if (!under.isEmpty()) {
                hovered = under;
            }
        }
        Ui.popClip(g);

        if (maxScroll(l) > 0) {
            int barX = l.panelX + l.panelW - 10;
            int thumbH = Math.max(30, l.sheetH * l.sheetH / Math.max(1, l.sheetTotal));
            int thumbY = l.sheetTop + (l.sheetH - thumbH) * offset / Math.max(1, maxScroll(l));
            Ui.roundRect(g, barX, l.sheetTop, 4, l.sheetH, 2, t.controlOff());
            Ui.roundRect(g, barX, thumbY, 4, thumbH, 2, t.accent());
        }

        ItemStack underInv = drawInventory(g, t, l, menu, query, mx, my);
        if (!underInv.isEmpty()) {
            hovered = underInv;
        }

        SEARCH.bounds(l.panelX + PAD, l.searchY, l.panelW - PAD * 2, SEARCH_H);
        SEARCH.render(g, t, mx, my);

        // The cursor's own item, which vanilla would have drawn and no longer gets the chance to.
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            item(g, carried, mx - l.slot / 2, my - l.slot / 2, l.slot);
        }
        Ui.endHiRes(g);

        // Outside the supersampled pose: a tooltip lays itself out against the real window, and
        // nothing is on the cursor to be covered by it.
        if (!hovered.isEmpty() && carried.isEmpty()) {
            g.setTooltipForNextFrame(mc.font, hovered, mouseX, mouseY);
        }
        return true;
    }

    private static void header(GuiGraphicsExtractor g, Theme t, Layout l,
                               List<StorageData.Page> pages, StorageData.Page live) {
        Minecraft mc = Minecraft.getInstance();
        Fonts.draw(g, mc.font, "Storage", l.panelX + PAD, l.panelY + 14, Fonts.UI_LABEL, t.text());

        String state = navTarget != null
                ? "Opening " + navTarget.name + "..."
                : live != null
                        ? live.name + " is open - click it to move items"
                        : StorageData.itemCount() + " items across " + pages.size() + " pages";
        Fonts.drawRight(g, mc.font, state, l.panelX + l.panelW - PAD, l.panelY + 17,
                Fonts.UI_SMALL, navTarget != null ? t.accent() : t.textDim());
    }

    /**
     * One page. Returns the stack under the cursor, or empty.
     *
     * <p>Drawn from the cache even for the page you are standing in: the scan captures an open page
     * every tick, so the live block is at most one tick behind, and reading everything the same way
     * means the search text and rarity colours are the pre-derived ones for every block rather than
     * being recomputed per frame for the one that happens to be open.
     */
    private static ItemStack drawBlock(GuiGraphicsExtractor g, Theme t, Layout l, Block block,
                                       int x, int y, StorageData.Page live,
                                       String query, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        StorageData.Page page = block.page();
        boolean isLive = live != null && live.kind == page.kind && live.index == page.index;
        int h = blockHeight(l, block.rows());

        Ui.roundRect(g, x, y, l.blockW, h, t.cardRadius(), t.surfaceAlt());
        if (isLive) {
            // The one page whose clicks are real, said in the only way that cannot be missed.
            Ui.roundOutline(g, x, y, l.blockW, h, t.cardRadius(), 1, t.accent());
        }
        Fonts.drawTruncated(g, mc.font, page.name, x + BLOCK_PAD, y + 6,
                l.blockW - BLOCK_PAD * 2 - 40, Fonts.UI_SMALL, isLive ? t.accent() : t.textDim());
        if (!page.scanned() && !isLive) {
            Fonts.drawRight(g, mc.font, "unread", x + l.blockW - BLOCK_PAD, y + 6,
                    Fonts.UI_SMALL, t.textFaint());
        }

        ItemStack hovered = ItemStack.EMPTY;
        int gridX = x + BLOCK_PAD;
        int gridY = y + CAPTION_H + BLOCK_PAD - 4;
        int count = page.size();
        int shown = Math.max(count, block.rows() * StorageData.COLUMNS);
        for (int i = 0; i < shown; i++) {
            int cx = gridX + (i % StorageData.COLUMNS) * l.cell;
            int cy = gridY + (i / StorageData.COLUMNS) * l.cell;
            // A row scrolled past the edge of the sheet costs nothing to skip and a model to draw.
            if (cy + l.slot < l.sheetTop || cy > l.sheetTop + l.sheetH) {
                continue;
            }
            boolean real = i < count;
            ItemStack stack = real ? page.item(i) : ItemStack.EMPTY;
            boolean dim = !query.isEmpty() && real && !stack.isEmpty()
                    && !page.searchText(i).contains(query);
            if (slot(g, t, l, stack, cx, cy, real ? page.rarity(i) : 0, dim, query, mx, my, real)) {
                hovered = stack;
            }
        }
        return hovered;
    }

    /** Your own inventory, from the live menu - the same 36 slots whatever page is open. */
    private static ItemStack drawInventory(GuiGraphicsExtractor g, Theme t, Layout l,
                                           AbstractContainerMenu menu, String query, int mx, int my) {
        ItemStack hovered = ItemStack.EMPTY;
        int first = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < 36 && first + i < menu.slots.size(); i++) {
            int col = i % StorageData.COLUMNS;
            int row = i / StorageData.COLUMNS;
            // The hotbar is the last row and sits apart, which is what tells it from the rest.
            int cy = l.invY + row * l.cell + (row == 3 ? 8 : 0);
            int cx = l.invX + col * l.cell;
            ItemStack stack = menu.slots.get(first + i).getItem();
            // Thirty-six slots, so this one is worth reading live - and it is the only place that
            // still costs a rarity lookup per frame.
            int rarity = stack.isEmpty() ? 0 : ItemRarity.color(stack);
            boolean dim = !query.isEmpty() && !stack.isEmpty() && !matches(stack, query);
            if (slot(g, t, l, stack, cx, cy, rarity, dim, query, mx, my, true)) {
                hovered = stack;
            }
        }
        return hovered;
    }

    /**
     * One slot. Returns whether the cursor is over it.
     *
     * <p>Every shape here is a flat rectangle, and that is the difference between the sheet running
     * and the sheet crawling. A rounded corner costs a fill <i>per scanline of the corner</i>, so a
     * 4-unit radius is around twenty-five fills; at a thousand visible slots, plus a second shape
     * for the rarity tint, that was some fifty thousand fills a frame for corners two units across
     * that nobody can see. The panel and the page blocks are still rounded - there are a dozen of
     * those, not a thousand.
     */
    private static boolean slot(GuiGraphicsExtractor g, Theme t, Layout l, ItemStack stack,
                                int x, int y, int rarity, boolean dim, String query,
                                int mx, int my, boolean real) {
        StorageOverlayModule module = StorageOverlayModule.INSTANCE;
        boolean over = real && Ui.hovered(mx, my, x, y, l.slot, l.slot);
        Ui.rect(g, x, y, l.slot, l.slot,
                real ? (over ? t.cardHover() : t.card()) : Ui.fade(t.card(), 0.4f));
        if (stack == null || stack.isEmpty()) {
            return over;
        }

        if (rarity != 0 && (module == null || module.rarity())) {
            Ui.rect(g, x, y, l.slot, l.slot, Ui.withAlpha(rarity, dim ? 0x18 : 0x44));
        }
        item(g, stack, x + 2, y + 2, l.slot - 4);
        if (stack.getCount() > 1) {
            Fonts.drawRight(g, Minecraft.getInstance().font, String.valueOf(stack.getCount()),
                    x + l.slot - 2, y + l.slot - 14, Fonts.UI_SMALL, t.text());
        }
        // A search veils what does not match rather than removing it: the sheet is a map of where
        // your things are, and a map that reflows as you type is not one.
        if (dim) {
            Ui.rect(g, x, y, l.slot, l.slot, Ui.withAlpha(t.surfaceAlt(), 0xC8));
        } else if (!query.isEmpty()) {
            outline(g, x, y, l.slot, t.accent());
        }
        if (over) {
            outline(g, x, y, l.slot, t.text());
        }
        return over;
    }

    /** A one-unit square frame: four fills, against the twenty-five a rounded one would cost. */
    private static void outline(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        Ui.rect(g, x, y, size, 1, color);
        Ui.rect(g, x, y + size - 1, size, 1, color);
        Ui.rect(g, x, y + 1, 1, size - 2, color);
        Ui.rect(g, x + size - 1, y + 1, 1, size - 2, color);
    }

    /** Only the player's own inventory needs this now; the pages match against derived text. */
    private static boolean matches(ItemStack stack, String needle) {
        if (LegacyText.strip(stack.getHoverName().getString())
                .toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        for (Component line : lore.lines()) {
            if (LegacyText.strip(line.getString()).toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** An item model at an arbitrary size - {@code fakeItem} only ever submits a 16x16 one. */
    private static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.fakeItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    // ---------------------------------------------------------------- input

    /** Handles a click. Returns true when the overlay took it, which denies it to the menu. */
    public static boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX,
                                       double mouseY, int button, boolean shift) {
        if (!active(screen)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        List<StorageData.Page> pages = StorageData.pages();
        Layout l = layout(mc, pages);
        int mx = Ui.u(mouseX);
        int my = Ui.u(mouseY);

        if (SEARCH.mouseClicked(mx, my, button)) {
            return true;
        }

        AbstractContainerMenu menu = screen.getMenu();
        int first = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < 36 && first + i < menu.slots.size(); i++) {
            int row = i / StorageData.COLUMNS;
            int cx = l.invX + (i % StorageData.COLUMNS) * l.cell;
            int cy = l.invY + row * l.cell + (row == 3 ? 8 : 0);
            if (Ui.hovered(mx, my, cx, cy, l.slot, l.slot)) {
                // Your own inventory is in every menu at the same place, so it is always live.
                click(mc, menu, first + i, button, shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
                return true;
            }
        }

        if (Ui.hovered(mx, my, l.panelX, l.sheetTop, l.panelW, l.sheetH)) {
            clickSheet(mc, screen, l, menu, mx, my, button, shift);
            return true;
        }
        // Anywhere else inside the panel is swallowed; outside it closes, the way a menu does -
        // except while something is on the cursor. The panel is smaller than the window, so the
        // click that would be "throw this item away" in a vanilla chest lands here often, and
        // closing on it hands the stack back to your inventory from somewhere you cannot see.
        if (!Ui.hovered(mx, my, l.panelX, l.panelY, l.panelW, l.panelH)) {
            if (!menu.getCarried().isEmpty()) {
                Toasts.show("Put it down first", "Closing while holding something would return it");
                return true;
            }
            screen.onClose();
        }
        return true;
    }

    private static void clickSheet(Minecraft mc, AbstractContainerScreen<?> screen, Layout l,
                                   AbstractContainerMenu menu, int mx, int my,
                                   int button, boolean shift) {
        StorageData.Page live = livePage(screen);
        int offset = Math.round(scroll);
        for (Block block : l.blocks) {
            int bx = l.panelX + PAD + block.x();
            int by = l.sheetTop + block.y() - offset;
            int gridX = bx + BLOCK_PAD;
            int gridY = by + CAPTION_H + BLOCK_PAD - 4;
            if (!Ui.hovered(mx, my, gridX, gridY,
                    StorageData.COLUMNS * l.cell, block.rows() * l.cell)) {
                continue;
            }
            int col = (mx - gridX) / l.cell;
            int row = (my - gridY) / l.cell;
            if (col < 0 || col >= StorageData.COLUMNS || row < 0) {
                return;
            }
            int index = row * StorageData.COLUMNS + col;
            StorageData.Page page = block.page();
            boolean isLive = live != null && live.kind == page.kind && live.index == page.index;
            if (isLive) {
                // Back through the navigation row the sheet does not draw: what the overlay calls
                // storage slot 0 is menu slot 9, and the menu is the one being clicked.
                int menuSlot = page.menuSlot(index);
                if (index < page.size() && menuSlot < containerSlots(menu)) {
                    click(mc, menu, menuSlot, button,
                            shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
                }
                return;
            }
            if (!menu.getCarried().isEmpty()) {
                // Changing container hands the cursor's item back to your inventory. Refusing is
                // the only honest option: the alternative looks exactly like the overlay losing it.
                Toasts.show("Put it down first", "Opening another page would return what you are holding");
                return;
            }
            navigateTo(mc, page, shift ? index : -1);
            return;
        }
    }

    /**
     * Refuses the release and the drag outright while the sheet is up.
     *
     * <p><b>This is the item-dropping bug.</b> The sheet claimed the mouse <i>press</i> and nothing
     * else, and vanilla's {@code mouseReleased} is not a formality - it calls {@code slotClicked}
     * seven times over, one of them with slot id {@code -999}, which is the id that means "outside
     * the window" and whose effect is to throw whatever is on the cursor onto the floor. So picking
     * an item up in the sheet and letting go anywhere the real menu does not have a slot handed it
     * to the ground. Occasional rather than constant, because it needs something on the cursor and a
     * release over the wrong place - which is exactly how Diego described it.
     *
     * <p>{@code mouseDragged} goes the same way: it drives the quick-craft distribution and moves
     * items too. Neither is vetoed by the press veto, because a press that never reached vanilla
     * does not stop the release from arriving.
     *
     * <p>Refused wholesale rather than by working out which of those paths was firing. The sheet has
     * no drag or release behaviour of its own beyond the search box's text selection, so there is
     * nothing to preserve here and every one of those paths moves items - a narrower fix would be a
     * guess about which one, and being wrong costs somebody their items.
     */
    public static boolean mouseReleased(AbstractContainerScreen<?> screen) {
        return active(screen);
    }

    /** As {@link #mouseReleased}: the drag moves items too. Text selection still reaches the box. */
    public static boolean mouseDragged(AbstractContainerScreen<?> screen, double mouseX,
                                       double mouseY, int button) {
        if (!active(screen)) {
            return false;
        }
        SEARCH.mouseDragged(Ui.u(mouseX), Ui.u(mouseY), button);
        return true;
    }

    public static boolean mouseScrolled(AbstractContainerScreen<?> screen, double amount) {
        if (!active(screen)) {
            return false;
        }
        Layout l = layout(Minecraft.getInstance(), StorageData.pages());
        scroll = Math.clamp(scroll - (float) amount * l.cell * 2, 0f, maxScroll(l));
        return true;
    }

    /**
     * Takes every key while the sheet is up. Escape closes the menu, as it would anyway, and the
     * inventory key does too once you are not typing.
     *
     * <p><b>Nothing is passed down to the menu, and that is what stops items being thrown.</b> The
     * menu's own key handling works off {@code hoveredSlot}, which is assigned in
     * {@code extractContents} - the very method {@link
     * dev.diego.diegoaddons.mixin.StorageOverlayMixin} cancels. So while the sheet is drawn that
     * field is frozen at whatever the cursor happened to be over in the frame before the overlay
     * took over, and vanilla will happily throw <i>that</i> slot's item on the drop key, or swap it
     * on a hotbar number, at a slot you can no longer see. A letter is not safe either: the key for
     * the search box arrives as a key press first, so "e" reached the menu as the inventory key and
     * closed it mid-word.
     */
    public static boolean keyPressed(AbstractContainerScreen<?> screen, KeyEvent event) {
        if (!active(screen)) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            screen.onClose();
            return true;
        }
        if (SEARCH.keyPressed(event.key(), event.modifiers())) {
            return true;
        }
        // The search box takes focus when the sheet opens, so this is the case where it was clicked
        // away: with nothing being typed, the inventory key should still close the menu.
        if (!SEARCH.capturingInput()
                && Minecraft.getInstance().options.keyInventory.matches(event)) {
            close();
            screen.onClose();
            return true;
        }
        return true;
    }

    public static boolean charTyped(AbstractContainerScreen<?> screen, char c) {
        return active(screen) && SEARCH.charTyped(c);
    }
}
