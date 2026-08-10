package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.Minigames;
import net.minecraft.client.Minecraft;

/**
 * Games against another DiegoAddons user, played through whispers.
 *
 * <p>{@code /da play <name>} invites someone; they get a chat line with buttons; the board is a
 * screen of the mod's own ({@link dev.diego.diegoaddons.gui.GameScreen}). The moves travel as
 * marked whispers that both clients hide from the chat, so a game looks like nothing at all to
 * anyone else - and to the two playing it, like a GUI rather than like chat.
 *
 * <p>Off by default, and it sends nothing until you invite someone: the mod whispering on your
 * behalf is not something to opt out of after the fact.
 */
public class MinigamesModule extends Module {
    public static MinigamesModule INSTANCE;

    private final BooleanSetting hideProtocol =
            new BooleanSetting(this, "hideProtocol", "Hide protocol lines", true);
    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Sound on invitation", true);
    private final BooleanSetting autoDecline =
            new BooleanSetting(this, "autoDecline", "Decline invitations automatically", false);

    public MinigamesModule() {
        super("minigames", Category.MISC, "Minigames",
                "Noughts and crosses, connect four, blackjack and battleships against "
                        + "another DiegoAddons user: /da play <name> [ttt|c4|bj|bs].");
        settings.add(hideProtocol);
        settings.add(sound);
        settings.add(autoDecline);
        INSTANCE = this;
        Minigames.init();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        Minigames.tick(mc);
    }

    @Override
    protected void onDisable() {
        // Switching the module off in the middle of a game leaves the other side waiting forever,
        // so the game goes with it. Nothing is sent - the module is off.
        Minigames.reset();
    }

    /**
     * Whether the protocol's own whispers are kept out of the chat.
     *
     * <p>A setting rather than always-on because it is the one thing here that is worth being able
     * to see: if a game is not working, the lines going past are the whole diagnosis.
     */
    public boolean hideProtocol() {
        return hideProtocol.get();
    }

    public boolean sound() {
        return sound.get();
    }

    public boolean autoDecline() {
        return autoDecline.get();
    }
}
