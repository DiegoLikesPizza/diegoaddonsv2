package dev.diego.diegoaddons.module.modules;

import dev.diego.configlib.hud.HudWidget;
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
import net.minecraft.client.renderer.RenderPipelines;
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
        // The shared text chip offers these; this element lays its own rows out and names itself
        // in them, so both were switches wired to nothing.
        settings.remove(centered);
        settings.remove(showLabel);
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

    /**
     * Never prefixed with a caption.
     *
     * <p>The rows already say what they are - a song and an artist - so "Music: " in front of them
     * was only ever taking up width. The setting is not offered, so this cannot drift back on.
     */
    @Override
    public boolean showLabel() {
        return false;
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
        out.add(first);
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

    // --- the HUD element ------------------------------------------------------------------------

    /**
     * Cover art beside the track rows, with a progress bar under them.
     *
     * <p>The cover and the bar are settings, so this one element has to cover both shapes: the widget
     * is built once at registration and cannot be swapped later. With both off it is simply the rows
     * on their panel, which is what the shared text chip drew anyway.
     *
     * <p>The artwork is a texture id from {@link CoverArt} rather than a URL. RenderLib needed the URL
     * because its image component only loaded pack assets; drawing the texture directly is what the
     * texture manager was always for, and it drops the remote-load path out of the render entirely.
     */
    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                Minecraft mc = Minecraft.getInstance();
                Font font = mc.font;
                if (font == null) {
                    return 1;
                }
                return PAD * 2 + textW(font, mc, false)
                        + (cover.get() ? contentH(mc, false) + GAP : 0);
            }

            @Override
            public int height() {
                return PAD * 2 + contentH(Minecraft.getInstance(), false);
            }

            @Override
            public boolean shouldRender() {
                return visible();
            }

            @Override
            public void render(GuiGraphicsExtractor g) {
                paint(g, false);
            }

            /** Nothing has to be playing for the element to be placed, so the editor uses the sample. */
            @Override
            public void renderPreview(GuiGraphicsExtractor g) {
                paint(g, true);
            }
        };
    }

    private void paint(GuiGraphicsExtractor g, boolean editor) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        List<String> lines = editor ? editorLines(mc) : hudLines(mc);
        if (lines.isEmpty()) {
            return;
        }
        Theme t = dev.diego.diegoaddons.gui.Themes.current();
        int colour = style().textColor();

        int contentH = contentH(mc, editor);
        int textW = textW(font, mc, editor);
        boolean wantCover = cover.get();
        int coverW = wantCover ? contentH + GAP : 0;
        int w = PAD * 2 + textW + coverW;
        int h = PAD * 2 + contentH;

        dev.diego.diegoaddons.hud.HudElements.panel(g, this, w, h, 7,
                dev.diego.diegoaddons.config.ConfigManager.get().smoothCorners);

        if (wantCover) {
            // The box is drawn whether or not the artwork has landed: its tint is what stands in
            // while the lookup is still out, so the layout does not jump when the cover arrives.
            UiRender.fillRounded(g, PAD, PAD, contentH, contentH, 3,
                    Theme.withAlpha(t.textFaint(), 0.25f), true);
            Identifier art = CoverArt.get(MediaWatcher.artist(), MediaWatcher.title());
            if (art != null) {
                // Region and texture sizes given as one square: the uv range then covers the whole
                // image whatever its real pixel size, which we do not know and do not need to.
                g.blit(RenderPipelines.GUI_TEXTURED, art, PAD, PAD, 0f, 0f,
                        contentH, contentH, contentH, contentH, contentH, contentH, 0xFFFFFFFF);
            }
        }

        int x = PAD + coverW;
        int y = PAD;
        for (String line : lines) {
            UiRender.text(g, font, line, Fonts.MEDIUM, x, y, colour);
            y += LINE_H;
        }

        if (progress.get()) {
            int by = PAD + lines.size() * LINE_H + GAP;
            UiRender.fillRounded(g, x, by, textW, BAR_H, BAR_H / 2,
                    Theme.withAlpha(t.textFaint(), 0.35f), true);
            int duration = MediaWatcher.duration();
            float f = duration > 0 ? Math.min(1f, MediaWatcher.position() / (float) duration) : 0f;
            int filled = Math.round(textW * f);
            if (filled > 0) {
                UiRender.fillRounded(g, x, by, filled, BAR_H, BAR_H / 2, colour, true);
            }
        }
    }
}
