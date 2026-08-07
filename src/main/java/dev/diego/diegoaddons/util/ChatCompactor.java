package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.mixin.ChatComponentAccessor;
import dev.diego.diegoaddons.module.modules.ChatModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Merges repeated chat messages into one line with a counter, the way most chat mods do: the older
 * copy is removed and the new one gains a grey {@code (x3)}.
 *
 * <p>Matching is on the message's plain text with any existing counter stripped, so the third repeat
 * still matches the line that already reads {@code (x2)}. Entries older than the configured window
 * are dropped, so a message that comes back much later starts counting again rather than continuing
 * from a stale total.
 */
public final class ChatCompactor {
    /** The counter this class appends, so it can be recognised and stripped again. */
    private static final Pattern COUNTER = Pattern.compile("\\s*\\(x\\d+\\)$");

    private record Seen(String text, long time, int count) {
    }

    private static final List<Seen> RECENT = new ArrayList<>();

    private ChatCompactor() {
    }

    /**
     * Called for every incoming chat message.
     *
     * @return the component to actually display - either unchanged, or with a counter appended and
     *         the previous copy already removed from the chat
     */
    public static Component compact(ChatComponent chat, Component message) {
        ChatModule mod = ChatModule.INSTANCE;
        if (mod == null || !mod.compacting() || message == null) {
            return message;
        }
        String plain = strip(message.getString());
        if (plain.isBlank() || isSeparator(plain)) {
            return message;
        }
        long now = System.currentTimeMillis();
        long window = (long) (mod.windowSeconds() * 1000);
        prune(now, window);

        int count = 0;
        for (Iterator<Seen> it = RECENT.iterator(); it.hasNext(); ) {
            Seen s = it.next();
            if (s.text().equals(plain)) {
                count = s.count();
                it.remove();
                break;
            }
        }

        if (count == 0) {
            RECENT.add(new Seen(plain, now, 1));
            return message;
        }

        int next = count + 1;
        RECENT.add(new Seen(plain, now, next));
        remove(chat, plain);
        return message.copy().append(
                Component.literal(" (x" + next + ")").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Drops the previous copy of this message so only the counted one remains.
     *
     * <p>The scroll position is saved and put back around the rebuild. {@code refreshTrimmedMessages}
     * re-adds the whole backlog one message at a time, and vanilla's per-message path nudges the
     * scroll by one to keep what you are reading in place - which is right for one new message and
     * catastrophic for a thousand replayed ones. Without this, compacting a repeated message while
     * scrolled up threw the chat somewhere else entirely.
     *
     * <p>One line is subtracted: the removed copy is gone from the backlog, so everything above it
     * has genuinely moved down by its height, and holding the old number would drift by a line each
     * time a message repeated.
     */
    private static void remove(ChatComponent chat, String plain) {
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage> all = acc.diego$allMessages();
        for (int i = 0; i < all.size(); i++) {
            if (strip(all.get(i).content().getString()).equals(plain)) {
                int scroll = acc.diego$chatScrollbarPos();
                all.remove(i);
                acc.diego$refreshTrimmedMessages();
                if (scroll > 0) {
                    acc.diego$setChatScrollbarPos(Math.max(0,
                            Math.min(scroll - 1, acc.diego$trimmedMessages().size())));
                }
                return;
            }
        }
    }

    private static void prune(long now, long window) {
        RECENT.removeIf(s -> now - s.time() > window);
    }

    /** Plain text without colour codes or a counter this class added earlier. */
    /**
     * Whether a line is one of the rules Hypixel draws around an announcement.
     *
     * <p>These are the most repeated "messages" on the server - every party join, every guild
     * notice, every profile switch is wrapped in a pair - and they are not messages at all. Counting
     * them turns the divider above a party invite into "(x9)" and, worse, deletes the earlier rule
     * so the block it was framing loses its top edge.
     *
     * <p>Recognised by content rather than by an exact string: a rule is nothing but rule characters,
     * and Hypixel varies both the character and the width by context and chat width.
     */
    private static boolean isSeparator(String plain) {
        // Short runs are not rules - "---" can be someone typing, and a single character never is.
        if (plain.length() < 6) {
            return false;
        }
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            boolean rule = c == '▬'      // ▬ heavy rectangle, the SkyBlock rule
                    || c == '-' || c == '=' || c == '_'
                    || c == '─' || c == '━'   // ─ ━ box drawing
                    || c == '═'                     // ═ double box drawing
                    || c == '*' || c == '·'         // · middle dot
                    || Character.isWhitespace(c);
            if (!rule) {
                return false;
            }
        }
        return true;
    }

    private static String strip(String s) {
        String plain = s.replaceAll("§.", "");
        return COUNTER.matcher(plain).replaceAll("").trim();
    }

    /** Forget everything, e.g. when changing servers. */
    public static void reset() {
        RECENT.clear();
    }

    /** True once the chat exists and is safe to touch. */
    public static boolean ready() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gui != null;
    }
}
