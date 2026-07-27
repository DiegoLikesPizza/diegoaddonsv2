package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Alerts you the moment a <b>Prince</b> is killed in the Catacombs.
 *
 * <p>The Prince is the dungeon mob tied to the Reborn attribute shard (added with the Hunting /
 * attribute-shard update): killing one grants +1 bonus dungeon score. Hypixel announces it with a
 * single fixed line - {@code "A Prince falls. +1 Bonus Score"} - so detection is exact rather than
 * heuristic.
 *
 * <p>Whenever that line is seen this also flags the kill on {@link DungeonState} so the score readout
 * stays right, regardless of whether this module's own notification is switched on.
 */
public class PrinceMessageModule extends Module {
    public static PrinceMessageModule INSTANCE;

    private static final String LINE = "A Prince falls";

    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Play a sound", true);
    private final BooleanSetting announceParty =
            new BooleanSetting(this, "announce", "Announce in party chat", false);

    public PrinceMessageModule() {
        super("princemessage", Category.DUNGEONS, "Prince Message",
                "Tells you when a Prince is killed (Reborn shard, +1 bonus score).");
        settings.add(sound);
        settings.add(announceParty);
        INSTANCE = this;
    }

    /**
     * Called for every game chat line (unconditionally, so the score flag is set even when the
     * module is off). Only the visible notification is gated on the module being enabled.
     */
    public static void onMessage(String plain) {
        if (!plain.contains(LINE) || !DungeonState.inDungeons()) {
            return;
        }
        DungeonState.setPrinceKilled();
        PrinceMessageModule mod = INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mod.sound.get()) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
        }
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    Component.literal("§b[DiegoAddons] §dPrince killed! §7(+1 bonus score)"));
        }
        if (mod.announceParty.get()) {
            mc.player.connection.sendCommand("pc Prince Killed!");
        }
    }
}
