package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.GfsItem;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
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

    private final ActionSetting edit =
            new ActionSetting(this, "items", "Items", "Edit", AutoGfsModule::openEditor);

    public AutoGfsModule() {
        super("autogfs", Category.MISC, "Auto GFS",
                "Keep SkyBlock items topped up from your sacks.");
        settings.add(edit);
        INSTANCE = this;
    }

    private static void openEditor() {
        Minecraft.getInstance().execute(() -> new dev.diego.diegoaddons.gui.AutoGfsView().open());
    }

    /**
     * The items currently switched on, in the order they are checked.
     *
     * <p>Pearls, superboom and leaps used to be three hard-coded toggles sharing one threshold.
     * They are ordinary entries now - seeded on first use so nothing is lost - which is what lets
     * anything else be added beside them, and lets each carry the threshold that suits it.
     */
    public List<GfsItem> enabledItems() {
        List<GfsItem> all = ConfigManager.get().gfsItems;
        if (all.isEmpty() && !ConfigManager.get().gfsSeeded) {
            all.add(new GfsItem("Ender Pearl", "", 4));
            all.add(new GfsItem("Superboom TNT", "", 4));
            all.add(new GfsItem("Spirit Leap", "", 2));
            for (GfsItem it : all) {
                it.enabled = false;   // this sends commands for you; nothing starts switched on
            }
            ConfigManager.get().gfsSeeded = true;
            ConfigManager.save();
        }
        List<GfsItem> out = new ArrayList<>();
        for (GfsItem it : all) {
            if (it.enabled && !it.name.isBlank()) {
                out.add(it);
            }
        }
        return out;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        AutoGfs.tick(mc);
    }
}
