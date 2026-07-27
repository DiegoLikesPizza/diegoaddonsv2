package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.EtherwarpHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

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
    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Sound when ready", false);
    private final CycleSetting soundType =
            new CycleSetting(this, "soundType", "Sound", 0, "Pling", "Bell", "Harp", "Anvil", "Orb");
    private final NumberSetting soundPitch =
            new NumberSetting(this, "soundPitch", "Sound pitch", 1.6, 0.5, 2.0, 0.1);

    /** Was the aim on a valid block last tick, so the sound fires once on becoming ready. */
    private boolean wasValid;

    public EtherwarpModule() {
        super("etherwarp", Category.MISC, "Etherwarp Helper",
                "Show where an etherwarp would land while sneaking.");
        settings.add(zoom);
        settings.add(zoomAmount);
        settings.add(sound);
        settings.add(soundType);
        settings.add(soundPitch);
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
        boolean valid = EtherwarpHelper.valid();
        if (valid && !wasValid && sound.get() && mc.player != null) {
            mc.player.playSound(chosenSound(), 1.0f, (float) soundPitch.get());
        }
        wasValid = valid;
    }

    /** The sound event picked by the cycle setting. */
    private SoundEvent chosenSound() {
        return switch (soundType.get()) {
            case 1 -> SoundEvents.NOTE_BLOCK_BELL.value();
            case 2 -> SoundEvents.NOTE_BLOCK_HARP.value();
            case 3 -> SoundEvents.ANVIL_LAND;
            case 4 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            default -> SoundEvents.NOTE_BLOCK_PLING.value();
        };
    }
}
