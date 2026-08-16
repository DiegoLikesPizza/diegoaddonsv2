package dev.diego.diegoaddons.gui;

import dev.diego.configlib.render.Fonts;
import dev.diego.configlib.render.Theme;
import dev.diego.configlib.render.Ui;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.util.Battleship;
import dev.diego.diegoaddons.util.Blackjack;
import dev.diego.diegoaddons.util.ConnectFour;
import dev.diego.diegoaddons.util.MiniGame;
import dev.diego.diegoaddons.util.Minigames;
import dev.diego.diegoaddons.util.TicTacToe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The board for whichever game is running, drawn the way the rest of the mod is drawn.
 *
 * <p>The screen is a <b>view of the game, not the game</b>: everything it shows is read from the
 * {@link MiniGame} every frame, and a click asks that to make a move rather than moving anything
 * itself. So closing the window mid-game loses nothing ({@code /da game} brings it back), and a move
 * arriving from the other side simply appears.
 *
 * <p>The frame - panel, title, status, buttons - is shared; only the board area differs per game,
 * and each game says how much room it needs. Marks are drawn from primitives rather than glyphs: a
 * cross of two bars and a ring read at any size, and no font is guaranteed to have a shape that
 * looks deliberate at sixty units square.
 */
public final class GameScreen extends Screen {

    private static final int PAD = 26;
    private static final int HEADER_H = 132;
    private static final int BUTTON_H = 40;
    private static final int BUTTON_GAP = 10;

    public GameScreen() {
        super(Component.literal("DiegoAddons"));
    }

