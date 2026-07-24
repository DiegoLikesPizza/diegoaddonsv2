package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;

/**
 * Reshapes the item held in first person: its size, where it sits, and how fast the swing plays.
 *
 * <p>Only the local player's own view is affected - other players still see your normal animations,
 * since all of this is applied in the first-person hand renderer.
 */
public class AnimationsModule extends Module {
    public static AnimationsModule INSTANCE;

    private final NumberSetting scale =
            new NumberSetting(this, "scale", "Size", 1.0, 0.1, 3.0, 0.05);
    private final NumberSetting x =
            new NumberSetting(this, "x", "Position X", 0.0, -1.0, 1.0, 0.01);
    private final NumberSetting y =
            new NumberSetting(this, "y", "Position Y", 0.0, -1.0, 1.0, 0.01);
    private final NumberSetting z =
            new NumberSetting(this, "z", "Position Z", 0.0, -1.0, 1.0, 0.01);
    /** 1.0 is vanilla speed, 0.0 stops the swing entirely, 3.0 is three times as fast. */
    private final NumberSetting swingSpeed =
            new NumberSetting(this, "swingSpeed", "Swing speed", 1.0, 0.0, 3.0, 0.05);
    /** Leaves the bare arm alone, so only held items are moved and resized. */
    private final BooleanSetting excludeHand =
            new BooleanSetting(this, "excludeHand", "Exclude empty hand", false);

    public AnimationsModule() {
        super("animations", Category.RENDER, "Animations",
                "Change the size, position and swing speed of the item in your hand.");
        settings.add(scale);
        settings.add(x);
        settings.add(y);
        settings.add(z);
        settings.add(swingSpeed);
        settings.add(excludeHand);
        INSTANCE = this;
    }

    public float scale() {
        return (float) scale.get();
    }

    public float x() {
        return (float) x.get();
    }

    public float y() {
        return (float) y.get();
    }

    public float z() {
        return (float) z.get();
    }

    public double swingSpeed() {
        return swingSpeed.get();
    }

    public boolean excludeHand() {
        return excludeHand.get();
    }
}
