package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.EquipmentOverlayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Marks up Hypixel SkyBlock's <b>Equipment Sets</b> menu: every saved set (one column of the menu)
 * gets a numbered frame, the set you are actually wearing is highlighted, and hovering a set shows a
 * card listing its four pieces by name in their rarity colours.
 *
 * <p>Unlike armour, equipment is never drawn on the player model, so there is nothing to preview -
 * a mannequin per set would look identical in every column. The useful information is instead
 * <i>which</i> set is which, so this overlay is textual. See {@link WardrobeOverlay} for the armour
 * counterpart.
 *
 * <p>The card is drawn beside the menu rather than over it, so it never hides the items themselves.
 */
public final class EquipmentOverlay {
    private static final int PLAYER_INV_SLOTS = 36; // 27 storage + 9 hotbar, always last in a chest menu
    private static final int SLOT_SZ = 16;

    /**
     * Menu titles this overlay applies to, lower-case. The title carries a page prefix, e.g.
     * "(1/2) Equipment Sets". This is the tuning knob if SkyBlock renames the menu.
     */
    private static final String[] TITLES = {"equipment sets", "equipment"};

    // Card metrics, in GUI pixels.
    private static final int CARD_PAD = 6;
    private static final int CARD_LINE = 11;
    private static final int CARD_GAP = 6;   // gap between the menu and the card
    private static final int LABEL_SZ = 9;   // backdrop size for the set number

    /** One saved set: the column it occupies, its pieces by category, and whether it is worn. */
    private record Set(int x, int top, int bottom, ItemStack[] pieces, boolean equipped) {
    }

    private EquipmentOverlay() {
    }

    /** Whether {@code title} is the equipment menu, ignoring page prefix, colour codes and case. */
    private static boolean isEquipmentMenu(String title) {
        String t = title.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        for (String name : TITLES) {
            if (t.contains(name)) {
                return true;
            }
        }
        return false;
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EquipmentOverlayModule mod = EquipmentOverlayModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (!isEquipmentMenu(screen.getTitle().getString())) {
            return;
        }
        List<Set> sets = collect(screen);
        if (sets.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Theme t = Themes.current();
        int leftPos = ((AbstractContainerScreenAccessor) screen).diego$leftPos();
        int topPos = ((AbstractContainerScreenAccessor) screen).diego$topPos();

        Set hovered = null;
        for (int i = 0; i < sets.size(); i++) {
            Set s = sets.get(i);
            int x = leftPos + s.x() - 1;
            int y = topPos + s.top() - 1;
            int w = SLOT_SZ + 2;
            int h = s.bottom() - s.top() + SLOT_SZ + 2;

            // The worn set gets the accent frame; the rest just get a quiet outline.
            int frame = s.equipped() ? t.accent() : Theme.withAlpha(t.border(), 0.85f);
            UiRender.strokeRounded(g, x, y, w, h, 3, frame, false);

            // Set number, in the column's top-left corner so it never collides with the menu title.
            String label = String.valueOf(i + 1);
            int lw = Fonts.width(mc.font, label, Fonts.SMALL);
            UiRender.fillRounded(g, x - 2, y - 2, Math.max(LABEL_SZ, lw + 4), LABEL_SZ, 2,
                    (0xE0 << 24) | (t.surface() & 0x00FFFFFF), false);
            UiRender.text(g, mc.font, label, Fonts.SMALL, x, y - 3,
                    s.equipped() ? t.accent() : t.textMuted());

            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                hovered = s;
            }
        }

        if (hovered != null) {
            card(g, screen, mc, t, hovered, sets.indexOf(hovered) + 1, leftPos, topPos);
        }
    }

    /**
     * Groups the menu's equipment pieces into sets by the column they sit in. Pieces are recognised
     * by {@link SkyblockHud#categoryOf} (their lore's category line), so this does not depend on the
     * menu's exact slot indices.
     */
    private static List<Set> collect(AbstractContainerScreen<?> screen) {
        List<Slot> slots = screen.getMenu().slots;
        int chestCount = Math.min(slots.size(), slots.size() - PLAYER_INV_SLOTS);
        TreeMap<Integer, ItemStack[]> byColumn = new TreeMap<>();
        TreeMap<Integer, int[]> yRange = new TreeMap<>();
        TreeMap<Integer, Boolean> worn = new TreeMap<>();

        for (int i = 0; i < chestCount; i++) {
            Slot s = slots.get(i);
            ItemStack st = s.getItem();
            if (st.isEmpty()) {
                continue;
            }
            int cat = SkyblockHud.categoryOf(st);
            if (cat < 0) {
                continue;
            }
            byColumn.computeIfAbsent(s.x, k -> new ItemStack[4])[cat] = st;
            int[] range = yRange.computeIfAbsent(s.x, k -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
            range[0] = Math.min(range[0], s.y);
            range[1] = Math.max(range[1], s.y);
            if (SkyblockHud.isEquipped(st)) {
                worn.put(s.x, Boolean.TRUE);
            }
        }

        List<Set> out = new ArrayList<>();
        for (var e : byColumn.entrySet()) {
            int[] range = yRange.get(e.getKey());
            out.add(new Set(e.getKey(), range[0], range[1], e.getValue(),
                    worn.getOrDefault(e.getKey(), Boolean.FALSE)));
        }
        return out;
    }

    /** The hover card: set number, worn marker, and the four pieces in their rarity colours. */
    private static void card(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Minecraft mc,
                             Theme t, Set s, int number, int leftPos, int topPos) {
        String title = "Set " + number + (s.equipped() ? "  ✔" : "");
        List<String> names = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ItemStack st = s.pieces()[i];
            if (st == null || st.isEmpty()) {
                names.add(SkyblockHud.CATEGORY_NAMES[i] + ": —");
                colours.add(t.textFaint());
            } else {
                names.add(st.getHoverName().getString().replaceAll("§.", ""));
                colours.add(SkyblockHud.nameColour(st));
            }
        }

        int w = Fonts.width(mc.font, title, Fonts.MEDIUM);
        for (String n : names) {
            w = Math.max(w, Fonts.width(mc.font, n, Fonts.MEDIUM));
        }
        w += CARD_PAD * 2;
        int h = CARD_PAD * 2 + CARD_LINE * 5 + 2;

        // Beside the menu, so the items stay visible; flip to the left if there is no room right.
        int imageWidth = ((AbstractContainerScreenAccessor) screen).diego$imageWidth();
        int x = leftPos + imageWidth + CARD_GAP;
        if (x + w > screen.width) {
            x = leftPos - CARD_GAP - w;
        }
        x = Math.max(2, Math.min(x, screen.width - w - 2));
        int y = Math.max(2, Math.min(topPos, screen.height - h - 2));

        UiRender.fillRounded(g, x, y, w, h, 6, (0xEE << 24) | (t.surface() & 0x00FFFFFF), true);
        UiRender.strokeRounded(g, x, y, w, h, 6, Theme.withAlpha(t.border(), 0.9f), true);

        int ty = y + CARD_PAD;
        UiRender.text(g, mc.font, title, Fonts.MEDIUM, x + CARD_PAD, ty,
                s.equipped() ? t.accent() : t.text());
        ty += CARD_LINE + 2;
        for (int i = 0; i < names.size(); i++) {
            UiRender.text(g, mc.font, names.get(i), Fonts.MEDIUM, x + CARD_PAD, ty, colours.get(i));
            ty += CARD_LINE;
        }
    }
}
