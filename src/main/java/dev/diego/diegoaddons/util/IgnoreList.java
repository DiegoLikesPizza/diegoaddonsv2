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

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mod.announceReason() && !blocked.reason.isBlank()) {
            mc.player.connection.sendCommand("pc Blocked for reason: " + blocked.reason);
        }
        mc.player.connection.sendCommand("party kick " + name);
        mc.gui.getChat().addClientSystemMessage(Component.literal(
                "§b[DiegoAddons] §fKicked blocked player §e" + name
                        + (blocked.reason.isBlank() ? "" : " §7(" + blocked.reason + ")")));
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
