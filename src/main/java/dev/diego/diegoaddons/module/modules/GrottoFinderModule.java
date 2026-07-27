package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.CrystalHollows;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Marks Fairy Grottos in the Crystal Hollows. Grottos are tucked behind the terrain, so the useful
 * moment is the one you enter one: the area line turns to "Fairy Grotto", which records the entrance
 * as a beam and (optionally) pings, so you can return to it or note it for a friend. Cleared on
 * leaving the Hollows.
 */
public class GrottoFinderModule extends Module {
    public static GrottoFinderModule INSTANCE;

    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Ping on a new grotto", true);

    private int seen;

    public GrottoFinderModule() {
        super("grottofinder", Category.MINING, "Grotto Finder",
                "Marks Fairy Grottos in the Crystal Hollows.");
        settings.add(sound);
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!CrystalHollows.inHollows()) {
            seen = 0;
            return;
        }
        CrystalHollows.detect(mc, true);
        int count = 0;
        for (CrystalHollows.Waypoint w : CrystalHollows.waypoints()) {
            if (w.type() == CrystalHollows.Type.GROTTO) {
                count++;
                CrystalHollows.drawBeam(mc, w.pos(), w.type().color, w.name());
            }
        }
        if (count > seen) {
            seen = count;
            if (mc.player != null) {
                if (sound.get()) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.6f);
                }
                if (mc.gui != null) {
                    mc.gui.getChat().addClientSystemMessage(
                            Component.literal("§b[DiegoAddons] §dFound a Fairy Grotto!"));
                }
            }
        }
    }
}
