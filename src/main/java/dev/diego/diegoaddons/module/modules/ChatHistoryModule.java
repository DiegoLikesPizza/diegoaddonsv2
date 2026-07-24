package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Lifts vanilla's 100-message chat cap so the scrollback keeps everything from the session - useful
 * on servers that push a lot of chat, where a drop or a trade message scrolls away in seconds.
 *
 * <p>The cap is raised rather than removed outright: chat lines are kept in memory for as long as
 * they are in the scrollback, so an unbounded list would grow for the whole session. {@link #limit()}
 * is high enough to be unlimited in practice while still having an end.
 *
 * <p>See {@code ChatComponentMixin} for the three places vanilla trims.
 */
public class ChatHistoryModule extends Module {
    /** Kept messages/lines while enabled. Reached only after tens of thousands of messages. */
    private static final int LIMIT = 100_000;

    public static ChatHistoryModule INSTANCE;

    public ChatHistoryModule() {
        super("chathistory", Category.MISC, "Unlimited Chat History",
                "Keep the whole session's chat instead of only the last 100 messages.");
        INSTANCE = this;
    }

    public static int limit() {
        return LIMIT;
    }
}
