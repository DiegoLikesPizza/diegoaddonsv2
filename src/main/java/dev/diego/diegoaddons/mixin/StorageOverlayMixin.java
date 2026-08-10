package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.gui.StorageOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the storage sheet in place of SkyBlock's storage menu.
 *
 * <p>The menu is left open and only its <i>drawing</i> is taken over - which is the point of doing
 * it this way rather than opening a screen of our own. The container stays exactly as the server
 * believes it to be, so a click the overlay sends is the same click the menu would have sent, and
 * closing the sheet closes the menu the ordinary way.
 *
 * <p>Input is not here: Fabric's screen events already offer a veto on clicks, keys and typed
 * characters, and a registered listener is easier to reason about than four more injections. Only
 * drawing needs a mixin, because there is no event that can cancel it.
 */
@Mixin(AbstractContainerScreen.class)
public class StorageOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void diego$storageOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                                      CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (StorageOverlay.render(screen, g, mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
