package dev.diego.diegoaddons.util;

import java.util.List;

/**
 * What every game against another player has in common: whose turn it is, how it ended, and the
 * rematch handshake. The rules and the board are the subclass's.
 *
 * <p><b>Both clients run the same class and neither is a server.</b> That is the constraint the
 * whole design answers to: a move is applied locally, sent, and applied again at the other end, so
 * every subclass must reject the same things at both ends - a move out of turn, onto a taken square,
 * after the end. Anything either side would accept but the other would not is two boards drifting
 * apart, which is the only failure here that cannot be recovered from.
 *
 * <p>Where a game needs cards or any other shuffled state, it is derived from a <b>seed both sides
 * were told</b> rather than sent card by card: the same seed makes the same deck, so there is
 * nothing to keep in step and nothing to disagree about.
 */
public abstract class MiniGame {

    /** How the game ended, or {@link #PLAYING} while it has not. */
    public enum Result {
        PLAYING,
        YOU_WON,
        THEY_WON,
        DRAW,
        YOU_RESIGNED,
        THEY_RESIGNED,
        ABANDONED
    }

    protected Result result = Result.PLAYING;
    protected boolean yourTurn;

    /** Their rematch offer, and the seed it carried. */
    private boolean theyOffered;
    private long offeredSeed;

    /** Our own offer, waiting to be accepted. */
    private boolean weOffered;
    private long ourSeed;

    /** The protocol's name for this game: {@code ttt}, {@code c4}, {@code bj}, {@code bs}. */
    public abstract String id();

    /** What the screen calls it. */
    public abstract String title();

    /** The board area the screen should reserve, in units. */
    public abstract int boardWidth();

    public abstract int boardHeight();

    /** A line under the title saying what to do now, or "" for the default turn text. */
    public String hint() {
        return "";
    }

    /** Buttons this game wants right now - "Ziehen"/"Halten", "Neu würfeln"/"Bereit". */
    public List<String> actions() {
        return List.of();
    }

    /** One of {@link #actions()} was pressed. */
    public void act(int index) {
    }

    /** A line from the other side. {@code parts[0]} is the verb. */
    public abstract void receive(String[] parts);

    /** Starts the board over. {@code youFirst} says who moves, {@code seed} seeds any shuffling. */
    protected abstract void restart(boolean youFirst, long seed);

    public boolean over() {
        return result != Result.PLAYING;
    }

    public Result result() {
        return result;
    }

    public boolean yourTurn() {
        return yourTurn && !over();
    }

    /** Ends the game for a reason that is not the board - a resignation, a silence. */
    public void finish(Result how) {
        if (result == Result.PLAYING) {
            result = how;
        }
    }

    public boolean rematchPending() {
        return theyOffered;
    }

    public boolean rematchOffered() {
        return weOffered;
    }

    /**
     * Offers a rematch, or accepts the one that was offered.
     *
     * <p>Two verbs rather than one, because "again?" and "yes" are different messages and treating
     * them as the same one means both sides restarting at different moments - and with different
     * ideas of who moves first. The side that <b>accepts</b> moves first: the offering side has been
     * sitting on a finished board waiting, and handing them the first move as well would make
     * offering the winning move.
     */
    public void rematch() {
        if (theyOffered) {
            theyOffered = false;
            restart(true, offeredSeed);
            send("ra");
            Minigames.heard();
            return;
        }
        if (weOffered) {
            return;
        }
        weOffered = true;
        // Not for secrecy - a seed only has to be the same at both ends, and this is the cheapest
        // thing that differs between two games.
        ourSeed = System.nanoTime();
        send("re", Long.toHexString(ourSeed));
    }

    /** Handles the two rematch verbs. Returns true when the line was one of them. */
    protected boolean rematchVerb(String[] parts) {
        if (!over()) {
            return false;
        }
        if ("re".equals(parts[0]) && parts.length >= 2) {
            theyOffered = true;
            try {
                offeredSeed = Long.parseUnsignedLong(parts[1], 16);
            } catch (NumberFormatException e) {
                offeredSeed = 0L;
            }
            return true;
        }
        if ("ra".equals(parts[0]) && weOffered) {
            weOffered = false;
            restart(false, ourSeed);
            return true;
        }
        return false;
    }

    /**
     * Line numbering, so a lost line can be sent again without being applied twice.
     *
     * <p>Chat is not a reliable transport: a whisper can be swallowed by a filter, a rate limit or a
     * client that was not listening yet, and a turn-based game with one message per move has no way
     * to notice. Every line therefore carries a number, the receiver ignores one it has already
     * applied, and that is what makes {@link #resend()} safe - the fix for a game that has stopped
     * is to say the same thing again, and it either lands or was already there.
     *
     * <p>The numbers do not reset for a rematch. They identify a line within this conversation, and
     * starting over at one after a restart is how a stale line gets applied to a fresh board.
     */
    private int outSeq;
    private int inSeq;
    private String[] lastLine;

    /** Sends a line for this game to the opponent, numbered. */
    protected void send(String... parts) {
        String[] numbered = new String[parts.length + 1];
        numbered[0] = parts[0];
        numbered[1] = Integer.toString(++outSeq);
        System.arraycopy(parts, 1, numbered, 2, parts.length - 1);
        lastLine = numbered;
        Minigames.sendTo(id(), numbered);
    }

    /**
     * Hands an incoming line to the game, once.
     *
     * <p>A repeat - which is what a nudge produces when nothing was actually lost - is dropped here
     * rather than being applied a second time, which for blackjack would mean drawing another card
     * and for battleships a second free shot.
     */
    void deliver(String[] parts) {
        if (parts.length < 2) {
            return;
        }
        int seq;
        try {
            seq = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        if (seq <= inSeq) {
            return;
        }
        inSeq = seq;
        String[] stripped = new String[parts.length - 1];
        stripped[0] = parts[0];
        System.arraycopy(parts, 2, stripped, 1, parts.length - 2);
        receive(stripped);
    }

    /** Whether there is anything to say again. */
    public boolean canResend() {
        return lastLine != null;
    }

    /** Says the last line again, for a game that has stopped because one went missing. */
    public void resend() {
        if (lastLine != null) {
            Minigames.sendTo(id(), lastLine);
        }
    }
}
