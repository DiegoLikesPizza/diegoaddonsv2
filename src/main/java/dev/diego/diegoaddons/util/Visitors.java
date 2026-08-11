package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.VisitorHelperModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Garden visitor's offer and works out what it is worth.
 *
 * <p>The menu is recognised by its shape rather than by its title: a visitor's title is the
 * visitor's own name, and there are dozens of those, but the item in the middle always ends its
 * lore with "Offers Accepted:" and the accept and refuse buttons are always in the same two slots.
 * Matching on structure means a visitor Hypixel adds tomorrow is read like the rest.
 *
 * <p><b>The number that decides things is cost per copper.</b> What you hand over is priced from the
 * bazaar; what you get back is copper, valued the way the community does it - through the Green
 * Thumb book, which is the item copper is reliably spent on. Two things follow, and both are
 * deliberate: an offer whose items are not all on the bazaar has <b>no</b> cost per copper rather
 * than a partial one, and an offer that makes money is never declined whatever the ratio says.
 */
public final class Visitors {
    /** The slots Hypixel puts the offer and the two buttons in. */
    public static final int INFO_SLOT = 13;
    public static final int ACCEPT_SLOT = 29;
    public static final int REFUSE_SLOT = 33;

    /**
     * Copper is valued through Green Thumb I: 1,500 copper buys one, so one copper is worth a
     * fifteen-hundredth of what the book sells for. It is a proxy, not a price - copper has no
     * market of its own - but it is the same proxy everyone else uses, which makes the number
     * comparable to what Diego reads elsewhere.
     */
    private static final String COPPER_PROXY = "ENCHANTMENT_GREEN_THUMB_1";
    private static final int COPPER_PER_PROXY = 1500;

    private Visitors() {
    }

    /** One read of a visitor's offer. */
    public record Offer(String visitor, Rarity rarity, Map<String, Integer> required, int copper,
                        List<String> rewardItems) {

        /** What the required items cost on the bazaar, or -1 when one of them cannot be priced. */
        public double cost() {
            double total = 0;
            for (Map.Entry<String, Integer> e : required.entrySet()) {
                double each = Bazaar.priceOf(e.getKey());
                if (each <= 0) {
                    return -1;
                }
                total += each * e.getValue();
            }
            return total;
        }

        /** Coins paid per copper earned, or -1 when the offer cannot be priced. */
        public double costPerCopper() {
            double cost = cost();
            if (cost < 0 || copper <= 0) {
                return -1;
            }
            return cost / copper;
        }

        /** Coins in minus coins out, or {@link Double#NaN} when it cannot be worked out. */
        public double profit() {
            double cost = cost();
            if (cost < 0) {
                return Double.NaN;
            }
            double reward = copper * copperValue();
            for (String item : rewardItems) {
                reward += Bazaar.priceOf(item);
            }
            return reward - cost;
        }

        /** Whether this offer is known to make money. Unknown is not profitable, and not a decline. */
        public boolean profitable() {
            double p = profit();
            return !Double.isNaN(p) && p > 0;
        }

        public boolean priced() {
            return cost() >= 0;
        }
    }

    /** What one copper is worth in coins, or 0 when the proxy is not priced yet. */
    public static double copperValue() {
        return Bazaar.buyPrice(COPPER_PROXY) / COPPER_PER_PROXY;
    }

    /** A visitor's rarity, which is the colour Hypixel writes its name in. */
    public enum Rarity {
        COMMON('f', "Common"),
        UNCOMMON('a', "Uncommon"),
        RARE('9', "Rare"),
        EPIC('5', "Epic"),
        LEGENDARY('6', "Legendary"),
        MYTHIC('d', "Mythic"),
        SPECIAL('c', "Special"),
        UNKNOWN('r', "Unknown");

        public final char code;
        public final String display;

        Rarity(char code, String display) {
            this.code = code;
            this.display = display;
        }

        static Rarity byCode(char c) {
            for (Rarity r : values()) {
                if (r.code == c) {
                    return r;
                }
            }
            return UNKNOWN;
        }
    }

    // --- reading the menu ---------------------------------------------------------------------------

