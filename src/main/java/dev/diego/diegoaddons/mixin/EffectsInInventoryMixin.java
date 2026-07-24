package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.HideEffectsModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backs the {@link HideEffectsModule}: stops the potion-effect panel beside the inventory from being
 * drawn.
 *
 * <p>Two injections are needed, because in vanilla the two are not connected.
 * {@code extractRenderState} is what actually draws, and it does <b>not</b> consult
 * {@code canSeeEffects} - it repeats the same "is there room to the right of the menu" test inline -
 * so cancelling the draw is the part that hides the panel. {@code canSeeEffects} is what the
 * inventory screen asks when placing itself, so overriding that as well lets the menu sit centred
 * rather than shifted aside for a panel that is no longer there.
 */
@Mixin(EffectsInInventory.class)
public class EffectsInInventoryMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
            at = @At("HEAD"),
            cancellable = true)
    private void diego$skipEffectPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
        if (enabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "canSeeEffects()Z", at = @At("RETURN"), cancellable = true)
    private void diego$reportNoRoom(CallbackInfoReturnable<Boolean> cir) {
        if (enabled()) {
            cir.setReturnValue(false);
        }
    }

    private static boolean enabled() {
        HideEffectsModule mod = HideEffectsModule.INSTANCE;
        return mod != null && mod.isEnabled();
    }
}
