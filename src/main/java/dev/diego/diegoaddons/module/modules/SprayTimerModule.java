package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.Garden;
import dev.diego.diegoaddons.util.Pests;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What is sprayed on which plot, and how long is left of it.
 *
 * <p>A spray runs for thirty minutes and then stops without saying so, which is the whole problem:
 * the plot looks exactly the same, and the pest you were farming for stops coming. Hypixel only
 * shows the spray on the plot you are standing on, so this keeps the timer for every plot from the
 * moment the Sprayonator announced it in chat, and lets the widget correct it whenever you walk back
 * onto that plot - see {@link Garden}.
 */
public class SprayTimerModule extends HudModule {
    public static SprayTimerModule INSTANCE;

    private final BooleanSetting onlyMine =
            new BooleanSetting(this, "onlyCurrent", "Only the plot you are in", false);
    private final NumberSetting warnBefore =
            new NumberSetting(this, "warnBefore", "Warn before it ends (seconds)", 60, 0, 300, 15);
    private final BooleanSetting warnTitle =
            new BooleanSetting(this, "warnTitle", "Warning as a title", true);
    private final BooleanSetting warnSound =
            new BooleanSetting(this, "warnSound", "Play a sound", true);
    private final BooleanSetting warnChat =
            new BooleanSetting(this, "warnChat", "Warning in chat", false);

    /**
     * The plots already warned about, cleared when their spray is gone.
     *
     * <p>Keyed on the plot rather than on the spray, because re-spraying the same plot replaces the
     * timer without any event to notice: a plot leaves this set when its spray expires or is washed
     * off, and a fresh spray on it therefore warns again.
     */
    private final Set<Integer> warned = new HashSet<>();

    public SprayTimerModule() {
        super("spraytimer", Category.GARDEN, "Sprayonator Timer",
                "How long each plot's spray has left, with a warning before it runs out.");
        settings.add(onlyMine);
        settings.add(warnBefore);
        settings.add(warnTitle);
        settings.add(warnSound);
        settings.add(warnChat);
        INSTANCE = this;
    }

    @Override
    protected String label() {
        return "Spray";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // one line per sprayed plot; see hudLines
    }

    @Override
    protected String sampleValue() {
        return "Compost 12m 3s";
    }

    @Override
    public List<String> hudLines(Minecraft mc) {
        if (!Pests.inGarden()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (onlyMine.get()) {
            int here = Garden.currentPlot(mc);
            Garden.Spray spray = here < 0 ? null : Garden.spray(here);
            if (spray != null) {
                out.add(line(here, spray));
            }
            return out;
        }
        for (Map.Entry<Integer, Garden.Spray> e : Garden.sprays()) {
            out.add(line(e.getKey(), e.getValue()));
        }
        return out;
    }

    /** "Plot 4: Compost 12m 3s", or just the spray when the plot name is switched off. */
    private String line(int plot, Garden.Spray spray) {
        String value = spray.type() + " " + Garden.time(spray.msLeft());
        return showLabel() ? Garden.plotName(plot) + ": " + value : value;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || !Pests.inGarden()) {
            return;
        }
        long lead = (long) (warnBefore.get() * 1000);
        Set<Integer> live = new HashSet<>();
        for (Map.Entry<Integer, Garden.Spray> e : Garden.sprays()) {
            int plot = e.getKey();
            live.add(plot);
            if (lead <= 0 || warned.contains(plot)) {
                continue;
            }
            if (e.getValue().msLeft() <= lead) {
                warned.add(plot);
                warn(mc, e.getValue().type() + " on " + Garden.plotName(plot).toLowerCase(java.util.Locale.ROOT)
                        + " ends in " + Garden.time(e.getValue().msLeft()));
            }
        }
        // A spray that is gone is no longer warned about, which is what lets the next one warn.
        warned.retainAll(live);
    }

    private void warn(Minecraft mc, String detail) {
        if (warnTitle.get() && mc.gui != null) {
            mc.gui.setTitle(Component.literal("§a§lSPRAY RUNNING OUT"));
            mc.gui.setSubtitle(Component.literal("§e" + detail));
        }
        if (warnChat.get() && mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    Component.literal("§b[DiegoAddons] §f" + detail));
        }
        if (warnSound.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.2f);
        }
    }
}
