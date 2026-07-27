package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Brightens the world to maximum by driving the brightness (gamma) setting to the top and holding it
 * there, so caves and night are fully lit. The previous brightness is remembered and restored when
 * the module is switched off, so it does not quietly change a setting you meant to keep.
 */
public class FullbrightModule extends Module {
    private Double saved;

    public FullbrightModule() {
        super("fullbright", Category.RENDER, "Fullbright", "Light the whole world up.");
    }

    @Override
    protected void onEnable() {
        // May be toggled on while applying the saved config, before options exist - the tick below
        // captures the original value the first time it runs instead.
        var options = Minecraft.getInstance().options;
        if (options != null && saved == null) {
            saved = options.gamma().get();
        }
    }

    @Override
    protected void onDisable() {
        var options = Minecraft.getInstance().options;
        if (options != null && saved != null) {
            options.gamma().set(saved);
        }
        saved = null;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.options == null) {
            return;
        }
        if (saved == null) {
            saved = mc.options.gamma().get();
        }
        // Pin brightness to the maximum the option allows. Anything above 1.0 is rejected and logged
        // as an "Illegal option value" every tick, so 1.0 is as bright as this route goes; true
        // fullbright would need a light-texture mixin.
        if (mc.options.gamma().get() < 1.0) {
            mc.options.gamma().set(1.0);
        }
    }
}
