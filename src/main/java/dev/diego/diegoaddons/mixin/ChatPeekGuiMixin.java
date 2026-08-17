package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.ChatPeek;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The whole of the chat peek: while the key is held, the HUD draws its chat the way the chat
 * <b>screen</b> draws it.
 *
 * <p>26.1 hands the chat a {@code DisplayMode}, and that one argument is the entire difference
 * between the two. {@code BACKGROUND} - what the HUD passes - fades each line out on a timer, which
 * is why the chat you can see while playing is only the last ten seconds. {@code FOREGROUND} - what
 * {@code ChatScreen} passes - uses {@code AlphaCalculator.FULLY_VISIBLE}, so every line in the page
 * is drawn whatever its age. Swapping the argument is therefore not an approximation of "open the
 * chat"; it is the same code path the open chat runs, minus the screen.
 *
 * <p><b>Minus the screen is the point.</b> Nothing is opened, no input is captured, so you keep
 * walking and looking around and there is nothing to type into - which is what was asked for.
 *
 * <p>{@code @ModifyArg} rather than an inject: the value being changed is one argument of one call,
 * and taking only that leaves the surrounding method - the early return while the chat screen is
 * genuinely open, the mouse position, the stratum - vanilla's.
 */
@Mixin(Gui.class)
public class ChatPeekGuiMixin {
    @ModifyArg(
            method = "extractChat",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState("
                            + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                            + "Lnet/minecraft/client/gui/Font;III"
                            + "Lnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"),
            index = 5)
    private ChatComponent.DisplayMode diego$peekChat(ChatComponent.DisplayMode original) {
        return ChatPeek.active() ? ChatComponent.DisplayMode.FOREGROUND : original;
    }
}
