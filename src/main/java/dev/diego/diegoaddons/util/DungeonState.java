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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live dungeon run state, read once per tick from the two places Hypixel exposes it: the <b>tab
 * list</b> (secrets, crypts, completed rooms, deaths, puzzle results, time) and the <b>sidebar
 * scoreboard</b> (location/floor and cleared percent). Nothing here is guessed - every value is a
 * parse of text the server already shows the player.
 *
 * <p>From those it derives the dungeon <b>score</b> using the same well-known formula the
 * established mods use (skill + explore + speed + bonus). Two of the bonus inputs - whether the
 * Mimic and the Prince have been killed - are not on the scoreboard, so the message features
 * (which detect those events) flag them here via {@link #setMimicKilled()} / {@link #setPrinceKilled()}.
 */
public final class DungeonState {
    private static final Pattern FLOOR = Pattern.compile("\\((M?\\d|E)\\d?\\)");
    private static final Pattern TIME = Pattern.compile("(?:(\\d+)m)?\\s*(\\d+)s");

    private static boolean inDungeons;
    private static String floor = "";        // "F7", "M3", "E", or ""
    private static int secretsFound;
    private static double secretsPercent;    // 0..1
    private static int crypts;
    private static int completedRooms;
    private static int deaths;
    private static int puzzleFails;
    private static double clearedPercent;    // 0..1, from the sidebar
    private static int secondsElapsed;
    private static int score;

    private static boolean mimicKilled;
    private static boolean princeKilled;
    private static boolean bloodDone;

    private DungeonState() {
    }

    public static boolean inDungeons() {
        return inDungeons;
    }

    public static String floor() {
        return floor;
    }

    /** True on floors where a Mimic can spawn (6 and 7, normal or master). */
    public static boolean mimicFloor() {
        return floor.endsWith("6") || floor.endsWith("7");
    }

    public static int secretsFound() {
        return secretsFound;
    }

    public static int crypts() {
        return crypts;
    }

    public static int score() {
        return score;
    }

    public static boolean mimicKilled() {
        return mimicKilled;
    }

    public static boolean princeKilled() {
        return princeKilled;
    }

    public static void setMimicKilled() {
        mimicKilled = true;
    }

    public static void setPrinceKilled() {
        princeKilled = true;
    }

    /** Watches for the Blood-room boss line, which the score's room count depends on. */
    public static void onMessage(String plain) {
        if (plain.contains("[BOSS] The Watcher: You have proven yourself. You may pass.")) {
            bloodDone = true;
        }
    }

    public static void reset() {
        inDungeons = false;
        floor = "";
        secretsFound = 0;
        secretsPercent = 0;
        crypts = 0;
        completedRooms = 0;
        deaths = 0;
        puzzleFails = 0;
        clearedPercent = 0;
        secondsElapsed = 0;
        score = 0;
        mimicKilled = false;
        princeKilled = false;
        bloodDone = false;
    }

    /** Called every client tick. Cheap when not in a dungeon (bails after the area check). */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            inDungeons = false;
            return;
        }
        List<String> sidebar = sidebarLines(mc);
        inDungeons = sidebar.stream().anyMatch(l -> l.contains("The Catacombs"))
                || tabLines(mc).stream().anyMatch(l -> l.startsWith("Dungeon: The Catacombs"));
        if (!inDungeons) {
            return;
        }

        // Floor, from either the tab "Dungeon:" line or the sidebar location line.
        for (String l : tabLines(mc)) {
            if (l.startsWith("Dungeon: The Catacombs")) {
                floor = parseFloor(l);
            }
        }
        if (floor.isEmpty()) {
            for (String l : sidebar) {
                if (l.contains("The Catacombs")) {
                    floor = parseFloor(l);
                }
            }
        }

        parseTab(mc);
        for (String l : sidebar) {
            if (l.startsWith("Cleared:")) {
                clearedPercent = parsePercent(l);
            }
        }
        recomputeScore();
    }

    private static String parseFloor(String line) {
        Matcher m = FLOOR.matcher(line);
        return m.find() ? m.group(1) : (line.contains("Entrance") ? "E" : floor);
    }

    private static void parseTab(Minecraft mc) {
        puzzleFails = 0;   // recounted fresh from the current tab list every pass
        for (String l : tabLines(mc)) {
            if (l.startsWith("Secrets Found:")) {
                if (l.endsWith("%")) {
                    secretsPercent = parsePercent(l);
                } else {
                    secretsFound = intAfterColon(l);
                }
            } else if (l.startsWith("Crypts:")) {
                crypts = intAfterColon(l);
            } else if (l.startsWith("Completed Rooms:")) {
                completedRooms = intAfterColon(l);
            } else if (l.startsWith("Team Deaths:")) {
                deaths = intAfterColon(l);
            } else if (l.contains("Time Elapsed:") || l.startsWith("Time:")) {
                secondsElapsed = parseTime(l);
            }
            // Only a failed puzzle costs score. "[✦]" is one you have not finished yet, and
            // counting those as failures took 10 points off each, which sank the whole run.
            if (l.contains(": [✖]")) {
                puzzleFails++;
            }
        }
    }

    // --- Score (skill + explore + speed + bonus), matching the shared community formula ----------

    private static void recomputeScore() {
        if (clearedPercent <= 0) {
            return;   // total-rooms estimate divides by this; wait until the sidebar has it
        }
        int fails = puzzleFails;   // re-counted fresh each pass in parseTab
        int totalRooms = (int) Math.round(completedRooms / clearedPercent);
        // The tab list does not count the boss room you are standing in, so add it. Blood is not
        // added: a blood room you have not opened is not a room you have cleared.
        int clearedRooms = completedRooms + 1;
        if (totalRooms <= 0) {
            return;
        }

        int deathPenalty = Math.max(0, deaths * 2 - 1);
        int skill = 20 + clamp(Math.min(floored(80.0 * clearedRooms / totalRooms), 80)
                - fails * 10 - deathPenalty, 0, 80);

        double secretsNeeded = secretsNeeded();
        int explore = clamp(Math.min(floored(60.0 * clearedRooms / totalRooms), 60)
                + Math.min(floored(40.0 * secretsPercent / secretsNeeded), 40), 0, 100);

        int speed = speedScore();

        int bonus = crypts >= 5 ? 5 : Math.max(0, crypts);
        if (mimicKilled) {
            bonus += 2;
        }
        if (princeKilled) {
            bonus += 1;
        }

        score = skill + explore + speed + bonus;
    }

    private static double secretsNeeded() {
        return switch (floor) {
            case "F1" -> 0.3;
            case "F2" -> 0.4;
            case "F3" -> 0.5;
            case "F4" -> 0.6;
            case "F5" -> 0.7;
            case "F6" -> 0.85;
            default -> 1.0;
        };
    }

    private static int timeLimit() {
        return switch (floor) {
            case "F1", "F2", "F3", "F5", "M6" -> 600;
            case "F4", "F6" -> 720;
            case "F7", "M7" -> 840;
            case "M1", "M2", "M3", "M4", "M5" -> 480;
            default -> 0;
        };
    }

    private static int speedScore() {
        int overtime = secondsElapsed + 480 - timeLimit();
        if (overtime < 492) {
            return 100;
        }
        if (overtime < 600) {
            return floored(140 - overtime / 12.0);
        }
        if (overtime < 840) {
            return floored(115 - overtime / 24.0);
        }
        if (overtime < 1140) {
            return floored(108 - overtime / 30.0);
        }
        if (overtime < 3570) {
            return floored(98.5 - overtime / 40.0);
        }
        return 0;
    }

    /** Score band label ("D".."S+") and its colour, for the HUD. */
    public static String scoreLabel() {
        if (score < 100) {
            return "D";
        }
        if (score < 160) {
            return "C";
        }
        if (score < 230) {
            return "B";
        }
        if (score < 270) {
            return "A";
        }
        if (score < 300) {
            return "S";
        }
        return "S+";
    }

    public static int scoreColor() {
        if (score < 100) {
            return 0xFFFC3E1C;
        }
        if (score < 160) {
            return 0xFF35A0FD;
        }
        if (score < 230) {
            return 0xFF90F35D;
        }
        if (score < 270) {
            return 0xFFBE1AFF;
        }
        return 0xFFFFCE1A;
    }

    // --- Reading the tab list and sidebar --------------------------------------------------------

    private static List<String> tabLines(Minecraft mc) {
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

    private static List<String> sidebarLines(Minecraft mc) {
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

    // --- Small parse helpers ---------------------------------------------------------------------

    private static int intAfterColon(String line) {
        String v = line.substring(line.indexOf(':') + 1).replaceAll("[^0-9]", "").trim();
        try {
            return v.isEmpty() ? 0 : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parsePercent(String line) {
        String v = line.substring(line.indexOf(':') + 1).replace("%", "").trim();
        try {
            return Double.parseDouble(v) * 0.01;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseTime(String line) {
        Matcher m = TIME.matcher(line.toLowerCase(Locale.ROOT));
        if (m.find()) {
            int min = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
            int sec = Integer.parseInt(m.group(2));
            return min * 60 + sec;
        }
        return secondsElapsed;
    }

    private static int floored(double v) {
        return (int) Math.floor(v);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
