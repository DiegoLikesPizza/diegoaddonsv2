package dev.diego.diegoaddons.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Album artwork for the track the {@link MediaWatcher} reports.
 *
 * <p><b>This looks the cover up online</b>, at Apple's public iTunes search endpoint, which means
 * the artist and title of what you are playing leave this machine. That is why the feature is
 * off by default and named accordingly - it is the user's choice to make, not a silent one.
 *
 * <p>Windows' own media session does carry a thumbnail, which would have been the local answer, but
 * it is only reachable as a WinRT stream and Windows PowerShell cannot project the generic WinRT
 * interfaces needed to read one - so the bridge this mod uses cannot get at it.
 *
 * <p>Lookups run on a daemon thread and each track is resolved once, successes and failures alike,
 * so a missing cover is not retried every frame.
 */
public final class CoverArt {
    private static final String ENDPOINT = "https://itunes.apple.com/search?limit=1&entity=song&term=";
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    /** Track key -> texture, or null once a lookup has failed (so it is not retried). */
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static volatile String inFlight;
    private static int counter;

    private CoverArt() {
    }

    /** A stable key for the current track. */
    private static String key(String artist, String title) {
        return (artist + "|" + title).toLowerCase(Locale.ROOT);
    }

    /**
     * The cover for the given track, or null while it is still loading or if there is none.
     * Starts the lookup on first call for a track. Safe to call every frame.
     */
    public static Identifier get(String artist, String title) {
        if (artist == null || title == null || title.isBlank()) {
            return null;
        }
        String key = key(artist, title);
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) {
                return CACHE.get(key);
            }
            if (key.equals(inFlight)) {
                return null;   // already being fetched
            }
            inFlight = key;
        }
        Thread t = new Thread(() -> fetch(key, artist, title), "DiegoAddons Cover");
        t.setDaemon(true);
        t.start();
        return null;
    }

    private static void fetch(String key, String artist, String title) {
        try {
            String term = URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8);
            String json = read(ENDPOINT + term);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            var results = root.getAsJsonArray("results");
            if (results == null || results.isEmpty()) {
                store(key, null);
                return;
            }
            String art = results.get(0).getAsJsonObject().get("artworkUrl100").getAsString();
            // Apple serves any size from the same path; ask for a larger PNG than the 100px JPEG.
            String big = art.replace("100x100bb.jpg", "512x512bb.png");
            byte[] png = readBytes(big);
            upload(key, png);
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.debug("[DiegoAddons] Cover lookup failed for {}: {}", key, e.toString());
            store(key, null);
        }
    }

    /** Texture registration must happen on the render thread, so hop back onto it. */
    private static void upload(String key, byte[] png) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try (InputStream in = new ByteArrayInputStream(png)) {
                NativeImage img = NativeImage.read(in);
                DynamicTexture tex = new DynamicTexture(() -> "diego-cover-" + key, img);
                Identifier id = Identifier.fromNamespaceAndPath(
                        DiegoAddonsV2Client.MOD_ID, "cover/" + (counter++));
                mc.getTextureManager().register(id, tex);
                store(key, id);
            } catch (Exception e) {
                DiegoAddonsV2Client.LOGGER.debug("[DiegoAddons] Cover decode failed: {}", e.toString());
                store(key, null);
            }
        });
    }

    private static void store(String key, Identifier id) {
        synchronized (CACHE) {
            CACHE.put(key, id);
            if (key.equals(inFlight)) {
                inFlight = null;
            }
        }
    }

    private static String read(String url) throws Exception {
        return new String(readBytes(url), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "DiegoAddonsV2");
        try (InputStream in = c.getInputStream()) {
            return in.readNBytes(MAX_BYTES);
        } finally {
            c.disconnect();
        }
    }
}
