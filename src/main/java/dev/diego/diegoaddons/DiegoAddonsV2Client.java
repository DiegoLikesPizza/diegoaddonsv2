package dev.diego.diegoaddons;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.module.ModuleManager;
import dev.diego.diegoaddons.util.SkinChanger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for DiegoAddons V2.
 *
 * <ul>
 *   <li>Loads the per-instance config.</li>
 *   <li>Registers the "open menu" key mapping (default: backslash).</li>
 *   <li>Each client tick: opens the configlib settings screen when the key is pressed in-game.</li>
 * </ul>
 */
public class DiegoAddonsV2Client implements ClientModInitializer {
    public static final String MOD_ID = "diegoaddonsv2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyMapping OPEN_MENU;

    /** configlib's view of the module list. Opens the menu, and will own the HUD next. */
    public static dev.diego.configlib.ConfigHandle<?> CONFIG;

    @Override
    public void onInitializeClient() {
        SkinChanger.ensureFolder();
        ModuleManager.init();
        // After ModuleManager.init(), because the spec describes what is actually registered.
        //
        // configlib owns the config now: registering builds the spec off the live modules and then
        // reads the file straight into them. The separate path is kept because the old file still
        // exists in people's instances and is read once, below, to carry their settings over.
        // Registering reads the file, which means calling every setter - so it runs marked as a
        // load. Otherwise each restored value would ask for a save while the file was still being
        // read, and every module that came back enabled would wake up here rather than below.
        dev.diego.diegoaddons.config.ConfigManager.whileLoading(() ->
                CONFIG = dev.diego.configlib.ConfigLib.builder(MOD_ID)
                        .spec(dev.diego.diegoaddons.config.ModuleSpec.build())
                        .title("DiegoAddons V2")
                        .subtitle(net.fabricmc.loader.api.FabricLoader.getInstance()
                                .getModContainer(MOD_ID)
                                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
                                .orElse(""))
                        .file(net.fabricmc.loader.api.FabricLoader.getInstance()
                                .getConfigDir().resolve("diegoaddonsv2-configlib.json"))
                        .register());
        // A config written by 2.4.1 or earlier, carried over once. Does nothing if there is none,
        // or if configlib's own file already has settings in it.
        dev.diego.diegoaddons.config.LegacyImport.run(CONFIG);
        // The mod's theme, reaching the parts configlib draws. Both are suppliers rather than
        // values, because configlib re-reads them every frame - so changing the theme in the menu
        // recolours the menu you are standing in rather than waiting for a restart.
        CONFIG.accent(() -> dev.diego.diegoaddons.gui.Themes.accent());
        CONFIG.hudStyle(dev.diego.diegoaddons.hud.HudElements::sharedStyle);
        // Everything that came back enabled is woken here rather than as the flag was set, because
        // the flags are set while this method is still running and a module's onEnable expects a
        // client that exists.
        ModuleManager.applyEnabled();
        // The inventory buttons and the party finder's class picker attach themselves to any
        // container screen that qualifies, deciding per screen whether they apply - so this stays
        // The drawing half of the HUD, attached after registration because it binds to the HUD
        // nodes the spec just declared.
        dev.diego.diegoaddons.hud.HudElements.attach(CONFIG);
        // configlib's own main menu, offered through the Title Screen module. install() hooks the
        // swap once; whether it actually happens is read live from the module every tick, so
        // turning it on or off in the menu takes effect the next time the title screen appears
        // rather than at the next restart.
        dev.diego.configlib.menu.MenuFeature.install(CONFIG);
        // The custom menu replaces the vanilla title screen outright, taking the Hypixel button
        // that stands in for Realms with it. Put it back on the new screen, so switching menus
        // changes how the screen looks rather than what it can do.
        dev.diego.configlib.menu.MenuFeature.extraButton(CONFIG,
                dev.diego.diegoaddons.util.Hypixel.LABEL,
                dev.diego.diegoaddons.util.Hypixel::connect);
        dev.diego.diegoaddons.util.WorldRender.init();
        dev.diego.diegoaddons.command.DiegoCommands.register();

        OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.diegoaddonsv2.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSLASH,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            dev.diego.diegoaddons.util.AccessibilityDefaults.tick(client);
            syncCustomMenu();

            // The one-time welcome, the first time the title screen shows up in this instance.
            // Either title screen counts: configlib's own is a plain Screen rather than a
            // TitleScreen, and it may well have taken over before this runs - so keying on the
            // vanilla one alone means the welcome never appears for anyone using the custom menu,
            // which is now the default.
            if (!ConfigManager.get().introShown && atTitleScreen(client.screen)) {
                client.setScreen(new dev.diego.diegoaddons.gui.IntroScreen(client.screen));
            }

            while (OPEN_MENU.consumeClick()) {
                if (client.screen == null) {
                    CONFIG.open();
                }
            }

        });

        LOGGER.info("[DiegoAddons V2] Initialised for Minecraft 26.1.2 - press '\\' to open the menu");
    }

    /** Whether this screen is a main menu - the vanilla one, or configlib's standing in for it. */
    private static boolean atTitleScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof net.minecraft.client.gui.screens.TitleScreen
                || screen instanceof dev.diego.configlib.menu.MainMenuScreen;
    }

    /**
     * The custom-menu switch as last seen on each side, so a change can be told from a difference.
     *
     * <p>Null until the first tick has looked at both.
     */
    private static Boolean lastModuleMenu;
    private static Boolean lastMenuEnabled;

    /**
     * Keeps configlib's menu switch and the Title Screen module's own in step, in both directions.
     *
     * <p>The same switch is reachable from two places - the module's card in the settings menu, and
     * the title screen's own options - so neither can simply be treated as the truth. Whichever
     * <i>changed</i> since the last tick is the one that wins; a plain difference says nothing about
     * who moved. Pushing one way unconditionally is what made the title-screen switch flip back a
     * tick after it was touched.
     *
     * <p>Written only when the two actually differ: both setters persist, and pushing the same
     * value every tick would rewrite the config twenty times a second.
     */
    private static void syncCustomMenu() {
        var module = dev.diego.diegoaddons.module.modules.TitleScreenModule.INSTANCE;
        if (module == null) {
            return;
        }
        var menu = dev.diego.configlib.menu.MenuFeature.settings(CONFIG);
        boolean fromModule = module.customMenu();
        boolean fromMenu = menu.enabled();

        if (lastModuleMenu == null) {
            // First look: nothing has "changed" yet, so the mod's own saved state is the truth.
            if (fromMenu != fromModule) {
                menu.enabled(fromModule);
            }
        } else if (fromModule != lastModuleMenu) {
            if (fromMenu != fromModule) {
                menu.enabled(fromModule);
            }
        } else if (fromMenu != lastMenuEnabled) {
            module.customMenu(fromMenu);
        }

        lastModuleMenu = module.customMenu();
        lastMenuEnabled = menu.enabled();
    }
}
