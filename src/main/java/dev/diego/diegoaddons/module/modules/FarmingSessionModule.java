package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.Bazaar;
import dev.diego.diegoaddons.util.FarmingSession;
import dev.diego.diegoaddons.util.Garden;
import dev.diego.diegoaddons.util.Pests;
import dev.diego.diegoaddons.util.Visitors;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * This session's farming, in the numbers that answer "is this setup better than the last one":
 * crops and coins an hour, copper, pests, seasoning.
 *
 * <p>Everything is a delta of Hypixel's own counters rather than a tally kept here - see
 * {@link FarmingSession} for why that is the difference between a figure you can act on and one
 * that drifts. Coins need the bazaar, so that line appears once prices have loaded and not before.
 */
public class FarmingSessionModule extends HudModule {
    public static FarmingSessionModule INSTANCE;

    private final BooleanSetting showRates =
            new BooleanSetting(this, "showRates", "Show per hour", true);
    private final BooleanSetting showCoins =
            new BooleanSetting(this, "showCoins", "Show coins from crops", true);
    private final BooleanSetting showCopper =
            new BooleanSetting(this, "showCopper", "Show copper", true);
    private final BooleanSetting showPests =
            new BooleanSetting(this, "showPests", "Show pests killed", true);
    private final BooleanSetting showSeasoning =
            new BooleanSetting(this, "showSeasoning", "Show seasoning", true);
    private final BooleanSetting splitProfit =
            new BooleanSetting(this, "splitProfit", "Split farming and pest profit", true);
    /**
     * Idle handling, and the two numbers are doing different jobs: pausing keeps a break out of the
     * rates, ending throws away a session that has stopped being one. Without the second, a client
     * left running overnight comes back with an hour of crops against three minutes of working time.
     */
    private final NumberSetting pauseAfter =
            new NumberSetting(this, "pauseAfter", "Pause after idle (minutes)", 2, 1, 15, 1);
    private final NumberSetting endAfter =
            new NumberSetting(this, "endAfter", "End after idle (minutes)", 60, 5, 180, 5);
    private final ActionSetting reset =
            new ActionSetting(this, "reset", "This session", "Reset", FarmingSession::reset);

    public FarmingSessionModule() {
        super("farmingsession", Category.GARDEN, "Farming Session",
                "Crops, coins, copper and pests for this session, with rates per hour.");
        settings.add(showRates);
        settings.add(showCoins);
        settings.add(showCopper);
        settings.add(showPests);
        settings.add(showSeasoning);
        settings.add(splitProfit);
        settings.add(pauseAfter);
        settings.add(endAfter);
        settings.add(reset);
        INSTANCE = this;
    }

    @Override
    protected String label() {
        return "Session";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // a block of lines; see hudLines
    }

    @Override
    protected String sampleValue() {
        return "12m";
    }

    @Override
    public void onClientTick(Minecraft mc) {
        FarmingSession.setTimeouts((long) (pauseAfter.get() * 60_000), (long) (endAfter.get() * 60_000));
        FarmingSession.tick(mc);
        if (showCoins.get() && Pests.inGarden()) {
            Bazaar.refreshIfDue();
        }
    }

    @Override
    public List<String> hudLines(Minecraft mc) {
        if (!Pests.inGarden() || !FarmingSession.active()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(8);
        // The clock says paused rather than silently stopping: a frozen number with no explanation
        // reads as a broken tracker, which is how you end up not trusting the ones that are right.
        String clock = Garden.time(FarmingSession.elapsed()) + (FarmingSession.paused() ? " (paused)" : "");
        out.add(showLabel() ? "Session: " + clock : clock);

        long crops = FarmingSession.crops();
        if (crops >= 0) {
            out.add(line(FarmingSession.crop().isEmpty() ? "Crops" : FarmingSession.crop(),
                    count(crops), FarmingSession.perHour(crops)));
        }
        if (showCoins.get()) {
            double total = FarmingSession.profit();
            double farming = Math.max(0, FarmingSession.coins());
            double pestSide = FarmingSession.pestProfit();
            // The reason matters: "no prices yet" resolves itself, "not on the bazaar" does not.
            if (total > 0) {
                out.add(line("Profit", Visitors.coins(total), FarmingSession.perHour((long) total)));
                if (splitProfit.get() && farming > 0 && pestSide > 0) {
                    out.add("  from crops: " + Visitors.coins(farming));
                    out.add("  from pests: " + Visitors.coins(pestSide));
                }
            } else if (!Bazaar.fresh()) {
                out.add("Profit: waiting for prices");
            }
        }
        if (showCopper.get()) {
            long copper = FarmingSession.copper();
            if (copper >= 0) {
                out.add(line("Copper", count(copper), FarmingSession.perHour(copper)));
            }
        }
        if (showPests.get() && FarmingSession.pests() > 0) {
            out.add(line("Pests", count(FarmingSession.pests()),
                    FarmingSession.perHour(FarmingSession.pests())));
        }
        if (showSeasoning.get() && FarmingSession.seasoning() > 0) {
            out.add("Seasoning: " + FarmingSession.seasoning());
        }
        return out;
    }

    /** "Wheat: 12,480 (48k/h)", with the rate left off while it would still be noise. */
    private String line(String name, String total, double rate) {
        if (!showRates.get() || rate < 0) {
            return name + ": " + total;
        }
        return name + ": " + total + " (" + count(Math.round(rate)) + "/h)";
    }

    /** Short form for the big ones - a HUD line is not a spreadsheet cell. */
    private static String count(long value) {
        if (Math.abs(value) >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (Math.abs(value) >= 10_000) {
            return String.format(Locale.ROOT, "%.0fk", value / 1_000.0);
        }
        return String.format(Locale.ROOT, "%,d", value);
    }
}
