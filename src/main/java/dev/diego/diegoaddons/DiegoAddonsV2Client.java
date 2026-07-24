package dev.diego.diegoaddons;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.ClickGuiScreen;
import dev.diego.diegoaddons.gui.IntroScreen;
import dev.diego.diegoaddons.module.ModuleManager;
import dev.diego.diegoaddons.util.SkinChanger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for DiegoAddons V2.
 *
 * <ul>
 *   <li>Loads the per-instance config.</li>
 *   <li>Registers the "open menu" key mapping (default: backslash).</li>
 *   <li>Each client tick: opens {@link MainScreen} when the key is pressed in-game, and shows the
 *       one-time {@link IntroScreen} the first time the title screen appears in this instance.</li>
 * </ul>
 */
public class DiegoAddonsV2Client implements ClientModInitializer {
    public static final String MOD_ID = "diegoaddonsv2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyMapping OPEN_MENU;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        SkinChanger.ensureFolder();
        ModuleManager.init();

        OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.diegoaddonsv2.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSLASH,
                KeyMapping.Category.MISC));
        dev.diego.diegoaddons.module.modules.WardrobeKeybindsModule.registerKeys();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ClickGuiScreen());
                }
            }

            if (!ConfigManager.get().introShown && client.screen instanceof TitleScreen) {
                client.setScreen(new IntroScreen(client.screen));
            }
        });

        LOGGER.info("[DiegoAddons V2] Initialised for Minecraft 26.1.2 - press '\\' to open the menu");
    }
}
