package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.ChatModule;
import net.minecraft.client.Minecraft;

/**
 * Whether the chat is being peeked at: held open to be read, with the game still yours to play.
 *
 * <p>The two mixins behind the feature ask this, and it is deliberately the only state the feature
 * has. <b>Nothing is opened and nothing is toggled</b> - the key is polled where the answer is
 * needed, so releasing it takes effect on the next frame rather than on the next tick, and there is
 * no flag that can be left set by a screen change, a disconnect or a module being switched off
 * mid-hold.
 *
 * <p>What the mixins do with the answer is give the HUD's chat the treatment the chat <i>screen</i>
 * gets: every line fully visible instead of fading out after ten seconds, over the taller focused
 * area. Which is the whole feature - the chat you can already see is the last few seconds, and what
 * you actually want back is the part that has faded.
 */
public final class ChatPeek {
    private ChatPeek() {
    }

    /**
     * True while the peek key is held and the game is being played.
     *
     * <p>A screen being open rules it out, and both cases it rules out are the point: with the chat
     * screen up the chat is already open and the HUD does not draw it at all, and with any other
     * screen up you are not playing, which is the thing this exists to let you keep doing.
     */
    public static boolean active() {
        ChatModule module = ChatModule.INSTANCE;
        if (module == null || !module.isEnabled()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return false;
        }
        return module.peekKey().isDown();
    }

    /** True while a peek should also use the taller focused chat area. */
    public static boolean fullHeight() {
        ChatModule module = ChatModule.INSTANCE;
        return module != null && module.peekFullHeight() && active();
    }
}
