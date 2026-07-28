package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which SkyBlock profile you are on, what gamemode it is, and what level you are - read off the tab
 * list, which is where Hypixel writes all three.
 *
 * <p>Nothing here asks the server anything. That keeps it safe, and it is also why every reader
 * returns an "unknown" value rather than guessing: on a lobby, on another gamemode, or in the second
 * before the tab list is populated, there is genuinely no answer.
 *
 * <p>The gamemode is read from the symbol Hypixel appends to the profile name. Those symbols are the
 * part most likely to drift between SkyBlock updates, so they are constants below and a profile that
 * matches none of them is simply {@code normal}.
 */
public final class SkyblockProfile {
    /** The tab-list line naming the profile. */
    private static final String TAB_PREFIX = "Profile: ";
    /** Said in chat when you switch, which is the fastest notice we get. */
    private static final Pattern SWITCHED =
            Pattern.compile("You are playing on profile: (\\w+)", Pattern.CASE_INSENSITIVE);
    /** A SkyBlock level, worn as a prefix on your own tab entry: {@code [218] diego}. */
    private static final Pattern LEVEL = Pattern.compile("^\\[(\\d{1,4})]");

    private static final char IRONMAN = '♻';    // ♻
    private static final char STRANDED = '⏣';   // ⏣-adjacent; Hypixel's stranded mark
    private static final char BINGO = 'Ⓑ';      // Ⓑ

    /** Last profile seen in chat, which beats the tab list to the news by a second or so. */
    private static String announced = "";

    private SkyblockProfile() {
    }

    /** Watches for the profile-switch line. */
    public static void onMessage(String plain) {
        Matcher m = SWITCHED.matcher(plain);
        if (m.find()) {
            announced = m.group(1);
        }
    }

    public static void reset() {
        announced = "";
    }

    /** The raw {@code Profile: ...} line, symbols and all, or "" if there is none. */
    private static String rawLine(Minecraft mc) {
        for (String line : SkyblockLocation.tabLines(mc)) {
            if (line.startsWith(TAB_PREFIX)) {
                return line.substring(TAB_PREFIX.length()).trim();
            }
        }
        return "";
    }

    /** The profile name, or "" when we are not on SkyBlock or cannot tell yet. */
    public static String name(Minecraft mc) {
        String raw = rawLine(mc);
        if (!raw.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (char c : raw.toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return announced;
    }

    /** Whether we can see a SkyBlock profile at all - the mod's stand-in for "on SkyBlock". */
    public static boolean onSkyblock(Minecraft mc) {
        return !name(mc).isEmpty();
    }

    public static String gamemode(Minecraft mc) {
        String raw = rawLine(mc);
        if (raw.indexOf(IRONMAN) >= 0) {
            return "ironman";
        }
        if (raw.indexOf(BINGO) >= 0) {
            return "bingo";
        }
        if (raw.indexOf(STRANDED) >= 0) {
            return "stranded";
        }
        return "normal";
    }

    /** Your SkyBlock level, or 0 when the tab list has not said. */
    public static int level(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) {
            return 0;
        }
        PlayerInfo self = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (self == null || self.getTabListDisplayName() == null) {
            return 0;
        }
        String plain = LegacyText.strip(self.getTabListDisplayName().getString()).trim();
        Matcher m = LEVEL.matcher(plain);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
