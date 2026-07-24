package dev.diego.diegoaddons.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/**
 * Converts a {@link Component} to a legacy {@code §}-coded string and back.
 *
 * <p>Rewriting text inside a component tree is awkward: a phrase can be split across several runs
 * with different styles, so a plain search-and-replace on any one run misses it. Flattening to
 * legacy codes puts the whole message in a single string where a match is found reliably, and the
 * codes carry enough formatting to rebuild it afterwards.
 *
 * <p>The cost is that anything legacy codes cannot express - click and hover events, fonts - is lost,
 * so only rewrite a component when the rewrite is the point.
 */
public final class LegacyText {
    private static final char SECTION = '§';

    private LegacyText() {
    }

    /** Flatten to a legacy string, one absolute style prefix per text run. */
    public static String toLegacy(Component c) {
        StringBuilder sb = new StringBuilder();
        c.visit((style, text) -> {
            sb.append(codes(style)).append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    /** Rebuild a component from a legacy string. */
    public static MutableComponent fromLegacy(String s) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == SECTION && i + 1 < s.length()) {
                ChatFormatting fmt = ChatFormatting.getByCode(s.charAt(i + 1));
                if (fmt != null) {
                    if (!run.isEmpty()) {
                        out.append(Component.literal(run.toString()).setStyle(style));
                        run.setLength(0);
                    }
                    style = style.applyLegacyFormat(fmt);
                    i++;
                    continue;
                }
            }
            run.append(c);
        }
        if (!run.isEmpty()) {
            out.append(Component.literal(run.toString()).setStyle(style));
        }
        return out;
    }

    /** Strip every legacy code, leaving readable text. */
    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static String codes(Style st) {
        StringBuilder sb = new StringBuilder();
        TextColor col = st.getColor();
        ChatFormatting cf = col != null ? colorFormat(col.getValue()) : null;
        // A colour code (or reset) clears prior formatting in legacy, so emit it first.
        sb.append(SECTION).append(cf != null ? cf.getChar() : 'r');
        if (st.isBold()) {
            sb.append(SECTION).append('l');
        }
        if (st.isItalic()) {
            sb.append(SECTION).append('o');
        }
        if (st.isUnderlined()) {
            sb.append(SECTION).append('n');
        }
        if (st.isStrikethrough()) {
            sb.append(SECTION).append('m');
        }
        if (st.isObfuscated()) {
            sb.append(SECTION).append('k');
        }
        return sb.toString();
    }

    /** The legacy colour whose RGB matches exactly, or null for a custom colour. */
    private static ChatFormatting colorFormat(int rgb) {
        for (ChatFormatting f : ChatFormatting.values()) {
            if (f.isColor() && f.getColor() != null && f.getColor() == rgb) {
                return f;
            }
        }
        return null;
    }
}
