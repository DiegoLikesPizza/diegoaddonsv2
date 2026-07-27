package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.config.MiningRoute;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.MiningRoutes;
import dev.diego.diegoaddons.util.WorldRender;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the mining route you are currently showing: the points you recorded, joined into a path you
 * can follow. Routes are recorded and switched with {@code /da route ...} and kept in the config, so
 * the same loop can be reused every session. Only the active route is drawn.
 */
public class MiningRoutesModule extends Module {
    private static final int COLOR = 0xFF29D3FF;

    private final BooleanSetting numbers =
            new BooleanSetting(this, "numbers", "Number the points", true);

    public MiningRoutesModule() {
        super("miningroutes", Category.MINING, "Mining Routes",
                "Draws a recorded walking route (manage with /da route).");
        settings.add(numbers);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        MiningRoute r = MiningRoutes.activeRoute();
        if (r == null || r.points.isEmpty() || mc.player == null || mc.level == null) {
            return;
        }
        // A box on each waypoint block, numbered in order. No connecting path: the points are whole
        // blocks anywhere in 3-D, and a box drawn between two of them would balloon into one huge
        // diagonal cube (the "big flickering box"), so the order is shown by the numbers instead.
        for (int i = 0; i < r.points.size(); i++) {
            double[] p = r.points.get(i);
            int bx = (int) Math.floor(p[0]);
            int by = (int) Math.floor(p[1]);
            int bz = (int) Math.floor(p[2]);
            WorldRender.thickBox(new AABB(bx, by, bz, bx + 1, by + 1, bz + 1), COLOR, 0.04, true);
            if (numbers.get()) {
                WorldRender.text("§b" + (i + 1), new Vec3(bx + 0.5, by + 1.2, bz + 0.5), 1.0f);
            }
        }
    }
}
