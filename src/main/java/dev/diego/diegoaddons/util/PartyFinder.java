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
