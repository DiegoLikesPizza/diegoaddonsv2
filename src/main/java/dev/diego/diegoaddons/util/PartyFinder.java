package dev.diego.diegoaddons.util;

import dev.diego.configlib.gui.widget.ToggleWidget;
import dev.diego.configlib.render.Fonts;
import dev.diego.configlib.render.Theme;
import dev.diego.configlib.render.Ui;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.AddonConfig;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.PartyFinderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highlights party-finder listings that still have room for a class you want to play, and puts the
 * class toggles into the menu itself so the choice can be changed without leaving it.
 *
 * <p>Everything here is drawn with the menu <b>background</b>, before the items and their tooltips.
 * That puts the highlight behind the item instead of over it, and keeps the toggle strip from
 * covering the tooltip of whatever listing you are hovering.
 *
 * <p>A listing is recognised by its item <b>name</b> ending in "'s Party", and its members by the
 * line shape Hypixel uses for them, {@code " Name: Class (Level)"}. Reading the members rather than
 * searching the whole lore for class words means a class named in someone's note cannot be mistaken
 * for a filled slot.
 */
public final class PartyFinder {
    /** The dungeon classes, lower-case for matching. */
    public static final String[] CLASSES = {"healer", "mage", "berserk", "archer", "tank"};
    /** Display names, indexed alongside {@link #CLASSES}. */
    public static final String[] CLASS_NAMES = {"Healer", "Mage", "Berserk", "Archer", "Tank"};

    /** "PlayerName's Party" - the item name every listing carries. */
    private static final Pattern LISTING = Pattern.compile(".*'s Party$");
    /** " 4sn_: Archer (29)" - one party member. */
    private static final Pattern MEMBER = Pattern.compile("^\\s*(.+?): (.+?) \\(.*?(\\d+).*?\\)\\s*$");

    private static final int SLOT = 16;
    private static final int PLAYER_INV_SLOTS = 36;
    /**
     * The same green the feature this imitates uses. Fully opaque is fine because this draws with
     * the menu background, so the item is painted on top of it afterwards.
     */
    private static final int HIGHLIGHT = 0xFF55FF55;

    // The panel beside the menu, in configlib units (two to a screen pixel).
    private static final int PANEL_W = 214;
    private static final int PAD = 12;
    private static final int ROW_H = 30;
    private static final int TITLE_H = 28;
    private static final int DIVIDER_H = 11;
    /** Gap between the menu and the panel, in screen pixels - the menu's own coordinates. */
    private static final int PANEL_GAP = 6;

    /**
     * The five class toggles and the "missing classes" one, built on first use.
     *
     * <p>One set for the whole game rather than one per screen: they hold no state of their own
     * beyond the knob's slide animation, and read the settings they edit on every frame.
     */
    private static ToggleWidget[] widgets;

    /** Grab offset while the title bar is held, in screen pixels, or null when it is not. */
    private static int[] grab;

    private PartyFinder() {
    }

    /** Whether {@code title} is the party finder, ignoring colour codes and case. */
    public static boolean isPartyFinder(AbstractContainerScreen<?> screen) {
        String t = LegacyText.strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        return t.contains("party finder");
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        PartyFinderModule mod = PartyFinderModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !isPartyFinder(screen)) {
            return;
        }
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int leftPos = acc.diego$leftPos();
        int topPos = acc.diego$topPos();

