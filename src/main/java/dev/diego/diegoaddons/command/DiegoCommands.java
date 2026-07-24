package dev.diego.diegoaddons.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.diego.diegoaddons.gui.ClickGuiScreen;
import dev.diego.diegoaddons.gui.HudEditorScreen;
import dev.diego.diegoaddons.gui.InventoryButtonsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

/**
 * Client-side commands for opening the mod's screens: {@code /da} and {@code /diego} open the main
 * menu, with {@code hud} and {@code invbuttons} sub-commands for the two editors.
 *
 * <p>These never reach the server - they are handled entirely on the client by Fabric's client
 * command API, so they work on any server without it seeing an unknown command.
 */
public final class DiegoCommands {
    private DiegoCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            root(dispatcher, "da");
            root(dispatcher, "diego");
        });
    }

    /** Registers one command root, so both names get the same sub-commands. */
    private static void root(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
        LiteralArgumentBuilder<FabricClientCommandSource> cmd = ClientCommands.literal(name)
                .executes(c -> open(ClickGuiScreen::new))
                .then(ClientCommands.literal("hud")
                        .executes(c -> open(HudEditorScreen::new)))
                .then(ClientCommands.literal("invbuttons")
                        .executes(c -> open(() -> new InventoryButtonsScreen(null))));
        dispatcher.register(cmd);
    }

    /**
     * Opens a screen once the command has finished running. Setting it directly would fight with the
     * chat screen closing itself immediately afterwards, which would drop us back to the game.
     */
    private static int open(Supplier<Screen> screen) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(screen.get()));
        return 1;
    }
}
