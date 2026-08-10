package dev.diego.diegoaddons.util;

/**
 * Noughts and crosses: three in a row on a 3x3 board.
 *
 * <p>Every move is validated at both ends - your turn, empty square, game not over - and dropped
 * otherwise, which is what keeps the two clients' boards identical when something goes wrong at one
 * of them. See {@link MiniGame} for why that matters here.
 */
public final class TicTacToe extends MiniGame {

    public static final String ID = "ttt";

    public static final int EMPTY = 0;
    public static final int YOU = 1;
    public static final int THEM = 2;

    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6},
    };

    private final int[] board = new int[9];
    private int[] winningLine;

    TicTacToe(boolean youFirst) {
        this.yourTurn = youFirst;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Tic Tac Toe";
    }

    @Override
    public int boardWidth() {
        return 400;
    }

    @Override
    public int boardHeight() {
        return 400;
    }

    public int[] board() {
        return board;
    }

    public int[] winningLine() {
        return winningLine;
    }

    /** Your click on a square. Returns true when it was legal, and therefore sent. */
    public boolean play(int cell) {
        if (!yourTurn() || cell < 0 || cell > 8 || board[cell] != EMPTY) {
            return false;
        }
        board[cell] = YOU;
        yourTurn = false;
        judge();
        send("mv", Integer.toString(cell));
        return true;
    }

    @Override
    public void receive(String[] parts) {
        if (rematchVerb(parts)) {
            return;
        }
        if (!"mv".equals(parts[0]) || parts.length < 2) {
            return;
        }
        int cell;
        try {
            cell = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (over() || yourTurn || cell < 0 || cell > 8 || board[cell] != EMPTY) {
            return;
        }
        board[cell] = THEM;
        yourTurn = true;
        judge();
    }

    @Override
    protected void restart(boolean youFirst, long seed) {
        java.util.Arrays.fill(board, EMPTY);
        winningLine = null;
        result = Result.PLAYING;
        yourTurn = youFirst;
    }

    private void judge() {
        for (int[] line : LINES) {
            int first = board[line[0]];
            if (first != EMPTY && board[line[1]] == first && board[line[2]] == first) {
                winningLine = line;
                result = first == YOU ? Result.YOU_WON : Result.THEY_WON;
                return;
            }
        }
        for (int cell : board) {
            if (cell == EMPTY) {
                return;
            }
        }
        result = Result.DRAW;
    }
}
