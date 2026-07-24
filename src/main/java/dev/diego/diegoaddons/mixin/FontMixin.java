package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.GlobalTextReplacer;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Applies the word replacements to every string the game draws.
 *
 * <p>These two methods are the choke point: all three {@code drawInBatch} overloads - the ones taking
 * a String, a Component and a FormattedCharSequence - end up calling one of them, so rewriting here
 * covers chat, the tab list, name tags, item names and lore, and the chat box as you type, without a
 * separate hook per feature.
 *
 * <p>Rewriting at draw time is what makes this visual only: the text being sent, stored or logged is
 * never touched.
 */
@Mixin(Font.class)
public class FontMixin {
    @ModifyVariable(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private String diego$replacePlain(String text) {
        return GlobalTextReplacer.forString(text);
    }

    @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private FormattedCharSequence diego$replaceStyled(FormattedCharSequence text) {
        return GlobalTextReplacer.forSequence(text);
    }
}
