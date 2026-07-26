package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Re-queues the dungeon automatically once the current run ends, so a party can chain runs without
 * anyone typing the command. The end of a run is announced by Hypixel's score summary line; a short
 * delay after it, the requeue command is sent once. It never fires more than once per run.
 */
public class AutoRequeueModule extends Module {
    public static AutoRequeueModule INSTANCE;

    /** The score-summary line Hypixel prints when a dungeon finishes. */
    private static final String END_MARKER = "> EXTRA STATS <";
    /** "Party > [rank] Name: message" - to catch the !dt trigger typed in party chat. */
    private static final Pattern PARTY_LINE = Pattern.compile(
            "^Party\\s*>\\s*(?:\\[[^\\]]+]\\s*)?\\w{1,16}\\s*:\\s*(.+)$");

    private final NumberSetting delay =
            new NumberSetting(this, "delay", "Delay (s)", 3, 0, 10, 0.5);
    private final BooleanSetting skipIfLeft =
            new BooleanSetting(this, "skipLeft", "Skip if someone left", true);

    private int countdown = -1;
    /** Set when a party member leaves; clears on requeue. Blocks the requeue if the option is on. */
    private boolean partyLeft;

    public AutoRequeueModule() {
        super("autorequeue", Category.DUNGEONS, "Auto Requeue",
                "Requeue the dungeon automatically when the run ends. Also triggered by !dt in party chat.");
        settings.add(delay);
        settings.add(skipIfLeft);
        INSTANCE = this;
    }

    @Override
    protected void onDisable() {
        countdown = -1;
        partyLeft = false;
    }

    /** Called for every game chat line; arms the requeue on the run summary or a party !dt. */
    public static void onMessage(String plain) {
        AutoRequeueModule mod = INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        // Someone leaving the party marks it incomplete, so an auto-requeue would drag a short party
        // into a run - remember it until the next requeue.
        if (plain.contains(" has left the party") || plain.contains(" was removed from the party")
                || plain.contains(" has been removed from the party")) {
            mod.partyLeft = true;
            return;
        }
        if (plain.contains(END_MARKER)) {
            mod.arm();
            return;
        }
        Matcher m = PARTY_LINE.matcher(plain.trim());
        if (m.matches() && m.group(1).trim().equalsIgnoreCase("!dt")) {
            mod.arm();
        }
    }

    private void arm() {
        if (countdown >= 0 || (skipIfLeft.get() && partyLeft)) {
            return;
        }
        countdown = (int) Math.round(delay.get() * 20);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (countdown < 0) {
            return;
        }
        if (countdown-- == 0) {
            countdown = -1;
            partyLeft = false;
            if (mc.player != null) {
                mc.player.connection.sendCommand("instancerequeue");
                if (mc.gui != null) {
                    mc.gui.getChat().addClientSystemMessage(
                            Component.literal("§b[DiegoAddons] §fRequeuing…"));
                }
            }
        }
    }
}
