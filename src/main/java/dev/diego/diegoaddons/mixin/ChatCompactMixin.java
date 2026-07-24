package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.ChatCompactor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Backs the Compact Chat feature: rewrites the incoming message to carry a counter, and drops the
 * previous copy, before the chat ever stores it.
 *
 * <p>This modifies the argument rather than injecting after the fact, so the counted line is the one
 * that gets logged, wrapped and scrolled - there is never a moment where both copies exist.
 */
@Mixin(ChatComponent.class)
public class ChatCompactMixin {
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Component diego$compact(Component message) {
        return ChatCompactor.compact((ChatComponent) (Object) this, message);
    }
}