    /**
     * Whether this screen is a visitor's offer.
     *
     * <p>The last lore line of the middle item is the tell, and it is a good one: it is there for
     * every visitor, it is not there for any other Garden menu, and it does not depend on a name.
     */
    public static boolean isVisitorMenu(AbstractContainerScreen<?> screen) {
        return isOfferItem(slot(screen, INFO_SLOT)) || hasAcceptButton(screen);
    }

    /**
     * The "Accept Offer" button, Diego's suggestion and the better of the two signals.
     *
     * <p>It is a name rather than a lore line, it is the same for every visitor, and it is the one
     * thing a visitor menu must have to be one. Kept alongside the lore check rather than replacing
     * it: two independent tells mean a wrong guess about either one costs nothing, and both strings
     * here are still guesses at Hypixel's exact wording.
     */
    private static boolean hasAcceptButton(AbstractContainerScreen<?> screen) {
        ItemStack accept = slot(screen, ACCEPT_SLOT);
        if (accept == null || accept.isEmpty()) {
            return false;
        }
        return LegacyText.strip(accept.getHoverName().getString())
                .toLowerCase(Locale.ROOT).contains("accept offer");
    }

    /**
     * Whether this item is a visitor's offer, judged by its own lore.
     *
     * <p>By content rather than by which slot it sits in, because the tooltip hook is handed a stack
     * and asking "is this the same object as slot 13" turned out to be the wrong question - the
     * hovered stack need not be that identical instance, and when it is not, the whole valuation
     * silently never appears. Content also answers for the menu itself, so one check serves both.
     */
    private static boolean isOfferItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (String line : lore(stack)) {
            if (line.contains("Offers Accepted")) {
                return true;
            }
        }
        return false;
    }

    /** Reads the offer from the menu's own middle slot. */
    public static Offer read(AbstractContainerScreen<?> screen) {
        return read(slot(screen, INFO_SLOT), screen);
    }

    /**
     * Reads an offer from the item that carries it.
     *
     * <p>The screen is still needed, but only for the visitor's name and rarity - those live in the
     * window title, not in the offer.
     */
    public static Offer read(ItemStack info, AbstractContainerScreen<?> screen) {
        if (info == null || info.isEmpty()) {
            return null;
        }
        List<String> lines = lore(info);
        Map<String, Integer> required = new LinkedHashMap<>();
        List<String> rewards = new ArrayList<>();
        int copper = 0;

        boolean inItems = false;
        boolean inRewards = false;
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            if (s.startsWith("Items Required")) {
                inItems = true;
                inRewards = false;
                continue;
            }
            if (s.startsWith("Rewards")) {
                inItems = false;
                inRewards = true;
                continue;
            }
            if (s.startsWith("Offers Accepted")) {
                break;
            }
            if (inItems) {
                Matcher m = ITEM.matcher(s);
                if (m.matches()) {
                    int amount = m.group("amount") == null
                            ? 1 : Integer.parseInt(m.group("amount").replace(",", ""));
                    required.merge(m.group("name").trim(), amount, Integer::sum);
                }
            } else if (inRewards) {
                Matcher c = COPPER.matcher(s);
                if (c.matches()) {
                    copper += Integer.parseInt(c.group("amount").replace(",", ""));
                    continue;
                }
                Matcher m = REWARD.matcher(s);
                if (m.matches()) {
                    rewards.add(m.group("name").trim());
                }
            }
        }
        if (required.isEmpty() && copper == 0) {
            return null;
        }
        return new Offer(nameOf(screen), rarityOf(screen), required, copper, rewards);
    }

    /** "- 24x Enchanted Carrot", "24x Enchanted Carrot", "Enchanted Carrot". */
    private static final Pattern ITEM = Pattern.compile(
            "^[-+•\\s]*(?:(?<amount>[\\d,]+)x\\s+)?(?<name>[A-Za-z][A-Za-z' ]+)$");

    /** "+150 Copper". */
    private static final Pattern COPPER = Pattern.compile(
            "^\\+?(?<amount>[\\d,]+) Copper.*$");

    /** Any other reward line, e.g. "+1x Space Helmet". */
    private static final Pattern REWARD = Pattern.compile(
            "^[+\\s]*(?:[\\d,]+x\\s+)?(?<name>[A-Za-z][A-Za-z' ]+)$");

    private static String nameOf(AbstractContainerScreen<?> screen) {
        return LegacyText.strip(screen.getTitle().getString()).trim();
    }

    /**
     * The rarity, from the first colour code in the visitor's name.
     *
     * <p>Hypixel colours a visitor by how rare it is and writes nothing else that says so, which is
     * why this reads the code rather than a word. An unrecognised colour is {@link Rarity#UNKNOWN},
     * and unknown never satisfies a rarity rule - a visitor is not thrown away because its colour
     * was new.
     */
    private static Rarity rarityOf(AbstractContainerScreen<?> screen) {
        String raw = screen.getTitle().getString();
        int i = raw.indexOf('§');
        return i >= 0 && i + 1 < raw.length()
                ? Rarity.byCode(Character.toLowerCase(raw.charAt(i + 1)))
                : Rarity.UNKNOWN;
    }

    private static ItemStack slot(AbstractContainerScreen<?> screen, int index) {
        AbstractContainerMenu menu = screen.getMenu();
        return index < menu.slots.size() ? menu.slots.get(index).getItem() : null;
    }

    /** An item's lore with the colour codes taken off. */
    public static List<String> lore(ItemStack stack) {
        ItemLore l = stack.get(DataComponents.LORE);
        if (l == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(l.lines().size());
        for (var line : l.lines()) {
            // Trimmed: Hypixel indents most lore, and a leading space defeats every startsWith in
            // this file - which is a whole feature failing on an invisible character.
            out.add(LegacyText.strip(line.getString()).trim());
        }
        return out;
    }

    // --- acting on it -------------------------------------------------------------------------------

    /**
     * The menu this decision was made in, so one decision cannot be applied to the next visitor.
     *
     * <p>A refusal is a click on a slot number, and a slot number means nothing without the menu it
     * belongs to. If the menu changes between deciding and clicking - the visitor walked off, the
     * server re-sent the window - the click would land somewhere else entirely.
     */
    private static int decidedIn = -1;

    /** Forgets the last decision. Called when a menu closes. */
    public static void forget() {
        decidedIn = -1;
    }

    /**
     * Declines the offer in front of you, once.
     *
     * @return whether the click was actually sent
     */
    public static boolean decline(Minecraft mc, AbstractContainerScreen<?> screen, String why) {
        AbstractContainerMenu menu = screen.getMenu();
        if (mc.gameMode == null || mc.player == null || menu.containerId == decidedIn) {
            return false;
        }
        if (REFUSE_SLOT >= menu.slots.size()) {
            return false;
        }
        decidedIn = menu.containerId;
        mc.gameMode.handleContainerInput(menu.containerId, REFUSE_SLOT, 0, ContainerInput.PICKUP, mc.player);
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§b[DiegoAddons] §fDeclined §e" + nameOf(screen) + "§f: " + why));
        }
        DiegoAddonsV2Client.LOGGER.info("[visitors] declined {} ({})", nameOf(screen), why);
        return true;
    }

    /** Dumps a visitor's lore, for tuning the patterns against a real menu. */
    public static void debugDump(AbstractContainerScreen<?> screen) {
        ItemStack info = slot(screen, INFO_SLOT);
        DiegoAddonsV2Client.LOGGER.info("[visitors] --- {} ---", screen.getTitle().getString());
        if (info == null || info.isEmpty()) {
            DiegoAddonsV2Client.LOGGER.info("[visitors] no item in slot {}", INFO_SLOT);
            return;
        }
        for (String line : lore(info)) {
            DiegoAddonsV2Client.LOGGER.info("[visitors]   {}", line);
        }
        Offer offer = read(screen);
        DiegoAddonsV2Client.LOGGER.info("[visitors] parsed: {}", offer);
    }

    /** A number with thousands separators, for the tooltip. */
    public static String coins(double value) {
        if (Math.abs(value) >= 1_000_000) {
            return String.format(Locale.ROOT, "%.2fM", value / 1_000_000);
        }
        if (Math.abs(value) >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }

    /**
     * Writes the price per copper onto Hypixel's own copper line, the way SkyHanni does it:
     * {@code +150 Copper §7(paying §61.2k §7per)}.
     *
     * <p>On the line rather than under the lore, because that is where the question is asked. You
     * are looking at "150 Copper" and wondering what it costs you; an answer eight lines further
     * down is an answer you have to go and find.
     *
     * <p>The original component is kept and appended to rather than rebuilt from its text. Rebuilding
     * would mean re-deriving Hypixel's own colours from a stripped string, and getting that subtly
     * wrong on every visitor line is a poor trade for a suffix.
     */
    private static void annotateCopperLine(Offer offer, List<net.minecraft.network.chat.Component> lines) {
        double perCopper = offer.costPerCopper();
        if (perCopper < 0 || !Bazaar.fresh()) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String plain = LegacyText.strip(lines.get(i).getString()).trim();
            if (!COPPER.matcher(plain).matches()) {
                continue;
            }
            lines.set(i, lines.get(i).copy().append(net.minecraft.network.chat.Component.literal(
                    " §7(paying §6" + coins(perCopper) + " §7per)")));
            return;
        }
    }

    /**
     * Adds the valuation under the offer item's own lore.
     *
     * <p>Every line says where its number came from, including when it did not come from anywhere:
     * "not on the bazaar" is more use than a confident zero, because it tells you the auto-decline
     * will leave this one alone.
     */
    public static void appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack,
                                     List<net.minecraft.network.chat.Component> lines) {
        VisitorHelperModule m = VisitorHelperModule.INSTANCE;
        if (m == null || !m.isEnabled() || !m.showTooltip() || !isOfferItem(stack)) {
            return;
        }
        // Read from the hovered item itself rather than from the screen's slot 13 - the same reason
        // the check above moved to content: what is under the cursor is the thing to price.
        Offer offer = read(stack, screen);
        if (offer == null) {
            return;
        }
        annotateCopperLine(offer, lines);
        lines.add(net.minecraft.network.chat.Component.literal(""));
        if (!Bazaar.fresh()) {
            lines.add(net.minecraft.network.chat.Component.literal("§8Bazaar prices not loaded yet"));
            return;
        }
        if (!offer.priced()) {
            lines.add(net.minecraft.network.chat.Component.literal(
                    "§8Not all items are on the bazaar - no value"));
            return;
        }
        lines.add(net.minecraft.network.chat.Component.literal(
                "§7Items cost: §6" + coins(offer.cost())));

        // What you already have changes the answer more than the price does: an offer costing two
        // million is free if the items are sitting in your Agronomy Sack. Only shown once a sack has
        // actually been read - see Sacks, where the counts are a floor rather than an inventory.
        if (Sacks.known()) {
            for (Map.Entry<String, Integer> e : offer.required().entrySet()) {
                int have = Sacks.count(e.getKey());
                int need = e.getValue();
                if (have <= 0) {
                    continue;
                }
                lines.add(net.minecraft.network.chat.Component.literal(have >= need
                        ? "§a✔ " + e.getKey() + " §7- " + have + " in sacks"
                        : "§e" + e.getKey() + " §7- " + have + " in sacks, " + (need - have) + " short"));
            }
        }
        // No "per copper" line here: it is written onto Hypixel's own copper line instead, by
        // annotateCopperLine. Saying it twice in one tooltip is worse than saying it once.
        double profit = offer.profit();
        if (!Double.isNaN(profit)) {
            lines.add(net.minecraft.network.chat.Component.literal(
                    "§7Profit: " + (profit >= 0 ? "§a+" : "§c") + coins(profit)));
        }
        String why = m.declineReason(offer);
        if (why != null) {
            lines.add(net.minecraft.network.chat.Component.literal("§cAuto-decline: " + why));
        } else if (offer.profitable()) {
            lines.add(net.minecraft.network.chat.Component.literal("§aProfitable - never declined"));
        }
    }

    /** Whether the helper is on and should act in this menu. */
    public static boolean active() {
        VisitorHelperModule m = VisitorHelperModule.INSTANCE;
        return m != null && m.isEnabled() && Pests.inGarden();
    }
}
