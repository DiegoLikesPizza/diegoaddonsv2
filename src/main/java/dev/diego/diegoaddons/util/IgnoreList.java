package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.BlockedPlayer;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.module.modules.BetterIgnoreListModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A block list that remembers <b>why</b> someone is on it, and can act on it when they turn up in
 * your party.
 *
 * <p>The party watcher reads Hypixel's own join line. Names carry rank prefixes and the line varies
 * a little between party types, so the pattern below is the tuning knob if it ever stops matching.
 */
public final class IgnoreList {
    /**
     * "Party > [MVP+] Name joined the party." - deliberately unanchored: Hypixel prefixes the line
     * with "Party > ", and an optional rank sits before the name. This is the tuning knob if the
     * wording ever changes.
     */
    private static final Pattern JOINED = Pattern.compile(
            "(?:\\[[^\\]]+]\\s*)?(\\w{1,16}) joined the party");
    /** Guard so one join is never kicked twice if the line is repeated. */
    private static String lastKicked = "";
    private static long lastKickAt;

    /**
     * A command waiting to go out, and the note to print once it has.
     *
     * <p>Kicking used to happen inside the chat handler: both commands left in the same tick, the
     * instant the join line arrived. That is too fast in two different ways. It reads as a bot -
     * nobody types a kick in the same frame somebody joins - and Hypixel rate-limits commands, so
     * the second of two sent together is the one that quietly does not happen. Which meant the
     * announcement went out and the kick sometimes did not.
     */
    private record Pending(String command, long dueAt, String notice) {
    }

    private static final java.util.Deque<Pending> QUEUE = new java.util.ArrayDeque<>();

    /** Minimum gap between two commands, so they are never in the same tick. */
    private static final long COMMAND_GAP_MS = 700L;

    private static long lastCommandAt;

    private IgnoreList() {
    }

    /** The blocked players (never null). */
    public static List<BlockedPlayer> all() {
        if (ConfigManager.get().blockedPlayers == null) {
            ConfigManager.get().blockedPlayers = new ArrayList<>();
        }
        return ConfigManager.get().blockedPlayers;
    }

    public static BlockedPlayer find(String name) {
        if (name == null) {
            return null;
        }
        for (BlockedPlayer b : all()) {
            if (b.name.equalsIgnoreCase(name)) {
                return b;
            }
        }
        return null;
    }

    public static boolean isBlocked(String name) {
        return find(name) != null;
    }

    /** Adds or updates a block. Returns false when the name was empty. */
    public static boolean block(String name, String reason) {
        if (name == null || name.isBlank()) {
            return false;
        }
        BlockedPlayer existing = find(name);
        if (existing != null) {
            existing.reason = reason == null ? "" : reason;
        } else {
            all().add(new BlockedPlayer(name, reason == null ? "" : reason));
        }
        ConfigManager.save();
        return true;
    }

    public static boolean unblock(String name) {
        BlockedPlayer b = find(name);
        if (b == null) {
            return false;
        }
        all().remove(b);
        ConfigManager.save();
        return true;
    }

    /**
     * Called for every incoming system message. When a blocked player joins the party and auto-kick
     * is on, kick them - optionally saying why first, so the party knows it was deliberate.
     */
    public static void onMessage(String plain) {
        BetterIgnoreListModule mod = BetterIgnoreListModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.autoKick()) {
            return;
        }
        Matcher m = JOINED.matcher(plain.trim());
        if (!m.find()) {
            return;
        }
        String name = m.group(1);
        BlockedPlayer blocked = find(name);
        if (blocked == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (name.equalsIgnoreCase(lastKicked) && now - lastKickAt < 3000) {
            return;
        }
        lastKicked = name;
        lastKickAt = now;

        // Queued rather than sent: see Pending. The delay is how long after the join line the first
        // command goes out, and the gap keeps the two apart after that.
        long due = now + Math.round(mod.kickDelay() * 1000);
        if (mod.announceReason() && !blocked.reason.isBlank()) {
            QUEUE.addLast(new Pending("pc Blocked for reason: " + blocked.reason, due, null));
        }
        QUEUE.addLast(new Pending("party kick " + name, due,
                "§b[DiegoAddons] §fKicked blocked player §e" + name
                        + (blocked.reason.isBlank() ? "" : " §7(" + blocked.reason + ")")));
    }

    /**
     * Sends at most one queued command, and never two in the same tick.
     *
     * <p>Called every client tick while the module is on. A command whose turn has not come simply
     * waits, and the note that says what happened is printed when the kick actually leaves rather
     * than when it was decided on.
     */
    public static void tick(Minecraft mc) {
        if (QUEUE.isEmpty() || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Pending next = QUEUE.peekFirst();
        if (now < next.dueAt() || now - lastCommandAt < COMMAND_GAP_MS) {
            return;
        }
        QUEUE.pollFirst();
        lastCommandAt = now;
        mc.player.connection.sendCommand(next.command());
        if (next.notice() != null && mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(next.notice()));
        }
    }

    /** Drops anything still waiting - on a disconnect, where the party is gone anyway. */
    public static void reset() {
        QUEUE.clear();
        lastKicked = "";
    }

    /** Lower-case name list, for suggestions. */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (BlockedPlayer b : all()) {
            out.add(b.name.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
