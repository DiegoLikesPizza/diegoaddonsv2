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
 *   <li><b>Camera clip</b> answers the question itself, before any of the block checks run.</li>
 * </ul>
 *
 * <p>Clipping cannot be done on the way out. Vanilla walks eight rays and narrows the distance by
 * <b>writing the shorter value back into its own parameter</b>, then returns that parameter - so at
 * {@code RETURN} the parameter no longer holds what was asked for, it holds what the walls allowed.
 * Returning it there sets the result to the value it already had, which is why the option did
 * nothing at all. Answering at {@code HEAD} skips the walk entirely, and reads the wanted distance
 * from the module rather than from the parameter, so it does not depend on whether the injection
 * above it has run yet.
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

    @Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true)
    private void diego$clip(float requested, CallbackInfoReturnable<Float> cir) {
        CustomF5 mod = CustomF5.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.cameraClip()) {
            return;
        }
        cir.setReturnValue(mod.customDistance() ? mod.distance() : requested);
    }
}
