package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Shows the player's block position. */
public class CoordinatesModule extends HudModule {
    public CoordinatesModule() {
        super("coordinates", "Coordinates", "Your X / Y / Z block position.");
    }

    @Override
    protected String label() {
        return "XYZ";
    }

    @Override
    protected String value(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        BlockPos p = mc.player.blockPosition();
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    @Override
    protected String sampleValue() {
        return "128, 71, -412";
    }
}
