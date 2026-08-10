package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.MinigamesModule;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The link between two DiegoAddons clients, built out of whispers.
 *
 * <p>The mod is client-side: two players' games cannot talk to each other except through something
 * the server already carries between them. On Hypixel that is chat, so a move is a {@code /msg} with
 * a marked line in it - {@code [DA1]ttt|mv|4|a3f} - which the other client reads and this one hides
 * from the chat it is displayed in. To anyone without the mod, nothing about this exists except a
 * whisper they were not sent.
 *
 * <h2>Why it is deliberately slow</h2>
 * Hypixel rate-limits chat and refuses a message identical to the one before it ("You cannot say the
 * same message twice"). Both are designed against exactly the shape of traffic a game protocol has:
 * short, frequent, repetitive. So every line carries a <b>nonce</b> that makes it unique, and they
 * leave a queue at no more than one every {@link #SEND_INTERVAL_MS} - which is also why the games
 * built on this are turn-based, one message per move. A live cursor or a timer tick would be a mute
 * waiting to happen, and a mute is a real cost to a real account.
 *
 * <p>This is not a general chat automation: it sends only in answer to something you did - an
 * invitation you typed, a move you clicked - and it never repeats a message on its own.
 */
public final class GameLink {

    /** Marks a line as ours. Versioned, so a future protocol change cannot be misread as this one. */
    public static final String TAG = "[DA1]";

    /** Minimum gap between two sends. Comfortably under Hypixel's limit rather than at it. */
    private static final long SEND_INTERVAL_MS = 400L;

    /** A queued line is dropped rather than sent late once it is this old. */
    private static final long STALE_MS = 8_000L;

    /**
     * Hypixel's incoming whisper, colour codes already stripped: {@code From [MVP+] Name: text}.
     * The rank is optional because not everyone has one.
     */
    private static final Pattern FROM = Pattern.compile("^From (?:\\[[^]]+] )?(\\w{1,16}): (.+)$");

    /** The echo of a whisper you sent: {@code To [MVP+] Name: text}. Ours are hidden too. */
    private static final Pattern TO = Pattern.compile("^To (?:\\[[^]]+] )?(\\w{1,16}): (.+)$");

    /** What a received line is handed to. */
    public interface Listener {
        /**
         * @param from the sender's name
         * @param game which game the line belongs to, e.g. {@code ttt}
         * @param parts the line's fields after the game, nonce already removed
         */
        void onMessage(String from, String game, String[] parts);
    }

    private record Queued(String to, String line, long queued) {
    }

    private static final Deque<Queued> OUTBOX = new ArrayDeque<>();
    private static Listener listener;
    private static long lastSend;
    private static int nonce = (int) (System.nanoTime() & 0xFFF);

    private GameLink() {
    }

    public static void listener(Listener l) {
        listener = l;
    }

    /**
     * Queues one line to a player.
     *
     * <p>Queued rather than sent: two moves can be answered in the same tick, and Hypixel would
     * drop the second. The nonce is appended here so no two lines are ever identical.
     */
    public static void send(String to, String game, String... parts) {
        if (to == null || to.isBlank()) {
            return;
        }
        StringBuilder line = new StringBuilder(TAG).append(game);
        for (String part : parts) {
            line.append('|').append(part);
        }
        line.append('|').append(Integer.toHexString(nonce++ & 0xFFF));
        OUTBOX.addLast(new Queued(to, line.toString(), System.currentTimeMillis()));
    }

    /** Called every client tick: sends at most one queued line. */
    public static void tick(Minecraft mc) {
        if (OUTBOX.isEmpty() || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSend < SEND_INTERVAL_MS) {
            return;
        }
        Queued next = OUTBOX.pollFirst();
        if (next == null) {
            return;
        }
        if (now - next.queued() > STALE_MS) {
            // A move nobody is waiting for any more is worse than no move: the game at the other
            // end has moved on, and sending it now only confuses it.
            DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] games: dropped a stale line to {}", next.to());
            return;
        }
        lastSend = now;
        mc.player.connection.sendCommand("msg " + next.to() + " " + next.line());
    }

    /**
     * Offers a chat line to the protocol.
     *
     * @return true when it was ours, in which case it must not be shown
     */
    public static boolean receive(String plain) {
        MinigamesModule module = MinigamesModule.INSTANCE;
        if (module == null || !module.isEnabled() || !plain.contains(TAG)) {
            return false;
        }
        Matcher to = TO.matcher(plain);
        if (to.matches() && to.group(2).startsWith(TAG)) {
            // The server's echo of what we just sent. Nothing to act on - only to hide, so a game
            // does not fill your own chat with its own protocol.
            return module.hideProtocol();
        }
        Matcher from = FROM.matcher(plain);
        if (!from.matches()) {
            return false;
        }
        String body = from.group(2);
        if (!body.startsWith(TAG)) {
            return false;
        }
        String[] parts = body.substring(TAG.length()).split("\\|");
        if (parts.length >= 2 && listener != null) {
            // The last field is the nonce, which exists only to make the line unique.
            String[] payload = new String[parts.length - 2];
            System.arraycopy(parts, 1, payload, 0, payload.length);
            String sender = from.group(1);
            try {
                listener.onMessage(sender, parts[0].toLowerCase(Locale.ROOT), payload);
            } catch (RuntimeException e) {
                DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] games: bad line from {}", sender, e);
            }
        }
        return module.hideProtocol();
    }

    /** Forgets anything queued - on disconnect, or when a game ends. */
    public static void reset() {
        OUTBOX.clear();
    }
}
