package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.platform.NativeImage;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads player-skin PNGs from disk and registers them as GPU textures so the renderer can draw
 * players with a replacement skin. Files live in {@code <config>/diegoaddons/skins/} and are named:
 *
 * <ul>
 *   <li>{@code <PlayerName>.png} - replaces that one player's skin,</li>
 *   <li>{@code _all.png} - a fallback skin for every other player,</li>
 *   <li>{@code _self.png} - your own skin.</li>
 * </ul>
 *
 * <p>Each file is read once and cached (both hits and misses), so lookups are cheap per frame.
 * {@link #reload()} clears the cache so newly added or edited files are picked up. All methods must
 * be called on the render thread (texture registration touches GL state).
 */
public final class SkinChanger {
    private static final Map<String, Identifier> loaded = new HashMap<>();
    private static final Set<String> missing = new HashSet<>();
    private static int counter = 0;

    private SkinChanger() {
    }

    public static Path folder() {
        return dev.diego.diegoaddons.config.ModFiles.skins();
    }

    /** Create the skins folder if it does not exist yet. Best-effort; safe to call repeatedly. */
    public static void ensureFolder() {
        folder();
    }

    /** Forget cached textures (and misses) so added or changed PNGs are re-read on next use. */
    public static void reload() {
        loaded.clear();
        missing.clear();
        ensureFolder();
    }

    /**
     * The registered texture for the skin file {@code key + ".png"}, loaded and cached on first use.
     * Returns {@code null} if the file is absent or cannot be read.
     */
    public static Identifier get(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        Identifier cached = loaded.get(key);
        if (cached != null) {
            return cached;
        }
        if (missing.contains(key)) {
            return null;
        }

        Path file = folder().resolve(key + ".png");
        if (!Files.isRegularFile(file)) {
            // Case-insensitive fallback so "Notch.png" still resolves from a lowercased key, etc.
            Path lower = folder().resolve(key.toLowerCase() + ".png");
            if (!Files.isRegularFile(lower)) {
                missing.add(key);
                return null;
            }
            file = lower;
        }

        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            DynamicTexture tex = new DynamicTexture(() -> "diego-skin-" + key, img);
            Identifier id = Identifier.fromNamespaceAndPath(
                    DiegoAddonsV2Client.MOD_ID, "skins/" + sanitize(key) + "_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            loaded.put(key, id);
            return id;
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Failed to load skin '{}': {}", key, e.toString());
            missing.add(key);
            return null;
        }
    }

    /** Reduce an arbitrary key to the character set allowed in a resource-location path. */
    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            sb.append(ok ? c : '_');
        }
        return sb.isEmpty() ? "skin" : sb.toString();
    }
}
