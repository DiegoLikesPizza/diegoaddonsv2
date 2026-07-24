package dev.diego.diegoaddons.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites Hypixel SkyBlock dungeon-star item names from the current master-star style back to the
 * old one.
 *
 * <p>Current style: five gold stars {@code §6✪✪✪✪✪} followed by a single red master glyph
 * {@code §c➊}–{@code §c➎} (U+278A–U+278E) for master levels 1–5. Old style: no separate glyph -
 * the five stars themselves turn red from the left, one per master level, so a 10-star item shows
 * five red stars and a 6-star item shows one red + four gold.
 *
 * <p>Works on the fully styled {@link Component}: it flattens to a legacy {@code §}-string, swaps the
 * star segment, and rebuilds. Non-star names (no master glyph) are returned untouched.
 */
public final class OldMasterStars {
    private static final char SECTION = '§';   // §
    private static final char STAR = '✪';      // ✪
    private static final char MASTER_1 = '➊';  // ➊
    private static final char MASTER_5 = '➎';  // ➎

    /** Five (optionally coloured) stars followed by an (optionally coloured) master glyph. */
    private static final Pattern STARS = Pattern.compile(
            "(?:§.)*✪(?:(?:§.)*✪){4}(?:§.)*([➊-➎])");

    private OldMasterStars() {
    }

    /** The old-style name, or {@code name} unchanged when there is nothing to convert. */
    public static Component transform(Component name) {
        if (name == null || !hasMasterGlyph(name.getString())) {
            return name;
        }
        String legacy = toLegacy(name);
        Matcher m = STARS.matcher(legacy);
        if (!m.find()) {
            return name;
        }
        StringBuilder out = new StringBuilder();
        m.reset();
        while (m.find()) {
            int master = m.group(1).charAt(0) - MASTER_1 + 1; // 1..5
            StringBuilder rep = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                rep.append(SECTION).append(i < master ? 'c' : '6').append(STAR);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(out);
        return fromLegacy(out.toString());
    }

    private static boolean hasMasterGlyph(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= MASTER_1 && c <= MASTER_5) {
                return true;
            }
        }
        return false;
    }

    /** Flatten a component to a legacy {@code §}-string, one absolute style prefix per text run. */
    private static String toLegacy(Component c) {
        StringBuilder sb = new StringBuilder();
        c.visit((style, text) -> {
            sb.append(codes(style)).append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
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
        if (st.isStrikethrough()) {
            sb.append(SECTION).append('m');
        }
        if (st.isUnderlined()) {
            sb.append(SECTION).append('n');
        }
        if (st.isItalic()) {
            sb.append(SECTION).append('o');
        }
        if (st.isObfuscated()) {
            sb.append(SECTION).append('k');
        }
        return sb.toString();
    }

    private static ChatFormatting colorFormat(int rgb) {
        for (ChatFormatting f : ChatFormatting.values()) {
            if (f.isColor()) {
                Integer c = f.getColor();
                if (c != null && c == rgb) {
                    return f;
                }
            }
        }
        return null;
    }

    private static Component fromLegacy(String s) {
        MutableComponent root = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == SECTION && i + 1 < s.length()) {
                if (buf.length() > 0) {
                    root.append(Component.literal(buf.toString()).setStyle(style));
                    buf.setLength(0);
                }
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(s.charAt(++i)));
                if (f == ChatFormatting.RESET) {
                    style = Style.EMPTY;
                } else if (f != null) {
                    style = style.applyLegacyFormat(f);
                }
            } else {
                buf.append(ch);
            }
        }
        if (buf.length() > 0) {
            root.append(Component.literal(buf.toString()).setStyle(style));
        }
        return root;
    }
}
