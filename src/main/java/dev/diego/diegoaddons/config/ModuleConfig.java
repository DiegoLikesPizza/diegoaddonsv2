package dev.diego.diegoaddons.config;

import java.util.HashMap;
import java.util.Map;

/** Persisted per-module state: enabled flag, HUD position, and arbitrary boolean settings. */
public class ModuleConfig {
    public boolean enabled = false;

    /** HUD position in GUI pixels; {@code -1} means "unset" (use the default stacked position). */
    public int hudX = -1;
    public int hudY = -1;

    /** HUD chip scale multiplier (1.0 = default), adjusted with the scroll wheel in the HUD editor. */
    public float hudScale = 1.0f;

    /** Feature-specific boolean settings, keyed by the setting key. */
    public Map<String, Boolean> options = new HashMap<>();

    /**
     * Feature-specific key bindings, keyed by the setting key, as GLFW key codes. Bound in this
     * mod's own GUI rather than Minecraft's controls screen, so every setting lives in one place.
     */
    public Map<String, Integer> keys = new HashMap<>();

    /** Feature-specific numeric settings, keyed by the setting key. */
    public Map<String, Double> numbers = new HashMap<>();

    /**
     * Feature-specific text settings, keyed by the setting key - for the things a toggle or a number
     * cannot say, such as the order the inventory HUD lays its sections out in.
     */
    public Map<String, String> texts = new HashMap<>();

    public ModuleConfig() {
    }

    public ModuleConfig(boolean enabled) {
        this.enabled = enabled;
    }
}
