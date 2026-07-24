package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.EtherwarpHelper;
import net.minecraft.client.Minecraft;

/**
 * Highlights the block an etherwarp would land you on while sneaking, green when the warp would work
 * and red when it would not. See {@link EtherwarpHelper} for what "would work" checks.
 *
 * <p>The optional zoom narrows the field of view while aiming, which makes distant blocks easier to
 * pick out - the same reason a spyglass exists.
 */
public class EtherwarpModule extends Module {
    public static EtherwarpModule INSTANCE;

    private final BooleanSetting zoom =
            new BooleanSetting(this, "zoom", "Zoom while aiming", false);
    private final NumberSetting zoomAmount =
            new NumberSetting(this, "zoomAmount", "Zoom strength", 1.5, 1.1, 4.0, 0.1);

    public EtherwarpModule() {
        super("etherwarp", Category.MISC, "Etherwarp Helper",
                "Show where an etherwarp would land while sneaking.");
        settings.add(zoom);
        settings.add(zoomAmount);
        INSTANCE = this;
    }

    /** The divisor to apply to the field of view, or 1 when the zoom is off or not aiming. */
    public double zoomFactor() {
        if (!zoom.get() || EtherwarpHelper.target() == null) {
            return 1.0;
        }
        return zoomAmount.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        EtherwarpHelper.tick(mc);
    }
}
