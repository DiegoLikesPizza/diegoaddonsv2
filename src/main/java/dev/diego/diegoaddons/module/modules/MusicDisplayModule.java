package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.CoverArt;
import dev.diego.diegoaddons.util.MediaWatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the track currently playing on this machine as a HUD element.
 *
 * <p>Backed by Windows' System Media Transport Controls (see {@link MediaWatcher}), so it follows
 * whatever app owns the media session - Spotify, a browser tab, a local player - rather than being
 * tied to one program.
 *
 * <p>With the progress bar or the album cover enabled the element is drawn custom; otherwise it
 * stays the plain themed text chip every other HUD module uses.
 */
public class MusicDisplayModule extends HudModule {
    private static final int PAD = 5;
    private static final int LINE_H = Fonts.BODY_H;
    private static final int BAR_H = 3;
    private static final int GAP = 4;

    private final BooleanSetting showArtist =
            new BooleanSetting(this, "artist", "Show artist", true);
    private final BooleanSetting artistFirst =
            new BooleanSetting(this, "artistFirst", "Artist first", false);
    private final BooleanSetting twoLines =
            new BooleanSetting(this, "twoLines", "Two lines", false);
    private final BooleanSetting showTime =
            new BooleanSetting(this, "time", "Show time", false);
    private final BooleanSetting hideWhenPaused =
            new BooleanSetting(this, "hidePaused", "Hide when paused", false);
    private final BooleanSetting progress =
            new BooleanSetting(this, "progress", "Progress bar", false);
    /** Off by default on purpose: this one sends the track title to an online service. */
    private final BooleanSetting cover =
            new BooleanSetting(this, "cover", "Album cover (online)", false);

    public MusicDisplayModule() {
        super("music", "Music Display", "Shows the track playing on your PC.");
        settings.add(showArtist);
        settings.add(artistFirst);
        settings.add(twoLines);
        settings.add(showTime);
        settings.add(hideWhenPaused);
        settings.add(progress);
        settings.add(cover);
    }

    @Override
    protected void onEnable() {
        MediaWatcher.start();
    }

    @Override
    protected void onDisable() {
        MediaWatcher.stop();
    }

    /** Whether there is anything worth drawing right now, honouring the paused option. */
    private boolean visible() {
        if (!MediaWatcher.hasTrack()) {
            return false;
        }
        return !(MediaWatcher.isPaused() && hideWhenPaused.get());
    }

    @Override
    protected String label() {
        return MediaWatcher.isPaused() ? "Paused" : "Music";
    }

    @Override
    protected String value(Minecraft mc) {
        if (!visible()) {
            return null;   // nothing playing - the chip hides itself
        }
        String song = MediaWatcher.title();
        String artist = MediaWatcher.artist();
        String main = !showArtist.get() ? song
                : (artistFirst.get() ? artist + " - " + song : song + " - " + artist);
        return showTime.get() ? main + "  " + time() : main;
    }

    private String time() {
        return MediaWatcher.time(MediaWatcher.position()) + " / " + MediaWatcher.time(MediaWatcher.duration());
    }

    @Override
    protected String sampleValue() {
        return "misery. - pupsies";
    }

    /** Optionally splits song, artist and time across rows, which reads better in a narrow chip. */
    @Override
    public List<String> hudLines(Minecraft mc) {
        if (!twoLines.get() || !visible()) {
            return super.hudLines(mc);
        }
        String first = artistFirst.get() ? MediaWatcher.artist() : MediaWatcher.title();
        String second = artistFirst.get() ? MediaWatcher.title() : MediaWatcher.artist();

        List<String> out = new ArrayList<>(2);
        out.add(showLabel.get() ? label() + ": " + first : first);
        if (showArtist.get()) {
            out.add(showTime.get() ? second + "  " + time() : second);
        } else if (showTime.get()) {
            out.add(time());
        }
        return out;
    }

    // --- custom drawing, only once the bar or the cover is on ------------------------------------

