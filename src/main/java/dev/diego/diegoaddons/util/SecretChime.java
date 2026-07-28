package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.SecretChimeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

/**
 * Watches the dungeon secret count and chimes when it goes up.
 *
 * <p>The count comes from {@link DungeonState}, which the score and the map already run on. It used
 * to read the tab list itself, with a pattern loose enough to match "Secrets Found: 40%" as well as
 * a count - so on a floor where the tab shows the percentage first it was watching a number that
 * moves in steps of several secrets, and mostly never chimed at all.
 *
 * <p>Only increases chime. The count resets to zero on entering a dungeon, and jumping from a high
 * number back to zero is a new run rather than progress.
 */
public final class SecretChime {
    /** Recheck a few times a second; the tab list does not change faster than that. */
    private static final int INTERVAL = 5;

    private static int lastCount = -1;
    private static int tick;

    private SecretChime() {
    }

    public static void reset() {
        lastCount = -1;
        tick = 0;
    }

    public static void tick(Minecraft mc) {
        SecretChimeModule mod = SecretChimeModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null) {
            return;
        }
        if (++tick < INTERVAL) {
            return;
        }
        tick = 0;

        int count = DungeonState.inDungeons() ? DungeonState.secretsFound() : -1;
        if (count < 0) {
            lastCount = -1;   // left the dungeon; start fresh next time
            return;
        }
        if (lastCount >= 0 && count > lastCount) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
        }
        lastCount = count;
    }

}
