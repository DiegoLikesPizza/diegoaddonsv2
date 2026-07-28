package dev.diego.diegoaddons.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Water Board: works out which levers to pull, in what order, and when.
 *
 * <p>The room has a fixed set of levers and a variable puzzle. Two things pick the solution: which
 * of the five wool slots are extended, and which of four board patterns is built - told apart by
 * probing the blocks the patterns differ in.
 *
 * <p>The solution is a list of levers with times. Each lever's remaining times float above it as a
 * live countdown, measured against the server's own tick clock ({@link ServerTicks}) so it stays
 * right through lag, and the full order is also announced once in chat.
 */
public final class WaterSolver {
    private static final double LINE = 0.06;
    private static final int FIRST = 0xFF00FF00;
    private static final int SECOND = 0xFFFFFF00;
    private static final int LATER = 0x60FFFFFF;

    /** The levers, relative to the room. Order matters: it breaks ties between equal times. */
    private enum Lever {
        COAL("coal_block", 20, 61, 10),
        GOLD("gold_block", 20, 61, 15),
        QUARTZ("quartz_block", 20, 61, 20),
        DIAMOND("diamond_block", 10, 61, 20),
        EMERALD("emerald_block", 10, 61, 15),
        CLAY("hardened_clay", 10, 61, 10),
        WATER("water", 15, 60, 5);

        final String key;
        final BlockPos rel;
        /** How many of this lever's times have already been used, so they stop being shown. */
        int used;

        Lever(String key, int x, int y, int z) {
            this.key = key;
            this.rel = new BlockPos(x, y, z);
        }

        static Lever byKey(String k) {
            for (Lever l : values()) {
                if (l.key.equals(k)) {
                    return l;
                }
            }
            return null;
        }
    }

    /** The five wool slots; which are extended is half of what picks the solution. */
    private static final BlockPos[] WOOL = {
            new BlockPos(15, 56, 19), new BlockPos(15, 56, 18), new BlockPos(15, 56, 17),
            new BlockPos(15, 56, 16), new BlockPos(15, 56, 15),
    };

    private record Step(Lever lever, double time) {
    }

    private static JsonObject data;
    private static final List<Step> STEPS = new ArrayList<>();
    private static String lastRoom;
    private static boolean scanned;
    /** Server-tick the water gate was opened, or -1 while it is still shut. */
    private static long openedAt = -1;

    private WaterSolver() {
    }

    public static void reset() {
        STEPS.clear();
        lastRoom = null;
        scanned = false;
        openedAt = -1;
        for (Lever l : Lever.values()) {
            l.used = 0;
        }
    }

    /**
     * Records a lever being pulled: its next time is used up, and the water lever additionally
     * starts the clock every other time is measured against.
     */
    public static void onInteract(BlockPos clicked) {
        if (STEPS.isEmpty()) {
            return;
        }
        for (Lever l : Lever.values()) {
            BlockPos pos = DungeonRooms.toWorld(l.rel);
            if (pos != null && pos.equals(clicked)) {
                if (l == Lever.WATER && openedAt == -1) {
                    openedAt = ServerTicks.get();
                }
                l.used++;
                return;
            }
        }
    }

