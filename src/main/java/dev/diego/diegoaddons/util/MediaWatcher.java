package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Reads the currently playing track from Windows' <b>System Media Transport Controls</b> - the same
 * source that drives the media popup on the volume keys. That means it works for any player that
 * registers a media session (Spotify, browsers, foobar, …), not just one app, and it reports proper
 * playback state, album and position.
 *
 * <p>SMTC is a WinRT API with no direct Java binding, so the bridge is a <b>single long-lived
 * PowerShell child process</b>: it resolves the session manager once, then prints one tab-separated
 * line per second which this class parses. A persistent process is what makes this viable - spawning
 * PowerShell per poll would cost hundreds of milliseconds every time.
 *
 * <p>Playback position is interpolated between updates so it advances smoothly at one poll a second
 * instead of stepping.
 */
public final class MediaWatcher {
    private static final long RESTART_DELAY_MS = 5000;

    private static volatile String artist = "";
    private static volatile String title = "";
    private static volatile String album = "";
    private static volatile String app = "";
    private static volatile boolean playing = false;
    private static volatile boolean paused = false;
    private static volatile int positionSec = 0;
    private static volatile int durationSec = 0;
    private static volatile long updatedAt = 0;

    private static volatile boolean wanted = false;
    private static Thread thread;
    private static Process process;

    private MediaWatcher() {
    }

    public static String artist() {
        return artist;
    }

    public static String title() {
        return title;
    }

    public static String album() {
        return album;
    }

    /** The app owning the media session, e.g. {@code Spotify.exe}. */
    public static String app() {
        return app;
    }

    /** True while something is actually playing. */
    public static boolean isPlaying() {
        return playing;
    }

    /** True when a track is loaded but paused - distinguishable from nothing playing at all. */
    public static boolean isPaused() {
        return paused;
    }

    /** True when there is a track to show, playing or paused. */
    public static boolean hasTrack() {
        return playing || paused;
    }

    public static int duration() {
        return durationSec;
    }

    /** Playback position in seconds, advanced between polls so it does not visibly step. */
    public static int position() {
        if (!playing) {
            return positionSec;
        }
        long elapsed = (System.currentTimeMillis() - updatedAt) / 1000L;
        int p = positionSec + (int) Math.max(0, elapsed);
        return durationSec > 0 ? Math.min(p, durationSec) : p;
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Starts the bridge if it is not already running. Safe to call repeatedly. */
    public static synchronized void start() {
        if (!supported() || wanted) {
            return;
        }
        wanted = true;
        thread = new Thread(MediaWatcher::loop, "DiegoAddons Media");
        thread.setDaemon(true);   // must never hold the game open
        thread.start();
    }

    /** Stops the bridge and kills the child process. */
    public static synchronized void stop() {
        wanted = false;
        clear();
        if (process != null) {
            process.destroy();
            process = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private static void clear() {
        playing = false;
        paused = false;
        artist = "";
        title = "";
        album = "";
        positionSec = 0;
        durationSec = 0;
    }

    private static void loop() {
        while (wanted) {
            try {
                run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Media bridge failed: {}", e.toString());
            }
            clear();
            if (!wanted) {
                return;
            }
            // The bridge died (PowerShell blocked, session manager gone). Back off and retry.
            try {
                Thread.sleep(RESTART_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void run() throws Exception {
        Path script = writeScript();
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", script.toString());
        pb.redirectErrorStream(false);
        Process p = pb.start();
        synchronized (MediaWatcher.class) {
            process = p;
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (wanted && (line = r.readLine()) != null) {
                parse(line);
            }
        } finally {
            p.destroy();
        }
    }

    /** One line per poll: {@code status \t artist \t title \t album \t pos \t duration \t app}. */
    private static void parse(String line) {
        if (line.isEmpty()) {
            return;
        }
        if (line.equals("NONE")) {
            clear();
            return;
        }
        String[] f = line.split("\t", -1);
        if (f.length < 7) {
            return;
        }
        String status = f[0];
        playing = status.equalsIgnoreCase("Playing");
        paused = status.equalsIgnoreCase("Paused");
        artist = f[1];
        title = f[2];
        album = f[3];
        positionSec = parseInt(f[4]);
        durationSec = parseInt(f[5]);
        app = f[6];
        updatedAt = System.currentTimeMillis();
    }

    private static int parseInt(String s) {
        try {
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Formats seconds as {@code m:ss}, the way a player shows it. */
    public static String time(int seconds) {
        return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    /**
     * Writes the bridge script next to the config, overwriting it each launch so an updated mod
     * always ships an updated script.
     */
    private static Path writeScript() throws Exception {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("diegoaddons");
        Files.createDirectories(dir);
        Path script = dir.resolve("smtc.ps1");
        Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
        return script;
    }

    /**
     * Resolves the SMTC session manager once, then reports the current session every second.
     * {@code Console.Out} is written directly and flushed so lines arrive immediately when stdout is
     * redirected, which PowerShell's own output stream does not guarantee.
     */
    private static final String SCRIPT = """
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
                $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            function Await($t, $rt) {
                $m = $asTask.MakeGenericMethod($rt)
                $nt = $m.Invoke($null, @($t))
                $nt.Wait(-1) | Out-Null
                $nt.Result
            }
            function C($x) {
                if ($null -eq $x) { return '' }
                return ([string]$x) -replace "[`t`r`n]", ' '
            }
            $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType=WindowsRuntime]
            $mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
            $propType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
            $mgr = Await ($mgrType::RequestAsync()) ($mgrType)
            while ($true) {
                $out = 'NONE'
                try {
                    $s = $mgr.GetCurrentSession()
                    if ($null -ne $s) {
                        $p = Await ($s.TryGetMediaPropertiesAsync()) ($propType)
                        $tl = $s.GetTimelineProperties()
                        $st = $s.GetPlaybackInfo().PlaybackStatus
                        $out = @(
                            (C $st), (C $p.Artist), (C $p.Title), (C $p.AlbumTitle),
                            [int]$tl.Position.TotalSeconds, [int]$tl.EndTime.TotalSeconds,
                            (C $s.SourceAppUserModelId)
                        ) -join "`t"
                    }
                } catch { $out = 'NONE' }
                [Console]::Out.WriteLine($out)
                [Console]::Out.Flush()
                Start-Sleep -Milliseconds 1000
            }
            """;
}
