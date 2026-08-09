package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Announces in party chat when Hypixel kicks you back to the lobby - a crash or a silent kick
 * otherwise leaves your party wondering where you went.
 *
 * <p>Driven by the server's own kick message rather than by watching the sidebar, which is what it
 * used to do: the sidebar losing "SKYBLOCK" means you are in a lobby, and going to a lobby on
 * purpose is by far the commoner way for that to happen.
 */
public class AnnounceKickModule extends Module {
    public static AnnounceKickModule INSTANCE;

    private final BooleanSetting toParty =
            new BooleanSetting(this, "party", "Send to party chat", true);

    public AnnounceKickModule() {
        super("announcekick", Category.MISC, "Announce SB Kick",
                "Tell your party when you get kicked to the lobby.");
        settings.add(toParty);
        INSTANCE = this;
    }

    /**
     * Hypixel's own words for it: {@code A kick occurred in your connection, so you were put in the
     * SkyBlock lobby!}
     *
     * <p>Matched on the stable middle of the sentence. The game it names varies, and the wording
     * around it has been reworded before, but "a kick occurred in your connection" is the part that
     * has stayed put - and it is the part that actually means a kick.
     */
    private static final String KICK_LINE = "a kick occurred in your connection";

    /**
     * Announces a kick, from the message the server sends when one happens.
     *
     * <p>This used to watch the sidebar instead, and fire whenever "SKYBLOCK" stopped being on it.
     * That is not a kick - it is every trip to a lobby, deliberate or not - so the module told your
     * party you had been kicked each time you went to the hub yourself. There is a message for this;
     * reading it is both simpler and actually correct.
     */
    public static void onMessage(String plain) {
        AnnounceKickModule mod = INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (!plain.toLowerCase(Locale.ROOT).contains(KICK_LINE)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mod.toParty.get() && mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("pc Got kicked to lobby!");
        }
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    net.minecraft.network.chat.Component.literal("§b[DiegoAddons] §fKicked to the lobby."));
        }
    }
}
