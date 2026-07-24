package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HudModule;
import net.minecraft.client.Minecraft;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Shows the real-world wall-clock time. */
public class ClockModule extends HudModule {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClockModule() {
        super("clock", "Real-Time Clock", "Your computer's current time.");
    }

    /** Purely numeric, so the digit-normalised chip leaves visible slack unless the text is centred. */
    @Override
    protected boolean defaultCentered() {
        return true;
    }

    @Override
    protected String label() {
        return "Time";
    }

    @Override
    protected String value(Minecraft mc) {
        return LocalTime.now().format(FMT);
    }

    @Override
    protected String sampleValue() {
        return "13:37:00";
    }
}
