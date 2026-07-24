package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.mixin.ChatComponentAccessor;
import dev.diego.diegoaddons.module.modules.ChatSearchModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Ctrl+click a line in the chat to copy that whole message to the clipboard.
 *
 * <p>There is no vanilla "which message is under the cursor" helper any more, so the lookup below
 * reproduces the geometry the chat renderer uses: lines are laid out upwards from
 * {@code (screenHeight - 40) / scale}, each {@code lineHeight} tall, with index 0 at the bottom and
 * the scrollbar position added on. Keeping that in one place means there is a single spot to fix if
 * the layout changes.
 */
public final class ChatCopy {
    /** Vanilla's gap between the bottom of the screen and the chat, before scaling. */
    private static final int BOTTOM_MARGIN = 40;

    private ChatCopy() {
    }

    /**
     * Handles a click in the chat screen.
     *
     * @return true when a message was copied, so the click should not also do anything else
     */
    public static boolean tryCopy(double mouseX, double mouseY) {
        ChatSearchModule mod = ChatSearchModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.ctrlClickCopy()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return false;
        }
        GuiMessage message = messageAt(mc.gui.getChat(), mouseX, mouseY);
        if (message == null) {
            return false;
        }
        String text = message.content().getString().replaceAll("§.", "");
        if (text.isBlank()) {
            return false;
        }
        mc.keyboardHandler.setClipboard(text);
        Toasts.show("Copied to clipboard", text);
        return true;
    }

    /** The message under the given screen position, or null if the cursor is not over chat text. */
    private static GuiMessage messageAt(ChatComponent chat, double mouseX, double mouseY) {
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = acc.diego$trimmedMessages();
        if (lines.isEmpty()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        double scale = acc.diego$getScale();
        if (scale <= 0) {
            return null;
        }
        int lineHeight = acc.diego$getLineHeight();
        if (lineHeight <= 0) {
            return null;
        }

        // Into chat space: the renderer scales by `scale` and shifts x by 4.
        double chatX = mouseX / scale - 4.0;
        double chatY = mouseY / scale;
        int baseY = Mth.floor((mc.getWindow().getGuiScaledHeight() - BOTTOM_MARGIN) / scale);

        int width = Mth.ceil(acc.diego$getWidth() / scale);
        if (chatX < 0 || chatX > width) {
            return null;
        }

        // Index 0 is the bottom-most visible line and they stack upwards from baseY.
        int fromBottom = Mth.floor((baseY - chatY) / lineHeight);
        if (fromBottom < 0) {
            return null;
        }
        int index = fromBottom + acc.diego$chatScrollbarPos();
        if (index >= lines.size()) {
            return null;
        }
        return lines.get(index).parent();
    }
}
