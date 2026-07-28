package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.ChatModule;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Backs {@link ChatModule}'s unlimited history: raises vanilla's 100-entry chat cap.
 *
 * <p>Three separate places trim to 100 and all of them matter, or the history is still cut short:
 * the wrapped display lines, the messages behind them, and the sent-message history you scroll with
 * the up arrow. Each is rewritten to {@link ChatModule#limit()}.
 *
 * <p>The chat box constructor also mentions 100, but only as the initial capacity of a growable
 * deque, so it is deliberately left alone.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @ModifyConstant(
            method = {
                    "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
                    "addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
                    "addRecentChat(Ljava/lang/String;)V"
            },
            constant = @Constant(intValue = 100))
    private int diego$chatHistoryLimit(int vanilla) {
        ChatModule mod = ChatModule.INSTANCE;
        return mod != null && mod.unlimitedHistory() ? ChatModule.limit() : vanilla;
    }
}
