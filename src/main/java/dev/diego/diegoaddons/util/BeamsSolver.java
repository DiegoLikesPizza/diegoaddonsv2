package dev.diego.diegoaddons.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Creeper Beams: marks which sea lanterns belong together.
 *
 * <p>The room always uses the same set of possible lantern pairs, recorded relative to the room, so
 * the work is turning them to the room's rotation and keeping the pairs whose <b>both</b> ends are
 * actually lanterns in this instance of the puzzle - the rest of the recorded pairs are not part of
 * it. Each surviving pair gets its own colour, which is the whole point: the puzzle is about knowing
 * which lantern connects to which.
 *
 * <p>The pairs are rechecked as the room changes, so a lantern that has already been shot drops out
 * instead of staying marked.
 */
public final class BeamsSolver {
    /** Distinct colours so two pairs are never confused for one another. */
    private static final int[] COLORS = {
            0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55,
            0xFFFF55FF, 0xFF55FFFF, 0xFFFFAA00, 0xFFAA00FF,
    };
    private static final double EDGE = 0.06;
    /** How often to recheck which lanterns are still standing, in ticks. */
    private static final int RECHECK_TICKS = 10;

    private static List<int[]> recorded;
    private static final List<Pair> ACTIVE = new ArrayList<>();
    private static int recheckIn;
    private static String lastRoom;

    private record Pair(BlockPos a, BlockPos b, int color) {
    }

    private BeamsSolver() {
    }

    public static void reset() {
        ACTIVE.clear();
        recheckIn = 0;
        lastRoom = null;
    }

    /** Called every client tick while the solver is on. */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.beams() || mc.level == null) {
            return;
        }
        String room = DungeonRooms.currentRoomName();
        if (!"Creeper Beams".equals(room)) {
            if (lastRoom != null) {
                reset();
            }
            return;
        }
        if (!room.equals(lastRoom)) {
            lastRoom = room;
            recheckIn = 0;
        }
        if (--recheckIn <= 0) {
            recheckIn = RECHECK_TICKS;
            rebuild(mc);
        }
        for (Pair p : ACTIVE) {
            WorldRender.thickBox(new AABB(p.a()), p.color(), EDGE, true);
            WorldRender.thickBox(new AABB(p.b()), p.color(), EDGE, true);
        }
    }

    /**
     * Keeps the recorded pairs whose both ends are lanterns right now. Each pair's colour is tied to
     * its <b>fixed position in the recorded list</b>, not to a running count of survivors - so
     * completing a pair (its lanterns disappear) drops it out without recolouring any of the others.
     */
    private static void rebuild(Minecraft mc) {
        List<int[]> ps = pairs();
        ACTIVE.clear();
        for (int idx = 0; idx < ps.size(); idx++) {
            int[] p = ps.get(idx);
            BlockPos a = DungeonRooms.toWorld(new BlockPos(p[0], p[1], p[2]));
            BlockPos b = DungeonRooms.toWorld(new BlockPos(p[3], p[4], p[5]));
            if (a == null || b == null) {
                return;   // room rotation not known yet; nothing can be placed
            }
            if (mc.level.getBlockState(a).getBlock() == Blocks.SEA_LANTERN
                    && mc.level.getBlockState(b).getBlock() == Blocks.SEA_LANTERN) {
                ACTIVE.add(new Pair(a, b, COLORS[idx % COLORS.length]));
            }
        }
    }

    /** The recorded lantern pairs, loaded once. */
    private static List<int[]> pairs() {
        if (recorded != null) {
            return recorded;
        }
        recorded = new ArrayList<>();
        Identifier id = Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "puzzles/creeper_beams.json");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open();
                     InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonArray root = JsonParser.parseReader(r).getAsJsonArray();
                    for (JsonElement e : root) {
                        JsonArray a = e.getAsJsonArray();
                        if (a.size() >= 6) {
                            recorded.add(new int[]{
                                    a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                                    a.get(3).getAsInt(), a.get(4).getAsInt(), a.get(5).getAsInt()});
                        }
                    }
                }
            }
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Could not load beam pairs: {}", e.toString());
        }
        return recorded;
    }
}
