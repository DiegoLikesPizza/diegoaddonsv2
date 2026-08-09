package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;

import java.util.List;

/** The built-in theme presets and helpers to select between them. */
public final class Themes {
    //                       name        overlay     surface     surfaceAlt  elevated    accent      accentTo    accentText  text        textMuted   textFaint   border      shadow
    public static final Theme GALAXY = new Theme("Galaxy",
            0xC8060411, 0xFF15111E, 0xFF1C1728, 0xFF272033, 0xFF8B5CF6, 0xFFC084FC, 0xFF140A22,
            0xFFF5F2FC, 0xFFA79CBE, 0xFF6A6182, 0xFF2C2540, 0xEE050208);

    public static final Theme MIDNIGHT = new Theme("Midnight",
            0xC8030610, 0xFF0D131E, 0xFF141B28, 0xFF1D2636, 0xFF3B82F6, 0xFF67C7FF, 0xFF06101E,
            0xFFEFF4FB, 0xFF93A4BD, 0xFF5C6A80, 0xFF212C3D, 0xEE020610);

    public static final Theme MINT = new Theme("Mint",
            0xC8021009, 0xFF0C1714, 0xFF12201B, 0xFF1A2A23, 0xFF10B981, 0xFF5EEAD4, 0xFF03130D,
            0xFFEDFBF5, 0xFF8FB8AB, 0xFF587A70, 0xFF1D2E27, 0xEE020D09);

    public static final Theme CRIMSON = new Theme("Crimson",
            0xC8110404, 0xFF1A1012, 0xFF241618, 0xFF301C1F, 0xFFF43F5E, 0xFFFB7185, 0xFF1E0709,
            0xFFFCEBEE, 0xFFC79A9F, 0xFF8A6165, 0xFF382226, 0xEE0E0304);

    public static final Theme LIGHT = new Theme("Light",
            0x55121A2B, 0xFFFCFDFF, 0xFFF2F4F9, 0xFFE9EDF6, 0xFF6366F1, 0xFF8B8FF9, 0xFFFFFFFF,
            0xFF171C29, 0xFF636B80, 0xFF9AA2B6, 0xFFDFE4EE, 0x33667088);

    public static final List<Theme> ALL = List.of(GALAXY, MIDNIGHT, MINT, CRIMSON, LIGHT);

    private Themes() {
    }

    public static Theme byName(String name) {
        for (Theme t : ALL) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return GALAXY;
    }

    /**
     * The active theme, with the user's accent applied if they set one.
     *
     * <p>Everything that draws reads this rather than {@link #byName}, so a custom accent reaches
     * the HUD, the toasts and the settings GUI from one place instead of each of them having to
     * remember to check the override.
     */
    public static Theme current() {
        Theme base = byName(ConfigManager.get().theme);
        if (!ConfigManager.get().customAccent) {
            return base;
        }
        return base.withAccent(ConfigManager.get().accentColor);
    }

    /** The active theme's own accent, ignoring any override - for showing what a preset looks like. */
    public static Theme preset() {
        return byName(ConfigManager.get().theme);
    }

    /** The accent in effect: the override when on, otherwise the theme's own. */
    public static int accent() {
        return current().accent();
    }

    /** Index of the active theme in {@link #ALL}, for the settings menu's choice row. */
    public static int currentIndex() {
        Theme active = preset();
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).name().equals(active.name())) {
                return i;
            }
        }
        return 0;
    }

    /** Names of every built-in theme, in order, for the settings menu's choice row. */
    public static String[] names() {
        return ALL.stream().map(Theme::name).toArray(String[]::new);
    }

    /** Selects by index, as the settings menu's choice row hands it back. */
    public static void selectIndex(int index) {
        if (index >= 0 && index < ALL.size()) {
            select(ALL.get(index));
        }
    }

    public static void select(Theme theme) {
        ConfigManager.get().theme = theme.name();
        ConfigManager.save();
    }
}
