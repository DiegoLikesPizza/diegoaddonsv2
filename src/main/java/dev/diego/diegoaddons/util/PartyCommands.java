package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.PartyCommandsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets party members trigger party actions by typing {@code !pt}, {@code !warp} and so on in party
 * chat, which your client then runs.
 *
 * <p><b>This hands other people a trigger on your client</b>, so it is fenced in deliberately:
 *
 * <ul>
 *   <li>Only party chat is read - never normal chat, DMs or guild chat.</li>
 *   <li>The destructive actions (kick, transfer, disband) are separate options, off by default.</li>
 *   <li>Anyone on your block list is ignored outright.</li>
 *   <li>A short cooldown per player stops one person spamming your client into a command flood.</li>
 * </ul>
 *
 * <p>Commands are still just commands: whether they work depends on you having the party rank for
 * them, exactly as if you had typed them.
 */
public final class PartyCommands {
    /** "Party > [MVP+] Name: !pt" - rank prefix optional, and the message is the rest of the line. */
    private static final Pattern PARTY_LINE = Pattern.compile(
            "^Party\\s*>\\s*(?:\\[[^\\]]+]\\s*)?(\\w{1,16})\\s*:\\s*(.+)$");
    /** Minimum gap between two commands from the same player. */
    private static final long COOLDOWN_MS = 2000;

    private static final Map<String, Long> LAST_USED = new HashMap<>();

    private PartyCommands() {
    }

    /** Called for every incoming system message. */
    public static void onMessage(String plain) {
        PartyCommandsModule mod = PartyCommandsModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        Matcher m = PARTY_LINE.matcher(plain.trim());
        if (!m.find()) {
            return;
        }
        String sender = m.group(1);
        String message = m.group(2).trim();
        if (!message.startsWith("!") || message.length() < 2) {
            return;
        }
        if (IgnoreList.isBlocked(sender)) {
            return;   // blocked people do not get to drive your client
        }

        String[] parts = message.substring(1).split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : sender;

        String toRun = resolve(mod, cmd, sender, arg);
        String toSay = toRun != null ? null : fun(mod, cmd, sender, message);
        if (toRun == null && toSay == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_USED.get(sender.toLowerCase(Locale.ROOT));
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        LAST_USED.put(sender.toLowerCase(Locale.ROOT), now);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (toRun != null) {
            mc.player.connection.sendCommand(toRun);
            mc.gui.getChat().addClientSystemMessage(Component.literal(
                    "§b[DiegoAddons] §e" + sender + " §fran §e!" + cmd));
        } else {
            // The answer goes to the party, since the question was asked there.
            mc.player.connection.sendCommand("pc " + toSay);
        }
    }

    /**
     * Maps a chat trigger to the command to run, or null when it is unknown or switched off.
     * {@code arg} defaults to the sender, so "!pt" alone means "transfer to me".
     */
    private static String resolve(PartyCommandsModule mod, String cmd, String sender, String arg) {
        return switch (cmd) {
            case "pt", "ptme", "transfer" -> mod.allowTransfer() ? "party transfer " + arg : null;
            case "kick" -> mod.allowKick() ? "party kick " + arg : null;
            case "kickoffline" -> mod.allowKick() ? "party kickoffline" : null;
            case "promote" -> mod.allowPromote() ? "party promote " + arg : null;
            case "demote" -> mod.allowPromote() ? "party demote " + arg : null;
            case "invite", "inv" -> mod.allowInvite() ? "party invite " + arg : null;
            case "allinvite" -> mod.allowInvite() ? "party settings allinvite" : null;
            case "warp" -> mod.allowWarp() ? "party warp" : null;
            case "disband" -> mod.allowDisband() ? "party disband" : null;
            case "mute" -> mod.allowMute() ? "party mute" : null;
            case "unmute" -> mod.allowMute() ? "party unmute" : null;
            default -> null;
        };
    }

    /** The 8ball's repertoire, which is the traditional one. */
    private static final String[] EIGHT_BALL = {
            "It is certain", "Without a doubt", "You may rely on it", "Yes, definitely",
            "As I see it, yes", "Most likely", "Signs point to yes", "Reply hazy, try again",
            "Ask again later", "Better not tell you now", "Cannot predict now",
            "Don't count on it", "My reply is no", "My sources say no", "Outlook not so good",
            "Very doubtful",
    };

    private static final String[] RPS = {"Rock", "Paper", "Scissors"};

    /**
     * The commands that answer rather than do: they reply in party chat instead of running anything.
     *
     * <p>Kept apart from {@link #resolve} on purpose. Those hand someone a party command on your
     * account; these hand them a sentence, so they sit behind one option rather than seven.
     *
     * @return what to say, or null when this is not one of them
     */
    private static String fun(PartyCommandsModule mod, String cmd, String sender, String message) {
        if (!mod.allowFun()) {
            return null;
        }
        String rest = message.length() > cmd.length() + 1
                ? message.substring(cmd.length() + 1).trim() : "";
        return switch (cmd) {
            case "8ball" -> sender + ": " + pick(EIGHT_BALL);
            case "coinflip", "cf" -> sender + " flipped " + (Math.random() < 0.5 ? "Heads" : "Tails");
            case "rps" -> sender + ": " + pick(RPS);
            case "dice", "roll" -> {
                int sides = 6;
                try {
                    if (!rest.isEmpty()) {
                        sides = Math.max(2, Math.min(1000, Integer.parseInt(rest.split("\\s+")[0])));
                    }
                } catch (NumberFormatException ignored) {
                    // "!roll a lot" is a d6 like everything else
                }
                yield sender + " rolled " + (1 + (int) (Math.random() * sides)) + " / " + sides;
            }
            case "pick", "choose" -> {
                String[] options = rest.split("\\s*(?:\\||,| or )\\s*");
                if (rest.isEmpty() || options.length < 2) {
                    yield null;   // nothing to pick between; say nothing rather than something silly
                }
                yield sender + ": " + pick(options).trim();
            }
            default -> null;
        };
    }

    private static String pick(String[] from) {
        return from[(int) (Math.random() * from.length)];
    }

    /** Clears the per-player cooldowns, e.g. on disconnect. */
    public static void reset() {
        LAST_USED.clear();
    }
}
