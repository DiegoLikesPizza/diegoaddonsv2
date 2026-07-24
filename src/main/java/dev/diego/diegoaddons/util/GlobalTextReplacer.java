package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.WordReplacement;
import dev.diego.diegoaddons.module.modules.ReplaceWordsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/**
 * Applies the word replacements at the moment text is <b>drawn</b>, rather than at each place text
 * is produced.
 *
 * <p>Every text the game draws funnels through the font's two prepare methods, so rewriting there
 * catches all of it at once: chat, the tab list, name tags above heads, item names and lore, the
 * pop-up above the hotbar - and the chat box while you are still typing, which is why a rename
 * appears the moment you finish the word and disappears again on backspace.
 *
 * <p>This is purely visual. Nothing that gets sent, stored or logged is touched, so a renamed player
 * is still messaged under their real name.
 *
 * <p>Cost matters here, since this runs for every string drawn every frame. The order is deliberate:
 * an enabled check first, then a plain-text scan for any search term, and only on a hit the
 * expensive rebuild through legacy colour codes.
 */
public final class GlobalTextReplacer {
    /** Above this length a line is treated as prose, not a name, for the friend-list bracket. */
    private static final int NAME_LENGTH = 32;

    private GlobalTextReplacer() {
    }

    private static boolean active() {
        ReplaceWordsModule mod = ReplaceWordsModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return false;
        }
        for (WordReplacement r : WordReplacer.all()) {
            if (r.enabled && r.from != null && !r.from.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the text contains anything worth rewriting - the cheap gate before any rebuild. */
    private static boolean matches(String plain) {
        String lower = plain.toLowerCase(Locale.ROOT);
        for (WordReplacement r : WordReplacer.all()) {
            if (r.enabled && r.from != null && !r.from.isEmpty()
                    && lower.contains(r.from.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Rewrites a plain string, or returns it unchanged. */
    public static String forString(String text) {
        if (text == null || text.isEmpty() || !active() || !matches(text)) {
            return text;
        }
        return withBracket(WordReplacer.replace(text), text);
    }

    /**
     * Rewrites a styled text run. The sequence is walked into a legacy string so a phrase split
     * across several differently-coloured runs still matches, then rebuilt only if it changed.
     */
    public static FormattedCharSequence forSequence(FormattedCharSequence text) {
        if (text == null || !active()) {
            return text;
        }
        StringBuilder legacy = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        Style[] last = {null};
        text.accept((index, style, codePoint) -> {
            if (!style.equals(last[0])) {
                legacy.append(LegacyText.codesOf(style));
                last[0] = style;
            }
            legacy.appendCodePoint(codePoint);
            plain.appendCodePoint(codePoint);
            return true;
        });

        String before = plain.toString();
        if (!matches(before)) {
            return text;
        }
        String replaced = WordReplacer.replace(legacy.toString());
        return LegacyText.fromLegacy(withBracket(replaced, before)).getVisualOrderText();
    }

    /**
     * In the friend list, keeps the real IGN in brackets after a rename so the account stays
     * identifiable. Limited to short lines, so a renamed word inside a lore sentence is not followed
     * by the whole sentence repeated.
     */
    private static String withBracket(String replaced, String original) {
        ReplaceWordsModule mod = ReplaceWordsModule.INSTANCE;
        if (mod == null || !mod.ignInFriendList() || !inFriendList()) {
            return replaced;
        }
        String plainOriginal = LegacyText.strip(original).trim();
        if (plainOriginal.isEmpty() || plainOriginal.length() > NAME_LENGTH) {
            return replaced;
        }
        if (LegacyText.strip(replaced).contains(plainOriginal)) {
            return replaced;   // the original survived the rewrite; no point repeating it
        }
        return replaced + "§7 (" + plainOriginal + ")";
    }

    /** Hypixel's friend list is a chest menu whose title carries "Friends". */
    private static boolean inFriendList() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof AbstractContainerScreen<?> s
                && LegacyText.strip(s.getTitle().getString()).toLowerCase(Locale.ROOT).contains("friend");
    }

    /** Exposed for tests and debugging: the currently active rules. */
    public static List<WordReplacement> rules() {
        return WordReplacer.all();
    }
}
