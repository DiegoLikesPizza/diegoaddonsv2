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
 * Shows where an etherwarp would land you while you sneak with an etherwarp item, and optionally
 * gives the warp itself a different sound.
 *
 * <p>The highlight is green when the warp would work and red when it would not - see
 * {@link EtherwarpHelper} for what "would work" means, which is mostly about the two blocks of
 * headroom you cannot see from where you are standing.
 *
 * <p>The sound <b>replaces</b> the teleport's own noise rather than announcing that the aim has gone
 * valid. A tone on becoming ready fired over and over while you swept the aim around; on the warp it
 * lands once, when something actually happened. See
 * {@link dev.diego.diegoaddons.mixin.EtherwarpSoundMixin}.
 */
public class EtherwarpModule extends Module {
    public static EtherwarpModule INSTANCE;

    private final BooleanSetting highlight =
            new BooleanSetting(this, "highlight", "Highlight landing block", true);
    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Replace teleport sound", false);
    private final CycleSetting soundType =
            new CycleSetting(this, "soundType", "Sound", 0, "Pling", "Bell", "Harp", "Anvil", "Orb");
    private final NumberSetting soundPitch =
            new NumberSetting(this, "soundPitch", "Sound pitch", 1.6, 0.5, 2.0, 0.1);
    /** Temporary: says in chat what the helper sees, to find out why a highlight does not appear. */
    private final BooleanSetting debug =
            new BooleanSetting(this, "debug", "Debug (say what it sees)", false);

    public EtherwarpModule() {
        super("etherwarp", Category.MISC, "Etherwarp Helper",
                "Show where an etherwarp would land while sneaking.");
        settings.add(highlight);
        settings.add(sound);
        settings.add(soundType);
        settings.add(soundPitch);
        settings.add(debug);
        INSTANCE = this;
    }

    /** Whether to draw the box on the aimed block. */
    public boolean highlight() {
        return highlight.get();
    }

    /** Whether the teleport's own sound should be swapped for the one picked here. */
    public boolean replacesSound() {
        return sound.get();
    }

    public float soundPitch() {
        return (float) soundPitch.get();
    }

    public boolean debug() {
        return debug.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        EtherwarpHelper.tick(mc);
    }

    /** The sound event picked by the cycle setting. */
    public SoundEvent chosenSound() {
        return switch (soundType.get()) {
            case 1 -> SoundEvents.NOTE_BLOCK_BELL.value();
            case 2 -> SoundEvents.NOTE_BLOCK_HARP.value();
            case 3 -> SoundEvents.ANVIL_LAND;
            case 4 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            default -> SoundEvents.NOTE_BLOCK_PLING.value();
        };
    }
}
