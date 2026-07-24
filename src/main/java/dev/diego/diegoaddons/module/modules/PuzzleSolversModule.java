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
 * <p>Two are decided entirely in chat and answer there; the blaze puzzle reads the blazes themselves
 * and marks the next target in the world. The remaining puzzles (Creeper Beams, Water Board, Boulder,
 * Ice Fill, Teleport Maze) are solved from fixed coordinates inside a room, so they still need room
 * identification with rotation - see {@link PuzzleSolvers}.
 */
public class PuzzleSolversModule extends Module {
    public static PuzzleSolversModule INSTANCE;

    private final BooleanSetting quiz =
            new BooleanSetting(this, "quiz", "Quiz (Oruo)", true);
    private final BooleanSetting weirdos =
            new BooleanSetting(this, "weirdos", "Three Weirdos", true);
    private final BooleanSetting blaze =
            new BooleanSetting(this, "blaze", "Higher Or Lower (Blaze)", true);
    private final BooleanSetting blazeShowAll =
            new BooleanSetting(this, "blazeAll", "Blaze: show whole order", true);
    /**
     * Only consulted when the puzzle's own instruction text was not found. Off means "do not guess":
     * a wrong order is worse than no highlight, since it reads as confident and is not.
     */
    private final BooleanSetting blazeGuess =
            new BooleanSetting(this, "blazeGuess", "Blaze: guess order if unknown", false);
    private final BooleanSetting blazeGuessHighest =
            new BooleanSetting(this, "blazeHigh", "Blaze: guess highest first", true);
    /** Off by default: it speaks in party chat, which is not something to switch on silently. */
    private final BooleanSetting announceToParty =
            new BooleanSetting(this, "announce", "Announce in party chat", false);

    @Override
    public void onClientTick(net.minecraft.client.Minecraft mc) {
        dev.diego.diegoaddons.util.BlazeSolver.tick(mc);
    }

    public PuzzleSolversModule() {
        super("puzzlesolvers", Category.MISC, "Puzzle Solvers",
                "Solve dungeon puzzles that can be answered from chat.");
        settings.add(quiz);
        settings.add(weirdos);
        settings.add(blaze);
        settings.add(blazeShowAll);
        settings.add(blazeGuess);
        settings.add(blazeGuessHighest);
        settings.add(announceToParty);
        INSTANCE = this;
    }

    public boolean quiz() {
        return quiz.get();
    }

    public boolean weirdos() {
        return weirdos.get();
    }

    public boolean blaze() {
        return blaze.get();
    }

    public boolean blazeShowAll() {
        return blazeShowAll.get();
    }

    /** The fallback order, or null when guessing is switched off. */
    public Boolean blazeFallbackOrder() {
        return blazeGuess.get() ? blazeGuessHighest.get() : null;
    }

    public boolean announceToParty() {
        return announceToParty.get();
    }
}
