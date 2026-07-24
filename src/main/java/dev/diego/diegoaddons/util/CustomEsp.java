package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The user's own ESP terms, matched against mob name plates. Managed with {@code /da esp}. */
public final class CustomEsp {
    private CustomEsp() {
    }

    public static List<String> all() {
        if (ConfigManager.get().espTerms == null) {
            ConfigManager.get().espTerms = new ArrayList<>();
        }
        return ConfigManager.get().espTerms;
    }

    /** Adds a term. Returns false when it was already there. */
    public static boolean add(String term) {
        String t = term.trim();
        if (t.isEmpty() || contains(t)) {
            return false;
        }
        all().add(t);
        ConfigManager.save();
        return true;
    }

    public static boolean remove(String term) {
        boolean removed = all().removeIf(s -> s.equalsIgnoreCase(term.trim()));
        if (removed) {
            ConfigManager.save();
        }
        return removed;
    }

    private static boolean contains(String term) {
        return all().stream().anyMatch(s -> s.equalsIgnoreCase(term));
    }

    /** The term matching this name plate, or null. Matching is loose so partial names work. */
    public static String match(String plate) {
        String lower = plate.toLowerCase(Locale.ROOT);
        for (String term : all()) {
            if (!term.isEmpty() && lower.contains(term.toLowerCase(Locale.ROOT))) {
                return term;
            }
        }
        return null;
    }
}
