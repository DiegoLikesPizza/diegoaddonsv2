package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.CustomScoreboardModule;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backs {@link CustomScoreboardModule}: cancels the vanilla sidebar draw so the addon's own themed
 * panel can take its place. {@code extractScoreboardSidebar} is the method the HUD calls to draw the
 * sidebar, so cancelling it at the head hides the vanilla one entirely.
 */
@Mixin(Gui.class)
public class ScoreboardSidebarMixin {
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void diego$hideVanillaSidebar(CallbackInfo ci) {
        CustomScoreboardModule mod = CustomScoreboardModule.INSTANCE;
        if (mod != null && mod.isEnabled()) {
            ci.cancel();
        }
    }
}
