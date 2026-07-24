package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.AutoGfs;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Refills pearls, superboom and leaps from your sacks when they run low.
 *
 * <p>Every item ships <b>off</b>: this sends commands for you, which is not something to enable
 * without meaning to. Refills are rate-limited and never fire while a menu is open. An item you are
 * not carrying at all is left alone - that is a choice not to bring it, not running out.
 */
public class AutoGfsModule extends Module {
    public static AutoGfsModule INSTANCE;

    private final BooleanSetting pearls =
            new BooleanSetting(this, "pearls", "Ender Pearls", false);
    private final BooleanSetting superboom =
            new BooleanSetting(this, "superboom", "Superboom TNT", false);
    private final BooleanSetting leaps =
            new BooleanSetting(this, "leaps", "Spirit Leaps", false);
    private final NumberSetting threshold =
            new NumberSetting(this, "threshold", "Refill below", 4, 1, 32, 1);

    public AutoGfsModule() {
        super("autogfs", Category.MISC, "Auto GFS",
                "Refill pearls, superboom and leaps from your sacks.");
        settings.add(pearls);
        settings.add(superboom);
        settings.add(leaps);
        settings.add(threshold);
        INSTANCE = this;
    }

    public int threshold() {
        return (int) threshold.get();
    }

    /** The items currently switched on, in the order they are checked. */
    public List<AutoGfs.Item> enabledItems() {
        List<AutoGfs.Item> out = new ArrayList<>(3);
        if (pearls.get()) {
            out.add(AutoGfs.PEARLS);
        }
        if (superboom.get()) {
            out.add(AutoGfs.SUPERBOOM);
        }
        if (leaps.get()) {
            out.add(AutoGfs.LEAPS);
        }
        return out;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        AutoGfs.tick(mc);
    }
}
