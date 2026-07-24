package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.SecretChimeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches the dungeon secret count and chimes when it goes up.
 *
 * <p>The count comes from the tab list rather than chat: several kinds of secret - levers, some
 * chests - never announce themselves in chat, so a chat-based watcher would silently miss them.
 *
 * <p>Only increases chime. The count resets to zero on entering a dungeon, and jumping from a high
 * number back to zero is a new run rather than progress.
 */
public final class SecretChime {
    /** " Secrets Found: 12" or " Secrets Found: 40%" - the tab list carries both forms. */
    private static final Pattern SECRETS = Pattern.compile("Secrets Found:\\s*([\\d.]+)");
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
        if (mod == null || !mod.isEnabled() || mc.player == null || mc.getConnection() == null) {
            return;
        }
        if (++tick < INTERVAL) {
            return;
        }
        tick = 0;

        int count = readCount(mc);
        if (count < 0) {
            lastCount = -1;   // left the dungeon; start fresh next time
            return;
        }
        if (lastCount >= 0 && count > lastCount) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
        }
        lastCount = count;
    }

    /** The secret count from the tab list, or -1 when it is not shown. */
    private static int readCount(Minecraft mc) {
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (info.getTabListDisplayName() == null) {
                continue;
            }
            String line = LegacyText.strip(info.getTabListDisplayName().getString());
            Matcher m = SECRETS.matcher(line);
            if (m.find()) {
                try {
                    return (int) Double.parseDouble(m.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
