package dev.diego.diegoaddons.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens {@code Font.getGlyphSource}, which is private, so
 * {@link dev.diego.diegoaddons.util.GlyphProbe} can ask a typeface whether it actually holds a
 * character rather than guessing from what a screenshot looks like.
 */
@Mixin(Font.class)
public interface FontGlyphAccessor {
    @Invoker("getGlyphSource")
    GlyphSource diego$glyphSource(FontDescription face);
}
