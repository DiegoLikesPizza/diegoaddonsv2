package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.StringSetting;

/**
 * Small changes to Minecraft's own title screen: Realms becomes a button that joins Hypixel, and the
 * language and accessibility icons in the corner go.
 *
 * <p>Deliberately not a replacement menu. One of those existed for a version and was thrown away -
 * RenderLib draws its screens into a fixed 16:9 canvas, so on any other shape of window a strip down
 * each side is dead. Vanilla's menu already works at every aspect ratio; it only needed three things
 * changed. See {@link dev.diego.diegoaddons.mixin.TitleScreenMixin}.
 */
public class TitleScreenModule extends Module {
    public static TitleScreenModule INSTANCE;

    private final BooleanSetting replaceRealms =
            new BooleanSetting(this, "realms", "Replace Realms with a server button", true);
    private final BooleanSetting hideCorners =
            new BooleanSetting(this, "corners", "Hide language and accessibility", true);
    private final StringSetting label =
            new StringSetting(this, "label", "Button text", "Join Hypixel", null);
    private final StringSetting server =
            new StringSetting(this, "server", "Server address", "mc.hypixel.net", null);

    public TitleScreenModule() {
        super("titlescreen", Category.RENDER, "Title Screen",
                "Replace Realms with a one-click server join, and drop the corner icons.");
        settings.add(replaceRealms);
        settings.add(hideCorners);
        settings.add(label);
        settings.add(server);
        INSTANCE = this;
    }

    public boolean replaceRealms() {
        return replaceRealms.get();
    }

    public boolean hideCornerButtons() {
        return hideCorners.get();
    }

    public String buttonLabel() {
        return label.get();
    }

    /** Where the button connects to - a setting, because the address has changed before. */
    public String server() {
        return server.get();
    }
}