    /** Called every client tick while the solver is on. */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.waterBoard() || mc.level == null) {
            return;
        }
        String room = DungeonRooms.currentRoomName();
        if (!"Water Board".equals(room)) {
            if (lastRoom != null) {
                reset();
            }
            return;
        }
        if (!room.equals(lastRoom)) {
            lastRoom = room;
            scanned = false;
            STEPS.clear();
        }
        if (!scanned) {
            scan(mc, mod);
        }
        draw(mc);
    }

    /**
     * Draws each lever's remaining times as a countdown above it.
     *
     * <p>Before the water is opened a time of zero means "pull it now" and anything else is just how
     * long after the water it is due. Once the water is running the label counts down against the
     * clock, and turns into a prompt the moment it reaches zero - which is the whole point of the
     * puzzle, and what a box on its own cannot say.
     */
    private static void draw(Minecraft mc) {
        int shown = 0;
        for (Lever lever : Lever.values()) {
            BlockPos pos = DungeonRooms.toWorld(lever.rel);
            if (pos == null) {
                return;
            }
            List<Double> times = timesOf(lever);
            for (int index = lever.used; index < times.size(); index++) {
                double time = times.get(index);
                int dueTicks = (int) (time * 20);
                String text;
                boolean now;
                if (openedAt == -1) {
                    now = dueTicks == 0;
                    text = now ? "§a§lCLICK ME!" : "§e" + time + "s";
                } else {
                    long left = openedAt + dueTicks - ServerTicks.get();
                    now = left <= 0;
                    text = now ? "§a§lCLICK ME!"
                            : "§e" + String.format(Locale.ROOT, "%.1f", left / 20f) + "s";
                }
                WorldRender.text(text, new Vec3(
                        pos.getX() + 0.5,
                        pos.getY() + (index - lever.used) * 0.5 + 1.5,
                        pos.getZ() + 0.5), 1.0f);
                if (index == lever.used) {
                    WorldRender.thickBox(new AABB(pos), now ? FIRST : (shown == 0 ? SECOND : LATER), LINE, true);
                    shown++;
                }
            }
        }
    }

    /** The recorded times for a lever, in order. */
    private static List<Double> timesOf(Lever lever) {
        List<Double> out = new ArrayList<>();
        for (Step s : STEPS) {
            if (s.lever() == lever) {
                out.add(s.time());
            }
        }
        return out;
    }

    /** Identifies the board, looks the solution up, and announces it once. */
    private static void scan(Minecraft mc, PuzzleSolversModule mod) {
        load();
        if (data == null) {
            scanned = true;
            return;
        }
        String extended = extendedSlots(mc);
        if (extended == null) {
            return;   // rotation not known yet, or the slots could not be read
        }
        // Three extended slots is what every recorded solution assumes; anything else means the
        // puzzle has already been started and the board no longer matches what was recorded.
        if (extended.isEmpty()) {
            // Not one wool anywhere: the room's blocks have not arrived yet. Every probe reading air
            // is what a room looks like before it loads, and announcing "already started" for it was
            // simply wrong - so wait and look again instead.
            return;
        }
        if (extended.length() != 3) {
            scanned = true;
            // Say what was read, not what it was taken to mean. Every recorded solution is for a
            // board with three slots out, so any other count is as likely to be a misread of the
            // room as a puzzle somebody has already started - and only the reading tells them apart.
            say(mc, "Water Board: read " + extended.length() + " wool slots, expected 3 ["
                    + slotReadout(mc) + "]");
            return;
        }
        Integer pattern = patternOf(mc);
        if (pattern == null) {
            scanned = true;
            say(mc, "Water Board: could not identify the pattern");
            return;
        }
        scanned = true;

        JsonObject byPattern = child(data, mod.waterShortRoute() ? "true" : "false");
        JsonObject bySlots = byPattern == null ? null : child(byPattern, String.valueOf(pattern));
        JsonObject levers = bySlots == null ? null : child(bySlots, extended);
        if (levers == null) {
            say(mc, "Water Board: no recorded solution for this board");
            return;
        }

        STEPS.clear();
        for (var e : levers.entrySet()) {
            Lever lever = Lever.byKey(e.getKey());
            if (lever == null) {
                continue;
            }
            for (JsonElement t : e.getValue().getAsJsonArray()) {
                STEPS.add(new Step(lever, t.getAsDouble()));
            }
        }
        // Everything at 0 goes first, in lever order; the rest follows by its time.
        STEPS.sort(Comparator
                .comparingInt((Step s) -> s.time() == 0.0 ? 0 : 1)
                .thenComparingDouble(s -> s.time() == 0.0 ? s.lever().ordinal() : s.time()));

        StringBuilder sb = new StringBuilder("Water Board: ");
        for (int i = 0; i < STEPS.size(); i++) {
            Step s = STEPS.get(i);
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append(s.lever().name().toLowerCase(Locale.ROOT));
            if (s.time() > 0) {
                sb.append(" (").append(s.time()).append("s)");
            }
        }
        say(mc, sb.toString());
    }

    /**
     * The indices of the extended wool slots, e.g. "013".
     *
     * <p>The probe asks for wool specifically rather than "not air". Any solid block counted before,
     * so a probe landing a block off - on the frame around the slots, say - read as five extended
     * and the solver announced the puzzle was already under way when nobody had touched it.
     */
    private static String extendedSlots(Minecraft mc) {
        StringBuilder sb = new StringBuilder(3);
        for (int i = 0; i < WOOL.length; i++) {
            BlockPos pos = DungeonRooms.toWorld(WOOL[i]);
            if (pos == null) {
                return null;
            }
            if (mc.level.getBlockState(pos).is(net.minecraft.tags.BlockTags.WOOL)) {
                sb.append(i);
            }
        }
        return sb.toString();
    }

    /** What the five probes actually found, so a bad reading can be reported instead of guessed at. */
    private static String slotReadout(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < WOOL.length; i++) {
            BlockPos pos = DungeonRooms.toWorld(WOOL[i]);
            if (i > 0) {
                sb.append(", ");
            }
            if (pos == null) {
                sb.append(i).append("=?");
                continue;
            }
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(mc.level.getBlockState(pos).getBlock()).getPath();
            sb.append(i).append('=').append(name)
                    .append('@').append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
        }
        return sb.toString();
    }

    /** Which of the four boards is built, told apart by the blocks they differ in. */
    private static Integer patternOf(Minecraft mc) {
        BlockPos a = DungeonRooms.toWorld(new BlockPos(14, 77, 27));
        BlockPos b = DungeonRooms.toWorld(new BlockPos(16, 78, 27));
        BlockPos c = DungeonRooms.toWorld(new BlockPos(14, 78, 27));
        if (a == null || b == null || c == null) {
            return null;
        }
        if (mc.level.getBlockState(a).getBlock() == Blocks.TERRACOTTA) {
            return 0;
        }
        if (mc.level.getBlockState(b).getBlock() == Blocks.EMERALD_BLOCK) {
            return 1;
        }
        if (mc.level.getBlockState(c).getBlock() == Blocks.DIAMOND_BLOCK) {
            return 2;
        }
        if (mc.level.getBlockState(c).getBlock() == Blocks.QUARTZ_BLOCK) {
            return 3;
        }
        return null;
    }

    private static JsonObject child(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static void say(Minecraft mc, String text) {
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§b[DiegoAddons] §a" + text));
        }
    }

    private static void load() {
        if (data != null) {
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "puzzles/water_board.json");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open();
                     InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    data = JsonParser.parseReader(r).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Could not load water solutions: {}", e.toString());
        }
    }
}
