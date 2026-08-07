package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.CommandHotkeys;
import net.minecraft.client.Minecraft;

/**
 * Binds commands to keys - any number of them, managed in this feature's own list rather than as
 * fixed settings, since which commands you want is entirely personal.
 *
 * <p>Hotkeys only fire during normal play, never while a screen is open, so a bound letter stays
 * typeable in chat.
 */
public class CommandHotkeysModule extends Module {
    public static CommandHotkeysModule INSTANCE;


    public CommandHotkeysModule() {
        super("commandhotkeys", Category.MISC, "Command Hotkeys",
                "Run your own commands from key presses.");
        // The list itself is a row on this card now, declared in ListSpecs - a button that
        // opens a screen showing the same thing would be a second door to one room.
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        CommandHotkeys.tick(mc);
    }
}
