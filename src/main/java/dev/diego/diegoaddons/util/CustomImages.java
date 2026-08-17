package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.platform.NativeImage;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ModFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * PNGs the user drops into {@code <config>/diegoaddons/images/}, loaded as GPU textures for the
 * features that paint a picture onto something in the world - the portals and the Hub's map wall.
 *
 * <p>Modelled on {@link SkinChanger}, which has read PNGs off disk since 2.2, with one addition
 * that matters here: <b>the size comes back too</b>. A skin is always the same shape so its
 * dimensions never had to be known; an image stretched over a portal has to know its own aspect
 * ratio, or "fit" and "fill" cannot be told apart from "stretch".
 *
 * <p>Each name is read once and the answer cached, misses included, so a wrong filename costs one
 * failed read rather than one per frame. {@link #reload()} drops the cache, which is how an edited
 * file is picked up without restarting.
 *
 * <p>Everything here touches GL state, so it must be called on the render thread.
 */
public final class CustomImages {
    /** A loaded image: the texture to draw with, and how big the PNG was. */
    public record Image(Identifier id, int width, int height) {
        /** Width over height, or 1 for a degenerate image - never a division by zero. */
        public double aspect() {
            return width > 0 && height > 0 ? (double) width / height : 1.0;
        }
    }

    private static final Map<String, Image> LOADED = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
    private static int counter;

    private CustomImages() {
    }

    /** {@code <config>/diegoaddons/images/}, created if it is not there yet. */
    public static Path folder() {
        return ModFiles.folder("images");
    }

    /** Forget every loaded texture, so added or edited PNGs are re-read on next use. */
    public static void reload() {
        LOADED.clear();
        MISSING.clear();
        folder();
    }

    /** The PNG file names in the folder, sorted, for the commands to list. */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder())) {
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(out::add);
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.debug("[DiegoAddons] could not list {}: {}", folder(), e.toString());
        }
        return out;
    }

    /**
     * The image for a file name, loaded on first use. Null if the name is blank, the file is not
     * there, or it is not a readable PNG.
     *
     * <p>The {@code .png} is optional in what the user types - the folder holds nothing else, so
     * demanding the extension would only ever be a way to get it wrong.
     */
    public static Image get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim();
        Image cached = LOADED.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(key)) {
            return null;
        }
        Path file = resolve(key);
        if (file == null) {
            MISSING.add(key);
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            int w = img.getWidth();
            int h = img.getHeight();
            DynamicTexture tex = new DynamicTexture(() -> "diego-image-" + key, img);
            Identifier id = Identifier.fromNamespaceAndPath(
                    DiegoAddonsV2Client.MOD_ID, "images/" + sanitize(key) + "_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            Image loaded = new Image(id, w, h);
            LOADED.put(key, loaded);
            return loaded;
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] could not load image '{}': {}", key, e.toString());
            MISSING.add(key);
            return null;
        }
    }

    /**
     * The file for a typed name: as given, with {@code .png} appended, or either of those lowercased.
     * Windows would find all four anyway; Linux would not, and a config is shared between them.
     */
    private static Path resolve(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String[] candidates = name.toLowerCase(Locale.ROOT).endsWith(".png")
                ? new String[]{name, lower}
                : new String[]{name + ".png", lower + ".png", name, lower};
        for (String c : candidates) {
            Path p = folder().resolve(c);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    /** Reduce a file name to the character set a resource-location path allows. */
    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            sb.append(ok ? c : '_');
        }
        return sb.isEmpty() ? "image" : sb.toString();
    }
}
