package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.ChatPeek;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The second half of the chat peek: while the key is held, the chat is as tall as it is when it is
 * open.
 *
 * <p>Vanilla keeps two heights and picks between them on whether the chat screen is up - that is
 * the whole of {@code getHeight()}, and it is also what {@code getLinesPerPage()} is built on, so
 * this one number decides how much of the backlog is on screen. Without it the peek would un-fade
 * the lines and then show the same handful of them, which is the smaller half of the point.
 *
 * <p>Not driven by making {@code isChatFocused()} lie, which was the obvious way and is a trap:
 * {@code Gui.extractChat} returns early when the chat is focused, because the chat screen is drawing
 * it instead - so a peek that claimed to be focused would stop the HUD drawing the chat and put
 * nothing in its place.
 */
@Mixin(ChatComponent.class)
public class ChatPeekHeightMixin {
    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void diego$peekHeight(CallbackInfoReturnable<Integer> cir) {
        if (!ChatPeek.fullHeight()) {
            return;
        }
        cir.setReturnValue(ChatComponent.getHeight(
                Minecraft.getInstance().options.chatHeightFocused().get()));
    }
}
