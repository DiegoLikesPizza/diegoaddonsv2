package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.StringSetting;

/**
 * Replaces Minecraft's title screen with this mod's own: a moving gradient, and the four buttons
 * anybody actually presses.
 *
 * <p>Vanilla's has grown a row of things most people never touch, and the ones they do are the same
 * four every time. This keeps those, adds a button that goes straight to Hypixel, and a way into the
 * addon's own settings without loading a world first.
 *
 * <p>Off switches the whole thing back: the replacement is a screen extension that simply stops
 * applying, so vanilla's own title screen is what draws.
 */
public class CustomTitleScreenModule extends Module {
    public static CustomTitleScreenModule INSTANCE;

    private final BooleanSetting animate =
            new BooleanSetting(this, "animate", "Moving background", true);
    private final BooleanSetting hypixel =
            new BooleanSetting(this, "hypixel", "Join Hypixel button", true);
    private final StringSetting server =
            new StringSetting(this, "server", "Server address", "mc.hypixel.net", null);

    public CustomTitleScreenModule() {
        super("customtitlescreen", Category.RENDER, "Custom Title Screen",
                "Replace the main menu with a themed one, and a button straight to Hypixel.");
        settings.add(animate);
        settings.add(hypixel);
        settings.add(server);
        INSTANCE = this;
    }

    public boolean animate() {
        return animate.get();
    }

    public boolean showHypixel() {
        return hypixel.get();
    }

    /** Where the Hypixel button connects to - editable, since the address has changed before. */
    public String server() {
        return server.get();
    }
}
