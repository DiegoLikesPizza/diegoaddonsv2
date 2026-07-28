package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.SlotLocks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a locked hotbar slot from being thrown away with the drop key while no screen is open.
 *
 * <p>The other blocks a lock does - the click, the number keys, the off-hand swap - live in the
 * container screen, because that is where those inputs are read. The drop key is not: with the
 * inventory closed it goes straight to the player, which is exactly the moment a lock is meant to
 * save you from. Cancelling here stops it before the packet is sent, so the server never hears
 * about it either.
 */
@Mixin(LocalPlayer.class)
public class PlayerDropMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void diego$keepLockedSlot(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (SlotLocks.isLocked(player.getInventory().getSelectedSlot())
                && SlotLocks.locksEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
