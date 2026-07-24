package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.WordReplacement;
import dev.diego.diegoaddons.module.modules.ReplaceWordsModule;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the user's find/replace pairs to text - "Aspect of the Void" to "AOTV", a player's name to
 * whatever you prefer to call them.
 *
 * <p>Replacement happens on the message flattened to legacy codes (see {@link LegacyText}) rather
 * than per component run, because a phrase is often split across runs with different colours and a
 * per-run search would never match it.
 */
public final class WordReplacer {
    private WordReplacer() {
    }

    /** The configured pairs (never null). */
    public static List<WordReplacement> all() {
        if (ConfigManager.get().wordReplacements == null) {
            ConfigManager.get().wordReplacements = new ArrayList<>();
        }
        return ConfigManager.get().wordReplacements;
    }

    private static boolean active() {
        ReplaceWordsModule mod = ReplaceWordsModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return false;
        }
        for (WordReplacement r : all()) {
            if (r.enabled && r.from != null && !r.from.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Rewrites a component, or returns it untouched when nothing matches. */
    public static Component apply(Component text) {
        if (text == null || !active()) {
            return text;
        }
        String legacy = LegacyText.toLegacy(text);
        String out = replace(legacy);
        return out.equals(legacy) ? text : LegacyText.fromLegacy(out);
    }

    /**
     * Like {@link #apply}, but keeps the original in brackets after the replacement - so a renamed
     * player still shows their real IGN where you need to recognise the account, not the nickname.
     * Returns the component untouched when nothing was replaced.
     */
    public static Component applyWithOriginal(Component text) {
        if (text == null || !active()) {
            return text;
        }
        String legacy = LegacyText.toLegacy(text);
        String out = replace(legacy);
        if (out.equals(legacy)) {
            return text;
        }
        return LegacyText.fromLegacy(out + "§7 (" + LegacyText.strip(legacy) + ")");
    }

    /** Rewrites a plain string. */
    public static String replace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (WordReplacement r : all()) {
            if (!r.enabled || r.from == null || r.from.isEmpty()) {
                continue;
            }
            out = replaceIgnoreCase(out, r.from, r.to == null ? "" : r.to);
        }
        return out;
    }

    /**
     * Case-insensitive literal replace. Deliberately not a regex: these come from a text field, and
     * a stray bracket should be a character to find, not a syntax error.
     */
    private static String replaceIgnoreCase(String text, String from, String to) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String needle = from.toLowerCase(java.util.Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (true) {
            int hit = lower.indexOf(needle, i);
            if (hit < 0) {
                sb.append(text, i, text.length());
                return sb.toString();
            }
            sb.append(text, i, hit).append(to);
            i = hit + needle.length();
        }
    }
}
