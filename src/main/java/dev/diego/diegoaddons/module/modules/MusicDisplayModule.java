package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.MediaWatcher;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the track currently playing on this machine as a HUD element.
 *
 * <p>Backed by Windows' System Media Transport Controls (see {@link MediaWatcher}), so it follows
 * whatever app owns the media session - Spotify, a browser tab, a local player - rather than being
 * tied to one program.
 */
public class MusicDisplayModule extends HudModule {
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

    public MusicDisplayModule() {
        super("music", "Music Display", "Shows the track playing on your PC.");
        settings.add(showArtist);
        settings.add(artistFirst);
        settings.add(twoLines);
        settings.add(showTime);
        settings.add(hideWhenPaused);
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
}
