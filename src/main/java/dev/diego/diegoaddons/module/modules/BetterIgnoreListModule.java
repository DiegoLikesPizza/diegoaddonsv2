package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
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
    /**
     * How long after the join line the kick goes out.
     *
     * <p>It used to be no time at all, which is both conspicuous - nobody types a kick in the same
     * frame somebody joins - and unreliable, because a command sent that quickly can arrive before
     * Hypixel has finished adding them to the party. A second and a half looks like a person
     * noticing, and zero is still available for anyone who wants the old behaviour.
     */
    private final NumberSetting kickDelay =
            new NumberSetting(this, "kickDelay", "Kick delay (seconds)", 1.5, 0, 10, 0.5);
    private final BooleanSetting announceReason =
            new BooleanSetting(this, "announce", "Announce reason", false);

    public BetterIgnoreListModule() {
        super("betterignorelist", Category.MISC, "Better Ignore List",
                "Block players with a reason, and kick them from your party.");
        settings.add(autoKick);
        settings.add(kickDelay);
        settings.add(announceReason);
        // The list itself is a row on this card now, declared in ListSpecs - a button
        // that opens a screen showing the same thing would be a second door to one room.
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        // The kick is queued rather than sent from the chat handler - see IgnoreList.
        dev.diego.diegoaddons.util.IgnoreList.tick(mc);
    }

    public boolean autoKick() {
        return autoKick.get();
    }

    /** Seconds between the join line and the kick. */
    public double kickDelay() {
        return kickDelay.get();
    }

    public boolean announceReason() {
        return announceReason.get();
    }
}
