package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.Achievement;
import dev.diego.diegoaddons.config.AddonConfig;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.ProfileStats;
import dev.diego.diegoaddons.module.modules.AchievementsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keeps the per-profile record up to date and decides when one of your achievements has come true.
 *
 * <p>Two things happen here and they are deliberately separate. Observation runs every tick and only
 * ever writes down what the client can see. Evaluation runs a few times a second over what was
 * written down, and never reads the game directly - so an achievement about several profiles is
 * answered from the record rather than from the one profile you happen to be standing on.
 *
 * <p>Unlocking is once per account and permanent. Nothing here ever takes an unlock back: a level
 * misread for a single tick, or a profile you have not played in a year, must not be able to undo
 * something you earned.
 */
public final class Achievements {
    /** Evaluating every tick would be twenty times a second to answer a question about hours. */
    private static final int EVALUATE_EVERY = 20;

    private static long lastTick = 0;
    private static int sinceEvaluate = 0;

    private Achievements() {
    }

    // --- storage ------------------------------------------------------------------------------------

    public static List<Achievement> all() {
        return ConfigManager.get().achievements;
    }

    public static boolean isUnlocked(Achievement a) {
        return ConfigManager.get().achievementUnlocks.containsKey(a.id);
    }

    public static long unlockedAt(Achievement a) {
        Long at = ConfigManager.get().achievementUnlocks.get(a.id);
        return at == null ? 0 : at;
    }

    /** Ids only have to be unique, not pretty - nothing ever shows one. */
    public static String newId() {
        return "a" + System.currentTimeMillis() + "-" + (int) (all().size() + 1);
    }

    public static ProfileStats stats(String profile) {
        AddonConfig cfg = ConfigManager.get();
        return cfg.profileStats.computeIfAbsent(profile.toLowerCase(Locale.ROOT), k -> {
            ProfileStats s = new ProfileStats();
            s.name = profile;
            s.firstSeen = System.currentTimeMillis();
            return s;
        });
    }

    public static void reset() {
        lastTick = 0;
        SkyblockProfile.reset();
    }

    // --- observation --------------------------------------------------------------------------------

    /**
     * Writes down what this profile is doing, then asks whether anything has come true.
     *
     * <p>Playtime is added as the gap between ticks rather than by counting ticks, so a frozen or
     * lagging client cannot inflate it - and a gap too large to be real (alt-tabbed for an hour, a
     * long chunk load) is dropped rather than credited.
     */
    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        String profile = SkyblockProfile.name(mc);
        if (profile.isEmpty()) {
            lastTick = 0;           // not on SkyBlock: nothing to credit the time to
            return;
        }

        ProfileStats s = stats(profile);
        s.name = profile;
        s.gamemode = SkyblockProfile.gamemode(mc);
        s.level = Math.max(s.level, SkyblockProfile.level(mc));
        if (s.firstSeen == 0) {
            s.firstSeen = now;
        }
        if (lastTick > 0) {
            long gap = now - lastTick;
            if (gap > 0 && gap < 10_000) {
                s.playtimeMs += gap;
            }
        }
        lastTick = now;
        s.lastSeen = now;

        if (++sinceEvaluate >= EVALUATE_EVERY) {
            sinceEvaluate = 0;
            evaluate();
            ConfigManager.save();   // the record moved; write it with the same beat
        }
    }

    /** Chat triggers. An achievement with conditions as well has to satisfy those too. */
    public static void onMessage(String plain) {
        SkyblockProfile.onMessage(plain);
        if (AchievementsModule.INSTANCE == null || !AchievementsModule.INSTANCE.isEnabled()) {
            return;
        }
        for (Achievement a : List.copyOf(all())) {
            if (!a.enabled || a.chat.isBlank() || isUnlocked(a)) {
                continue;
            }
            if (matches(a.chat, plain) && conditionsMet(a)) {
                unlock(a);
            }
        }
    }

    // --- evaluation ---------------------------------------------------------------------------------

    /** Condition-only achievements: the ones that are true rather than the ones that happen. */
    private static void evaluate() {
        for (Achievement a : List.copyOf(all())) {
            if (!a.enabled || !a.chat.isBlank() || a.conditions.isEmpty() || isUnlocked(a)) {
                continue;
            }
            if (conditionsMet(a)) {
                unlock(a);
            }
        }
    }

    public static boolean conditionsMet(Achievement a) {
        for (Achievement.Condition c : a.conditions) {
            if (matching(c) < Math.max(1, c.profiles)) {
                return false;
            }
        }
        return true;
    }

    /** How many profiles satisfy one condition right now. */
    public static int matching(Achievement.Condition c) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (ProfileStats s : ConfigManager.get().profileStats.values()) {
            if (!"any".equals(c.gamemode) && !c.gamemode.equals(s.gamemode)) {
                continue;
            }
            if (compare(statValue(c.stat, s, now), c.comparator, c.value)) {
                count++;
            }
        }
        return count;
    }

    private static double statValue(String stat, ProfileStats s, long now) {
        return switch (stat) {
            case "playtime" -> s.playtimeHours();
            case "idle" -> s.idleDays(now);
            default -> s.level;
        };
    }

    private static boolean compare(double actual, String comparator, double target) {
        return switch (comparator) {
            case "<=" -> actual <= target;
            case "==" -> Math.abs(actual - target) < 0.5;
            default -> actual >= target;
        };
    }

    /**
     * Whether a chat line matches a trigger, with {@code *} standing for any run of characters.
     *
     * <p>A wildcard rather than a regex on purpose: this is typed into a text box by somebody who
     * wants to match "You found a Wither Essence", and every other character - brackets, dots, the
     * plus in "+1" - should mean itself rather than quietly turning the pattern into something else.
     */
    public static boolean matches(String pattern, String line) {
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.trim().split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE)
                .matcher(line.trim()).matches();
    }

    // --- unlocking ----------------------------------------------------------------------------------

    private static void unlock(Achievement a) {
        ConfigManager.get().achievementUnlocks.put(a.id, System.currentTimeMillis());
        ConfigManager.save();

        AchievementsModule m = AchievementsModule.INSTANCE;
        if (m != null && m.showToast()) {
            Toasts.show("Achievement unlocked", a.name);
        }
        if (m != null && m.announceInChat()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                // Marked SYSTEM_CLIENT: this line is the mod talking to you, not the server, and
                // anything reading chat history should be able to tell those apart.
                mc.gui.getChat().addClientSystemMessage(
                        Component.literal("§d✦ Achievement unlocked: §f" + a.name));
            }
        }
    }

    /** Lets the editor try an achievement out without waiting for the real thing to happen. */
    public static void unlockManually(Achievement a) {
        if (!isUnlocked(a)) {
            unlock(a);
        }
    }

    public static void relock(Achievement a) {
        ConfigManager.get().achievementUnlocks.remove(a.id);
        ConfigManager.save();
    }
}
