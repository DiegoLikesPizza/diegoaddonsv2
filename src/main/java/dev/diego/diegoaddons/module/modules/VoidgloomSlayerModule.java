package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Voidgloom Seraph (Enderman slayer) helper: highlights the thrown <b>beacon</b> with a countdown and
 * the <b>Nukekebi heads</b> it spawns. Each of highlight / tracer / arrow (plus the beacon timer) is
 * its own toggle. See {@link dev.diego.diegoaddons.util.VoidgloomSlayer}.
 */
public class VoidgloomSlayerModule extends Module {
    public static VoidgloomSlayerModule INSTANCE;

    private final BooleanSetting beaconHighlight =
            new BooleanSetting(this, "beaconBox", "Beacon: highlight", true);
    private final BooleanSetting beaconTracer =
            new BooleanSetting(this, "beaconTracer", "Beacon: tracer", false);
    private final BooleanSetting beaconArrow =
            new BooleanSetting(this, "beaconArrow", "Beacon: off-screen arrow", true);
    private final BooleanSetting beaconTimer =
            new BooleanSetting(this, "beaconTimer", "Beacon: countdown", true);
    private final BooleanSetting nukekebiHighlight =
            new BooleanSetting(this, "nukeBox", "Nukekebi: highlight", true);
    private final BooleanSetting nukekebiTracer =
            new BooleanSetting(this, "nukeTracer", "Nukekebi: tracer", false);
    private final BooleanSetting nukekebiArrow =
            new BooleanSetting(this, "nukeArrow", "Nukekebi: off-screen arrow", false);

    public VoidgloomSlayerModule() {
        super("voidgloomslayer", Category.SLAYER, "Voidgloom Slayer",
                "Highlight the Voidgloom Seraph's beacon (with countdown) and its Nukekebi heads.");
        settings.add(beaconHighlight);
        settings.add(beaconTracer);
        settings.add(beaconArrow);
        settings.add(beaconTimer);
        settings.add(nukekebiHighlight);
        settings.add(nukekebiTracer);
        settings.add(nukekebiArrow);
        INSTANCE = this;
    }

    public boolean beaconHighlight() {
        return beaconHighlight.get();
    }

    public boolean beaconTracer() {
        return beaconTracer.get();
    }

    public boolean beaconArrow() {
        return beaconArrow.get();
    }

    public boolean beaconTimer() {
        return beaconTimer.get();
    }

    public boolean nukekebiHighlight() {
        return nukekebiHighlight.get();
    }

    public boolean nukekebiTracer() {
        return nukekebiTracer.get();
    }

    public boolean nukekebiArrow() {
        return nukekebiArrow.get();
    }

    @Override
    protected void onDisable() {
        dev.diego.diegoaddons.util.VoidgloomSlayer.reset();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        dev.diego.diegoaddons.util.VoidgloomSlayer.tick(mc);
    }
}
