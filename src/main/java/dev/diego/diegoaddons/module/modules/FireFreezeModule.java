package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Locale;

/**
 * The countdown to the Fire Freeze Staff on F3 and M3, so the Professor is already frozen when he
 * turns up for the second phase.
 *
 * <p><b>Why a timer and not a cue you can see:</b> the staff freezes five seconds <i>after</i> it is
 * cast, so the cast has to happen before there is anything on screen to react to. The phase is
 * announced though - the Professor says it himself the moment the Guardians are done - and the gap
 * from that line to the right cast is fixed. So the one readable event in the fight is turned into a
 * clock, which is the whole feature.
 *
 * <p>The cue and the delay are not guesses: they are what
 * <a href="https://github.com/ItzGreenCat/SkyImprover">SkyImprover</a>'s M3 freeze helper uses -
 * the Guardians' weakness line, and 5.25 seconds. It is a slider anyway, because a fixed number that
 * turns out to be a quarter-second off is a feature nobody can fix from in game.
 */
public class FireFreezeModule extends HudModule {
    public static FireFreezeModule INSTANCE;

    /**
     * The Professor's own line when the Guardians die, which is the start of the window.
     *
     * <p>Matched on the fragment rather than the whole message: the full line carries a {@code [BOSS]}
     * prefix, his name and colour codes, and every one of those is a thing that can change under a
     * chat filter without the sentence itself changing.
     */
    private static final String CUE = "You found my Guardians' one weakness?";

    /**
     * The apostrophe as SkyBlock might also write it. The chat line uses the typewriter one today;
     * a curly one would silently break the match, and this costs one comparison to rule out.
     */
    private static final String CUE_CURLY = CUE.replace("'", "’");

    /** How long the "FREEZE!" call stays up after the moment passes. */
    private static final long SHOW_MS = 2000;

    private final NumberSetting castAfter =
            new NumberSetting(this, "castAfter", "Cast at (seconds after the cue)", 5.25, 3, 9, 0.25);
    private final BooleanSetting showTitle =
            new BooleanSetting(this, "showTitle", "Title on screen", true);
    private final BooleanSetting playSound =
            new BooleanSetting(this, "playSound", "Play a sound", true);
    /**
     * A tick of a sound each second on the way down, off by default. Some people count better with
     * it and it is noise for everyone else, which is exactly what a setting is for.
     */
    private final BooleanSetting tick =
            new BooleanSetting(this, "tick", "Tick each second", false);

    /** When the cue arrived, or 0 when no window is running. */
    private long cueAt;
    /** Whether the call has already been made for this window. */
    private boolean called;
    private long showingUntil;
    private int lastTick = -1;

    public FireFreezeModule() {
        super("firefreeze", Category.DUNGEONS, "Fire Freeze Timer",
                "Counts down to the Fire Freeze Staff on the Professor, from his own phase line.");
        settings.add(castAfter);
        settings.add(showTitle);
        settings.add(playSound);
        settings.add(tick);
        INSTANCE = this;
    }

    @Override
    protected String label() {
        return "Fire Freeze";
    }

    @Override
    protected boolean defaultCentered() {
        return true;
    }

    @Override
    protected String sampleValue() {
        return "2.4s";
    }

    /**
     * The countdown, then the call, then nothing.
     *
     * <p>One decimal place: the window is a couple of seconds wide and a whole-second readout would
     * sit on "2" for as long as it takes to miss it.
     */
    @Override
    protected String value(Minecraft mc) {
        if (cueAt == 0) {
            return null;
        }
        long left = castAt() - System.currentTimeMillis();
        if (left > 0) {
            return String.format(Locale.ROOT, "%.1fs", left / 1000.0);
        }
        return System.currentTimeMillis() < showingUntil ? "§cNOW" : null;
    }

    private long castAt() {
        return cueAt + (long) (castAfter.get() * 1000);
    }

    /** Starts the window on the Professor's line. */
    public static void onMessage(String plain) {
        FireFreezeModule m = INSTANCE;
        if (m == null || !m.isEnabled()) {
            return;
        }
        if (plain.contains(CUE) || plain.contains(CUE_CURLY)) {
            m.cueAt = System.currentTimeMillis();
            m.called = false;
            m.showingUntil = 0;
            m.lastTick = -1;
        }
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (cueAt == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long left = castAt() - now;
        if (!called && left <= 0) {
            called = true;
            showingUntil = now + SHOW_MS;
            call(mc);
            return;
        }
        // The window is dropped once the call has been up its time, so a run that ends here does not
        // leave a stale countdown on the HUD for the next boss.
        if (called && now > showingUntil) {
            cueAt = 0;
            return;
        }
        if (tick.get() && !called && mc.player != null) {
            int second = (int) Math.ceil(left / 1000.0);
            if (second != lastTick) {
                lastTick = second;
                mc.player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.6f, 1.2f);
            }
        }
    }

    private void call(Minecraft mc) {
        if (showTitle.get() && mc.gui != null) {
            mc.gui.setTitle(Component.literal("§c§lFREEZE!"));
            mc.gui.setSubtitle(Component.literal("§7Fire Freeze Staff"));
        }
        if (playSound.get() && mc.player != null) {
            // High and flat, and not the chime the rest of the mod uses: this one has to cut through
            // a boss fight and mean one thing.
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
        }
    }

    @Override
    public List<String> hudLines(Minecraft mc) {
        return cueAt == 0 ? List.of() : super.hudLines(mc);
    }
}