        if (mod.anySelected()) {
            List<Slot> slots = screen.getMenu().slots;
            int chestCount = Math.max(0, slots.size() - PLAYER_INV_SLOTS);
            for (int i = 0; i < chestCount && i < slots.size(); i++) {
                Slot s = slots.get(i);
                if (missesWantedClass(mod, s.getItem())) {
                    // Flat square over the slot, matching the look this imitates.
                    g.fill(leftPos + s.x, topPos + s.y,
                            leftPos + s.x + SLOT, topPos + s.y + SLOT, HIGHLIGHT);
                }
            }
        }
    }

    /**
     * Adds a "missing" line to a party listing's own tooltip.
     *
     * <p>This was a panel drawn beside the cursor, which meant two boxes of text about the same
     * party, in different styles, fighting for the same corner of the screen. The listing already
     * has a tooltip you are reading; the answer belongs at the bottom of it.
     */
    public static void appendTooltip(ItemStack stack, List<Component> lines) {
        PartyFinderModule mod = PartyFinderModule.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (mod == null || !mod.isEnabled() || !mod.showMissing()
                || !(mc.screen instanceof AbstractContainerScreen<?> screen)
                || !isPartyFinder(screen)) {
            return;
        }
        if (stack.isEmpty()
                || !LISTING.matcher(LegacyText.strip(stack.getHoverName().getString()).trim()).matches()) {
            return;
        }
        List<String> taken = memberClasses(stack);
        if (taken.isEmpty()) {
            return;   // unreadable listing; saying "missing everything" would be a lie
        }
        List<String> missing = new ArrayList<>();
        for (int c = 0; c < CLASSES.length; c++) {
            if (!taken.contains(CLASSES[c])) {
                missing.add(CLASS_NAMES[c]);
            }
        }
        lines.add(Component.literal(""));
        lines.add(missing.isEmpty()
                ? Component.literal("§7Full party")
                : Component.literal("§eMissing: §f" + String.join(", ", missing)));
    }

    /** True when the listing has nobody playing any of the classes you selected. */
    private static boolean missesWantedClass(PartyFinderModule mod, ItemStack stack) {
        if (stack.isEmpty() || !LISTING.matcher(LegacyText.strip(stack.getHoverName().getString()).trim()).matches()) {
            return false;
        }
        List<String> taken = memberClasses(stack);
        if (taken.isEmpty()) {
            return false;   // could not read the listing; better to say nothing than to mislead
        }
        for (int i = 0; i < CLASSES.length; i++) {
            if (mod.wants(i) && !taken.contains(CLASSES[i])) {
                return true;
            }
        }
        return false;
    }

    // --- the panel beside the menu --------------------------------------------------------------

    /**
     * Draws the class picks beside the party finder, so the choice can be changed without leaving
     * the menu.
     *
     * <p>This is the strip 2.0.x drew, rebuilt on configlib: the same pill toggles, palette and font
     * as the settings menu rather than a second look invented for the occasion, and each one wired
     * straight to the setting it edits, so a pick made here is a pick made there.
     *
     * <p>It sits <b>outside</b> the menu's own rectangle, which is what lets it be drawn with the
     * background: there is nothing of the menu left to paint over it, and an item tooltip that
     * reaches this far covers it, which is the right way round.
     */
    public static void renderPanel(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g,
                                   int mouseX, int mouseY) {
        if (!panelUp(screen)) {
            return;
        }
        Theme t = DiegoAddonsV2Client.CONFIG.theme();
        Minecraft mc = Minecraft.getInstance();

        Ui.beginHiRes(g);
        int[] rect = layout(screen);
        int x = rect[0];
        int y = rect[1];
        int h = rect[2];

        int mx = Ui.u(mouseX);
        int my = Ui.u(mouseY);
        int r = t.cardRadius();

        Ui.roundRect(g, x, y, PANEL_W, h, r, t.surface());
        // The title bar: its own surface, square along the bottom where it meets the rows, so it
        // reads as a bar rather than as a card sitting on a card.
        boolean onBar = grab != null || Ui.hovered(mx, my, x, y, PANEL_W, TITLE_H);
        Ui.roundRect(g, x, y, PANEL_W, TITLE_H, r, r, 0, 0,
                onBar ? t.cardHover() : t.surfaceAlt(), onBar ? t.cardHover() : t.surfaceAlt());
        Ui.hLine(g, x, y + TITLE_H, PANEL_W, t.stroke());
        Ui.roundOutline(g, x, y, PANEL_W, h, r, 1, t.stroke());
        Fonts.draw(g, mc.font, "PARTY FINDER", x + PAD,
                Fonts.centerY(y, TITLE_H, Fonts.EYEBROW_SZ), Fonts.UI_EYEBROW,
                onBar ? t.textDim() : t.textFaint());
        // The two-line grip on the right, the one mark that says a bar can be dragged. Faint until
        // the cursor is on the bar, which is where a window title bar earns its cursor change.
        int gripY = y + TITLE_H / 2 - 3;
        for (int i = 0; i < 2; i++) {
            Ui.hLine(g, x + PANEL_W - PAD - 16, gripY + i * 5, 16,
                    Ui.fade(t.textFaint(), onBar ? 0.9f : 0.45f));
        }

        int rowY = y + TITLE_H + PAD;
        for (int i = 0; i < widgets.length; i++) {
            if (i == CLASSES.length) {
                // The last row is not a class - it is what the tooltip says - so it is separated
                // from the five that are, rather than reading as a sixth class.
                Ui.hLine(g, x + PAD, rowY + DIVIDER_H / 2, PANEL_W - PAD * 2, t.stroke());
                rowY += DIVIDER_H;
            }
            boolean on = i < CLASSES.length
                    ? PartyFinderModule.INSTANCE.wants(i)
                    : PartyFinderModule.INSTANCE.showMissing();
            Fonts.draw(g, mc.font, label(i), x + PAD,
                    Fonts.centerY(rowY, ROW_H, Fonts.BODY_SZ), Fonts.UI_BODY,
                    on ? t.text() : t.textDim());
            widgets[i].render(g, t, mx, my);
            rowY += ROW_H;
        }
        Ui.endHiRes(g);
    }

    /**
     * Handles a click on the panel: a toggle, or the title bar starting a drag.
     *
     * @return true when the panel took the click, so the menu must not also process it
     */
    public static boolean click(AbstractContainerScreen<?> screen, double mouseX, double mouseY,
                                int button) {
        if (!panelUp(screen)) {
            return false;
        }
        int[] rect = layout(screen);
        int mx = Ui.u(mouseX);
        int my = Ui.u(mouseY);
        for (ToggleWidget w : widgets) {
            if (w.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        if (button == 0 && Ui.hovered(mx, my, rect[0], rect[1], PANEL_W, TITLE_H)) {
            // Where inside the bar it was grabbed, so the panel does not jump its corner to the
            // cursor on the first pixel of movement - the thing every window drag gets right.
            grab = new int[]{(int) mouseX - Ui.px(rect[0]), (int) mouseY - Ui.px(rect[1])};
            return true;
        }
        // The rest of the panel still swallows the click. It is a surface, not a hole: a press on
        // the label beside a toggle must not reach the menu underneath as a click on empty space.
        return Ui.hovered(mx, my, rect[0], rect[1], PANEL_W, rect[2]);
    }

    /**
     * Moves the panel while the title bar is held.
     *
     * @return true while a drag is in progress, so the menu does not read it as a slot drag
     */
    public static boolean drag(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (grab == null) {
            return false;
        }
        if (!panelUp(screen)) {
            grab = null;
            return false;
        }
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        AddonConfig cfg = ConfigManager.get();
        int[] pos = clamp(screen, (int) mouseX - grab[0], (int) mouseY - grab[1], panelHeight());
        cfg.partyFinderPanelMoved = true;
        cfg.partyFinderPanelX = pos[0] - acc.diego$leftPos();
        cfg.partyFinderPanelY = pos[1] - acc.diego$topPos();
        return true;
    }

    /**
     * Ends a drag, and this is where the new position is written to disk - a save per frame of a
     * drag would be a hundred writes for one move.
     *
     * @return true when a drag was in progress
     */
    public static boolean release() {
        if (grab == null) {
            return false;
        }
        grab = null;
        ConfigManager.save();
        return true;
    }

    /** Puts the panel back beside the menu. */
    public static void resetPosition() {
        AddonConfig cfg = ConfigManager.get();
        cfg.partyFinderPanelMoved = false;
        cfg.partyFinderPanelX = 0;
        cfg.partyFinderPanelY = 0;
        ConfigManager.save();
    }

    /** Whether the panel is on screen right now, building its toggles the first time it is. */
    private static boolean panelUp(AbstractContainerScreen<?> screen) {
        PartyFinderModule mod = PartyFinderModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.panel() || !isPartyFinder(screen)) {
            return false;
        }
        if (widgets == null) {
            widgets = new ToggleWidget[CLASSES.length + 1];
            for (int i = 0; i < CLASSES.length; i++) {
                int c = i;
                widgets[i] = new ToggleWidget(() -> mod.wants(c), v -> mod.setWanted(c, v));
            }
            widgets[CLASSES.length] = new ToggleWidget(mod::showMissing, mod::setShowMissing);
        }
        return true;
    }

    /** Height of the panel in units - the same sum wherever it is asked for. */
    private static int panelHeight() {
        return TITLE_H + PAD * 2 + DIVIDER_H + widgets.length * ROW_H;
    }

    /**
     * Places the panel and its toggles, in configlib units.
     *
     * <p>Until it has been dragged it sits beside the menu on the right, and on the left when the
     * window is too narrow for that - a panel half off the screen edge is worse than one on the
     * other side. After a drag it sits where it was put, as an <b>offset from the menu's own
     * corner</b>: the menu is centred, so that offset still means the same thing after a resize or
     * a GUI-scale change, where a remembered screen position would not.
     *
     * <p>Both the draw and the click go through here, so a toggle is always where it was painted.
     *
     * @return {@code {x, y, height}}, in units
     */
    private static int[] layout(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        AddonConfig cfg = ConfigManager.get();
        int h = panelHeight();

        int px;
        int py;
        if (cfg.partyFinderPanelMoved) {
            px = acc.diego$leftPos() + cfg.partyFinderPanelX;
            py = acc.diego$topPos() + cfg.partyFinderPanelY;
        } else {
            int right = acc.diego$leftPos() + acc.diego$imageWidth() + PANEL_GAP;
            px = right + Ui.px(PANEL_W) + 2 <= screen.width
                    ? right
                    : acc.diego$leftPos() - PANEL_GAP - Ui.px(PANEL_W);
            py = acc.diego$topPos();
        }
        // Clamped on every frame, not only while dragging: a window resized smaller since the drag
        // would otherwise leave the panel parked outside the screen with no way to grab it back.
        int[] pos = clamp(screen, px, py, h);
        int x = Ui.u(pos[0]);
        int y = Ui.u(pos[1]);

        int rowY = y + TITLE_H + PAD;
        for (int i = 0; i < widgets.length; i++) {
            if (i == CLASSES.length) {
                rowY += DIVIDER_H;
            }
            widgets[i].bounds(x + PAD, rowY, PANEL_W - PAD * 2, ROW_H);
            rowY += ROW_H;
        }
        return new int[]{x, y, h};
    }

    /** Keeps the panel's top-left inside the window, in screen pixels. */
    private static int[] clamp(AbstractContainerScreen<?> screen, int x, int y, int heightUnits) {
        int w = Ui.px(PANEL_W);
        int h = Ui.px(heightUnits);
        return new int[]{
                Math.max(0, Math.min(x, screen.width - w)),
                Math.max(0, Math.min(y, screen.height - h)),
        };
    }

    /** The row's label: the five classes, then the tooltip line. */
    private static String label(int row) {
        return row < CLASS_NAMES.length ? CLASS_NAMES[row] : "Missing";
    }

    /** The classes currently played in this listing, lower-case. */
    private static List<String> memberClasses(ItemStack stack) {
        List<String> out = new ArrayList<>();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return out;
        }
        for (var line : lore.lines()) {
            Matcher m = MEMBER.matcher(LegacyText.strip(line.getString()));
            if (m.matches()) {
                out.add(m.group(2).trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
