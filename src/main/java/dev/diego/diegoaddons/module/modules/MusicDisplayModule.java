package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.SpotifyWatcher;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Shows the track currently playing in the Spotify desktop client as a HUD element.
 *
 * <p>Reads the client's window title rather than the Spotify Web API, so there is nothing to set up
 * - no app registration, no login. See {@link SpotifyWatcher} for what that costs: Windows only, no
 * album art, and a paused client looks the same as a closed one.
 */
public class MusicDisplayModule extends HudModule {
    private final BooleanSetting showArtist =
            new BooleanSetting(this, "artist", "Show artist", true);
    private final BooleanSetting artistFirst =
            new BooleanSetting(this, "artistFirst", "Artist first", false);
    private final BooleanSetting twoLines =
            new BooleanSetting(this, "twoLines", "Two lines", false);

    public MusicDisplayModule() {
        super("music", "Music Display", "Shows what you are playing on Spotify.");
        settings.add(showArtist);
        settings.add(artistFirst);
        settings.add(twoLines);
    }

    @Override
    protected void onEnable() {
        SpotifyWatcher.start();
        SpotifyWatcher.wanted = true;
    }

    @Override
    protected void onDisable() {
        SpotifyWatcher.wanted = false;
    }

    @Override
    protected String label() {
        return "Music";
    }

    @Override
    protected String value(Minecraft mc) {
        if (!SpotifyWatcher.isPlaying()) {
            return null;   // nothing playing - the chip hides itself
        }
        String song = SpotifyWatcher.title();
        String artist = SpotifyWatcher.artist();
        if (!showArtist.get()) {
            return song;
        }
        return artistFirst.get() ? artist + " - " + song : song + " - " + artist;
    }

    @Override
    protected String sampleValue() {
        return "misery. - pupsies";
    }

    /** Optionally splits song and artist across two rows, which reads better in a narrow chip. */
    @Override
    public List<String> hudLines(Minecraft mc) {
        if (!twoLines.get() || !showArtist.get() || !SpotifyWatcher.isPlaying()) {
            return super.hudLines(mc);
        }
        String first = artistFirst.get() ? SpotifyWatcher.artist() : SpotifyWatcher.title();
        String second = artistFirst.get() ? SpotifyWatcher.title() : SpotifyWatcher.artist();
        return List.of(showLabel.get() ? label() + ": " + first : first, second);
    }
}
