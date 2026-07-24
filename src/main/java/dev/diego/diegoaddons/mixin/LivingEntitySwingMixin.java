package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.AnimationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backs the swing-speed part of the {@link AnimationsModule}. Swing progress is
 * {@code swingTime / duration}, so shortening the duration speeds the animation up.
 *
 * <p>Only the local player is affected, so other players keep their normal swings.
 *
 * <p>A speed of 0 means "off". Rather than cancelling the swing - which would also suppress the
 * packet the server needs for hit registration - the duration is stretched so far that the progress
 * never leaves zero and the arm simply stays at rest. Attacks keep working exactly as before.
 */
@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin {
    /** Long enough that swing progress stays at ~0 for any realistic session. */
    private static final int FROZEN = 72000;

    @Inject(method = "getCurrentSwingDuration()I", at = @At("RETURN"), cancellable = true)
    private void diego$swingSpeed(CallbackInfoReturnable<Integer> cir) {
        AnimationsModule mod = AnimationsModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        double speed = mod.swingSpeed();
        if (speed <= 0.01) {
            cir.setReturnValue(FROZEN);
            return;
        }
        if (speed != 1.0) {
            cir.setReturnValue(Math.max(1, (int) Math.round(cir.getReturnValue() / speed)));
        }
    }
}
