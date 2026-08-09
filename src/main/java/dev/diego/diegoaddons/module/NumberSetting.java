package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.config.ConfigManager;

/**
 * A numeric setting shown as a slider in the ClickGUI, persisted in the owner module's
 * {@code numbers} map.
 *
 * <p>Values are snapped to {@link #step} so dragging produces round numbers rather than the raw
 * pixel position, and clamped to {@code [min, max]} on the way in and out - a config edited by hand
 * can never push a value out of range.
 */
public class NumberSetting extends Setting {
    public final double min;
    public final double max;
    public final double def;
    public final double step;
    /** Decimal places to show; derived from the step so 0.05 reads "0.05" and 1 reads "1". */
    public final int decimals;

    private double value;

    public NumberSetting(Module owner, String key, String name, double def, double min, double max, double step) {
        super(owner, key, name);
        this.def = def;
        this.value = def;
        this.min = min;
        this.max = max;
        this.step = step > 0 ? step : 0.01;
        this.decimals = this.step >= 1 ? 0 : (this.step >= 0.1 ? 1 : 2);
    }

    /** Clamped on the way out as well as in, so a config edited by hand cannot push it out of range. */
    public double get() {
        return clamp(value);
    }

    public void set(double value) {
        double next = snap(clamp(value));
        if (next == this.value) {
            return;
        }
        this.value = next;
        ConfigManager.save();
    }

    public void reset() {
        set(def);
    }

    /** Where the current value sits in the range, 0..1 - what the slider draws. */
    public double fraction() {
        return max > min ? (get() - min) / (max - min) : 0;
    }

    /** Set from a slider position, 0..1. */
    public void setFraction(double f) {
        set(min + (max - min) * Math.max(0, Math.min(1, f)));
    }

    public String display() {
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", get());
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    private double snap(double v) {
        return Math.round(v / step) * step;
    }
}
