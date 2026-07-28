package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.PuzzleSolvers;

/**
 * Dungeon puzzle solvers, each one its own toggle.
 *
 * <p>Only display: a solver tells you the answer, it never clicks or moves for you.
 *
 * <p>Quiz and Three Weirdos are decided entirely in chat and answer there. The rest read the room:
 * blaze from the mobs themselves, and Creeper Beams, Boulder, Ice Fill, Water Board and Teleport Maze
 * from positions recorded relative to a room and turned to its rotation.
 */
public class PuzzleSolversModule extends Module {
    public static PuzzleSolversModule INSTANCE;

    private final BooleanSetting quiz =
            new BooleanSetting(this, "quiz", "Quiz (Oruo)", true);
    private final BooleanSetting weirdos =
            new BooleanSetting(this, "weirdos", "Three Weirdos", true);
    private final BooleanSetting tictactoe =
            new BooleanSetting(this, "tictactoe", "Tic Tac Toe", true);
    private final BooleanSetting blaze =
            new BooleanSetting(this, "blaze", "Higher Or Lower (Blaze)", true);
    private final BooleanSetting blazeShowAll =
            new BooleanSetting(this, "blazeAll", "Blaze: show whole order", true);
    private final BooleanSetting beams =
            new BooleanSetting(this, "beams", "Creeper Beams", true);
    private final BooleanSetting boulder =
            new BooleanSetting(this, "boulder", "Boulder", true);
    private final BooleanSetting boulderShowAll =
            new BooleanSetting(this, "boulderAll", "Boulder: show all pushes", false);
    private final BooleanSetting iceFill =
            new BooleanSetting(this, "iceFill", "Ice Fill", true);
    private final BooleanSetting iceFillShort =
            new BooleanSetting(this, "iceFillShort", "Ice Fill: short route", false);
    private final BooleanSetting waterBoard =
            new BooleanSetting(this, "water", "Water Board", true);
    private final BooleanSetting waterShort =
            new BooleanSetting(this, "waterShort", "Water Board: short route", false);
    private final BooleanSetting tpMaze =
            new BooleanSetting(this, "tpMaze", "Teleport Maze", true);
    /** Off by default: it speaks in party chat, which is not something to switch on silently. */
    private final BooleanSetting announceToParty =
            new BooleanSetting(this, "announce", "Announce in party chat", false);

    @Override
    public void onClientTick(net.minecraft.client.Minecraft mc) {
        dev.diego.diegoaddons.util.PuzzleSolvers.tick(mc);
        dev.diego.diegoaddons.util.TicTacToeSolver.tick(mc);
        dev.diego.diegoaddons.util.BlazeSolver.tick(mc);
        dev.diego.diegoaddons.util.BeamsSolver.tick(mc);
        dev.diego.diegoaddons.util.BoulderSolver.tick(mc);
        dev.diego.diegoaddons.util.IceFillSolver.tick(mc);
        dev.diego.diegoaddons.util.WaterSolver.tick(mc);
        dev.diego.diegoaddons.util.TpMazeSolver.tick(mc);
    }

    public PuzzleSolversModule() {
        super("puzzlesolvers", Category.DUNGEONS, "Puzzle Solvers",
                "Solve dungeon puzzles that can be answered from chat.");
        settings.add(quiz);
        settings.add(weirdos);
        settings.add(tictactoe);
        settings.add(blaze);
        settings.add(blazeShowAll);
        settings.add(beams);
        settings.add(boulder);
        settings.add(boulderShowAll);
        settings.add(iceFill);
        settings.add(iceFillShort);
        settings.add(waterBoard);
        settings.add(waterShort);
        settings.add(tpMaze);
        settings.add(announceToParty);
        INSTANCE = this;
    }

    public boolean quiz() {
        return quiz.get();
    }

    public boolean weirdos() {
        return weirdos.get();
    }

    public boolean tictactoe() {
        return tictactoe.get();
    }

    public boolean blaze() {
        return blaze.get();
    }

    public boolean beams() {
        return beams.get();
    }

    public boolean boulder() {
        return boulder.get();
    }

    public boolean boulderShowAll() {
        return boulderShowAll.get();
    }

    public boolean iceFill() {
        return iceFill.get();
    }

    public boolean iceFillShortRoute() {
        return iceFillShort.get();
    }

    public boolean waterBoard() {
        return waterBoard.get();
    }

    public boolean waterShortRoute() {
        return waterShort.get();
    }

    public boolean tpMaze() {
        return tpMaze.get();
    }

    public boolean blazeShowAll() {
        return blazeShowAll.get();
    }

    public boolean announceToParty() {
        return announceToParty.get();
    }
}
