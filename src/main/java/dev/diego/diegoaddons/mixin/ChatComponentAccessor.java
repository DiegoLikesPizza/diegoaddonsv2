package dev.diego.diegoaddons.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the chat's internals to the chat search and the Ctrl+click copy.
 *
 * <p>{@code allMessages} is the untrimmed backlog, so a search hit is a whole message rather than a
 * wrapped fragment. The rest is the geometry needed to work out which line sits under the cursor -
 * there is no vanilla helper for that any more, so the copy feature reproduces the layout the
 * renderer uses.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    /** Newest message first, as vanilla stores it. */
    @Accessor("allMessages")
    java.util.List<GuiMessage> diego$allMessages();

    /** The wrapped display lines, newest first; index 0 is the bottom-most line. */
    @Accessor("trimmedMessages")
    java.util.List<GuiMessage.Line> diego$trimmedMessages();

    /** How far the chat is scrolled back, in lines. */
    @Accessor("chatScrollbarPos")
    int diego$chatScrollbarPos();

    @Invoker("getScale")
    double diego$getScale();

    @Invoker("getWidth")
    int diego$getWidth();

    @Invoker("getLineHeight")
    int diego$getLineHeight();

    /** Rebuilds the wrapped display lines after a message was removed from the backlog. */
    @Invoker("refreshTrimmedMessages")
    void diego$refreshTrimmedMessages();
}
