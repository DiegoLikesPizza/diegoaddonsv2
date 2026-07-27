package dev.diego.diegoaddons.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ice Fill: draws the path to walk on each of the three floors.
 *
 * <p>Each floor is one of several fixed patterns. Which one is in play is told apart by a pair of
 * probe positions per candidate - one that must be air and one that must not - so the floor is
 * identified by its shape rather than guessed.
 *
 * <p>Two sets of paths exist: the straightforward one and a shorter route. Which is used is a
 * setting, because the short route is harder to walk and only worth it if you know the puzzle.
 */
public final class IceFillSolver {
    private static final double LINE = 0.09;
    private static final int COLOR = 0xC000FFFF;

    /** identifier[floor][candidate] = the two probe positions; easy/hard[floor][candidate] = path. */
    private static List<List<List<int[]>>> identifiers;
    private static List<List<List<int[]>>> easy;
    private static List<List<List<int[]>>> hard;

    /** How many ticks to wait before re-attempting a scan that turned up nothing. */
    private static final int RETRY_TICKS = 10;

    private static final List<Vec3> PATH = new ArrayList<>();
    private static String lastRoom;
    private static boolean scanned;
    private static int retryIn;

    private IceFillSolver() {
    }

    public static void reset() {
        PATH.clear();
        lastRoom = null;
        scanned = false;
        retryIn = 0;
    }

    /** Called every client tick while the solver is on. */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.iceFill() || mc.level == null) {
            return;
        }
        String room = DungeonRooms.currentRoomName();
        if (!"Ice Fill".equals(room)) {
            if (lastRoom != null) {
                reset();
            }
            return;
        }
        if (!room.equals(lastRoom)) {
            lastRoom = room;
            scanned = false;
            retryIn = 0;
            PATH.clear();
        }
        // Keep trying (throttled) until a path is actually found: the floors' blocks may not be
        // readable the instant the room resolves, and a single early miss must not give up for good.
        if (!scanned) {
            if (retryIn > 0) {
                retryIn--;
            } else {
                scan(mc, mod.iceFillShortRoute());
                if (!scanned) {
                    retryIn = RETRY_TICKS;
                }
            }
        }
        if (!PATH.isEmpty()) {
            WorldRender.lines(PATH, COLOR);
        }
    }

    /** Works out which pattern each floor is using and stitches their paths together. */
    private static void scan(Minecraft mc, boolean shortRoute) {
        load();
        if (identifiers == null || identifiers.isEmpty()) {
            scanned = true;
            return;
        }
        List<List<List<int[]>>> patterns = shortRoute ? hard : easy;
        PATH.clear();

        for (int floor = 0; floor < identifiers.size(); floor++) {
            List<List<int[]>> candidates = identifiers.get(floor);
            for (int i = 0; i < candidates.size(); i++) {
                List<int[]> probe = candidates.get(i);
                if (probe.size() < 2) {
                    continue;
                }
                BlockPos mustBeAir = DungeonRooms.toWorld(pos(probe.get(0)));
                BlockPos mustBeSolid = DungeonRooms.toWorld(pos(probe.get(1)));
                if (mustBeAir == null || mustBeSolid == null) {
                    return;   // rotation not known yet; try again next tick
                }
                if (mc.level.getBlockState(mustBeAir).isAir()
                        && !mc.level.getBlockState(mustBeSolid).isAir()) {
                    for (int[] p : patterns.get(floor).get(i)) {
                        BlockPos w = DungeonRooms.toWorld(pos(p));
                        if (w != null) {
                            // Slightly above the floor so the line is not buried in the ice.
                            PATH.add(new Vec3(w.getX() + 0.5, w.getY() + 0.1, w.getZ() + 0.5));
                        }
                    }
                    break;
                }
            }
        }
        // Only consider the room solved once a path was built; otherwise the caller retries.
        if (!PATH.isEmpty()) {
            scanned = true;
        }
    }

    private static BlockPos pos(int[] xyz) {
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    private static void load() {
        if (identifiers != null) {
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "puzzles/ice_fill.json");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open();
                     InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    identifiers = read(root.getAsJsonArray("identifier"));
                    easy = read(root.getAsJsonArray("easy"));
                    hard = read(root.getAsJsonArray("hard"));
                }
            }
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Could not load ice fill data: {}", e.toString());
        }
        if (identifiers == null) {
            identifiers = new ArrayList<>();
            easy = new ArrayList<>();
            hard = new ArrayList<>();
        }
    }

    /** floors -> candidates -> positions, each position an {x, y, z} object. */
    private static List<List<List<int[]>>> read(JsonArray floors) {
        List<List<List<int[]>>> out = new ArrayList<>();
        for (JsonElement floor : floors) {
            List<List<int[]>> cands = new ArrayList<>();
            for (JsonElement cand : floor.getAsJsonArray()) {
                List<int[]> positions = new ArrayList<>();
                for (JsonElement p : cand.getAsJsonArray()) {
                    JsonObject o = p.getAsJsonObject();
                    positions.add(new int[]{o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()});
                }
                cands.add(positions);
            }
            out.add(cands);
        }
        return out;
    }
}
