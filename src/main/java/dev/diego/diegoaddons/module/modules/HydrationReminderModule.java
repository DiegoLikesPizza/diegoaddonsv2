package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

/**
 * Says "Hydrate!" across the middle of the screen every so often.
 *
 * <p>The clock runs on real time rather than on ticks, so it keeps its interval through lag, and it
 * does not run at all in a menu or on the title screen - a reminder to drink is for while you are
 * playing, and one waiting on the pause screen is one you will see the moment you come back and
 * have already ignored.
 */
public class HydrationReminderModule extends Module {
    public static HydrationReminderModule INSTANCE;

    /** How long the message stays up. */
    private static final long SHOW_MS = 4000;

    private final NumberSetting minutes =
            new NumberSetting(this, "minutes", "Every (minutes)", 30, 15, 60, 5);
    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Play a sound", true);

    private long nextAt;
    private long showingUntil;

    public HydrationReminderModule() {
        super("hydration", Category.MISC, "Hydration Reminder",
                "A reminder to drink something, every so often.");
        settings.add(minutes);
        settings.add(sound);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        nextAt = System.currentTimeMillis() + interval();
        showingUntil = 0;
    }

    private long interval() {
        return (long) (minutes.get() * 60_000);
    }

    /** The line to draw, or null when there is nothing to say. */
    public String message() {
        return System.currentTimeMillis() < showingUntil ? "Hydrate!" : null;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        // Not while nothing is happening: a reminder that fires at the title screen has been missed.
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (nextAt == 0) {
            nextAt = now + interval();
            return;
        }
        if (now < nextAt) {
            return;
        }
        nextAt = now + interval();
        showingUntil = now + SHOW_MS;
        if (sound.get()) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 1.4f);
        }
    }
}
