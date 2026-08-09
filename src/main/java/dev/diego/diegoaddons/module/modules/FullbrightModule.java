package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;

/**
 * Lights the whole world up, so caves and night read as clearly as daylight.
 *
 * <p>This used to drive the brightness (gamma) option to its maximum and hold it there, which is not
 * fullbright and could never have been: the option refuses anything above {@code 1.0}, and the
 * lightmap shader only uses that value to lift the finished colour slightly. The work happens in
 * {@link dev.diego.diegoaddons.mixin.LightmapMixin} now, and your brightness setting is left alone.
 */
public class FullbrightModule extends Module {
    public static FullbrightModule INSTANCE;

    /** Set when the module is switched off, so the lightmap gets one more refresh to go dark again. */
    private static boolean dirty;

    private final NumberSetting strength =
            new NumberSetting(this, "strength", "Strength", 100, 10, 100, 5);
    private final BooleanSetting removeDarkness =
            new BooleanSetting(this, "darkness", "Ignore the Darkness effect", true);
    private final BooleanSetting removeBossDim =
            new BooleanSetting(this, "bossDim", "Ignore boss-fight dimming", true);

    public FullbrightModule() {
        super("fullbright", Category.RENDER, "Fullbright", "Light the whole world up.");
        settings.add(strength);
        settings.add(removeDarkness);
        settings.add(removeBossDim);
        INSTANCE = this;
    }

    /**
     * How far towards full white the ambient light is pushed, 0-1.
     *
     * <p>Anything under 1 leaves some of the world's own shading in, which is the difference between
     * "I can see in the cave" and "the cave is a flat grey wall".
     */
    public float strength() {
        return (float) (strength.get() / 100.0);
    }

    public boolean removeDarkness() {
        return removeDarkness.get();
    }

    public boolean removeBossDim() {
        return removeBossDim.get();
    }

    @Override
    protected void onDisable() {
        dirty = true;
    }

    /** True once after the module was switched off; the lightmap re-uploads on that frame. */
    public static boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }
}
