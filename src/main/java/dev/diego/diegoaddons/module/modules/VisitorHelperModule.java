package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.Bazaar;
import dev.diego.diegoaddons.util.Visitors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * What a Garden visitor's offer is actually worth, and - when you ask it to - the refusal of the
 * ones that are not worth taking.
 *
 * <p>The measure is <b>coins paid per copper earned</b>: the bazaar cost of the items the visitor
 * wants, divided by the copper it pays. Low is good. Above the ceiling you set, the offer is one
 * you are overpaying for, and that is what auto-decline acts on.
 *
 * <p><b>Three guards sit above every rule, and none of them is a setting.</b>
 * <ul>
 *   <li>A <b>profitable offer is never declined</b>, whatever the ratio or the rarity says. Diego's
 *       rule, and the right one: coins per copper measures how efficiently you are buying copper,
 *       not whether the trade makes money, and those two can disagree.</li>
 *   <li>An offer that <b>cannot be priced</b> is never declined. If one required item is not on the
 *       bazaar, there is no cost, so there is no ratio to compare - and refusing on a number you do
 *       not have is exactly the mistake this feature must not make.</li>
 *   <li>Prices must be <b>fresh</b>. A snapshot older than twenty minutes does not decide anything;
 *       see {@link Bazaar#fresh()}.</li>
 * </ul>
 * A refusal is irreversible and unattended, which is why the guards are code rather than checkboxes.
 */
public class VisitorHelperModule extends Module {
    public static VisitorHelperModule INSTANCE;

    /** Auto-decline modes, in the order the cycle shows them. */
    public static final int OFF = 0;
    public static final int BY_COPPER = 1;
    public static final int BY_RARITY = 2;
    public static final int BOTH = 3;

    private final BooleanSetting tooltip =
            new BooleanSetting(this, "tooltip", "Value in the tooltip", true);
    private final CycleSetting mode = new CycleSetting(this, "mode", "Auto decline", OFF,
            "Off", "Coins per copper", "Rarity", "Both");
    private final NumberSetting maxPerCopper =
            new NumberSetting(this, "maxPerCopper", "Max coins per copper", 5, 0.5, 50, 0.5);
    private final CycleSetting declineUpTo = new CycleSetting(this, "declineUpTo",
            "Decline rarity up to", 0, "Common", "Uncommon", "Rare", "Epic", "Legendary");
    private final BooleanSetting debug =
            new BooleanSetting(this, "debug", "Debug scan (log)", false);

    public VisitorHelperModule() {
        super("visitorhelper", Category.GARDEN, "Visitor Helper",
                "Prices a visitor's offer in coins per copper, and can decline the bad ones.");
        settings.add(tooltip);
        settings.add(mode);
        settings.add(maxPerCopper);
        settings.add(declineUpTo);
        settings.add(debug);
        INSTANCE = this;
    }

    public boolean showTooltip() {
        return tooltip.get();
    }

    public boolean debugScan() {
        return debug.get();
    }

    public double maxCoinsPerCopper() {
        return maxPerCopper.get();
    }

    /** The menu already looked at, so a visitor is read - and dumped, and judged - once. */
    private int handled = -1;

    /** Prices are only fetched while somebody is actually using them. */
    @Override
    public void onClientTick(Minecraft mc) {
        if (mode.get() != OFF || tooltip.get()) {
            Bazaar.refreshIfDue();
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            handled = -1;
            Visitors.forget();
            return;
        }
        int id = screen.getMenu().containerId;
        if (id == handled) {
            return;
        }
        // Read from the tick rather than when the screen opens: the window arrives empty and the
        // server fills it a moment later, so a read at open time sees a menu with no offer in it.
        if (!Visitors.isVisitorMenu(screen)) {
            return;
        }
        handled = id;
        onVisitorMenu(mc, screen);
    }

    /**
     * Whether this offer should be declined, and why - or null to leave it alone.
     *
     * <p>Returning the reason rather than a boolean is what lets the refusal say in chat what it
     * acted on. An unattended click you cannot account for afterwards is not one worth making.
     */
    public String declineReason(Visitors.Offer offer) {
        if (mode.get() == OFF || offer == null) {
            return null;
        }
        // The three guards, in the order they are cheapest to check.
        if (!Bazaar.fresh()) {
            return null;
        }
        if (!offer.priced()) {
            return null;
        }
        if (offer.profitable()) {
            return null;
        }

        boolean byCopper = mode.get() == BY_COPPER || mode.get() == BOTH;
        boolean byRarity = mode.get() == BY_RARITY || mode.get() == BOTH;

        if (byRarity && rarityAtOrBelow(offer.rarity())) {
            return offer.rarity().display + " visitor";
        }
        if (byCopper) {
            double perCopper = offer.costPerCopper();
            if (perCopper >= 0 && perCopper > maxPerCopper.get()) {
                return String.format(java.util.Locale.ROOT,
                        "%.1f coins per copper, over your %.1f",
                        perCopper, maxPerCopper.get());
            }
        }
        return null;
    }

    /**
     * Whether a rarity is at or below the chosen ceiling.
     *
     * <p>{@link Visitors.Rarity#UNKNOWN} deliberately never matches: a colour this mod does not
     * recognise is a visitor Hypixel changed or added, and the safe reading of "I do not know what
     * this is" is to keep it.
     */
    private boolean rarityAtOrBelow(Visitors.Rarity rarity) {
        if (rarity == Visitors.Rarity.UNKNOWN) {
            return false;
        }
        return rarity.ordinal() <= declineUpTo.get();
    }

    /** Called from the screen hook once per visitor menu; see {@link Visitors}. */
    public void onVisitorMenu(Minecraft mc, AbstractContainerScreen<?> screen) {
        if (debug.get()) {
            Visitors.debugDump(screen);
        }
        Visitors.Offer offer = Visitors.read(screen);
        if (offer == null) {
            return;
        }
        String why = declineReason(offer);
        if (why != null) {
            Visitors.decline(mc, screen, why);
        }
    }
}
