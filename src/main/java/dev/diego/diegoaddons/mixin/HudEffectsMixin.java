package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.HideEffectsModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backs {@link HideEffectsModule}'s second half: the row of effect icons in the top-right corner of
 * the screen, which is drawn by the HUD and has nothing to do with the panel beside the inventory.
 *
 * <p>They are the same annoyance in two places - on a server that keeps a dozen buffs running, one
 * covers the inventory and the other covers whatever you were looking at - so one option turns off
 * both, each with the injection its own drawing needs.
 */
@Mixin(Gui.class)
public class HudEffectsMixin {
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void diego$skipHudEffects(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        HideEffectsModule mod = HideEffectsModule.INSTANCE;
        if (mod != null && mod.isEnabled() && mod.hideHudIcons()) {
            ci.cancel();
        }
    }
}
