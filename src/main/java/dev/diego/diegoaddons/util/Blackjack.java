package dev.diego.diegoaddons.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Blackjack between two players, with no dealer and no house.
 *
 * <p>Whoever gets closest to 21 without going over wins; going over loses on the spot; the same
 * total is a draw. There is no dealer because there is nobody to be one - both ends are players'
 * clients, and inventing a third party would mean inventing someone to shuffle for it.
 *
 * <h2>The deck without a server</h2>
 * Neither side can be trusted to deal, and no card is ever sent. Instead both clients build the
 * <b>same</b> deck from the <b>same seed</b> - handed over once when the game is agreed - and draw
 * from it in a fixed order. Since turns are serialised (you play until you stand or bust, then they
 * do), the two draw pointers cannot diverge, and the cards each side computes are the cards the
 * other computes. Nothing about the deck travels over chat at all; only "I drew" and "I'm done".
 *
 * <p>A player can of course read their opponent's cards by looking at their own copy of the deck.
 * That is a real property of any peer-to-peer card game with no server, and it is worth knowing
 * rather than pretending otherwise: this is a game for people who are not trying to cheat.
 */
public final class Blackjack extends MiniGame {

    public static final String ID = "bj";

    /** Ranks in a suit-less deck: four of each, which is all blackjack cares about. */
    private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

    private final List<Integer> deck = new ArrayList<>();
    private final List<Integer> yours = new ArrayList<>();
    private final List<Integer> theirs = new ArrayList<>();

    private int next;
    private boolean youStood;
    private boolean theyStood;

    Blackjack(boolean youFirst, long seed) {
        restart(youFirst, seed);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Blackjack";
    }

    @Override
    public int boardWidth() {
        return 460;
    }

    @Override
    public int boardHeight() {
        return 300;
    }

    @Override
    public String hint() {
        if (over()) {
            return "";
        }
        return yourTurn() ? "Hit or stand" : "Waiting for their move";
    }

    @Override
    public List<String> actions() {
        return yourTurn() ? List.of("Hit", "Stand") : List.of();
    }

    @Override
    public void act(int index) {
        if (index == 0) {
            hit();
        } else if (index == 1) {
            stand();
        }
    }

    /** Your cards, as they are printed on the screen. */
    public List<String> yourCards() {
        return names(yours);
    }

    /** Their cards. Known here because the deck is known - see the class note. */
    public List<String> theirCards() {
        return names(theirs);
    }

    public int yourTotal() {
        return total(yours);
    }

    public int theirTotal() {
        return total(theirs);
    }

    public boolean youStood() {
        return youStood;
    }

    public boolean theyStood() {
        return theyStood;
    }

    private void hit() {
        if (!yourTurn()) {
            return;
        }
        yours.add(draw());
        boolean bust = total(yours) > 21;
        // The turn is *stated*, not left to be worked out at the other end - see receive().
        send("h", bust ? "1" : "0");
        if (bust) {
            // Busting ends your turn whether you like it or not, and settles the game if they are
            // already done.
            youStood = true;
            yourTurn = false;
            settleOrPass();
        }
    }

    private void stand() {
        if (!yourTurn()) {
            return;
        }
        youStood = true;
        yourTurn = false;
        send("s");
        settleOrPass();
    }

    /**
     * A line from the other side.
     *
     * <p>Whether they are finished is <b>read from the message</b> rather than worked out by adding
     * up their hand here. That was the original design and it deadlocked: their hand is only known
     * to us through our own copy of the deck, so one card of disagreement meant we decided they had
     * busted while they were still playing. We took the turn, their next line arrived while we
     * thought it was ours, the old guard dropped it as out of turn - and both sides sat waiting for
     * the other. Nobody was to move, and nothing could ever make it move again.
     *
     * <p>So the only thing that decides whose turn it is now is the player whose turn it just was.
     * The hand is still tracked for the display, and a disagreement there is now a cosmetic bug
     * rather than a stuck game.
     */
    @Override
    public void receive(String[] parts) {
        if (rematchVerb(parts)) {
            return;
        }
        if (over()) {
            return;
        }
        switch (parts[0]) {
            case "h" -> {
                theirs.add(draw());
                // "1" means that card finished them - they busted and said so.
                if (parts.length >= 2 && "1".equals(parts[1])) {
                    theyStood = true;
                    yourTurn = true;
                    settleOrPass();
                }
            }
            case "s" -> {
                theyStood = true;
                yourTurn = true;
                settleOrPass();
            }
            default -> {
            }
        }
    }

    /** Ends the game once both have finished; otherwise the turn simply passes. */
    private void settleOrPass() {
        if (!youStood || !theyStood) {
            return;
        }
        int you = total(yours);
        int them = total(theirs);
        boolean youBust = you > 21;
        boolean theyBust = them > 21;
        if (youBust && theyBust) {
            result = Result.DRAW;
        } else if (youBust) {
            result = Result.THEY_WON;
        } else if (theyBust) {
            result = Result.YOU_WON;
        } else if (you == them) {
            result = Result.DRAW;
        } else {
            result = you > them ? Result.YOU_WON : Result.THEY_WON;
        }
    }

    @Override
    protected void restart(boolean youFirst, long seed) {
        deck.clear();
        yours.clear();
        theirs.clear();
        for (int i = 0; i < 52; i++) {
            deck.add(i % RANKS.length);
        }
        // Seeded, so the other client shuffles into exactly the same order.
        Collections.shuffle(deck, new Random(seed));
        next = 0;
        youStood = false;
        theyStood = false;
        result = Result.PLAYING;
        yourTurn = youFirst;

        // Dealt the way a table deals: one each, then one each. Who gets the first card follows who
        // moves first, so both sides deal identically from the same deck.
        if (youFirst) {
            yours.add(draw());
            theirs.add(draw());
            yours.add(draw());
            theirs.add(draw());
        } else {
            theirs.add(draw());
            yours.add(draw());
            theirs.add(draw());
            yours.add(draw());
        }
    }

    private int draw() {
        if (next >= deck.size()) {
            // Fifty-two cards cannot run out in a two-hand game, but a deck that wrapped silently
            // would desynchronise both sides rather than fail here.
            next = 0;
        }
        return deck.get(next++);
    }

    private static List<String> names(List<Integer> cards) {
        List<String> out = new ArrayList<>(cards.size());
        for (int card : cards) {
            out.add(RANKS[card]);
        }
        return out;
    }

    /**
     * The hand's value, with aces counted as eleven and demoted to one while that busts.
     *
     * <p>Demoting one at a time rather than choosing up front is what makes A-A-9 come out as 21
     * instead of 12 or 31.
     */
    private static int total(List<Integer> cards) {
        int sum = 0;
        int aces = 0;
        for (int card : cards) {
            if (card == 0) {
                aces++;
                sum += 11;
            } else if (card >= 9) {
                sum += 10;
            } else {
                sum += card + 1;
            }
        }
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return sum;
    }
}
