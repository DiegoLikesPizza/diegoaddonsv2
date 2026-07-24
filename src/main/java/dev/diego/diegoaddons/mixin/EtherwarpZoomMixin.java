package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.EtherwarpModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Narrows the field of view while aiming an etherwarp, so a distant landing block is easier to pick.
 *
 * <p>Dividing the finished value keeps everything the game already folded into it - the player's own
 * setting, speed effects - and only scales the result.
 */
@Mixin(Camera.class)
public class EtherwarpZoomMixin {
    @Inject(method = "getFov()F", at = @At("RETURN"), cancellable = true)
    private void diego$zoom(CallbackInfoReturnable<Float> cir) {
        EtherwarpModule mod = EtherwarpModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        double factor = mod.zoomFactor();
        if (factor > 1.0) {
            cir.setReturnValue((float) (cir.getReturnValue() / factor));
        }
    }
}
