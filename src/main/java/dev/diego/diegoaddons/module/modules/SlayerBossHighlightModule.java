package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.EspModule;
import dev.diego.diegoaddons.util.EspDraw;
import dev.diego.diegoaddons.util.SlayerState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Highlights the player's <b>own</b> Slayer boss - the mob found under its "Spawned by: &lt;name&gt;"
 * stand ({@link SlayerState}) - with a box coloured by the quest tier, so it never gets lost in a busy
 * lobby full of other players' bosses. Optional extras: a tracer from the crosshair to it and a 2D
 * edge arrow while it is off-screen, both handy the moment the boss teleports out of view.
 */
public class SlayerBossHighlightModule extends EspModule {
    public static SlayerBossHighlightModule INSTANCE;

    private static final double EDGE = 0.06;

    private final BooleanSetting tierColor =
            new BooleanSetting(this, "tierColor", "Colour by tier", true);
    private final BooleanSetting tracer =
            new BooleanSetting(this, "tracer", "Tracer to boss", false);
    private final BooleanSetting arrow =
            new BooleanSetting(this, "arrow", "Off-screen arrow", true);

    public SlayerBossHighlightModule() {
        super("slayerbosshighlight", Category.SLAYER, "Boss Highlight",
                "Box your own slayer boss, coloured by tier, with an optional tracer and off-screen arrow.",
                0xFFFF5555);
        settings.add(tierColor);
        settings.add(tracer);
        settings.add(arrow);
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!SlayerState.inSlayer()) {
            return;
        }
        Entity boss = SlayerState.bossEntity();
        if (boss == null) {
            return;
        }
        // The tier colours say something the user's own colour cannot, so they win when asked for.
        int color = tierColor.get() ? tierColor(SlayerState.tier()) : espColor().argb();
        AABB box = boss.getBoundingBox().inflate(0.05);
        Vec3 center = box.getCenter();

        dev.diego.diegoaddons.util.EspRender.draw(box, this, color);
        if (tracer.get()) {
            EspDraw.tracer(center, color);
        }
        if (arrow.get()) {
            EspDraw.arrow2d(center, color);
        }
    }

    /** Tier 1..5 to an escalating colour; orange when the tier could not be read. */
    private static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> 0xFFAAAAAA;   // gray
            case 2 -> 0xFF55FF55;   // green
            case 3 -> 0xFF55FFFF;   // aqua
            case 4 -> 0xFFFF55FF;   // magenta
            case 5 -> 0xFFFF5555;   // red
            default -> 0xFFFFAA00;  // orange (unknown)
        };
    }
}
