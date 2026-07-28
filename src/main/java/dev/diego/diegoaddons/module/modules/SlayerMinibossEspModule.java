package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.EspModule;
import net.minecraft.client.Minecraft;

/**
 * ESP for Slayer minibosses (the bonus mobs a quest spawns). Boxes them by their health-plate name;
 * see {@link dev.diego.diegoaddons.util.SlayerMinibossEsp} for the matching.
 */
public class SlayerMinibossEspModule extends EspModule {
    public static SlayerMinibossEspModule INSTANCE;

    public SlayerMinibossEspModule() {
        super("slayerminibossesp", Category.SLAYER, "Miniboss ESP",
                "Box slayer minibosses (Revenant Sycophant, Tarantula Vermin, ...) by their nametag.",
                0xFFFFAA00);
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        dev.diego.diegoaddons.util.SlayerMinibossEsp.tick(mc);
    }
}
