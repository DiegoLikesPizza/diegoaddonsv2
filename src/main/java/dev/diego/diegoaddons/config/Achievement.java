package dev.diego.diegoaddons.config;

import java.util.ArrayList;
import java.util.List;

/**
 * One achievement you wrote yourself: what it is called, and what has to be true for it to unlock.
 *
 * <p>An achievement unlocks when <em>every</em> part of it holds at once - the chat trigger, if it
 * has one, and every {@link Condition}. That single rule covers both kinds people actually write:
 * something that happens (a chat line) and something that is the case (a profile is level 200).
 *
 * <p>Unlocks are not stored here, and not per profile either - they are held once for the account,
 * in {@link AddonConfig#achievementUnlocks}. Earning something is a fact about you, not about the
 * profile you happened to be standing on when it became true.
 */
public class Achievement {
    /** Stable across renames, so an unlock survives editing the name. */
    public String id = "";
    public String name = "";
    public String description = "";

    /**
     * A chat line that unlocks this, with {@code *} matching any run of characters. Empty means the
     * achievement is not triggered by chat at all and rests on its conditions.
     */
    public String chat = "";

    /**
     * A phrase that must <em>not</em> appear in the line for it to count.
     *
     * <p>Wildcards alone cannot say "no": the natural pattern for a Floor VII clear also matches a
     * Master Mode VII clear, because the master line contains the normal one word for word. Rather
     * than let people write regex to get around that, one exclusion covers the case that actually
     * comes up.
     */
    public String excludes = "";

    /**
     * The tally this counts towards, or empty if it unlocks the first time it happens.
     *
     * <p>Achievements sharing a counter share the count, which is the point: "10 runs" and "20,000
     * runs" are the same tally read at two heights, not two things to keep track of.
     */
    public String counter = "";

    /** How high the counter has to reach. Ignored when there is no counter. */
    public int threshold = 1;

    /** Grouping in the list, e.g. "Dungeons". Purely for finding things among hundreds. */
    public String category = "Custom";

    public List<Condition> conditions = new ArrayList<>();
    public boolean enabled = true;

    /**
     * Set on the ones the mod ships rather than the ones you wrote. Not persisted: built-ins are
     * rebuilt from code every launch, so they can be corrected and added to without rewriting
     * anybody's config. Only what you change about them is stored.
     */
    public transient boolean builtin;

    public Achievement() {
    }

    public Achievement(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** An achievement with neither a trigger nor a condition can never unlock; the GUI says so. */
    public boolean isComplete() {
        return !chat.isBlank() || !conditions.isEmpty();
    }

    /** Whether this is a tally rather than a one-off. */
    public boolean counted() {
        return !counter.isBlank() && threshold > 1;
    }

    /**
     * One thing that has to be true, counted across profiles.
     *
     * <p>The count is what makes "have 2 profiles at level 200+" expressible at all: no single
     * profile can know that about itself, so a condition asks how many profiles satisfy it rather
     * than whether this one does. A count of 1 with a gamemode filter is the ordinary case.
     */
    public static class Condition {
        /** One of {@code level}, {@code playtime} (hours), {@code idle} (days since last seen). */
        public String stat = "level";
        /** {@code any}, or one of {@code normal}, {@code ironman}, {@code stranded}, {@code bingo}. */
        public String gamemode = "any";
        /** One of {@code >=}, {@code <=}, {@code ==}. */
        public String comparator = ">=";
        public double value = 0;
        /** How many profiles have to satisfy it. */
        public int profiles = 1;

        public Condition() {
        }

        public Condition(String stat, String comparator, double value) {
            this.stat = stat;
            this.comparator = comparator;
            this.value = value;
        }
    }
}
