package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.SecretChime;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Plays a short note whenever a dungeon secret is found, so you can tell without watching the tab
 * list.
 *
 * <p>See {@link SecretChime} - the count is read from the tab list rather than from chat, because
 * not every kind of secret announces itself in chat.
 */
public class SecretChimeModule extends Module {
    public static SecretChimeModule INSTANCE;

    private final CycleSetting sound = new CycleSetting(this, "sound", "Sound", 0,
            "Pling", "Bell", "Chime", "Harp", "Xylophone", "Orb", "Anvil");
    private final NumberSetting pitch =
            new NumberSetting(this, "pitch", "Pitch", 2.0, 0.5, 2.0, 0.1);

    public SecretChimeModule() {
        super("secretchime", Category.DUNGEONS, "Secret Chime",
                "Play a sound when a dungeon secret is found.");
        settings.add(sound);
        settings.add(pitch);
        INSTANCE = this;
    }

    /** The sound picked in the settings. */
    public SoundEvent chosenSound() {
        return switch (sound.get()) {
            case 1 -> SoundEvents.NOTE_BLOCK_BELL.value();
            case 2 -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case 3 -> SoundEvents.NOTE_BLOCK_HARP.value();
            case 4 -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
            case 5 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case 6 -> SoundEvents.ANVIL_LAND;
            default -> SoundEvents.NOTE_BLOCK_PLING.value();
        };
    }

    public float pitch() {
        return (float) pitch.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        SecretChime.tick(mc);
    }
}
