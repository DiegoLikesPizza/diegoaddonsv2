package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reads the currently playing Spotify track from the desktop client's window title, which it keeps
 * as {@code "Artist - Song"} while playing.
 *
 * <p>This deliberately avoids the Spotify Web API: that would mean registering an application,
 * an OAuth login flow, a local redirect server and token refreshing, all so the HUD can show a line
 * of text. The window title needs none of it and works the moment Spotify is open.
 *
 * <p>The trade-offs are real and worth knowing: it is <b>Windows-only</b>, there is no album art or
 * playback position, and a paused client reports just "Spotify", so pausing is indistinguishable
 * from stopping.
 *
 * <p>Polling runs on its own daemon thread - it spawns a process, which must never happen on the
 * client thread.
 */
public final class SpotifyWatcher {
    private static final long POLL_MS = 2000;
    /** Window titles the Spotify process uses when it is not playing anything. */
    private static final String[] IDLE = {
            "spotify", "spotify premium", "spotify free", "n/a", "olemainthreadwndname",
            "msctfime ui", "default ime", "gdi+ window", "chrome_widgetwin_0", "chrome_widgetwin_1"
    };

    private static volatile String artist = "";
    private static volatile String title = "";
    private static volatile boolean playing = false;
    private static volatile boolean running = false;

    /** Set while the feature wants updates; the poll loop idles when false. */
    public static volatile boolean wanted = false;

    private SpotifyWatcher() {
    }

    public static String artist() {
        return artist;
    }

    public static String title() {
        return title;
    }

    public static boolean isPlaying() {
        return playing;
    }

    /** True on platforms where the window-title trick works at all. */
    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Starts the poll loop once; further calls are ignored. */
    public static synchronized void start() {
        if (running || !supported()) {
            return;
        }
        running = true;
        Thread t = new Thread(SpotifyWatcher::loop, "DiegoAddons Spotify");
        t.setDaemon(true);   // must never hold the game open
        t.start();
    }

    private static void loop() {
        while (true) {
            try {
                if (wanted) {
                    poll();
                } else {
                    playing = false;
                }
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // A failed poll must never kill the loop - just report nothing this round.
                playing = false;
            }
        }
    }

    private static void poll() throws Exception {
        Process p = new ProcessBuilder(
                "tasklist", "/v", "/fi", "IMAGENAME eq Spotify.exe", "/fo", "csv", "/nh")
                .redirectErrorStream(true)
                .start();
        String found = null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = windowTitle(line);
                if (t != null && isTrack(t)) {
                    found = t;   // several Spotify processes exist; only one carries the track
                    break;
                }
            }
        } finally {
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        }

        if (found == null) {
            playing = false;
            return;
        }
        int sep = found.indexOf(" - ");
        artist = found.substring(0, sep).trim();
        title = found.substring(sep + 3).trim();
        playing = true;
    }

    /**
     * The window title is the last CSV field. Taking everything after the final {@code ","}
     * separator keeps titles containing commas intact, which splitting on comma would not.
     */
    private static String windowTitle(String csvLine) {
        int i = csvLine.lastIndexOf("\",\"");
        if (i < 0) {
            return null;
        }
        String t = csvLine.substring(i + 3);
        if (t.endsWith("\"")) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim();
    }

    /** A real track always reads "Artist - Song"; everything else is an idle or helper window. */
    private static boolean isTrack(String t) {
        if (t.isEmpty() || !t.contains(" - ")) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        for (String idle : IDLE) {
            if (lower.equals(idle)) {
                return false;
            }
        }
        return true;
    }

    static {
        DiegoAddonsV2Client.LOGGER.debug("[DiegoAddons] Spotify watcher available: {}", supported());
    }
}
