package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.FishingRareAlertModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Rare sea-creature alert: watches chat for the line a rare creature prints when it spawns and, on a
 * match, shows a title and plays a sound so it is not missed mid-cast.
 *
 * <p>The spawn lines are matched by a distinctive fragment (the full line varies slightly by context).
 * The table is a confident starter set - Sea Emperor, Water Hydra, Yeti, Frozen Steve, Thunder,
 * Reindrake - and easy to extend once more wordings are confirmed in-game.
 */
public final class FishingAlerts {
    /** {distinctive chat fragment, creature name}. */
    private static final String[][] RARE = {
            {"Sea Emperor arises", "Sea Emperor"},
            {"Water Hydra has come", "Water Hydra"},
            {"What is this creature", "Yeti"},
            {"Frozen Steve fell into the ocean", "Frozen Steve"},
            {"Thunder is rumbling", "Thunder"},
            {"Reindrake forms from the depths", "Reindrake"},
            {"Great White Shark", "Great White Shark"},
            {"Lord Jawbus has emerged", "Lord Jawbus"},
    };

    private FishingAlerts() {
    }

    /** Called for every incoming system message. */
    public static void onMessage(String plain) {
        FishingRareAlertModule mod = FishingRareAlertModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        for (String[] entry : RARE) {
            if (plain.contains(entry[0])) {
                alert(mod, entry[1]);
                return;
            }
        }
    }

    private static void alert(FishingRareAlertModule mod, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mod.title() && mc.gui != null) {
            mc.gui.setTimes(2, 40, 8);
            mc.gui.setTitle(Component.literal("§b§lRARE CATCH"));
            mc.gui.setSubtitle(Component.literal("§e" + name));
        }
        if (mod.sound()) {
            mc.player.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 1.4f);
        }
    }
}
