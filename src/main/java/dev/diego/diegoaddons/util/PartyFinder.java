package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.PartyFinderModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;

/**
 * Highlights party-finder listings that still have room for the class you want to play.
 *
 * <p>Which classes a party already has is read from the listing's own lore. Rather than matching an
 * exact sentence, the lore is scanned for the five class names: a class not mentioned is one the
 * party has not filled. That survives Hypixel rewording the surrounding text, which an exact match
 * would not.
 *
 * <p>Only listings are considered - a slot whose lore mentions no class at all is a filler or a
 * control button and is skipped.
 */
public final class PartyFinder {
    /** The dungeon classes, lower-case for matching. */
    public static final String[] CLASSES = {"healer", "mage", "berserk", "archer", "tank"};
    /** Display names, indexed alongside {@link #CLASSES}. */
    public static final String[] CLASS_NAMES = {"Healer", "Mage", "Berserk", "Archer", "Tank"};

    private static final int SLOT = 16;
    private static final int PLAYER_INV_SLOTS = 36;
    /** Green fill and border for a match; deliberately not the theme accent, which changes. */
    private static final int FILL = 0x6033DD55;
    private static final int BORDER = 0xCC33DD55;

    private PartyFinder() {
    }

    /** Whether {@code title} is the party finder, ignoring colour codes and case. */
    private static boolean isPartyFinder(String title) {
        String t = LegacyText.strip(title).toLowerCase(Locale.ROOT);
        return t.contains("party finder") || t.contains("dungeon party");
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        PartyFinderModule mod = PartyFinderModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.anySelected()) {
            return;
        }
        if (!isPartyFinder(screen.getTitle().getString())) {
            return;
        }
        boolean sm = ConfigManager.get().smoothCorners;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int leftPos = acc.diego$leftPos();
        int topPos = acc.diego$topPos();

        List<Slot> slots = screen.getMenu().slots;
        int chestCount = Math.max(0, slots.size() - PLAYER_INV_SLOTS);
        for (int i = 0; i < chestCount && i < slots.size(); i++) {
            Slot s = slots.get(i);
            ItemStack st = s.getItem();
            if (st.isEmpty() || !wants(mod, st)) {
                continue;
            }
            int x = leftPos + s.x;
            int y = topPos + s.y;
            UiRender.fillRounded(g, x, y, SLOT, SLOT, 3, FILL, sm);
            UiRender.strokeRounded(g, x - 1, y - 1, SLOT + 2, SLOT + 2, 4, BORDER, sm);
        }
    }

    /**
     * True when this listing is missing at least one of the classes you selected.
     *
     * @return false for slots that are not listings at all
     */
    private static boolean wants(PartyFinderModule mod, ItemStack stack) {
        String lore = lore(stack);
        if (lore.isEmpty()) {
            return false;
        }
        boolean isListing = false;
        for (String cls : CLASSES) {
            if (lore.contains(cls)) {
                isListing = true;
                break;
            }
        }
        if (!isListing) {
            return false;   // filler pane or a control button, not a party
        }
        for (int i = 0; i < CLASSES.length; i++) {
            if (mod.wants(i) && !lore.contains(CLASSES[i])) {
                return true;
            }
        }
        return false;
    }

    /** The stack's lore as one lower-case string, colour codes removed. */
    private static String lore(ItemStack stack) {
        ItemLore l = stack.get(DataComponents.LORE);
        if (l == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var line : l.lines()) {
            sb.append(LegacyText.strip(line.getString())).append('\n');
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
