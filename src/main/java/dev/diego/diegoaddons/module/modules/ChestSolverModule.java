package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.CrystalHollows;
import dev.diego.diegoaddons.util.WorldRender;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Highlights the treasure chests that mining spawns in the Crystal Hollows and counts down the
 * ~60 seconds before each one vanishes.
 *
 * <p>The chests are ordinary chest blocks, so nearby ones are found by scanning the blocks around
 * you (throttled - a full scan every tick is wasteful). Each is remembered from when it was first
 * seen; the box turns from green to red as its timer runs down, and it is dropped the moment the
 * block is gone or the minute is up.
 */
public class ChestSolverModule extends Module {
    private static final int RADIUS = 6;
    private static final int SCAN_TICKS = 5;
    private static final long LIFETIME_MS = 60_000;

    private final BooleanSetting timer =
            new BooleanSetting(this, "timer", "Show despawn timer", true);

    private final Map<BlockPos, Long> chests = new HashMap<>();
    private int scanIn;

    public ChestSolverModule() {
        super("chestsolver", Category.MINING, "Chest Solver",
                "Highlights Crystal Hollows treasure chests with a despawn timer.");
        settings.add(timer);
    }

    @Override
    protected void onDisable() {
        chests.clear();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!CrystalHollows.inHollows() || mc.player == null || mc.level == null) {
            chests.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (scanIn > 0) {
            scanIn--;
        } else {
            scanIn = SCAN_TICKS;
            scan(mc, now);
        }

        Iterator<Map.Entry<BlockPos, Long>> it = chests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            BlockPos p = e.getKey();
            long age = now - e.getValue();
            boolean stillChest = mc.level.getBlockState(p).is(Blocks.CHEST);
            if (!stillChest || age >= LIFETIME_MS) {
                it.remove();
                continue;
            }
            int color = colorFor(age);
            WorldRender.thickBox(new AABB(p), color, 0.04, true);
            if (timer.get()) {
                double left = (LIFETIME_MS - age) / 1000.0;
                WorldRender.text("§e" + String.format(Locale.ROOT, "%.0fs", left),
                        new Vec3(p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5), 1.0f);
            }
        }
    }

    private void scan(Minecraft mc, long now) {
        BlockPos base = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                base.offset(-RADIUS, -RADIUS, -RADIUS), base.offset(RADIUS, RADIUS, RADIUS))) {
            if (mc.level.getBlockState(p).is(Blocks.CHEST)) {
                chests.putIfAbsent(p.immutable(), now);
            }
        }
    }

    /** Green with plenty of time, amber past half, red in the last ten seconds. */
    private static int colorFor(long age) {
        long left = LIFETIME_MS - age;
        if (left < 10_000) {
            return 0xFFFF4040;
        }
        if (left < 30_000) {
            return 0xFFFFC030;
        }
        return 0xFF40FF60;
    }
}
