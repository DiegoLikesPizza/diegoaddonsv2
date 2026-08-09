package dev.diego.diegoaddons.module.modules;

import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
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
    private static final int BAR_H = 3;

    // --- the card layout (cover / title / artist / bar / time) ----------------------------------
    /** Breathing room around the card's contents. Wider than the chip's, which is what makes it a card. */
    private static final int CARD_PAD = 6;
    /** Between the cover and the text column. */
    private static final int CARD_GAP = 7;
    private static final int TITLE_H = Fonts.BODY_H;
    private static final int ARTIST_H = Fonts.SMALL_H;
    private static final int TIME_H = Fonts.SMALL_H;
    /** Above the bar, and between the bar and the time under it. */
    private static final int BAR_GAP = 4;
    private static final int TIME_GAP = 3;
    private static final int CARD_RADIUS = 8;
    private static final int COVER_RADIUS = 4;

    private static final String SAMPLE_TITLE = "Pretty Girl";
    private static final String SAMPLE_ARTIST = "Clairo";

    private final BooleanSetting showArtist =
            new BooleanSetting(this, "artist", "Show artist", true);
    private final BooleanSetting artistFirst =
            new BooleanSetting(this, "artistFirst", "Artist first", false);
    private final BooleanSetting twoLines =
            new BooleanSetting(this, "twoLines", "Two lines", false);
    private final BooleanSetting showTime =
            new BooleanSetting(this, "time", "Show time", true);
    private final BooleanSetting hideWhenPaused =
            new BooleanSetting(this, "hidePaused", "Hide when paused", false);
    private final BooleanSetting progress =
            new BooleanSetting(this, "progress", "Progress bar", true);
    /** Off by default on purpose: this one sends the track title to an online service. */
    private final BooleanSetting cover =
            new BooleanSetting(this, "cover", "Album cover (online)", false);
    /**
     * How wide the text column is, in pixels.
     *
     * <p>The card cannot size itself to its text the way the chip does: the bar and the time are
     * laid out against a width, and a card that grew and shrank with every track title would jump
     * about on the HUD as songs changed. A fixed column is what makes it hold still - anything too
     * long for it is truncated.
     */
    private final NumberSetting width =
            new NumberSetting(this, "width", "Card width", 130, 70, 260, 5);

    public MusicDisplayModule() {
        super("music", "Music Display", "Shows the track playing on your PC.");
        settings.add(showArtist);
        settings.add(artistFirst);
        settings.add(twoLines);
        settings.add(showTime);
        settings.add(hideWhenPaused);
        settings.add(progress);
        settings.add(cover);
        settings.add(width);
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

    // --- the card ------------------------------------------------------------------------------

    /**
     * Height of the text column: the title, then whichever of artist, bar and time are on.
     *
     * <p>This is also the cover's side. The artwork is square and fills the column's height, which
     * is what keeps the card looking like one block rather than a picture with text loose beside it.
     */
    private int contentH() {
        int h = TITLE_H;
        if (showArtist.get()) {
            h += ARTIST_H;
        }
        if (progress.get()) {
            h += BAR_GAP + BAR_H;
        }
        if (showTime.get()) {
            h += TIME_GAP + TIME_H;
        }
        return h;
    }

    private int textW() {
        return (int) width.get();
    }

    /** Trims a line to the column, with an ellipsis, so a long title cannot widen the card. */
    private static String clip(Font font, String text, net.minecraft.network.chat.Style style, int max) {
        if (text == null || text.isEmpty() || Fonts.width(font, text, style) <= max) {
            return text == null ? "" : text;
        }
        int ellipsis = Fonts.width(font, "…", style);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (Fonts.width(font, sb.toString(), style) + ellipsis > max) {
                sb.setLength(Math.max(0, sb.length() - 1));
                break;
            }
        }
        return sb + "…";
    }

    // --- the HUD element ------------------------------------------------------------------------

    /**
     * The card: cover, title, artist, bar, time.
     *
     * <p>Every part of it is a setting, and the widget is built once at registration and cannot be
     * swapped later - so this one element has to draw every combination rather than there being a
     * shape per arrangement. Turning them all off leaves the title on its plate, which is the floor
     * this degrades to.
     *
     * <p>The artwork is a texture id from {@link CoverArt}, blitted straight from the texture
     * manager - there is no URL anywhere in the render path.
     */
    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                return CARD_PAD * 2 + textW() + (cover.get() ? contentH() + CARD_GAP : 0);
            }

            @Override
            public int height() {
                return CARD_PAD * 2 + contentH();
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

    /**
     * The card: cover on the left, title over artist, a progress bar under them with the time
     * right-aligned beneath it.
     *
     * <p><b>On the colours.</b> The title is the theme's plain text and the artist its muted shade,
     * rather than both taking the element's accent - a song and the person who made it are not the
     * same kind of thing, and drawing them in one colour flattened that. The bar keeps the accent,
     * which is where the eye should land. Switching the per-element override on hands the title back
     * to whatever colour was chosen, because that choice was made about this element specifically.
     */
    private void paint(GuiGraphicsExtractor g, boolean editor) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        Theme t = dev.diego.diegoaddons.gui.Themes.current();
        // Nothing is playing in the editor, so the card is drawn against a sample track.
        boolean live = !editor && visible();
        String title = live ? MediaWatcher.title() : SAMPLE_TITLE;
        String artist = live ? MediaWatcher.artist() : SAMPLE_ARTIST;
        if (title == null || title.isEmpty()) {
            title = SAMPLE_TITLE;
        }

        int accent = style().accentColor();
        int titleColour = customStyleOn() ? style().textColor() : t.text();
        int artistColour = t.textMuted();

        int contentH = contentH();
        int textW = textW();
        boolean wantCover = cover.get();
        int coverW = wantCover ? contentH + CARD_GAP : 0;
        int w = CARD_PAD * 2 + textW + coverW;
        int h = CARD_PAD * 2 + contentH;
        boolean smooth = dev.diego.diegoaddons.config.ConfigManager.get().smoothCorners;

        dev.diego.diegoaddons.hud.HudElements.panel(g, this, w, h, CARD_RADIUS, smooth);

        if (wantCover) {
            // The box is drawn whether or not the artwork has landed: its tint is what stands in
            // while the lookup is still out, so the layout does not jump when the cover arrives.
            UiRender.fillRounded(g, CARD_PAD, CARD_PAD, contentH, contentH, COVER_RADIUS,
                    Theme.withAlpha(t.textFaint(), 0.25f), smooth);
            Identifier art = live ? CoverArt.get(artist, title) : null;
            if (art != null) {
                // Region and texture sizes given as one square: the uv range then covers the whole
                // image whatever its real pixel size, which we do not know and do not need to.
                g.blit(RenderPipelines.GUI_TEXTURED, art, CARD_PAD, CARD_PAD, 0f, 0f,
                        contentH, contentH, contentH, contentH, contentH, contentH, 0xFFFFFFFF);
            }
        }

        int x = CARD_PAD + coverW;
        int y = CARD_PAD;
        UiRender.text(g, font, clip(font, title, Fonts.MEDIUM, textW), Fonts.MEDIUM, x, y, titleColour);
        y += TITLE_H;
        if (showArtist.get()) {
            UiRender.text(g, font, clip(font, artist, Fonts.SMALL, textW), Fonts.SMALL,
                    x, y, artistColour);
            y += ARTIST_H;
        }

        if (progress.get()) {
            y += BAR_GAP;
            UiRender.fillRounded(g, x, y, textW, BAR_H, BAR_H / 2,
                    Theme.withAlpha(t.textFaint(), 0.35f), smooth);
            int filled = Math.round(textW * fraction(live));
            if (filled > 0) {
                UiRender.fillRounded(g, x, y, filled, BAR_H, BAR_H / 2, accent, smooth);
            }
            y += BAR_H;
        }

        if (showTime.get()) {
            y += TIME_GAP;
            String time = live ? time() : "0:26 / 2:58";
            UiRender.textRight(g, font, time, Fonts.SMALL, x + textW, y, artistColour);
        }
    }

    /** How far through the track we are, 0-1. The sample sits partway along so the bar reads as one. */
    private float fraction(boolean live) {
        if (!live) {
            return 0.15f;
        }
        int duration = MediaWatcher.duration();
        return duration > 0 ? Math.min(1f, MediaWatcher.position() / (float) duration) : 0f;
    }
}
