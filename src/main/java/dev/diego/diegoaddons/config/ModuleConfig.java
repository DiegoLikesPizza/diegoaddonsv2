package dev.diego.diegoaddons.config;

import java.util.HashMap;
import java.util.Map;

/**
 * One module's block in the <b>old</b> config file.
 *
 * <p>Nothing writes this any more - configlib stores each setting under its own id, read and written
 * through the setting object itself. It survives only as the shape {@link LegacyImport} parses a
 * pre-configlib file into, and goes when that does.
 *
 * @deprecated read-only, for {@link LegacyImport}.
 */
@Deprecated
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
