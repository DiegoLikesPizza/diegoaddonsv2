package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.mixin.FontGlyphAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.network.chat.FontDescription;

/**
 * Answers, for one typeface and one character, the question a screenshot cannot: <b>is this box the
 * missing-glyph box?</b>
 *
 * <p>{@code FontSet} walks its providers in order and takes the first one holding the character; when
 * none does, it bakes {@link SpecialGlyphs#MISSING} - and a baked glyph keeps the {@code GlyphInfo}
 * it was made from, so the enum constant coming back out is the signal, not a guess about pixels.
 *
 * <p>This exists because a box in the mod's own face and a box in the vanilla face mean opposite
 * things. The first is the mod's fault - a fallback missing from
 * {@code assets/diegoaddonsv2/font/*.json}. The second is not fixable from here at all: the game
 * itself does not have that character, and only a resource pack can add it.
 */
public final class GlyphProbe {
    private GlyphProbe() {
    }

    /** Whether {@code face} draws {@code codePoint} as the missing-glyph box. */
    public static boolean missing(Font font, FontDescription face, int codePoint) {
        GlyphSource source = ((FontGlyphAccessor) font).diego$glyphSource(face);
        BakedGlyph glyph = source.getGlyph(codePoint);
        return glyph.info() == SpecialGlyphs.MISSING;
    }

    /** Whether the game's own default typeface draws {@code codePoint} as the missing-glyph box. */
    public static boolean missingInVanilla(Font font, int codePoint) {
        return missing(font, FontDescription.DEFAULT, codePoint);
    }
}
