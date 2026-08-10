package dev.diego.diegoaddons.util;

/**
 * Four in a row on a seven by six grid: you pick a column, the disc falls to the lowest free row.
 *
 * <p>The move sent is the <b>column</b>, not the square, which is also what makes the two boards
 * agree: where a disc lands is worked out from the board rather than trusted, so a client with a
 * different idea of the board cannot place one in mid-air.
 */
public final class ConnectFour extends MiniGame {

    public static final String ID = "c4";

    public static final int COLUMNS = 7;
    public static final int ROWS = 6;

    public static final int EMPTY = 0;
    public static final int YOU = 1;
    public static final int THEM = 2;

    /** Row 0 is the top, so a disc falls towards the last row. */
    private final int[] board = new int[COLUMNS * ROWS];
    private int[] winningLine;
    private int lastDrop = -1;

    ConnectFour(boolean youFirst) {
        this.yourTurn = youFirst;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Connect Four";
    }

    @Override
    public int boardWidth() {
        return COLUMNS * 64 + 16;
    }

    @Override
    public int boardHeight() {
        return ROWS * 64 + 16;
    }

    public int[] board() {
        return board;
    }

    public int[] winningLine() {
        return winningLine;
    }

    /** The square the last disc landed on, so the screen can mark it. */
    public int lastDrop() {
        return lastDrop;
    }

    /** Your click on a column. Returns true when it was legal, and therefore sent. */
    public boolean drop(int column) {
        if (!yourTurn() || landing(column) < 0) {
            return false;
        }
        place(column, YOU);
        yourTurn = false;
        send("mv", Integer.toString(column));
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
        int column;
        try {
            column = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (over() || yourTurn || landing(column) < 0) {
            return;
        }
        place(column, THEM);
        yourTurn = true;
    }

    @Override
    protected void restart(boolean youFirst, long seed) {
        java.util.Arrays.fill(board, EMPTY);
        winningLine = null;
        lastDrop = -1;
        result = Result.PLAYING;
        yourTurn = youFirst;
    }

    /** The row a disc dropped into this column would land on, or -1 when the column is full. */
    public int landing(int column) {
        if (column < 0 || column >= COLUMNS) {
            return -1;
        }
        for (int row = ROWS - 1; row >= 0; row--) {
            if (board[row * COLUMNS + column] == EMPTY) {
                return row;
            }
        }
        return -1;
    }

    private void place(int column, int who) {
        int row = landing(column);
        lastDrop = row * COLUMNS + column;
        board[lastDrop] = who;
        judge(row, column, who);
    }

    /**
     * Looks for four in a row through the square just filled.
     *
     * <p>Only through that square, in the four directions - a disc cannot complete a line it is not
     * part of, so scanning the whole grid every move would be forty-two squares of the same answer.
     */
    private void judge(int row, int column, int who) {
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            int count = 1;
            int first = row * COLUMNS + column;
            int last = first;
            for (int step = 1; step <= 3; step++) {
                int r = row + d[0] * step;
                int c = column + d[1] * step;
                if (!same(r, c, who)) {
                    break;
                }
                count++;
                last = r * COLUMNS + c;
            }
            for (int step = 1; step <= 3; step++) {
                int r = row - d[0] * step;
                int c = column - d[1] * step;
                if (!same(r, c, who)) {
                    break;
                }
                count++;
                first = r * COLUMNS + c;
            }
            if (count >= 4) {
                winningLine = line(first, last, d, count);
                result = who == YOU ? Result.YOU_WON : Result.THEY_WON;
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

    private boolean same(int row, int column, int who) {
        return row >= 0 && row < ROWS && column >= 0 && column < COLUMNS
                && board[row * COLUMNS + column] == who;
    }

    /** The squares of the winning run, walked from its far end back along the direction. */
    private static int[] line(int first, int last, int[] direction, int count) {
        int[] cells = new int[Math.min(count, 4)];
        int row = first / COLUMNS;
        int column = first % COLUMNS;
        // `first` was found by walking against the direction, so walking with it returns the run.
        int dr = direction[0];
        int dc = direction[1];
        if (last < first) {
            dr = -dr;
            dc = -dc;
        }
        for (int i = 0; i < cells.length; i++) {
            cells[i] = (row + dr * i) * COLUMNS + (column + dc * i);
        }
        return cells;
    }
}
