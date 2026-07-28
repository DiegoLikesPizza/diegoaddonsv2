package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.BorderlessFullscreenModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the game pausing itself when the window loses focus.
 *
 * <p>Vanilla waits half a second after focus goes and then opens the pause menu - which in single
 * player also halts the integrated server, and that is the hitch you feel a moment after pressing
 * the Windows key. In a borderless window losing focus is not leaving the game, it is looking at
 * something else on the same screen, so nothing should happen.
 *
 * <p>Only the unfocused path is cancelled. Escape is a different route entirely and still opens the
 * menu, as it should.
 *
 * <p>The alternative was setting {@code options.pauseOnLostFocus} to false, which works and is one
 * line - but that is the player's own saved setting, and a mod that quietly rewrites your
 * options.txt has changed something you did not ask it to and will not change back if it crashes.
 */
@Mixin(Minecraft.class)
public class PauseIfInactiveMixin {
    @Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
    private void diego$stayRunning(CallbackInfo ci) {
        BorderlessFullscreenModule mod = BorderlessFullscreenModule.INSTANCE;
        if (mod != null && mod.isEnabled() && mod.keepRunning()) {
            ci.cancel();
        }
    }
}
