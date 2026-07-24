package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.HideEffectsModule;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backs the {@link HideEffectsModule}. Answering {@code canSeeEffects} with false is what vanilla
 * itself checks both to draw the panel and to decide whether the menu needs shifting aside, so one
 * override removes the panel and re-centres the inventory together.
 */
@Mixin(EffectsInInventory.class)
public class EffectsInInventoryMixin {
    @Inject(method = "canSeeEffects()Z", at = @At("RETURN"), cancellable = true)
    private void diego$hideEffects(CallbackInfoReturnable<Boolean> cir) {
        HideEffectsModule mod = HideEffectsModule.INSTANCE;
        if (mod != null && mod.isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
