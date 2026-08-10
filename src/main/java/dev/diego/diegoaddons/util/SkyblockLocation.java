package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads where the player is in SkyBlock from the two HUD surfaces Hypixel writes it to: the sidebar
 * scoreboard (the {@code ⏣ Area} line, which names the exact sub-area you are standing in) and the
 * tab list ({@code Area: ...}, the island as a whole). Pure reads - nothing here changes anything.
 */
public final class SkyblockLocation {
    /** The zone glyph Hypixel prefixes the scoreboard location line with. */
    public static final char ZONE = '⏣';

    private SkyblockLocation() {
    }

    /**
     * Both readings, held for the tick that produced them.
     *
     * <p>These are read from eighteen places - the dungeon, slayer and Hollows trackers all want the
     * sidebar, and several want it more than once - and each read walked every scoreboard entry or
     * every player in tab, stripping colour codes into a fresh list. On a full lobby that was a few
     * hundred string allocations, twenty times a second, for answers that cannot change between
     * ticks. Neither can change without a packet, so once per tick is once too often already.
     */
    private static List<String> sidebarCache;
    private static List<String> tabCache;

    /** Dropped at the top of each client tick. Rendering may read a reading up to a tick old. */
    public static void invalidate() {
        sidebarCache = null;
        tabCache = null;
    }

    /** Every sidebar (right-hand scoreboard) line, colour codes stripped. Do not modify the result. */
    public static List<String> sidebarLines(Minecraft mc) {
        if (sidebarCache != null) {
            return sidebarCache;
        }
        return sidebarCache = readSidebar(mc);
    }

    private static List<String> readSidebar(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (mc.player == null || mc.player.connection == null) {
            return out;
        }
        Scoreboard sb = mc.player.connection.scoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            return out;
        }
        for (ScoreHolder holder : sb.getTrackedPlayers()) {
            if (!sb.listPlayerScores(holder).containsKey(obj)) {
                continue;
            }
            PlayerTeam team = sb.getPlayersTeam(holder.getScoreboardName());
            if (team == null) {
                continue;
            }
            String line = LegacyText.strip(
                    team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString()).trim();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    /** Every tab-list entry, colour codes stripped. Do not modify the result. */
    public static List<String> tabLines(Minecraft mc) {
        if (tabCache != null) {
            return tabCache;
        }
        return tabCache = readTab(mc);
    }

    private static List<String> readTab(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (mc.getConnection() == null) {
            return out;
        }
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (info != null && info.getTabListDisplayName() != null) {
                String s = LegacyText.strip(info.getTabListDisplayName().getString()).trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** The exact sub-area from the scoreboard's {@code ⏣} line (e.g. "Jungle"), or "" if unknown. */
    public static String area(Minecraft mc) {
        for (String line : sidebarLines(mc)) {
            int i = line.indexOf(ZONE);
            if (i >= 0) {
                return line.substring(i + 1).trim();
            }
        }
        return "";
    }

    /** The island from the tab list's {@code Area:} line (e.g. "Crystal Hollows"), or "" if unknown. */
    public static String island(Minecraft mc) {
        for (String line : tabLines(mc)) {
            if (line.startsWith("Area: ")) {
                return line.substring("Area: ".length()).trim();
            }
        }
        return "";
    }

    /**
     * The SkyBlock profile you are on (e.g. "Zucchini") from the tab list's {@code Profile:} line,
     * or "" if it is not there yet.
     *
     * <p>Anything cached per profile - your storage, your bank - has to be keyed on this rather than
     * on the account, because swapping profile swaps the items without any disconnect to notice. The
     * line takes a few seconds to appear after joining, so "" means "not known yet" and never
     * "no profile".
     */
    public static String profile(Minecraft mc) {
        for (String line : tabLines(mc)) {
            if (line.startsWith("Profile: ")) {
                return line.substring("Profile: ".length()).trim();
            }
        }
        return "";
    }
}
