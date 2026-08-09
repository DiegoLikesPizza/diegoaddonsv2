package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.config.ConfigManager;

/**
 * A colour, which is three different things depending on how it is set.
 *
 * <ul>
 *   <li><b>Single</b> - one colour, and that is all.</li>
 *   <li><b>Gradient</b> - two colours, blended across whatever is being drawn. What "across" means is
 *       the drawing's business: up a box, along a line.</li>
 *   <li><b>Rainbow</b> - the hue walks around the wheel over time, so everything drawn with it
 *       shifts together rather than each thing running its own clock.</li>
 * </ul>
 *
 * <p>Stored as one string - {@code mode|aarrggbb|aarrggbb} - in the owner module's {@code texts}
 * map, so a colour is one config entry rather than four, and an unreadable one falls back to its
 * default instead of throwing.
 */
public class ColorSetting extends Setting {
    public static final int SINGLE = 0;
    public static final int GRADIENT = 1;
    public static final int RAINBOW = 2;

    public static final String[] MODES = {"Single", "Gradient", "Rainbow"};

    /** A full turn of the rainbow, in milliseconds. */
    private static final float RAINBOW_PERIOD = 4000f;

    private final int defA;
    private final int defB;

    public ColorSetting(Module owner, String key, String name, int defaultColor) {
        this(owner, key, name, defaultColor, 0xFF00FFFF);
    }

    public ColorSetting(Module owner, String key, String name, int defaultA, int defaultB) {
        super(owner, key, name);
        this.defA = defaultA;
        this.defB = defaultB;
    }

    // --- state ------------------------------------------------------------------------------------

    public int mode() {
        return parse()[0];
    }

    public int colorA() {
        return parse()[1];
    }

    public int colorB() {
        return parse()[2];
    }

    public void setMode(int mode) {
        int[] v = parse();
        store(Math.floorMod(mode, MODES.length), v[1], v[2]);
    }

    public void cycleMode() {
        setMode(mode() + 1);
    }

    public void setColorA(int argb) {
        int[] v = parse();
        store(v[0], argb, v[2]);
    }

    public void setColorB(int argb) {
        int[] v = parse();
        store(v[0], v[1], argb);
    }

    // --- what to actually draw with ---------------------------------------------------------------

    /** The colour right now, for something drawn in one flat colour. */
    public int argb() {
        return argbAt(0f);
    }

    /**
     * The colour at {@code t} along whatever is being drawn, {@code 0..1}.
     *
     * <p>Single ignores it, gradient blends the two ends across it, and rainbow offsets the hue by
     * it - so a rainbow box is a band of the wheel rather than one flat colour that happens to be
     * changing.
     */
    public int argbAt(float t) {
        int[] v = parse();
        return switch (v[0]) {
            case GRADIENT -> lerp(v[1], v[2], clamp01(t));
            case RAINBOW -> rainbow(clamp01(t), alphaOf(v[1]));
            default -> v[1];
        };
    }

    /** The rainbow's current hue, offset by {@code t}, at the given alpha. */
    private static int rainbow(float t, int alpha) {
        float hue = ((System.currentTimeMillis() % (long) RAINBOW_PERIOD) / RAINBOW_PERIOD + t * 0.5f) % 1f;
        return (alpha << 24) | (hsvToRgb(hue, 0.85f, 1f) & 0x00FFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int ca = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int cr = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int cg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int cb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    /** Plain HSV, so a hue slider and the rainbow agree on what a hue looks like. */
    public static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6f) % 6;
        float f = h * 6f - (int) (h * 6f);
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static int alphaOf(int argb) {
        int a = (argb >>> 24) & 0xFF;
        return a == 0 ? 0xFF : a;
    }

    private static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    // --- persistence ------------------------------------------------------------------------------

    /**
     * The three values packed as {@code mode|aarrggbb|aarrggbb}.
     *
     * <p>One setting, three configlib options: the mode and the two colours are declared separately
     * (see {@code ModuleSpec}) and each persists on its own, so this string is now only how the three
     * are held together in memory. Keeping the packed form means the parsing below - and its
     * tolerance for a value edited into nonsense - did not have to be rewritten.
     */
    private String packed;

    /** {@code [mode, colorA, colorB]}, falling back to the defaults for anything unreadable. */
    private int[] parse() {
        String raw = packed;
        if (raw != null) {
            String[] parts = raw.split("\\|");
            if (parts.length == 3) {
                try {
                    int mode = Math.floorMod(Integer.parseInt(parts[0]), MODES.length);
                    return new int[]{mode,
                            (int) Long.parseLong(parts[1], 16),
                            (int) Long.parseLong(parts[2], 16)};
                } catch (NumberFormatException ignored) {
                    // Hand-edited into nonsense; the defaults below are a better answer than a crash.
                }
            }
        }
        return new int[]{SINGLE, defA, defB};
    }

    private void store(int mode, int a, int b) {
        String next = mode + "|" + String.format("%08x", a) + "|" + String.format("%08x", b);
        if (next.equals(packed)) {
            return;
        }
        packed = next;
        ConfigManager.save();
    }
}
