package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.MiningAbilityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the mining ability's cooldown from the game's own chat messages.
 *
 * <p>Both ends are announced - using an ability and it coming back - so the state is read rather
 * than timed. A timer started on use would drift against the server and would be wrong outright for
 * anyone whose cooldown is shortened by perks.
 */
public final class MiningAbility {
    /** "You used your Pickobulus ability!" */
    private static final Pattern USED = Pattern.compile("You used your (.+?) ability!");
    /** "Pickobulus is now available!" */
    private static final Pattern READY = Pattern.compile("^(.+?) is now available!$");
    /** Fallback so the readout counts down even before the ready message lands. */
    private static final int ASSUMED_COOLDOWN = 60;

    private static String ability;
    private static long readyAt;
    private static boolean ready;
    private static boolean announced;

    private MiningAbility() {
    }

    public static String abilityName() {
        return ability;
    }

    public static boolean isReady() {
        return ready;
    }

    public static int secondsLeft() {
        return (int) Math.max(0, (readyAt - System.currentTimeMillis()) / 1000);
    }

    public static void reset() {
        ability = null;
        ready = false;
        announced = false;
        readyAt = 0;
    }

    /** Called for every incoming system message. */
    public static void onMessage(String plain) {
        String msg = plain.trim();
        Matcher used = USED.matcher(msg);
        if (used.find()) {
            ability = used.group(1).trim();
            ready = false;
            announced = false;
            readyAt = System.currentTimeMillis() + ASSUMED_COOLDOWN * 1000L;
            return;
        }
        Matcher r = READY.matcher(msg);
        if (r.matches() && ability != null && r.group(1).trim().equalsIgnoreCase(ability)) {
            ready = true;
            readyAt = System.currentTimeMillis();
        }
    }

    /** Called every client tick while the module is on; handles the one-shot chime. */
    public static void tick(Minecraft mc) {
        MiningAbilityModule mod = MiningAbilityModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null) {
            return;
        }
        if (ready && !announced) {
            announced = true;
            if (mod.chime()) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.4f);
            }
        }
    }
}