    /** Whether this element needs the custom layout rather than the shared text chip. */
    private boolean custom() {
        return progress.get() || cover.get();
    }

    /** Height of the text block plus the bar beneath it. */
    private int contentH(Minecraft mc, boolean editor) {
        int lines = Math.max(1, (editor ? editorLines(mc) : hudLines(mc)).size());
        return lines * LINE_H + (progress.get() ? GAP + BAR_H : 0);
    }

    private int textW(Font font, Minecraft mc, boolean editor) {
        int w = 0;
        for (String line : (editor ? editorLines(mc) : hudLines(mc))) {
            w = Math.max(w, font.width(Fonts.t(line, Fonts.MEDIUM)));
        }
        return w;
    }

    /** Whether the cover art is on; read by the RenderLib element. */
    public boolean showCover() {
        return cover.get();
    }

    /** Whether the progress bar is on; read by the RenderLib element. */
    public boolean showProgress() {
        return progress.get();
    }

    /** Whether this element needs the cover/bar layout rather than the shared text chip. */
    public boolean customLayout() {
        return custom();
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.MusicElement(this, root);
    }

    @Override
    public int hudWidth(Font font, Minecraft mc, boolean editor) {
        if (!custom()) {
            return super.hudWidth(font, mc, editor);
        }
        int coverW = cover.get() ? contentH(mc, editor) + GAP : 0;
        return PAD * 2 + coverW + Math.max(60, textW(font, mc, editor));
    }

    @Override
    public int hudHeight(Minecraft mc, boolean editor) {
        if (!custom()) {
            return super.hudHeight(mc, editor);
        }
        return PAD * 2 + contentH(mc, editor);
    }

    @Override
    public boolean drawLocal(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth, Minecraft mc, boolean editor) {
        if (!custom()) {
            return super.drawLocal(g, font, t, smooth, mc, editor);
        }
        List<String> lines = editor ? editorLines(mc) : hudLines(mc);
        if (lines.isEmpty()) {
            return false;
        }
        int w = hudWidth(font, mc, editor);
        int h = hudHeight(mc, editor);
        int contentH = contentH(mc, editor);

        int bg = (0xCC << 24) | (t.surface() & 0x00FFFFFF);
        UiRender.fillRounded(g, 0, 0, w, h, 7, bg, smooth);
        UiRender.strokeRounded(g, 0, 0, w, h, 7, Theme.withAlpha(t.border(), 0.9f), smooth);

        int x = PAD;
        if (cover.get()) {
            int size = contentH;
            Identifier art = CoverArt.get(MediaWatcher.artist(), MediaWatcher.title());
            if (art != null) {
                g.blit(art, x, PAD, size, size, 0f, 1f, 0f, 1f);
            } else {
                // Placeholder while the lookup runs, so the layout does not jump when it lands.
                UiRender.fillRounded(g, x, PAD, size, size, 3,
                        Theme.withAlpha(t.textFaint(), 0.25f), smooth);
            }
            x += size + GAP;
        }

        for (int i = 0; i < lines.size(); i++) {
            int ty = Fonts.centerTop(PAD + i * LINE_H, LINE_H, 10);
            UiRender.text(g, font, lines.get(i), Fonts.MEDIUM, x, ty, color());
        }

        if (progress.get()) {
            int barY = PAD + contentH - BAR_H;
            int barW = w - PAD - x;
            UiRender.fillRounded(g, x, barY, barW, BAR_H, BAR_H / 2,
                    Theme.withAlpha(t.textFaint(), 0.35f), smooth);
            int dur = MediaWatcher.duration();
            float f = dur > 0 ? Math.min(1f, MediaWatcher.position() / (float) dur) : 0f;
            int filled = Math.round(barW * f);
            if (filled > 0) {
                UiRender.fillRounded(g, x, barY, filled, BAR_H, BAR_H / 2, color(), smooth);
            }
        }
        return true;
    }
}
