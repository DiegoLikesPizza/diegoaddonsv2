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
 * <p>Currently covers the two puzzles that are decided entirely in chat. The rest (Blaze, Creeper
 * Beams, Water Board, Boulder, Ice Fill, Teleport Maze) all need either boxes drawn in the world or
 * knowing which room you are standing in, neither of which this mod does yet - see
 * {@link PuzzleSolvers}.
 */
public class PuzzleSolversModule extends Module {
    public static PuzzleSolversModule INSTANCE;

    private final BooleanSetting quiz =
            new BooleanSetting(this, "quiz", "Quiz (Oruo)", true);
    private final BooleanSetting weirdos =
            new BooleanSetting(this, "weirdos", "Three Weirdos", true);
    /** Off by default: it speaks in party chat, which is not something to switch on silently. */
    private final BooleanSetting announceToParty =
            new BooleanSetting(this, "announce", "Announce in party chat", false);

    public PuzzleSolversModule() {
        super("puzzlesolvers", Category.MISC, "Puzzle Solvers",
                "Solve dungeon puzzles that can be answered from chat.");
        settings.add(quiz);
        settings.add(weirdos);
        settings.add(announceToParty);
        INSTANCE = this;
    }

    public boolean quiz() {
        return quiz.get();
    }

    public boolean weirdos() {
        return weirdos.get();
    }

    public boolean announceToParty() {
        return announceToParty.get();
    }
}
