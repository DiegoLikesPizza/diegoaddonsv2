package dev.diego.diegoaddons.util;

import java.util.List;
import java.util.Random;

/**
 * Battleships on a ten by ten grid, five ships a side.
 *
 * <p>Your fleet is placed on your machine and <b>never sent</b>, which is the only way a hidden
 * board works without a server to hold it. So a shot is a question and the answer comes back from
 * the other client: {@code sh|43} goes out, {@code rs|43|2} comes back - miss, hit, or sunk.
 *
 * <p>That means the answer is trusted. There is no way around it in a peer-to-peer game: the only
 * client that knows whether a square holds a ship is the one hiding it. A determined opponent could
 * lie, and this is a game for people who are not trying to. Everything that <i>can</i> be checked
 * locally still is - shooting out of turn, shooting the same square twice, answering a shot that was
 * never fired - so only the honesty of a hit is taken on faith.
 *
 * <p>Ships are placed for you rather than dragged into place: a placement UI is a whole feature of
 * its own, and "roll again until you like it" gets to the same game far sooner. The classic rule
 * applies - a hit keeps the turn.
 */
public final class Battleship extends MiniGame {

    public static final String ID = "bs";

    public static final int SIZE = 10;
    /** Carrier, battleship, two cruisers, destroyer - seventeen squares in all. */
    private static final int[] FLEET = {5, 4, 3, 3, 2};
    private static final int TOTAL_CELLS = 17;

    // Your own grid.
    public static final int WATER = 0;
    public static final int SHIP = 1;
    public static final int MISSED = 2;
    public static final int STRUCK = 3;

    // What you know of theirs.
    public static final int UNKNOWN = 0;
    public static final int MISS = 1;
    public static final int HIT = 2;

    private final int[] mine = new int[SIZE * SIZE];
    private final int[] theirs = new int[SIZE * SIZE];

    private boolean placing = true;
    private boolean weReady;
    private boolean theyReady;
    private boolean firstMove;

    private int hitsTaken;
    private int hitsMade;

    private final Random rng = new Random();

    Battleship(boolean youFirst) {
        firstMove = youFirst;
        scatter();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Battleships";
    }

    @Override
    public int boardWidth() {
        return SIZE * 30 * 2 + 40;
    }

    @Override
    public int boardHeight() {
        return SIZE * 30 + 30;
    }

    @Override
    public String hint() {
        if (over()) {
            return "";
        }
        if (placing) {
            return "Your fleet - roll again until you like it";
        }
        if (!theyReady) {
            return "Waiting for the other side to be ready";
        }
        return yourTurn() ? "Pick a square on the right" : "They are shooting";
    }

    @Override
    public List<String> actions() {
        return placing ? List.of("Roll again", "Ready") : List.of();
    }

    @Override
    public void act(int index) {
        if (!placing) {
            return;
        }
        if (index == 0) {
            scatter();
        } else if (index == 1) {
            placing = false;
            weReady = true;
            send("rdy");
            begin();
        }
    }

    public int[] mine() {
        return mine;
    }

    public int[] theirs() {
        return theirs;
    }

    public boolean placing() {
        return placing;
    }

    public boolean waiting() {
        return weReady && !theyReady;
    }

    public int hitsMade() {
        return hitsMade;
    }

    public int hitsTaken() {
        return hitsTaken;
    }

    /** Your shot at one of their squares. Returns true when it was legal, and therefore sent. */
    public boolean shoot(int cell) {
        if (!yourTurn() || placing || !theyReady
                || cell < 0 || cell >= theirs.length || theirs[cell] != UNKNOWN) {
            return false;
        }
        // The turn is held until the answer comes back, so a second click cannot fire twice at the
        // same square before the first is known.
        yourTurn = false;
        send("sh", Integer.toString(cell));
        return true;
    }

    @Override
    public void receive(String[] parts) {
        if (rematchVerb(parts)) {
            return;
        }
        switch (parts[0]) {
            case "rdy" -> {
                theyReady = true;
                begin();
            }
            case "sh" -> answer(parts);
            case "rs" -> record(parts);
            default -> {
            }
        }
    }

    /** They shot at us: work out what it hit and say so. */
    private void answer(String[] parts) {
        if (over() || parts.length < 2 || placing) {
            return;
        }
        int cell = parse(parts[1]);
        if (cell < 0) {
            return;
        }
        int state = mine[cell];
        if (state == MISSED || state == STRUCK) {
            // Already answered once. Answering again would give them a second free shot.
            return;
        }
        boolean hit = state == SHIP;
        mine[cell] = hit ? STRUCK : MISSED;
        if (hit) {
            hitsTaken++;
        }
        send("rs", Integer.toString(cell), hit ? "1" : "0");
        if (hitsTaken >= TOTAL_CELLS) {
            result = Result.THEY_WON;
            return;
        }
        // A hit keeps their turn; a miss hands it to us.
        yourTurn = !hit;
    }

    /** The answer to our shot. */
    private void record(String[] parts) {
        if (over() || parts.length < 3) {
            return;
        }
        int cell = parse(parts[1]);
        if (cell < 0 || theirs[cell] != UNKNOWN) {
            return;
        }
        boolean hit = "1".equals(parts[2]);
        theirs[cell] = hit ? HIT : MISS;
        if (hit) {
            hitsMade++;
            if (hitsMade >= TOTAL_CELLS) {
                result = Result.YOU_WON;
                return;
            }
        }
        yourTurn = hit;
    }

    private static int parse(String raw) {
        try {
            int cell = Integer.parseInt(raw.trim());
            return cell >= 0 && cell < SIZE * SIZE ? cell : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Starts the shooting once both fleets are down. */
    private void begin() {
        if (weReady && theyReady) {
            yourTurn = firstMove;
        }
    }

    @Override
    protected void restart(boolean youFirst, long seed) {
        java.util.Arrays.fill(theirs, UNKNOWN);
        hitsMade = 0;
        hitsTaken = 0;
        placing = true;
        weReady = false;
        theyReady = false;
        firstMove = youFirst;
        result = Result.PLAYING;
        yourTurn = false;
        scatter();
    }

    /**
     * Drops the fleet somewhere legal.
     *
     * <p>Rejection sampling: try a random spot, keep it if the ship fits and touches nothing already
     * placed. With seventeen squares on a hundred it succeeds almost immediately, and the attempt
     * cap only exists so a pathological run cannot spin forever.
     */
    private void scatter() {
        java.util.Arrays.fill(mine, WATER);
        for (int length : FLEET) {
            for (int attempt = 0; attempt < 500; attempt++) {
                boolean horizontal = rng.nextBoolean();
                int x = rng.nextInt(horizontal ? SIZE - length + 1 : SIZE);
                int y = rng.nextInt(horizontal ? SIZE : SIZE - length + 1);
                if (fits(x, y, length, horizontal)) {
                    for (int i = 0; i < length; i++) {
                        mine[(y + (horizontal ? 0 : i)) * SIZE + x + (horizontal ? i : 0)] = SHIP;
                    }
                    break;
                }
            }
        }
    }

    /** Whether a ship fits here with a clear square all around it, as the rules have it. */
    private boolean fits(int x, int y, int length, boolean horizontal) {
        for (int i = 0; i < length; i++) {
            int cx = x + (horizontal ? i : 0);
            int cy = y + (horizontal ? 0 : i);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE && mine[ny * SIZE + nx] == SHIP) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
