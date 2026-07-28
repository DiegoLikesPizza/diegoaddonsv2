package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.Achievement;
import dev.diego.diegoaddons.config.AddonConfig;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.ProfileStats;
import dev.diego.diegoaddons.module.modules.AchievementsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /** Evaluations between config writes - a minute, so playtime survives a crash without churn. */
    private static final int SAVE_EVERY = 60;

    private static long lastTick = 0;
    private static int sinceEvaluate = 0;
    private static int sinceSave = 0;

    private Achievements() {
    }

    // --- storage ------------------------------------------------------------------------------------

    /** The ones you wrote. */
    public static List<Achievement> all() {
        return ConfigManager.get().achievements;
    }

    /** Everything: the shipped list first, then yours. Rebuilt cheaply - both lists are in memory. */
    public static List<Achievement> everything() {
        List<Achievement> out = new ArrayList<>(AchievementCatalogue.all());
        out.addAll(all());
        return out;
    }

    /**
     * Whether an achievement is being watched at all. Built-ins carry no state of their own, so
     * theirs is held as a set of ids that are off; yours carry a flag.
     */
    public static boolean isOn(Achievement a) {
        return a.builtin ? !ConfigManager.get().achievementsOff.contains(a.id) : a.enabled;
    }

    public static void setOn(Achievement a, boolean on) {
        if (a.builtin) {
            if (on) {
                ConfigManager.get().achievementsOff.remove(a.id);
            } else {
                ConfigManager.get().achievementsOff.add(a.id);
            }
        } else {
            a.enabled = on;
        }
        ConfigManager.save();
    }

    /** The trigger in force: your correction if you made one, otherwise the shipped guess. */
    public static String pattern(Achievement a) {
        String override = ConfigManager.get().achievementPatterns.get(a.id);
        return override != null ? override : a.chat;
    }

    public static void setPattern(Achievement a, String chat) {
        if (a.builtin) {
            if (chat.equals(a.chat)) {
                ConfigManager.get().achievementPatterns.remove(a.id);
            } else {
                ConfigManager.get().achievementPatterns.put(a.id, chat);
            }
        } else {
            a.chat = chat;
        }
        ConfigManager.save();
    }

    public static int counter(String key) {
        Integer n = ConfigManager.get().achievementCounters.get(key);
        return n == null ? 0 : n;
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
            // Writing the record is separated from judging it: judging is cheap and wants to be
            // prompt, whereas this rewrites the whole config file and only guards against losing a
            // minute of playtime to a crash. Unlocks save themselves the moment they happen.
            if (++sinceSave >= SAVE_EVERY) {
                sinceSave = 0;
                ConfigManager.save();
            }
        }
    }

    /**
     * Chat triggers, in two passes.
     *
     * <p>Tallies are advanced first and only once each, because several achievements share a counter
     * - a Floor VII clear advances the 10-run, 100-run and 20,000-run entries, and it would be one
     * clear counted three times if each bumped the count itself. Only then is anything unlocked.
     */
    public static void onMessage(String plain) {
        SkyblockProfile.onMessage(plain);
        if (AchievementsModule.INSTANCE == null || !AchievementsModule.INSTANCE.isEnabled()) {
            return;
        }
        List<Achievement> everything = everything();

        Set<String> bumped = new HashSet<>();
        for (Achievement a : everything) {
            if (a.counter.isBlank() || bumped.contains(a.counter) || !isOn(a)) {
                continue;
            }
            if (triggered(a, plain)) {
                bumped.add(a.counter);
                ConfigManager.get().achievementCounters.merge(a.counter, 1, Integer::sum);
            }
        }

        for (Achievement a : everything) {
            if (!isOn(a) || isUnlocked(a)) {
                continue;
            }
            if (!a.counter.isBlank()) {
                // Counted: the tally decides, so a threshold already passed unlocks on any bump.
                if (bumped.contains(a.counter) && counter(a.counter) >= a.threshold
                        && conditionsMet(a)) {
                    unlock(a);
                }
            } else if (!pattern(a).isBlank() && triggered(a, plain) && conditionsMet(a)) {
                unlock(a);
            }
        }
        // Tallies are deliberately not written here. Some of them count kills, and a config write
        // per kill is a stutter; the tick loop persists them within the minute, and an unlock -
        // the part that would actually hurt to lose - saves itself immediately.
    }

    /** Whether this line fires this achievement: the pattern matches and the exclusion does not. */
    private static boolean triggered(Achievement a, String line) {
        String chat = pattern(a);
        if (chat.isBlank() || !matches(chat, line)) {
            return false;
        }
        return a.excludes.isBlank()
                || !line.toLowerCase(Locale.ROOT).contains(a.excludes.toLowerCase(Locale.ROOT));
    }

    // --- evaluation ---------------------------------------------------------------------------------

    /** Condition-only achievements: the ones that are true rather than the ones that happen. */
    private static void evaluate() {
        for (Achievement a : everything()) {
            if (!isOn(a) || !pattern(a).isBlank() || a.conditions.isEmpty() || isUnlocked(a)) {
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
        return compiled(pattern).matcher(line.trim()).matches();
    }

    /**
     * Patterns compiled once and kept.
     *
     * <p>There are several hundred triggers and a busy lobby is a lot of chat; compiling every
     * pattern against every line would be the one part of this feature anybody could feel.
     */
    private static final Map<String, Pattern> COMPILED = new HashMap<>();

    private static Pattern compiled(String pattern) {
        return COMPILED.computeIfAbsent(pattern, p -> {
            StringBuilder regex = new StringBuilder();
            for (String part : p.trim().split("\\*", -1)) {
                if (regex.length() > 0) {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(part));
            }
            return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        });
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
