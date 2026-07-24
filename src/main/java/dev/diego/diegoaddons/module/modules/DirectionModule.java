package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

/** Shows the cardinal direction the player is facing. */
public class DirectionModule extends HudModule {
    public DirectionModule() {
        super("direction", "Direction", "Cardinal direction you're facing.");
    }

    @Override
    protected String label() {
        return "Facing";
    }

    @Override
    protected String value(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        Direction d = mc.player.getDirection();
        return switch (d) {
            case NORTH -> "North (-Z)";
            case SOUTH -> "South (+Z)";
            case EAST -> "East (+X)";
            case WEST -> "West (-X)";
            default -> d.getName();
        };
    }

    @Override
    protected String sampleValue() {
        return "North (-Z)";
    }
}
