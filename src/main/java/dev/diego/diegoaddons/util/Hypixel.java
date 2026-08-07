package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Joining Hypixel in one click, from wherever the button happens to be.
 *
 * <p>There are two title screens - Minecraft's, where the button stands in for Realms, and
 * configlib's, where it sits under Multiplayer - and both need the same act. Having it once means
 * the two cannot drift into connecting differently.
 */
public final class Hypixel {

    public static final String ADDRESS = "mc.hypixel.net";
    public static final String LABEL = "Join Hypixel";

    private Hypixel() {
    }

    /** Straight to the server, the way the multiplayer list does it. */
    public static void connect() {
        Minecraft mc = Minecraft.getInstance();
        ServerData data = new ServerData("Hypixel", ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(ADDRESS), data,
                false, null);
    }
}
