package dev.diego.diegoaddons.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.diego.diegoaddons.module.modules.ChatModule;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Backs {@link ChatModule}'s unlimited history: raises vanilla's 100-entry chat cap.
 *
 * <p>Three separate places trim to 100 and all of them matter, or the history is still cut short:
 * the wrapped display lines, the messages behind them, and the sent-message history you scroll with
 * the up arrow. Each is rewritten to {@link ChatModule#limit()}.
 *
 * <p>The chat box constructor also mentions 100, but only as the initial capacity of a growable
 * deque, so it is deliberately left alone.
 *
 * <p><b>Why this is {@code @ModifyExpressionValue} and not {@code @ModifyConstant}.</b> It was the
 * latter, and it crashed somebody else's game. {@code @ModifyConstant} is a redirect, and a redirect
 * <i>owns</i> the instruction it lands on: the first mod to claim the constant wins and every other
 * mod's is skipped with a "@ModifyConstant conflict" warning. Skysoft modifies the same 100 for the
 * same reason, its injection then reported 0 of 1 succeeded, and Mixin turns a failed required
 * injection into a hard {@code InjectionError} - so a mod that was merely second to the constant
 * took the whole game down with it.
 *
 * <p>{@code @ModifyExpressionValue} does not replace the instruction; it takes the value <i>after</i>
 * it is produced and hands back a new one. Nothing is claimed, so any number of mods can stack on
 * the same constant. The knock-on is worth having too: when this feature is switched off we return
 * whatever we were given, which is now the other mod's number rather than vanilla's - so the two
 * features compose instead of one silently undoing the other.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @ModifyExpressionValue(
            method = {
                    "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
                    "addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
                    "addRecentChat(Ljava/lang/String;)V"
            },
            at = @At(value = "CONSTANT", args = "intValue=100"))
    private int diego$chatHistoryLimit(int original) {
        ChatModule mod = ChatModule.INSTANCE;
        return mod != null && mod.unlimitedHistory() ? ChatModule.limit() : original;
    }
}
