package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.LoadoutKeys;
import net.minecraft.client.Minecraft;

/**
 * A key per loadout: press it and the loadout is equipped, without opening anything by hand.
 *
 * <p>The list lives on this card - a row per loadout, with its name and the key to bind, the key
 * captured by pressing it rather than typed. Both halves are needed and neither is guessable, which
 * is why this got the config library's first key field rather than a text box asking for "F7".
 *
 * <p>The command that opens the Loadouts menu is a <b>text box, not a constant</b>. It is the one
 * part of this feature that is a guess about Hypixel, and the Storage Overlay already made the case
 * for that shape: a wrong constant is a feature nobody can fix, while a wrong default in a text box
 * is one line to correct.
 */
public class LoadoutKeybindModule extends Module {
    public static LoadoutKeybindModule INSTANCE;

    private final StringSetting command =
            new StringSetting(this, "command", "Menu command", "/loadout", null);

    public LoadoutKeybindModule() {
        super("loadoutkeys", Category.MISC, "Loadout Keybinds",
                "Bind a key to a loadout and switch to it without opening the menu.");
        settings.add(command);
        INSTANCE = this;
    }

    /** The command that opens the Loadouts menu. */
    public String command() {
        return command.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        LoadoutKeys.tick(mc);
    }
}
