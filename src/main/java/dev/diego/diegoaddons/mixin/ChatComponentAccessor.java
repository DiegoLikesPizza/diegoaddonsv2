package dev.diego.diegoaddons.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the chat's message backlog so the chat search can read it. This is the untrimmed message
 * list, not the wrapped display lines, so a search result is a whole message rather than a fragment.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    /** Newest message first, as vanilla stores it. */
    @Accessor("allMessages")
    java.util.List<GuiMessage> diego$allMessages();
}
