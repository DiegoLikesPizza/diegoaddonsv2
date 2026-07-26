package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.modules.CustomScoreboardModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Re-draws the SkyBlock sidebar with the addon's own panel styling. The vanilla sidebar is cancelled
 * by {@code ScoreboardSidebarMixin}; this draws a replacement from the same scoreboard data, but as a
 * rounded themed panel and <b>without the red score numbers</b> - only each line's visible text (the
 * team prefix+suffix), which is where SkyBlock puts everything worth reading.
 */
public final class CustomScoreboard {
    private static final int MAX_LINES = 15;

    private CustomScoreboard() {
    }

    /** Draws the replacement sidebar in the HUD layer. No-op unless the module is on. */
    public static void render(GuiGraphicsExtractor g, Minecraft mc) {
        CustomScoreboardModule mod = CustomScoreboardModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null || mc.player.connection == null
                || mc.options.hideGui) {
            return;
        }
        Scoreboard sb = mc.player.connection.scoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            return;
        }

        // Highest score at the top, like vanilla; the red number itself is dropped.
        List<PlayerScoreEntry> entries = new ArrayList<>(sb.listPlayerScores(obj));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());

        List<Component> lines = new ArrayList<>();
        for (PlayerScoreEntry e : entries) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            PlayerTeam team = sb.getPlayersTeam(e.owner());
            lines.add(team != null
                    ? Component.empty().append(team.getPlayerPrefix()).append(team.getPlayerSuffix())
                    : e.ownerName());
        }
        draw(g, mc, mod, obj.getDisplayName(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, CustomScoreboardModule mod,
                             Component title, List<Component> lines) {
        var font = mc.font;
        Theme t = Themes.current();
        boolean smooth = ConfigManager.get().smoothCorners;
        int pad = 5;
        int lineH = font.lineHeight + 1;

        int inner = font.width(title);
        for (Component line : lines) {
            inner = Math.max(inner, font.width(line));
        }
        int w = inner + pad * 2;
        int h = pad * 2 + lineH + 2 + lines.size() * lineH;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = sw - w - 4;
        int y = Math.max(2, (sh - h) / 2);

        if (mod.background()) {
            int bg = (0xCC << 24) | (t.surface() & 0x00FFFFFF);
            UiRender.panel(g, x, y, w, h, 6, bg, Theme.withAlpha(t.border(), 0.9f), smooth);
        }
        int ty = y + pad;
        g.text(font, title, x + (w - font.width(title)) / 2, ty, t.accent(), true);
        ty += lineH + 2;
        for (Component line : lines) {
            g.text(font, line, x + pad, ty, t.text(), true);
            ty += lineH;
        }
    }
}
