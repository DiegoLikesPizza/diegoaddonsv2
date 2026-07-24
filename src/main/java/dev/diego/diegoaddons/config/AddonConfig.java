package dev.diego.diegoaddons.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain data object persisted to {@code config/diegoaddonsv2.json} (per Prism instance, since each
 * instance has its own config directory). Gson serializes/deserializes these fields directly.
 */
public class AddonConfig {
    /** Name of the active theme (see {@link dev.diego.diegoaddons.gui.Themes}). */
    public String theme = "Galaxy";

    /** Set once the first-run introduction screen has been shown in this instance. */
    public boolean introShown = false;

    /** Set once the "open all your SkyBlock menu pages" hint has been shown. */
    public boolean sbHintShown = false;

    /** Anti-alias the rounded corners with a soft edge pixel. */
    public boolean smoothCorners = true;

    /** Per-module settings, keyed by the module id. */
    public Map<String, ModuleConfig> modules = new LinkedHashMap<>();
}
