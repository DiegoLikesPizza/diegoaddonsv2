package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Alerts with a title and sound when a rare sea creature spawns while fishing. Detection lives in
 * {@link dev.diego.diegoaddons.util.FishingAlerts}; this is the toggle and its options.
 */
public class FishingRareAlertModule extends Module {
    public static FishingRareAlertModule INSTANCE;

    private final BooleanSetting title =
            new BooleanSetting(this, "title", "Show title", true);
    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Play sound", true);

    public FishingRareAlertModule() {
        super("fishingrarealert", Category.FISHING, "Rare Sea Creature Alert",
                "Title and sound when a rare sea creature spawns.");
        settings.add(title);
        settings.add(sound);
        INSTANCE = this;
    }

    public boolean title() {
        return title.get();
    }

    public boolean sound() {
        return sound.get();
    }
}
