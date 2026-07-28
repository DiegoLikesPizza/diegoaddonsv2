package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.PartyFinderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
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

    // Toggle strip drawn beside the menu.
    private static final int ROW_H = 14;
    private static final int PANEL_W = 74;
    private static final int PANEL_GAP = 6;
    private static final int BOX = 8;

    private PartyFinder() {
    }

    /** Whether {@code title} is the party finder, ignoring colour codes and case. */
    public static boolean isPartyFinder(AbstractContainerScreen<?> screen) {
        String t = LegacyText.strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        return t.contains("party finder");
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g,
                              int mouseX, int mouseY) {
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
        drawToggles(screen, g, leftPos, topPos, acc.diego$imageWidth());
        if (mod.showMissing()) {
            drawMissing(screen, g, leftPos, topPos, mouseX, mouseY);
        }
    }

    /**
     * Names the classes nobody in the hovered listing is playing, beside the cursor.
     *
     * <p>The highlight only answers "does this party want what I picked". This answers the question
     * you actually have while reading the list - what is this party short of - without making you
     * count five names in the tooltip yourself.
     */
    private static void drawMissing(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g,
                                    int leftPos, int topPos, int mouseX, int mouseY) {
        List<Slot> slots = screen.getMenu().slots;
        int chestCount = Math.max(0, slots.size() - PLAYER_INV_SLOTS);
        for (int i = 0; i < chestCount && i < slots.size(); i++) {
            Slot s = slots.get(i);
            int x = leftPos + s.x;
            int y = topPos + s.y;
            if (mouseX < x || mouseX >= x + SLOT || mouseY < y || mouseY >= y + SLOT) {
                continue;
            }
            ItemStack stack = s.getItem();
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
            Theme t = Themes.current();
            boolean sm = ConfigManager.get().smoothCorners;
            Minecraft mc = Minecraft.getInstance();
            String line = missing.isEmpty() ? "Full party" : "Missing: " + String.join(", ", missing);
            int w = mc.font.width(line) + 12;
            int px = Math.min(mouseX + 10, screen.width - w - 2);
            int py = Math.max(2, mouseY - 18);
            UiRender.fillRounded(g, px, py, w, 16, 5, (0xEE << 24) | (t.surface() & 0x00FFFFFF), sm);
            UiRender.strokeRounded(g, px, py, w, 16, 5, t.border(), sm);
            g.text(mc.font, net.minecraft.network.chat.Component.literal(line), px + 6, py + 4,
                    missing.isEmpty() ? t.textMuted() : t.text(), false);
            return;
        }
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

    // --- in-menu class toggles ------------------------------------------------------------------

    private static int panelX(int leftPos, int imageWidth) {
        return leftPos + imageWidth + PANEL_GAP;
    }

    private static void drawToggles(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g,
                                    int leftPos, int topPos, int imageWidth) {
        PartyFinderModule mod = PartyFinderModule.INSTANCE;
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        Minecraft mc = Minecraft.getInstance();

        int x = panelX(leftPos, imageWidth);
        int y = topPos;
        int h = 16 + CLASSES.length * ROW_H + 6;

        UiRender.fillRounded(g, x, y, PANEL_W, h, 6, (0xEE << 24) | (t.surface() & 0x00FFFFFF), sm);
        UiRender.strokeRounded(g, x, y, PANEL_W, h, 6, Theme.withAlpha(t.border(), 0.9f), sm);
        UiRender.text(g, mc.font, "HIGHLIGHT", Fonts.SMALL, x + 6, y + 5, t.textFaint());

        for (int i = 0; i < CLASSES.length; i++) {
            int ry = y + 16 + i * ROW_H;
            boolean on = mod.wants(i);
            int bx = x + 6;
            int by = ry + (ROW_H - BOX) / 2;
            UiRender.fillRounded(g, bx, by, BOX, BOX, 2,
                    on ? HIGHLIGHT : Theme.withAlpha(t.textFaint(), 0.35f), sm);
            UiRender.strokeRounded(g, bx, by, BOX, BOX, 2, Theme.withAlpha(t.border(), 0.9f), sm);
            UiRender.text(g, mc.font, CLASS_NAMES[i], Fonts.SMALL, bx + BOX + 5, ry + 4,
                    on ? t.text() : t.textMuted());
        }
    }

    /**
     * Handles a click on the toggle strip.
     *
     * @return true when a toggle was hit, so the menu must not also process the click
     */
    public static boolean click(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        PartyFinderModule mod = PartyFinderModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || button != 0 || !isPartyFinder(screen)) {
            return false;
        }
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int x = panelX(acc.diego$leftPos(), acc.diego$imageWidth());
        int y = acc.diego$topPos();
        for (int i = 0; i < CLASSES.length; i++) {
            int ry = y + 16 + i * ROW_H;
            if (UiRender.inside(mouseX, mouseY, x, ry, PANEL_W, ROW_H)) {
                mod.toggle(i);
                return true;
            }
        }
        return false;
    }
}
