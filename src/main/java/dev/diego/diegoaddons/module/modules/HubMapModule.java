package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.CustomImages;
import dev.diego.diegoaddons.util.HubMap;
import dev.diego.diegoaddons.util.ImageQuad;
import net.minecraft.client.Minecraft;

/**
 * Puts your own PNG over the big map in the Hub.
 *
 * <p>Drop the file into {@code <config>/diegoaddons/images/} and name it here. The wall itself is
 * found by looking for the framed maps it is built from, so nothing has to be typed in coordinates
 * and the same module covers any other map wall you stand in front of - see
 * {@link HubMap} for what is assumed and how to check it.
 */
public class HubMapModule extends Module {
    /** The one registered instance, so the commands can reach its settings. */
    public static HubMapModule INSTANCE;

    private final StringSetting image =
            new StringSetting(this, "image", "Image", "hubmap.png", null);

    private final CycleSetting fit = new CycleSetting(this, "fit", "Fit", ImageQuad.FILL,
            "Stretch", "Fill", "Fit");

    private final NumberSetting opacity =
            new NumberSetting(this, "opacity", "Opacity", 100, 5, 100, 5);

    private final BooleanSetting bothSides =
            new BooleanSetting(this, "bothSides", "Both sides", false);

    private final BooleanSetting hubOnly =
            new BooleanSetting(this, "hubOnly", "Only in the Hub", true);

    private final NumberSetting radius =
            new NumberSetting(this, "radius", "Search radius", 48, 16, 96, 4);

    private final NumberSetting minFrames =
            new NumberSetting(this, "minFrames", "Smallest wall (frames)", 4, 1, 32, 1);

    private final BooleanSetting anyItem =
            new BooleanSetting(this, "anyItem", "Any framed item", false);

    private final ActionSetting debug = new ActionSetting(this, "debug", "Count frames (log)",
            "Run", () -> Minecraft.getInstance().execute(() -> HubMap.debug(this)));

    private final ActionSetting reload = new ActionSetting(this, "reload", "Reload images",
            "Reload", () -> Minecraft.getInstance().execute(CustomImages::reload));

    public HubMapModule() {
        super("hubmap", Category.RENDER, "Hub Map",
                "Shows your own PNG over the big framed map in the Hub.");
        INSTANCE = this;
        settings.add(image);
        settings.add(fit);
        settings.add(opacity);
        settings.add(bothSides);
        settings.add(hubOnly);
        settings.add(radius);
        settings.add(minFrames);
        settings.add(anyItem);
        settings.add(debug);
        settings.add(reload);
    }

    public String image() {
        return image.get();
    }

    public int fitMode() {
        return fit.get();
    }

    /** White at the chosen opacity - the tint the picture is drawn with. */
    public int tint() {
        int alpha = (int) Math.round(opacity.get() * 2.55);
        return (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFFFFF;
    }

    public boolean bothSides() {
        return bothSides.get();
    }

    public boolean hubOnly() {
        return hubOnly.get();
    }

    public double radius() {
        return radius.get();
    }

    public int minFrames() {
        return (int) minFrames.get();
    }

    /** Whether a frame counts even when what is in it is not a map. */
    public boolean anyItem() {
        return anyItem.get();
    }

    @Override
    protected void onDisable() {
        HubMap.clear();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        HubMap.tick(this);
    }
}
