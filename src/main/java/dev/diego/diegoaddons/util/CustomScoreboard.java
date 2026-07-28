package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.CustomScoreboardModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the SkyBlock sidebar and hands back what should be shown in its place. The vanilla sidebar
 * is cancelled by {@code ScoreboardSidebarMixin} and
 * {@link dev.diego.diegoaddons.hud.ScoreboardElement} draws the replacement - as a HUD element like
 * every other, which is what lets it be moved and scaled.
 *
 * <p>The red score numbers are dropped: only each line's visible text (the team prefix+suffix),
 * which is where SkyBlock puts everything worth reading.
 */
public final class CustomScoreboard {
    private static final int MAX_LINES = 15;

    private CustomScoreboard() {
    }

    /**
     * The sidebar's title, or null when there is nothing to draw.
     *
     * <p>Split out from the drawing so the HUD element can ask for the content and lay it out with
     * everything else. This used to draw itself into the HUD pass at a fixed spot on the right,
     * which is the one HUD element you could not move.
     */
    public static Component title(Minecraft mc) {
        CustomScoreboardModule mod = CustomScoreboardModule.INSTANCE;
        Objective obj = objective(mc);
        if (mod == null || obj == null) {
            return null;
        }
        return mod.customTitle().isBlank()
                ? obj.getDisplayName()
                : Component.literal(mod.customTitle().replace('&', '§'));
    }

    private static Objective objective(Minecraft mc) {
        if (mc.player == null || mc.player.connection == null) {
            return null;
        }
        return mc.player.connection.scoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    /** The sidebar's lines, filtered and decorated the way the settings ask for. */
    public static List<Component> lines(Minecraft mc) {
        CustomScoreboardModule mod = CustomScoreboardModule.INSTANCE;
        Objective obj = objective(mc);
        if (mod == null || obj == null) {
            return List.of();
        }
        Scoreboard sb = mc.player.connection.scoreboard();

        // Highest score at the top, like vanilla; the red number itself is dropped.
        List<PlayerScoreEntry> entries = new ArrayList<>(sb.listPlayerScores(obj));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());

        List<Component> lines = new ArrayList<>();
        if (!mod.topText().isBlank()) {
            lines.add(Component.literal(mod.topText().replace('&', '§')));
        }
        for (PlayerScoreEntry e : entries) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            PlayerTeam team = sb.getPlayersTeam(e.owner());
            Component line = team != null
                    ? Component.empty().append(team.getPlayerPrefix()).append(team.getPlayerSuffix())
                    : e.ownerName();
            if (dropped(mod, LegacyText.strip(line.getString()))) {
                continue;
            }
            lines.add(line);
        }
        if (mod.showBank()) {
            String bank = bankLine(mc);
            if (bank != null) {
                lines.add(Component.literal("§6Bank: §f" + bank));
            }
        }
        if (!mod.bottomText().isBlank()) {
            lines.add(Component.literal(mod.bottomText().replace('&', '§')));
        }
        return lines;
    }

    /**
     * Whether a sidebar line is one of the ones you asked not to see.
     *
     * <p>SkyBlock's sidebar ends with a server id and the network's own address, which are three of
     * fifteen lines saying nothing about what you are doing. They are matched by shape rather than
     * by position, since the sidebar's length changes with where you are.
     */
    private static boolean dropped(CustomScoreboardModule mod, String plain) {
        String s = plain.trim();
        if (s.isEmpty()) {
            return false;
        }
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        if (mod.hideUrl() && (lower.contains("hypixel.net") || lower.contains("www."))) {
            return true;
        }
        // A server id is the trailing token of the date line ("11/12/24 m123AB") or a line of its
        // own: a letter, then digits and letters, no spaces.
        if (mod.hideServerId() && SERVER_ID.matcher(s).matches()) {
            return true;
        }
        return mod.hideDate() && DATE.matcher(s).find();
    }

    /** "m123AB" / "M12-3" - the instance name Hypixel prints at the bottom. */
    private static final java.util.regex.Pattern SERVER_ID =
            java.util.regex.Pattern.compile("^[a-zA-Z]{1,3}[0-9]{1,4}[a-zA-Z0-9-]*$");
    private static final java.util.regex.Pattern DATE =
            java.util.regex.Pattern.compile("\\d{2}/\\d{2}/\\d{2}");

    /** The bank balance from the tab list, or null when it is not shown there. */
    private static String bankLine(Minecraft mc) {
        for (String line : SkyblockLocation.tabLines(mc)) {
            String plain = LegacyText.strip(line).trim();
            if (plain.startsWith("Bank:")) {
                return plain.substring("Bank:".length()).trim();
            }
        }
        return null;
    }
}
