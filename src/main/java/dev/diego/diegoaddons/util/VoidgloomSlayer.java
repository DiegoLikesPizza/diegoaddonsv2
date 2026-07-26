package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.VoidgloomSlayerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Helpers for the Voidgloom Seraph (Enderman slayer) panic mechanics:
 *
 * <ul>
 *   <li><b>Beacon</b> - the boss throws a beacon that explodes for heavy damage after a few seconds;
 *       it is found as a {@link Blocks#BEACON} block near the player and shown with a countdown.</li>
 *   <li><b>Nukekebi heads</b> - the floating heads it spawns, matched by their "Nukekebi" nametag.</li>
 * </ul>
 *
 * <p>Each of highlight / tracer / arrow (and, for the beacon, a timer) is an option, all drawn through
 * {@link EspDraw}. The work only runs while the active Slayer quest is a Voidgloom Seraph, since the
 * beacon block scan is the one heavy part.
 */
public final class VoidgloomSlayer {
    private static final int BEACON_R = 15;         // horizontal search radius for the beacon block
    private static final int BEACON_Y_DOWN = 4;
    private static final int BEACON_Y_UP = 3;
    private static final int SCAN_TICKS = 5;        // throttle the block scan
    private static final long BEACON_MS = 5000;     // ~5 s from appearing to exploding
    private static final double NUKE_RANGE = 22.0;

    private static final double EDGE = 0.08;
    private static final int BEACON_COLOR = 0xFF00E0FF;   // cyan
    private static final int NUKE_COLOR = 0xFFFF44FF;     // magenta

    /** Beacon blocks and the wall-clock time each was first seen, for the countdown. */
    private static final Map<BlockPos, Long> beacons = new HashMap<>();
    private static int scanIn;

    private VoidgloomSlayer() {
    }

    public static void reset() {
        beacons.clear();
        scanIn = 0;
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        VoidgloomSlayerModule mod = VoidgloomSlayerModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null || mc.level == null) {
            return;
        }
        if (SlayerState.activeType() != SlayerState.Type.VOIDGLOOM) {
            beacons.clear();
            return;
        }
        beacon(mc, mod);
        nukekebi(mc, mod);
    }

    // --- beacon ---------------------------------------------------------------------------------

    private static void beacon(Minecraft mc, VoidgloomSlayerModule mod) {
        long now = System.currentTimeMillis();
        if (scanIn > 0) {
            scanIn--;
        } else {
            scanIn = SCAN_TICKS;
            scanBeacons(mc, now);
        }

        Iterator<Map.Entry<BlockPos, Long>> it = beacons.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            BlockPos p = e.getKey();
            long age = now - e.getValue();
            if (!mc.level.getBlockState(p).is(Blocks.BEACON) || age >= BEACON_MS + 1500) {
                it.remove();
                continue;
            }
            AABB box = new AABB(p);
            Vec3 center = box.getCenter();
            if (mod.beaconHighlight()) {
                EspDraw.highlight(box, BEACON_COLOR, EDGE);
            }
            if (mod.beaconTracer()) {
                EspDraw.tracer(center, BEACON_COLOR);
            }
            if (mod.beaconArrow()) {
                EspDraw.arrow2d(center, BEACON_COLOR);
            }
            if (mod.beaconTimer()) {
                double left = Math.max(0, (BEACON_MS - age) / 1000.0);
                EspDraw.timerLabel(new Vec3(p.getX() + 0.5, p.getY() + 1.3, p.getZ() + 0.5),
                        "§b" + String.format(Locale.ROOT, "%.1fs", left));
            }
        }
    }

    private static void scanBeacons(Minecraft mc, long now) {
        BlockPos base = mc.player.blockPosition();
        for (int dx = -BEACON_R; dx <= BEACON_R; dx++) {
            for (int dz = -BEACON_R; dz <= BEACON_R; dz++) {
                for (int dy = -BEACON_Y_DOWN; dy <= BEACON_Y_UP; dy++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (mc.level.getBlockState(p).is(Blocks.BEACON)) {
                        beacons.putIfAbsent(p.immutable(), now);
                    }
                }
            }
        }
    }

    // --- nukekebi heads -------------------------------------------------------------------------

    private static void nukekebi(Minecraft mc, VoidgloomSlayerModule mod) {
        if (!mod.nukekebiHighlight() && !mod.nukekebiTracer() && !mod.nukekebiArrow()) {
            return;
        }
        AABB area = mc.player.getBoundingBox().inflate(NUKE_RANGE);
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (!e.hasCustomName()) {
                continue;
            }
            if (!LegacyText.strip(e.getCustomName().getString()).contains("Nukekebi")) {
                continue;
            }
            // The head sits at the named entity; box a head-sized region around it.
            Vec3 c = e.position();
            AABB box = new AABB(c.x - 0.6, c.y - 0.3, c.z - 0.6, c.x + 0.6, c.y + 1.0, c.z + 0.6);
            Vec3 center = box.getCenter();
            if (mod.nukekebiHighlight()) {
                EspDraw.highlight(box, NUKE_COLOR, EDGE);
            }
            if (mod.nukekebiTracer()) {
                EspDraw.tracer(center, NUKE_COLOR);
            }
            if (mod.nukekebiArrow()) {
                EspDraw.arrow2d(center, NUKE_COLOR);
            }
        }
    }
}
