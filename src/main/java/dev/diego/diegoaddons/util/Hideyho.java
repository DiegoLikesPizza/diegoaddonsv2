package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.HideyhoFinderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Haunted biome's hide-and-seek, turned from a search into a route.
 *
 * <p>Hideyho is the one critter that is a puzzle rather than a hunt: agree to play and it teleports
 * somewhere in the biome, blinds you for a second, and pays three shards if you find it inside
 * thirty seconds, two inside a minute, one after that. So the reward is a <b>timer</b>, and what
 * costs you the timer is not knowing where to look.
 *
 * <p>It hides in one of eleven documented spots. That is few enough to visit exhaustively and far
 * too many to remember, which is exactly the shape a waypoint list fits: every spot is drawn the
 * moment it agrees to hide, and each one is struck off as you get near it, so what is left on screen
 * is always "still to check" rather than "all of them".
 *
 * <p><b>The chat lines are the trigger and they are quoted from the wiki.</b> Matched loosely - a
 * distinctive fragment rather than the whole sentence - because the full line carries a player name,
 * a time and a shard count, and matching all of that exactly is how a state machine gets stuck.
 */
public final class Hideyho {
    /**
     * The eleven hiding spots, x/y/z flattened.
     *
     * <p>Wiki coordinates, which are block centres (the .5s) - kept as given rather than rounded, so
     * a marker sits on the spot rather than in the corner of the block containing it.
     */
    private static final double[] SPOTS = {
            -3.5, 66, -64,
            27.5, 69, -53,
            19.5, 68, -24,
            -20.5, 77, -52,
            -19.5, 69, -80,
            -3.5, 69, -79.5,
            -12.5, 77, -57,
            -16.5, 69, -59,
            0.5, 69, -56,
            13.5, 77, -50,
            13.5, 77, -88.5,
    };

    /** Where it stands before you talk to it, for the "where do I start" question. */
    private static final double[] STARTS = {
            -12.5, 77, -56.5,
            18.5, 77, -62.5,
            13.5, 69, -87.5,
            -27.5, 70, -79.5,
            37.5, 68, -13.5,
            -20.5, 77, -77.5,
    };

    /** It said it was hiding, and we have not found it since. */
    private static boolean hiding;
    /** Spot indices already walked past this round. */
    private static final Set<Integer> checked = new HashSet<>();
    private static long startedAt;

    private Hideyho() {
    }

    public static boolean hiding() {
        return hiding;
    }

    /** How long this round has been running, in milliseconds. */
    public static long elapsed() {
        return hiding ? System.currentTimeMillis() - startedAt : 0;
    }

    /** How many spots are left to check. */
    public static int remaining() {
        return SPOTS.length / 3 - checked.size();
    }

    /**
     * Watches for the three lines that move the state on.
     *
     * <p>"No peeking" starts the round, "you found me" ends it, and the offer to play is only worth
     * noticing so the module can say the game is available at all.
     */
    public static void onMessage(String plain) {
        HideyhoFinderModule m = HideyhoFinderModule.INSTANCE;
        if (m == null || !m.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!Safari.onSafari(mc)) {
            return;
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        if (!lower.contains("hideyho")) {
            // Every line this cares about is spoken by the mob, so its name is in all of them. This
            // is the cheap check that keeps the three below off the hot path of every chat message.
            return;
        }
        if (lower.contains("no peeking") || lower.contains("come find me")) {
            begin(mc, m);
        } else if (lower.contains("you found me")) {
            // Both "Hehe, you found me!" (the first meeting) and "Aah! You found me!" (the end of a
            // round) end a search - the first because there was not one running, the second because
            // it is over. One check covers both and neither can leave the state stuck on.
            end(mc, m);
        }
    }

    private static void begin(Minecraft mc, HideyhoFinderModule m) {
        hiding = true;
        startedAt = System.currentTimeMillis();
        checked.clear();
        if (m.announce() && mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(
                    "§b[DiegoAddons] §dHideyho is hiding §7- " + (SPOTS.length / 3)
                            + " spots marked. Three shards if you find it inside 30s."));
        }
        if (m.sound() && mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.4f);
        }
    }

    private static void end(Minecraft mc, HideyhoFinderModule m) {
        if (!hiding) {
            return;
        }
        long took = elapsed();
        hiding = false;
        checked.clear();
        if (m.announce() && mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(
                    "§b[DiegoAddons] §aFound Hideyho §7in " + (took / 1000) + "s."));
        }
    }

    /** Draws whatever is still worth walking to. Called once per client tick. */
    public static void tick(Minecraft mc, HideyhoFinderModule m) {
        if (mc.player == null || mc.level == null || !Safari.onSafari(mc)) {
            hiding = false;
            checked.clear();
            return;
        }
        Vec3 me = mc.player.position();
        if (hiding) {
            drawSpots(mc, m, me, SPOTS, true);
            return;
        }
        // Not playing: the start positions are the useful marks, and only if asked for - this is
        // the "where is it at all" question rather than the "where did it go" one.
        if (m.showStarts()) {
            drawSpots(mc, m, me, STARTS, false);
        }
    }

    /**
     * @param strike whether walking near a spot should cross it off - true only while it is hiding,
     *               since a start position you have walked past is still where it stands
     */
    private static void drawSpots(Minecraft mc, HideyhoFinderModule m, Vec3 me,
                                 double[] spots, boolean strike) {
        double near = m.strikeRadius();
        for (int i = 0; i < spots.length; i += 3) {
            int index = i / 3;
            if (strike && checked.contains(index)) {
                continue;
            }
            Vec3 at = new Vec3(spots[i], spots[i + 1], spots[i + 2]);
            double dist = me.distanceTo(at);
            if (strike && dist < near) {
                // Close enough to have seen it. Struck off rather than kept, because what is left on
                // screen has to mean "still to check" or the list is no better than a map.
                checked.add(index);
                continue;
            }
            if (dist > m.range()) {
                continue;
            }
            WorldRender.thickBox(
                    new net.minecraft.world.phys.AABB(at.x - 0.5, at.y, at.z - 0.5,
                            at.x + 0.5, at.y + 1.8, at.z + 0.5),
                    m.color(), 0.05, true);
            if (m.beams()) {
                WorldRender.path(List.of(at, at.add(0, 16, 0)), m.color(), 0.15);
            }
            if (m.labels()) {
                WorldRender.text(
                        (strike ? "Hideyho? " : "Hideyho ") + "§7(" + (int) dist + "m)",
                        at.add(0, 2.2, 0), 1.0f);
            }
        }
    }

    /** Everything this knows is about one round on one island. */
    public static void reset() {
        hiding = false;
        checked.clear();
    }

    /** The hiding spots, for anything that wants to list them. */
    public static List<Vec3> spots() {
        List<Vec3> out = new ArrayList<>(SPOTS.length / 3);
        for (int i = 0; i < SPOTS.length; i += 3) {
            out.add(new Vec3(SPOTS[i], SPOTS[i + 1], SPOTS[i + 2]));
        }
        return out;
    }
}
