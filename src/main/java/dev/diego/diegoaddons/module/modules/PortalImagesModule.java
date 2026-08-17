package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.CustomImages;
import dev.diego.diegoaddons.util.ImageQuad;
import dev.diego.diegoaddons.util.PortalImages;
import net.minecraft.client.Minecraft;

/**
 * Hangs your own PNGs on the portals around you.
 *
 * <p>Drop the files into {@code <config>/diegoaddons/images/}, then stand in front of a portal and
 * type {@code /da portal <file>} to say which one goes there. Every portal without an assignment of
 * its own shows {@link #defaultImage()}, so a single file named {@code portal.png} is enough to see
 * the feature work before deciding what belongs where.
 *
 * <p>The picture is hung a couple of centimetres off the portal's own surface rather than replacing
 * it: nothing here touches how the game draws a portal block, so a translucent PNG shows the swirl
 * through it and turning the module off leaves the world exactly as it was.
 */
public class PortalImagesModule extends Module {
    /** The one registered instance, so {@code /da portal} can reach its settings. */
    public static PortalImagesModule INSTANCE;

    private final StringSetting image =
            new StringSetting(this, "image", "Default image", "portal.png", null);

    private final CycleSetting fit = new CycleSetting(this, "fit", "Fit", ImageQuad.FILL,
            "Stretch", "Fill", "Fit");

    private final NumberSetting opacity =
            new NumberSetting(this, "opacity", "Opacity", 100, 5, 100, 5);

    private final BooleanSetting bothSides =
            new BooleanSetting(this, "bothSides", "Both sides", true);

    private final NumberSetting radius =
            new NumberSetting(this, "radius", "Search radius", 24, 8, 48, 1);

    private final StringSetting block =
            new StringSetting(this, "block", "Portal block", "minecraft:nether_portal", null);

    private final ActionSetting reload = new ActionSetting(this, "reload", "Reload images",
            "Reload", () -> Minecraft.getInstance().execute(CustomImages::reload));

    public PortalImagesModule() {
        super("portalimages", Category.RENDER, "Portal Images",
                "Shows your own PNGs on portals (/da portal <file>).");
        INSTANCE = this;
        settings.add(image);
        settings.add(fit);
        settings.add(opacity);
        settings.add(bothSides);
        settings.add(radius);
        settings.add(block);
        settings.add(reload);
    }

    /** The file used for a portal with no assignment of its own. */
    public String defaultImage() {
        return image.get();
    }

    public int fitMode() {
        return fit.get();
    }

    /** White at the chosen opacity - the tint every picture is drawn with. */
    public int tint() {
        int alpha = (int) Math.round(opacity.get() * 2.55);
        return (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFFFFF;
    }

    public boolean bothSides() {
        return bothSides.get();
    }

    public int radius() {
        return (int) radius.get();
    }

    public String blockId() {
        return block.get();
    }

    @Override
    protected void onDisable() {
        PortalImages.clear();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        PortalImages.tick(this);
    }
}
