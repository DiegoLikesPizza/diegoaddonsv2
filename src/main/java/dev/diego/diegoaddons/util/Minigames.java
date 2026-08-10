package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.gui.GameScreen;
import dev.diego.diegoaddons.module.modules.MinigamesModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;

/**
 * Games against another DiegoAddons user: who invited whom, whose turn it is, and what happens when
 * one side stops answering.
 *
 * <p>One game at a time, on purpose. The transport is whispers ({@link GameLink}) and a whisper has
 * no session in it - two games running at once would be two conversations down one wire, told apart
 * only by a name. One game means a move can always be attributed, and "you are already playing" is a
 * far better answer than a move landing on the wrong board.
 *
 * <p>The board itself is {@link TicTacToe}; this is the part that would be the same for any of them.
 */
public final class Minigames {

    /** How long an invitation stands before it is forgotten, either end. */
    private static final long INVITE_TIMEOUT_MS = 60_000L;

    /** Nothing at all from the opponent for this long during a game: assume they are gone. */
    private static final long SILENCE_MS = 120_000L;

    /** The games you can be invited to, by the name typed after {@code /da play <name>}. */
    public static final java.util.Map<String, String> GAMES = java.util.Map.of(
            TicTacToe.ID, "Tic Tac Toe",
            ConnectFour.ID, "Connect Four",
            Blackjack.ID, "Blackjack",
            Battleship.ID, "Battleships");

    private static String opponent;
    private static MiniGame game;
    private static long lastHeard;

    /** Which game an invitation was for - ours to them, and theirs to us. */
    private static String invitedGame;
    private static String invitedUsGame;
    /** The seed the inviter picked, so both sides shuffle the same way. */
    private static long invitedSeed;

    /** Set while we have asked someone and are waiting, or been asked and not answered. */
    private static String invitedThem;
    private static String invitedUs;
    private static long inviteAt;

    private Minigames() {
    }

    /** Wires the protocol up. Called once, from the module. */
    public static void init() {
        GameLink.listener(Minigames::onMessage);
    }

    public static MiniGame game() {
        return game;
    }

    public static String opponent() {
        return opponent == null ? "" : opponent;
    }

    public static boolean playing() {
        return game != null;
    }

    // ---------------------------------------------------------------- invitations

