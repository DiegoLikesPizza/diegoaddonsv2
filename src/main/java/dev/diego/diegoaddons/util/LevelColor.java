package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.LevelColorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws your own SkyBlock level badge in a colour you picked, rather than the one your level happens
 * to sit in.
 *
 * <p>Hypixel gives the badge a new colour every 40 levels up to 480, and the colour you are wearing
 * is simply the last one you passed. This lets you go back to an earlier one - and only an earlier
 * one: a tier above your level is not yours to wear, so a pick that far ahead falls back to the
 * highest you have actually reached.
 *
 * <p><b>Your level is read out of the badge being recoloured</b>, not from a scan of any menu. The
 * badge carries the number, so the same string that says "recolour me" also says what you are
 * allowed to recolour it to - which means this needs no state, cannot go stale, and works from the
 * first frame after a login.
 *
 * <p>Applied at draw time through the font, like {@link GlobalTextReplacer}, so one hook covers the
 * tab list, the name plate above your head and every chat line that carries your badge. Purely
 * visual and purely local: nothing sent, stored or logged changes, and nobody else sees it.
 */
public final class LevelColor {
    private static final char SECTION = '§';

    /** How far past the badge your name may sit for the badge to be counted as yours. */
    private static final int NAME_WINDOW = 32;

    /** The badge itself: a small number in square brackets. */
    private static final Pattern BADGE = Pattern.compile("\\[(\\d{1,4})]");

    /** What may stand between the badge and your name: spaces and bracketed tags, nothing else. */
    private static final Pattern GAP = Pattern.compile("\\s*(?:\\[[^\\]]{1,16}]\\s*)*");

    /**
     * The colour ramp, one step per 40 levels, ending at 480.
     *
     * <p>The whole "unlocked" rule is this table: index n is worn from level {@code n * STEP}, so the
     * tier you are in is your level divided by the step, and everything at or below it is yours. If
     * Hypixel ever reorders these, this array is the only thing to change.
     */
    private static final char[] CODES = {
            '7',   // 0   gray
            'f',   // 40  white
            'e',   // 80  yellow
            'a',   // 120 green
            '2',   // 160 dark green
            'b',   // 200 aqua
            '3',   // 240 dark aqua
            '9',   // 280 blue
            'd',   // 320 light purple
            '5',   // 360 dark purple
            '6',   // 400 gold
            'c',   // 440 red
            '4',   // 480 dark red
    };

    /** Levels between one colour and the next. */
    public static final int STEP = 40;

    /** Shown on the module card: the picker's options, in the order they are unlocked. */
    public static final String[] NAMES = {
            "Gray (0)", "White (40)", "Yellow (80)", "Green (120)", "Dark Green (160)",
            "Aqua (200)", "Dark Aqua (240)", "Blue (280)", "Light Purple (320)",
            "Dark Purple (360)", "Gold (400)", "Red (440)", "Dark Red (480)",
    };

    private LevelColor() {
    }

    /** The number of colours, so the module can size its picker from the table rather than a literal. */
    public static int tiers() {
        return CODES.length;
    }

    /** The highest tier a player of this level is wearing, and so the highest one they may pick. */
    public static int tierOf(int level) {
        int tier = level / STEP;
        return Math.max(0, Math.min(CODES.length - 1, tier));
    }

    private static boolean active() {
        LevelColorModule mod = LevelColorModule.INSTANCE;
        return mod != null && mod.isEnabled() && mod.picked() >= 0;
    }

