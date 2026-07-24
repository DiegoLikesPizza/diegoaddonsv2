package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.CustomF5;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backs the distance and clipping options of {@link CustomF5}.
 *
 * <p>Vanilla asks this method for a camera distance and it returns a shorter one when a block is in
 * the way, which is what stops the camera ending up inside a wall. Both options sit on that:
 *
 * <ul>
 *   <li><b>Distance</b> replaces the requested distance on the way in, so the block check still runs
 *       against the new value.</li>
 *   <li><b>Camera clip</b> throws the shortened result away on the way out and returns the requested
 *       distance instead, letting the camera pass through blocks.</li>
 * </ul>
 *
 * <p>Splitting them across entry and exit keeps the order unambiguous: by the time the exit runs, the
 * parameter already holds the custom distance, so clipping returns the right number.
 */
@Mixin(Camera.class)
public class CameraMixin {
    @ModifyVariable(method = "getMaxZoom(F)F", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float diego$distance(float requested) {
        CustomF5 mod = CustomF5.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.customDistance()) {
            return requested;
        }
        return mod.distance();
    }

    @Inject(method = "getMaxZoom(F)F", at = @At("RETURN"), cancellable = true)
    private void diego$clip(float requested, CallbackInfoReturnable<Float> cir) {
        CustomF5 mod = CustomF5.INSTANCE;
        if (mod != null && mod.isEnabled() && mod.cameraClip()) {
            cir.setReturnValue(requested);
        }
    }
}
