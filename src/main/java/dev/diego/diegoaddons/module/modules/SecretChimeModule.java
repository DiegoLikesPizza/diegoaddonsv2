package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.SecretChime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Plays a short note whenever a dungeon secret is found, so you can tell without watching the tab
 * list.
 *
 * <p>See {@link SecretChime} - the count is read from the tab list rather than from chat, because
 * not every kind of secret announces itself in chat.
 */
public class SecretChimeModule extends Module {
    public static SecretChimeModule INSTANCE;

    private static final String DEFAULT_SOUND = "minecraft:block.note_block.pling";

    // No chooser of its own: the sound is picked through configlib's searchable picker, declared
    // in ListSpecs. Leaving one here would give two ways in that could drift apart.
    private final StringSetting sound = new StringSetting(this, "sound", "Sound", DEFAULT_SOUND, null);
    private final NumberSetting pitch =
            new NumberSetting(this, "pitch", "Pitch", 2.0, 0.5, 2.0, 0.1);
    private final dev.diego.diegoaddons.module.BooleanSetting onInteract =
            new dev.diego.diegoaddons.module.BooleanSetting(this, "onInteract",
                    "Chime on chests, essences and levers", true);
    private final dev.diego.diegoaddons.module.BooleanSetting onPickup =
            new dev.diego.diegoaddons.module.BooleanSetting(this, "onPickup",
                    "Chime on picking an item up", true);

    public SecretChimeModule() {
        super("secretchime", Category.DUNGEONS, "Secret Chime",
                "Play a sound when a dungeon secret is found.");
        // Not added: the sound is declared to configlib as a picker (see ListSpecs), and
        // adding it here as well would put the same value on the card twice.
        settings.add(pitch);
        settings.add(onInteract);
        settings.add(onPickup);
        INSTANCE = this;
    }

    /**
     * The chosen sound, as an event the client can play.
     *
     * <p>Built from the id rather than looked up in the registry, so a sound a resource pack adds
     * works exactly as well as one the game ships - the client plays what it is asked for by name.
     */
    public SoundEvent chosenSound() {
        Identifier id = Identifier.tryParse(sound.get());
        return SoundEvent.createVariableRangeEvent(id == null
                ? Identifier.parse(DEFAULT_SOUND) : id);
    }

    /** The chosen sound id, for configlib's picker to read and write. */
    public String soundId() {
        return sound.get();
    }

    public void setSoundId(String id) {
        sound.set(id);
    }

    public float pitch() {
        return (float) pitch.get();
    }

    public boolean onInteract() {
        return onInteract.get();
    }

    public boolean onPickup() {
        return onPickup.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        SecretChime.tick(mc);
    }
}