    /** Your own name, or null when there is no player to have one. */
    private static String ownName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getGameProfile().name();
    }

    /** Rewrites a plain string, or returns it unchanged. */
    public static String forString(String text) {
        if (text == null || text.isEmpty() || !active()) {
            return text;
        }
        return rewrite(text);
    }

    /**
     * Rewrites a styled text run. Flattened to legacy codes first, exactly like the word replacer:
     * the badge is normally split across runs (the brackets one colour, the number another), so a
     * per-run rewrite would see neither half whole.
     */
    public static FormattedCharSequence forSequence(FormattedCharSequence text) {
        if (text == null || !active()) {
            return text;
        }
        StringBuilder legacy = new StringBuilder();
        Style[] last = {null};
        text.accept((index, style, codePoint) -> {
            if (!style.equals(last[0])) {
                legacy.append(LegacyText.codesOf(style));
                last[0] = style;
            }
            legacy.appendCodePoint(codePoint);
            return true;
        });

        String before = legacy.toString();
        String after = rewrite(before);
        if (after.equals(before)) {
            return text;
        }
        return LegacyText.fromLegacy(after).getVisualOrderText();
    }

    /**
     * The rewrite itself, over a legacy-coded string.
     *
     * <p>Ordered for cost, since this runs for every string the game draws: the two cheap
     * {@code indexOf} calls reject everything that is not a line with your name and a bracket in it,
     * and only what survives both is walked, matched and rebuilt.
     */
    private static String rewrite(String legacy) {
        String self = ownName();
        if (self == null || legacy.indexOf('[') < 0 || !legacy.contains(self)) {
            return legacy;
        }

        // The plain text, plus where each of its characters came from, so a match found in the
        // readable text can be spliced back into the coded one.
        StringBuilder plain = new StringBuilder(legacy.length());
        int[] source = new int[legacy.length()];
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == SECTION && i + 1 < legacy.length() && isCode(legacy.charAt(i + 1))) {
                i++;
                continue;
            }
            source[plain.length()] = i;
            plain.append(c);
        }

        String flat = plain.toString();
        Matcher m = BADGE.matcher(flat);
        while (m.find()) {
            if (!mine(flat, m.end(), self)) {
                continue;
            }
            int level = Integer.parseInt(m.group(1));
            Character code = codeFor(level);
            if (code == null) {
                return legacy;
            }
            int from = source[m.start()];
            int to = source[m.end() - 1] + 1;
            // The codes in effect where the badge ended are put back after it, so the colour stops
            // at the closing bracket rather than running on into your rank and your name.
            String resume = activeAt(legacy, to);
            return legacy.substring(0, from)
                    + SECTION + code + flat.substring(m.start(), m.end())
                    + (resume.isEmpty() ? String.valueOf(SECTION) + 'r' : resume)
                    + legacy.substring(to);
        }
        return legacy;
    }

    /**
     * Whether this badge is yours: your name has to come next, with nothing between the two but
     * spaces and bracketed tags - a rank, a guild tag.
     *
     * <p>"Somewhere in the next few characters" was tried first and is wrong in the case that
     * matters: {@code [200] Someone: hey Diego} would hand somebody else's badge your colour, and on
     * a line carrying two badges the wrong one is the one found first.
     */
    private static boolean mine(String plain, int badgeEnd, String self) {
        int limit = Math.min(plain.length(), badgeEnd + NAME_WINDOW + self.length());
        for (int at = plain.indexOf(self, badgeEnd); at >= 0 && at < limit;
                at = plain.indexOf(self, at + 1)) {
            int after = at + self.length();
            // Not a longer name that merely starts with yours.
            if (after < plain.length() && isNamePart(plain.charAt(after))) {
                continue;
            }
            if (GAP.matcher(plain).region(badgeEnd, at).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * The colour to draw a badge of this level in, or null to leave it alone.
     *
     * <p>Null covers both "nothing is picked" and "the pick is the colour Hypixel would have used
     * anyway" - including a pick above your level, which lands back on your own tier.
     */
    private static Character codeFor(int level) {
        LevelColorModule mod = LevelColorModule.INSTANCE;
        if (mod == null) {
            return null;
        }
        int picked = mod.picked();
        if (picked < 0) {
            return null;
        }
        int own = tierOf(level);
        int use = Math.min(picked, own);
        return use == own ? null : CODES[use];
    }

    /** The legacy codes in effect at this index - a colour or reset, plus any formats after it. */
    private static String activeAt(String legacy, int index) {
        StringBuilder active = new StringBuilder();
        for (int i = 0; i + 1 < index; i++) {
            if (legacy.charAt(i) != SECTION || !isCode(legacy.charAt(i + 1))) {
                continue;
            }
            char code = Character.toLowerCase(legacy.charAt(i + 1));
            // A colour (and the reset) clears what came before it in legacy; a format adds to it.
            if (code == 'r' || isColour(code)) {
                active.setLength(0);
            }
            active.append(SECTION).append(code);
            i++;
        }
        return active.toString();
    }

    private static boolean isCode(char c) {
        char l = Character.toLowerCase(c);
        return isColour(l) || (l >= 'k' && l <= 'o') || l == 'r';
    }

    private static boolean isColour(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
