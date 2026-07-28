package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.gui.IgnoreListView;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * A block list that keeps a reason with each name, and can kick blocked players who join your party.
 *
 * <p>Blocking is local: it does not use Hypixel's own ignore system, so the reason is yours to see
 * and nothing is sent anywhere until a kick actually happens.
 */
public class BetterIgnoreListModule extends Module {
    public static BetterIgnoreListModule INSTANCE;

    private final BooleanSetting autoKick =
            new BooleanSetting(this, "autoKick", "Auto-kick from party", true);
    private final BooleanSetting announceReason =
            new BooleanSetting(this, "announce", "Announce reason", false);
    private final ActionSetting list =
            new ActionSetting(this, "list", "Blocked players", "Open", BetterIgnoreListModule::open);

    public BetterIgnoreListModule() {
        super("betterignorelist", Category.MISC, "Better Ignore List",
                "Block players with a reason, and kick them from your party.");
        settings.add(autoKick);
        settings.add(announceReason);
        settings.add(list);
        INSTANCE = this;
    }

    private static void open() {
        Minecraft mc = Minecraft.getInstance();
        new IgnoreListView().open();
    }

    public boolean autoKick() {
        return autoKick.get();
    }

    public boolean announceReason() {
        return announceReason.get();
    }
}