    /** {@code /da play <name> [game]} - asks someone for a game. */
    public static void invite(String name, String gameId) {
        if (name == null || name.isBlank()) {
            return;
        }
        String id = gameId == null || gameId.isBlank()
                ? TicTacToe.ID
                : gameId.toLowerCase(Locale.ROOT);
        if (!GAMES.containsKey(id)) {
            tell("No game called §e" + id + "§f. Try §e" + String.join("§f, §e", GAMES.keySet()));
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name())) {
            tell("You cannot play yourself.");
            return;
        }
        if (playing()) {
            tell("You are already playing " + opponent + ". §7/da resign§f ends it.");
            return;
        }
        invitedThem = name;
        invitedGame = id;
        // Picked by the inviter and carried in the invitation, so a game that shuffles anything
        // shuffles it identically at both ends without another round trip.
        invitedSeed = System.nanoTime();
        inviteAt = System.currentTimeMillis();
        GameLink.send(name, id, "inv", Long.toHexString(invitedSeed));
        tell("Invited §e" + name + "§f to §b" + GAMES.get(id) + "§f. It stands for a minute.");
    }

    /** {@code /da accept} - takes the invitation we were sent. */
    public static void accept() {
        if (invitedUs == null) {
            tell("Nobody has invited you.");
            return;
        }
        if (playing()) {
            tell("You are already playing " + opponent + ".");
            return;
        }
        String from = invitedUs;
        String id = invitedUsGame;
        invitedUs = null;
        GameLink.send(from, id, "ok");
        // The one who asked goes first, which is also the only way both sides agree on it without
        // another round trip.
        start(from, id, false, invitedSeed);
    }

    /** {@code /da decline} - says no, so the other side is not left waiting for the timeout. */
    public static void decline() {
        if (invitedUs == null) {
            return;
        }
        GameLink.send(invitedUs, invitedUsGame, "no");
        tell("Declined.");
        invitedUs = null;
    }

    /** {@code /da resign} - gives up the running game, and says so at the other end. */
    public static void resign() {
        if (!playing()) {
            return;
        }
        GameLink.send(opponent, game.id(), "gg");
        game.finish(MiniGame.Result.YOU_RESIGNED);
        tell("You gave up.");
    }

    /** Drops everything without telling anyone - for a disconnect, where nothing can be told. */
    public static void reset() {
        opponent = null;
        game = null;
        invitedThem = null;
        invitedUs = null;
        GameLink.reset();
    }

    private static void start(String other, String id, boolean weMoveFirst, long seed) {
        opponent = other;
        game = create(id, weMoveFirst, seed);
        lastHeard = System.currentTimeMillis();
        invitedThem = null;
        invitedUs = null;
        if (game != null) {
            GameScreen.open();
        }
    }

    private static MiniGame create(String id, boolean weMoveFirst, long seed) {
        return switch (id) {
            case TicTacToe.ID -> new TicTacToe(weMoveFirst);
            case ConnectFour.ID -> new ConnectFour(weMoveFirst);
            case Blackjack.ID -> new Blackjack(weMoveFirst, seed);
            case Battleship.ID -> new Battleship(weMoveFirst);
            default -> null;
        };
    }

    // ---------------------------------------------------------------- the protocol

    private static void onMessage(String from, String gameId, String[] parts) {
        if (!GAMES.containsKey(gameId) || parts.length == 0) {
            return;
        }
        String verb = parts[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "inv" -> invited(from, gameId, parts.length >= 2 ? parts[1] : "0");
            case "ok" -> {
                // Only from the person we actually asked, for the game we asked about: an "ok" out
                // of nowhere would otherwise start a game with anybody who sent one.
                if (from.equalsIgnoreCase(invitedThem) && gameId.equals(invitedGame)) {
                    tell("§e" + from + "§f accepted.");
                    start(from, gameId, true, invitedSeed);
                }
            }
            case "no" -> {
                if (from.equalsIgnoreCase(invitedThem)) {
                    tell("§e" + from + "§f declined.");
                    invitedThem = null;
                }
            }
            case "gg" -> {
                if (playing() && from.equalsIgnoreCase(opponent)) {
                    game.finish(MiniGame.Result.THEY_RESIGNED);
                    tell("§e" + from + "§f gave up.");
                }
            }
            default -> {
                // Everything else is the game's own business - a move, a shot, a rematch. Only from
                // the opponent, and only for the game actually being played.
                if (playing() && from.equalsIgnoreCase(opponent) && gameId.equals(game.id())) {
                    lastHeard = System.currentTimeMillis();
                    game.receive(parts);
                }
            }
        }
    }

    private static void invited(String from, String gameId, String seed) {
        MinigamesModule module = MinigamesModule.INSTANCE;
        if (module != null && module.autoDecline()) {
            GameLink.send(from, gameId, "no");
            return;
        }
        if (playing()) {
            GameLink.send(from, gameId, "no");
            return;
        }
        invitedUs = from;
        invitedUsGame = gameId;
        try {
            invitedSeed = Long.parseUnsignedLong(seed, 16);
        } catch (NumberFormatException e) {
            invitedSeed = 0L;
        }
        inviteAt = System.currentTimeMillis();

        MutableComponent line = Component.literal("§b[DiegoAddons] §e" + from
                + "§f challenges you to §b" + GAMES.get(gameId) + "§f  ");
        line.append(button("[Accept]", ChatFormatting.GREEN, "/da accept", "Start the game"));
        line.append(Component.literal(" "));
        line.append(button("[Decline]", ChatFormatting.RED, "/da decline", "Say no"));
        say(line);

        if (module != null && module.sound()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.4f);
            }
        }
    }

    /** A clickable chat button, which is how an invitation is answered without typing anything. */
    private static MutableComponent button(String text, ChatFormatting colour, String command,
                                           String tooltip) {
        return Component.literal(text).setStyle(Style.EMPTY
                .withColor(colour)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip))));
    }

    // ---------------------------------------------------------------- upkeep

    /** Called every client tick by the module: sends the outbox and lets things expire. */
    public static void tick(Minecraft mc) {
        GameLink.tick(mc);
        long now = System.currentTimeMillis();

        if (invitedThem != null && now - inviteAt > INVITE_TIMEOUT_MS) {
            tell("§e" + invitedThem + "§f did not answer.");
            invitedThem = null;
        }
        if (invitedUs != null && now - inviteAt > INVITE_TIMEOUT_MS) {
            invitedUs = null;
        }
        // A game whose opponent has gone quiet is ended rather than left sitting there: they have
        // logged off, or the mod is gone at their end, and either way no move is coming.
        if (playing() && !game.over() && now - lastHeard > SILENCE_MS) {
            game.finish(MiniGame.Result.ABANDONED);
            tell("§e" + opponent + "§f stopped answering.");
        }
    }

    /** The one way a game talks: everything it sends goes through here, to the one opponent. */
    static void sendTo(String gameId, String... parts) {
        if (playing()) {
            GameLink.send(opponent, gameId, parts);
        }
    }

    static void heard() {
        lastHeard = System.currentTimeMillis();
    }

    private static void tell(String message) {
        say(Component.literal("§b[DiegoAddons] §f" + message));
    }

    private static void say(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            // Client-side only, like every other line the mod writes: it goes into your chat and
            // never near the server.
            mc.gui.getChat().addClientSystemMessage(message);
        }
    }
}
