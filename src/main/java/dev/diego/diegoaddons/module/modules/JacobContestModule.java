package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.Garden;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Jacob's Contest on the HUD: how long is left of the one running, and which crops it is for.
 *
 * <p>Deliberately the tab list's own words. Hypixel already writes the header and the three crops,
 * and the marker on a crop is its farming-fortune boost - re-wording any of that can only introduce
 * a way to be wrong that the original does not have. What is not here is the *next* contest and the
 * ones after it: that comes from an external contest API, and it is a dependency rather than a line
 * of parsing.
 *
 * <p>Not restricted to the Garden. The widget shows anywhere in SkyBlock, and a contest you want to
 * be reminded of is one you are not currently farming for.
 */
public class JacobContestModule extends HudModule {
    public static JacobContestModule INSTANCE;

    private final BooleanSetting showCrops =
            new BooleanSetting(this, "showCrops", "Show the crops", true);
    private final BooleanSetting onlyDuring =
            new BooleanSetting(this, "onlyDuring", "Only while a contest is running", false);

    public JacobContestModule() {
        super("jacobcontest", Category.GARDEN, "Jacob's Contest",
                "The running contest and its crops, from the tab list.");
        settings.add(showCrops);
        settings.add(onlyDuring);
        INSTANCE = this;
    }

    @Override
    protected String label() {
        return "Contest";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // header plus crops; see hudLines
    }

    @Override
    protected String sampleValue() {
        return "19m left";
    }

    @Override
    public List<String> hudLines(Minecraft mc) {
        List<String> widget = Garden.contest();
        if (widget.isEmpty()) {
            return List.of();
        }
        String header = widget.getFirst();
        boolean running = header.toLowerCase(Locale.ROOT).contains("left");
        if (onlyDuring.get() && !running) {
            return List.of();
        }
        List<String> out = new ArrayList<>(widget.size());
        // The header already says "Jacob's Contest", so the chip's own caption would say it twice.
        out.add(showLabel() ? header : header.replace("Jacob's Contest:", "").trim());
        if (showCrops.get()) {
            for (int i = 1; i < widget.size(); i++) {
                out.add(widget.get(i));
            }
        }
        return out;
    }
}
