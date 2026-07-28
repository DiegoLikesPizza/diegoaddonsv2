package dev.diego.diegoaddons.config;

/**
 * What one SkyBlock profile has been observed doing.
 *
 * <p>Unlocks are deliberately not here - they are held once for the account, in
 * {@link AddonConfig#achievementUnlocks}. Profiles are still tracked one by one because a condition
 * can ask about several at once ("two profiles past level 200"), which no single profile can answer
 * about itself.
 *
 * <p>Everything here is what this client could see for itself. That is an honest limit worth
 * knowing: {@link #playtimeMs} is time played <em>with this mod running</em>, not the profile's real
 * total, and {@link #level} is the highest level seen rather than the current one - so a level read
 * wrongly for a tick cannot take an unlock back.
 */
public class ProfileStats {
    /** As Hypixel spells it, e.g. "Zucchini". */
    public String name = "";
    /** {@code normal}, {@code ironman}, {@code stranded} or {@code bingo}. */
    public String gamemode = "normal";
    /** Highest SkyBlock level seen on this profile, or 0 if never read. */
    public int level = 0;
    /** Milliseconds observed on this profile. */
    public long playtimeMs = 0;
    public long firstSeen = 0;
    public long lastSeen = 0;

    public double playtimeHours() {
        return playtimeMs / 3_600_000d;
    }

    /** Days since this profile was last played. */
    public double idleDays(long now) {
        return lastSeen <= 0 ? 0 : (now - lastSeen) / 86_400_000d;
    }
}
