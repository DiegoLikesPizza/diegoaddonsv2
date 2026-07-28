package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

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

    public FullbrightModule() {
        super("fullbright", Category.RENDER, "Fullbright", "Light the whole world up.");
        INSTANCE = this;
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
