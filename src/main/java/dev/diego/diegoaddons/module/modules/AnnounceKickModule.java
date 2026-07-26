package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.LegacyText;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;

/**
 * Announces in party chat when you get thrown back to the Hypixel lobby - a server crash or a silent
 * kick otherwise leaves your party wondering where you went. It watches the sidebar title: while in
 * SkyBlock it reads "SKYBLOCK", and the moment that stops being true (without you having left on
 * purpose is not distinguishable, so it fires on any drop to the lobby) it sends one message.
 */
public class AnnounceKickModule extends Module {
    public static AnnounceKickModule INSTANCE;

    /** How long SkyBlock must stay gone before this counts as a kick rather than a warp/reload. */
    private static final int CONFIRM_TICKS = 60;

    private final BooleanSetting toParty =
            new BooleanSetting(this, "party", "Send to party chat", true);

    private boolean wasSkyblock;
    private int goneTicks = -1;   // ticks since SkyBlock disappeared while connected, or -1

    public AnnounceKickModule() {
        super("announcekick", Category.MISC, "Announce SB Kick",
                "Tell your party when you get kicked to the lobby.");
        settings.add(toParty);
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        // While loading between servers the connection/scoreboard is briefly absent - don't judge
        // anything then, or every warp to a dungeon or island reads as a kick.
        if (mc.player == null || mc.player.connection == null) {
            return;
        }
        boolean sky = inSkyblock(mc);
        if (sky) {
            wasSkyblock = true;
            goneTicks = -1;
            return;
        }
        if (!wasSkyblock) {
            return;
        }
        // SkyBlock is gone. A warp to another SkyBlock server brings it back within a second; only a
        // real kick to the lobby leaves it gone. So wait before announcing.
        if (goneTicks < 0) {
            goneTicks = 0;
        }
        if (++goneTicks < CONFIRM_TICKS) {
            return;
        }
        wasSkyblock = false;
        goneTicks = -1;
        if (toParty.get()) {
            mc.player.connection.sendCommand("pc Got kicked to lobby!");
        }
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    net.minecraft.network.chat.Component.literal("§b[DiegoAddons] §fKicked to the lobby."));
        }
    }

    private static boolean inSkyblock(Minecraft mc) {
        Scoreboard sb = mc.player.connection.scoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            return false;
        }
        return LegacyText.strip(obj.getDisplayName().getString())
                .toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }
}
