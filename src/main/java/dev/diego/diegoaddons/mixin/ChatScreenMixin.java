package dev.diego.diegoaddons.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.util.ChatCopy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ctrl+click on a chat line copies that message. Injected at the head so the copy happens instead of
 * the click's normal effect - otherwise a Ctrl+click on a message carrying a click event would also
 * follow the link or run its command.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void diego$ctrlClickCopy(MouseButtonEvent event, boolean doubleClick,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return;
        }
        boolean ctrl = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (ctrl && ChatCopy.tryCopy(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }
}