    /** Opens the board. Called when a game starts, and by {@code /da game}. */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new GameScreen()));
    }

    // ---------------------------------------------------------------- geometry

    private int panelW() {
        MiniGame game = Minigames.game();
        return Math.max(460, (game == null ? 400 : game.boardWidth()) + PAD * 2);
    }

    private int panelH() {
        MiniGame game = Minigames.game();
        return HEADER_H + (game == null ? 200 : game.boardHeight()) + BUTTON_H + PAD * 2;
    }

    private int panelX() {
        return (Ui.u(width) - panelW()) / 2;
    }

    private int panelY() {
        return (Ui.u(height) - panelH()) / 2;
    }

    private int boardX() {
        MiniGame game = Minigames.game();
        return panelX() + (panelW() - (game == null ? 0 : game.boardWidth())) / 2;
    }

    private int boardY() {
        return panelY() + HEADER_H;
    }

    private int buttonY() {
        return panelY() + panelH() - PAD - BUTTON_H;
    }

    /**
     * How long the other side may say nothing before the nudge is offered.
     *
     * <p>Long enough that it is not offered to somebody who is simply thinking, short enough that a
     * game which has actually stopped does not have to be given up on.
     */
    private static final long NUDGE_AFTER_MS = 15_000L;

    /** Whether the game looks stuck: not your move, nothing said for a while, something to repeat. */
    private static boolean stuck(MiniGame game) {
        return !game.over() && !game.yourTurn() && game.canResend()
                && Minigames.silence() > NUDGE_AFTER_MS;
    }

    /** Every button on the row: the game's own, the nudge when it is warranted, then the two constants. */
    private List<String> buttons(MiniGame game) {
        List<String> out = new java.util.ArrayList<>(game.actions());
        if (stuck(game)) {
            out.add("Send again");
        }
        out.add(game.over() ? (game.rematchPending() ? "Accept" : "Again") : "Give up");
        out.add("Close");
        return out;
    }

    private int buttonW(int count) {
        return (panelW() - PAD * 2 - BUTTON_GAP * (count - 1)) / count;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        Theme t = DiegoAddonsV2Client.CONFIG.theme();
        MiniGame game = Minigames.game();
        int mx = Ui.u(mouseX);
        int my = Ui.u(mouseY);
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();

        Ui.beginHiRes(g);
        // No drop shadow: configlib dropped Ui.shadow when rounded shapes moved to the SDF shader,
        // and its own panels now sit on the surface + outline alone. Matching that keeps this
        // screen looking like the config GUI rather than the odd one out.
        Ui.roundRect(g, x, y, w, h, t.radius(), t.surface());
        Ui.roundOutline(g, x, y, w, h, t.radius(), 1, t.stroke());

        if (game == null) {
            Fonts.drawCentered(g, font, "No game running", x + w / 2, y + h / 2 - 10,
                    Fonts.UI_BODY, t.textFaint());
            Fonts.drawCentered(g, font, "/da play <name> [ttt|c4|bj|bs]", x + w / 2, y + h / 2 + 16,
                    Fonts.UI_SMALL, t.textFaint());
            Ui.endHiRes(g);
            return;
        }

        Fonts.drawCentered(g, font, game.title(), x + w / 2, y + 26, Fonts.UI_TITLE, t.text());
        Fonts.drawCentered(g, font, "against " + Minigames.opponent(), x + w / 2, y + 62,
                Fonts.UI_SMALL, t.textDim());
        status(g, t, game, x, y, w);

        switch (game) {
            case TicTacToe ttt -> ticTacToe(g, t, ttt, mx, my);
            case ConnectFour c4 -> connectFour(g, t, c4, mx, my);
            case Blackjack bj -> blackjack(g, t, bj);
            case Battleship bs -> battleship(g, t, bs, mx, my);
            default -> {
            }
        }

        buttonRow(g, t, game, mx, my);
        Ui.endHiRes(g);
    }

    /** The one line that says what is going on: whose turn, what to do, or how it ended. */
    private void status(GuiGraphicsExtractor g, Theme t, MiniGame game, int x, int y, int w) {
        String text;
        int colour;
        if (game.over()) {
            text = switch (game.result()) {
                case YOU_WON -> "You won";
                case THEY_WON -> Minigames.opponent() + " won";
                case DRAW -> "A draw";
                case YOU_RESIGNED -> "You gave up";
                case THEY_RESIGNED -> Minigames.opponent() + " gave up";
                case ABANDONED -> Minigames.opponent() + " stopped answering";
                default -> "";
            };
            colour = game.result() == MiniGame.Result.YOU_WON ? t.accent() : t.text();
        } else if (!game.hint().isEmpty()) {
            text = game.hint();
            colour = game.yourTurn() ? t.accent() : t.textDim();
        } else {
            text = game.yourTurn() ? "Your turn" : Minigames.opponent() + " to move";
            colour = game.yourTurn() ? t.accent() : t.textDim();
        }
        Fonts.drawCentered(g, font, text, x + w / 2, y + 92, Fonts.UI_LABEL, colour);

        if (game.rematchPending()) {
            Fonts.drawCentered(g, font, Minigames.opponent() + " wants another game", x + w / 2, y + 116,
                    Fonts.UI_SMALL, t.accent());
        } else if (game.rematchOffered()) {
            Fonts.drawCentered(g, font, "Rematch offered", x + w / 2, y + 116,
                    Fonts.UI_SMALL, t.textFaint());
        } else if (stuck(game)) {
            // Said plainly rather than left as a board that has simply gone quiet.
            Fonts.drawCentered(g, font, "Nothing for a while - a move may have gone missing",
                    x + w / 2, y + 116, Fonts.UI_SMALL, t.danger());
        }
    }

    // ---------------------------------------------------------------- the four boards

    private static final int TTT_CELL = 128;
    private static final int TTT_GAP = 8;

    private void ticTacToe(GuiGraphicsExtractor g, Theme t, TicTacToe game, int mx, int my) {
        int bx = boardX();
        int by = boardY();
        int[] cells = game.board();
        for (int i = 0; i < 9; i++) {
            int cx = bx + (i % 3) * (TTT_CELL + TTT_GAP);
            int cy = by + (i / 3) * (TTT_CELL + TTT_GAP);
            boolean playable = game.yourTurn() && cells[i] == TicTacToe.EMPTY;
            boolean over = playable && Ui.hovered(mx, my, cx, cy, TTT_CELL, TTT_CELL);
            boolean won = contains(game.winningLine(), i);

            Ui.roundRect(g, cx, cy, TTT_CELL, TTT_CELL, t.cardRadius(),
                    won ? t.accentSoft() : over ? t.cardHover() : t.card());
            if (won) {
                Ui.roundOutline(g, cx, cy, TTT_CELL, TTT_CELL, t.cardRadius(), 1, t.accent());
            }
            if (cells[i] == TicTacToe.YOU) {
                cross(g, cx, cy, TTT_CELL, t.accent());
            } else if (cells[i] == TicTacToe.THEM) {
                ring(g, cx, cy, TTT_CELL, t.text(), won ? t.accentSoft() : t.card());
            } else if (over) {
                // A hint of the mark you are about to place, so an empty square reads as yours to
                // take rather than merely lighter.
                cross(g, cx, cy, TTT_CELL, Ui.fade(t.accent(), 0.25f));
            }
        }
    }

    private static final int C4_CELL = 64;

    private void connectFour(GuiGraphicsExtractor g, Theme t, ConnectFour game, int mx, int my) {
        int bx = boardX() + 8;
        int by = boardY() + 8;
        int hoverColumn = -1;
        if (game.yourTurn()) {
            int c = (mx - bx) / C4_CELL;
            if (c >= 0 && c < ConnectFour.COLUMNS && my >= by - 8
                    && my < by + ConnectFour.ROWS * C4_CELL && game.landing(c) >= 0) {
                hoverColumn = c;
            }
        }

        Ui.roundRect(g, boardX(), boardY(), game.boardWidth(), game.boardHeight(),
                t.cardRadius(), t.surfaceAlt());
        int[] cells = game.board();
        for (int i = 0; i < cells.length; i++) {
            int column = i % ConnectFour.COLUMNS;
            int cx = bx + column * C4_CELL;
            int cy = by + (i / ConnectFour.COLUMNS) * C4_CELL;
            int radius = C4_CELL / 2 - 6;
            int centreX = cx + C4_CELL / 2;
            int centreY = cy + C4_CELL / 2;
            boolean won = contains(game.winningLine(), i);

            int colour = switch (cells[i]) {
                case ConnectFour.YOU -> t.accent();
                case ConnectFour.THEM -> t.danger();
                default -> column == hoverColumn ? t.cardHover() : t.card();
            };
            Ui.circle(g, centreX, centreY, radius, colour);
            if (won) {
                Ui.circle(g, centreX, centreY, radius, Ui.fade(0xFFFFFFFF, 0.35f));
            }
            // The square the disc would fall into, shown before you commit to the column.
            if (cells[i] == ConnectFour.EMPTY && column == hoverColumn
                    && i / ConnectFour.COLUMNS == game.landing(column)) {
                Ui.circle(g, centreX, centreY, radius - 4, Ui.fade(t.accent(), 0.35f));
            }
        }
    }

    private void blackjack(GuiGraphicsExtractor g, Theme t, Blackjack game) {
        int x = boardX();
        int y = boardY();
        int w = game.boardWidth();

        hand(g, t, "You", game.yourCards(), game.yourTotal(), game.youStood(), x, y, w,
                game.yourTurn() ? t.accent() : t.text());
        hand(g, t, Minigames.opponent(), game.theirCards(), game.theirTotal(), game.theyStood(),
                x, y + 150, w, t.text());
    }

    /** One hand: a row of cards, the total, and whether they are finished. */
    private void hand(GuiGraphicsExtractor g, Theme t, String who, List<String> cards, int total,
                      boolean stood, int x, int y, int w, int colour) {
        Fonts.draw(g, font, who, x, y, Fonts.UI_BODY, colour);
        String note = total > 21 ? "bust (" + total + ")" : String.valueOf(total);
        Fonts.drawRight(g, font, stood ? note + " - done" : note, x + w, y,
                Fonts.UI_BODY, total > 21 ? t.danger() : t.textDim());

        int cw = 58;
        int ch = 82;
        for (int i = 0; i < cards.size(); i++) {
            int cx = x + i * (cw + 8);
            Ui.roundRect(g, cx, y + 26, cw, ch, 8, t.card());
            Ui.roundOutline(g, cx, y + 26, cw, ch, 8, 1, t.stroke());
            Fonts.drawCentered(g, font, cards.get(i), cx + cw / 2,
                    Fonts.centerY(y + 26, ch, Fonts.LABEL_SZ), Fonts.UI_LABEL, t.text());
        }
    }

    private static final int BS_CELL = 30;

    private void battleship(GuiGraphicsExtractor g, Theme t, Battleship game, int mx, int my) {
        int x = boardX();
        int y = boardY() + 24;
        int side = Battleship.SIZE * BS_CELL;

        Fonts.draw(g, font, "Your fleet", x, y - 22, Fonts.UI_SMALL, t.textDim());
        Fonts.draw(g, font, Minigames.opponent(), x + side + 40, y - 22, Fonts.UI_SMALL, t.textDim());

        int[] mine = game.mine();
        for (int i = 0; i < mine.length; i++) {
            int cx = x + (i % Battleship.SIZE) * BS_CELL;
            int cy = y + (i / Battleship.SIZE) * BS_CELL;
            int colour = switch (mine[i]) {
                case Battleship.SHIP -> t.controlOff();
                case Battleship.MISSED -> t.surfaceAlt();
                case Battleship.STRUCK -> t.danger();
                default -> t.card();
            };
            Ui.rect(g, cx, cy, BS_CELL - 2, BS_CELL - 2, colour);
        }

        int ox = x + side + 40;
        boolean canShoot = game.yourTurn() && !game.placing();
        int[] theirs = game.theirs();
        for (int i = 0; i < theirs.length; i++) {
            int cx = ox + (i % Battleship.SIZE) * BS_CELL;
            int cy = y + (i / Battleship.SIZE) * BS_CELL;
            boolean over = canShoot && theirs[i] == Battleship.UNKNOWN
                    && Ui.hovered(mx, my, cx, cy, BS_CELL - 2, BS_CELL - 2);
            int colour = switch (theirs[i]) {
                case Battleship.MISS -> t.surfaceAlt();
                case Battleship.HIT -> t.accent();
                default -> over ? t.cardHover() : t.card();
            };
            Ui.rect(g, cx, cy, BS_CELL - 2, BS_CELL - 2, colour);
        }

        Fonts.draw(g, font, "Hits taken: " + game.hitsTaken() + "/17", x, y + side + 8,
                Fonts.UI_SMALL, t.textFaint());
        Fonts.drawRight(g, font, "Hits landed: " + game.hitsMade() + "/17",
                ox + side, y + side + 8, Fonts.UI_SMALL, t.textFaint());
    }

    // ---------------------------------------------------------------- shared pieces

    private static boolean contains(int[] line, int cell) {
        if (line == null) {
            return false;
        }
        for (int i : line) {
            if (i == cell) {
                return true;
            }
        }
        return false;
    }

    /** Two bars through the middle of a cell, drawn as short runs since every fill is axis-aligned. */
    private static void cross(GuiGraphicsExtractor g, int x, int y, int cell, int colour) {
        int inset = cell / 4;
        int thickness = Math.max(4, cell / 14);
        int size = cell - inset * 2;
        for (int i = 0; i < size; i++) {
            Ui.rect(g, x + inset + i, y + inset + i, thickness, 1, colour);
            Ui.rect(g, x + inset + i, y + cell - inset - i - 1, thickness, 1, colour);
        }
    }

    /** A ring: a disc with a hole punched in it, since these fills are opaque. */
    private static void ring(GuiGraphicsExtractor g, int x, int y, int cell, int colour, int hole) {
        int r = cell / 2 - cell / 5;
        int cx = x + cell / 2;
        int cy = y + cell / 2;
        Ui.circle(g, cx, cy, r, colour);
        Ui.circle(g, cx, cy, r - Math.max(4, cell / 14), hole);
    }

    private void buttonRow(GuiGraphicsExtractor g, Theme t, MiniGame game, int mx, int my) {
        List<String> labels = buttons(game);
        int w = buttonW(labels.size());
        int y = buttonY();
        for (int i = 0; i < labels.size(); i++) {
            int x = panelX() + PAD + i * (w + BUTTON_GAP);
            boolean hot = Ui.hovered(mx, my, x, y, w, BUTTON_H);
            boolean resign = !game.over() && i == labels.size() - 2;
            boolean primary = game.over() && i == labels.size() - 2;
            int bg = primary
                    ? (hot ? t.accent() : t.accentSoft())
                    : resign
                            ? (hot ? t.danger() : t.controlOff())
                            : (hot ? t.cardHover() : t.card());
            Ui.roundRect(g, x, y, w, BUTTON_H, t.cardRadius(), bg);
            Fonts.drawCentered(g, font, labels.get(i), x + w / 2,
                    Fonts.centerY(y, BUTTON_H, Fonts.BODY_SZ), Fonts.UI_BODY,
                    primary && hot ? t.accentText() : t.text());
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MiniGame game = Minigames.game();
        if (game == null) {
            return super.mouseClicked(event, doubleClick);
        }
        int mx = Ui.u(event.x());
        int my = Ui.u(event.y());

        List<String> labels = buttons(game);
        int bw = buttonW(labels.size());
        int by = buttonY();
        for (int i = 0; i < labels.size(); i++) {
            int bx = panelX() + PAD + i * (bw + BUTTON_GAP);
            if (!Ui.hovered(mx, my, bx, by, bw, BUTTON_H)) {
                continue;
            }
            if (stuck(game) && i == labels.size() - 3) {
                game.resend();
                return true;
            }
            if (i < labels.size() - 2) {
                game.act(i);
            } else if (i == labels.size() - 2) {
                if (game.over()) {
                    game.rematch();
                } else {
                    Minigames.resign();
                }
            } else {
                onClose();
            }
            return true;
        }

        switch (game) {
            case TicTacToe ttt -> clickTicTacToe(ttt, mx, my);
            case ConnectFour c4 -> clickConnectFour(c4, mx, my);
            case Battleship bs -> clickBattleship(bs, mx, my);
            default -> {
            }
        }
        return true;
    }

    private void clickTicTacToe(TicTacToe game, int mx, int my) {
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < 9; i++) {
            int cx = bx + (i % 3) * (TTT_CELL + TTT_GAP);
            int cy = by + (i / 3) * (TTT_CELL + TTT_GAP);
            if (Ui.hovered(mx, my, cx, cy, TTT_CELL, TTT_CELL)) {
                game.play(i);
                return;
            }
        }
    }

    private void clickConnectFour(ConnectFour game, int mx, int my) {
        int bx = boardX() + 8;
        int by = boardY();
        if (my < by || my > by + game.boardHeight()) {
            return;
        }
        // The whole column is the target, not the square: you drop a disc into a column and it
        // lands where it lands.
        game.drop((mx - bx) / C4_CELL);
    }

    private void clickBattleship(Battleship game, int mx, int my) {
        int x = boardX() + Battleship.SIZE * BS_CELL + 40;
        int y = boardY() + 24;
        int col = (mx - x) / BS_CELL;
        int row = (my - y) / BS_CELL;
        if (col < 0 || col >= Battleship.SIZE || row < 0 || row >= Battleship.SIZE) {
            return;
        }
        game.shoot(row * Battleship.SIZE + col);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            // Closing the window is not leaving the game - the board lives in Minigames, and
            // /da game brings it back. Giving up is a button, and deliberately only a button.
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
