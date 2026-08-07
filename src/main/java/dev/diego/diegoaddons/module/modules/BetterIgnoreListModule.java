package dev.diego.diegoaddons.module.modules;

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

    public BetterIgnoreListModule() {
        super("betterignorelist", Category.MISC, "Better Ignore List",
                "Block players with a reason, and kick them from your party.");
        settings.add(autoKick);
        settings.add(announceReason);
        // The list itself is a row on this card now, declared in ListSpecs - a button
        // that opens a screen showing the same thing would be a second door to one room.
        INSTANCE = this;
    }

    public boolean autoKick() {
        return autoKick.get();
    }

    public boolean announceReason() {
        return announceReason.get();
    }
}
