package dev.diego.diegoaddons.config;

/**
 * One portal with a picture on it: where the portal is, and which file goes there. Persisted in
 * {@link AddonConfig}, so it is a plain Gson data object with a no-arg constructor and public fields.
 *
 * <p>The key is the portal's lowest corner as {@code x,y,z} - see
 * {@link dev.diego.diegoaddons.util.PortalImages} for why it is a position rather than an island
 * name and a position.
 */
public class PortalImage {
    public String key = "";
    /** The file name in {@code <config>/diegoaddons/images/}. */
    public String file = "";

    public PortalImage() {
    }

    public PortalImage(String key, String file) {
        this.key = key;
        this.file = file;
    }
}
