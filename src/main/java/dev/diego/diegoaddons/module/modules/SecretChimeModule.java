package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.SecretChime;
import net.minecraft.client.Minecraft;

/**
 * Plays a short note whenever a dungeon secret is found, so you can tell without watching the tab
 * list.
 *
 * <p>See {@link SecretChime} - the count is read from the tab list rather than from chat, because
 * not every kind of secret announces itself in chat.
 */
public class SecretChimeModule extends Module {
    public static SecretChimeModule INSTANCE;

    public SecretChimeModule() {
        super("secretchime", Category.DUNGEONS, "Secret Chime",
                "Play a sound when a dungeon secret is found.");
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        SecretChime.tick(mc);
    }
}
