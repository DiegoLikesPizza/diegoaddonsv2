package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
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
    /**
     * How long to wait after the menu appears before clicking the preset.
     *
     * <p>Zero used to be the only option: the click went in on the first tick the menu was open and
     * the preset was found. That is fine when the menu arrives complete, and it is exactly what
     * breaks when SkyBlock is still filling it in - a click into a half-built menu lands on whatever
     * happens to be at that slot number, which is the one failure this whole feature is built to
     * avoid. A short wait costs nothing and removes that race.
     */
    private final NumberSetting clickDelay =
            new NumberSetting(this, "clickDelay", "Wait before clicking (ms)", 150, 0, 2000, 25);
    /**
     * How long to wait after the click before shutting the menu.
     *
     * <p>Was a fixed 100 ms in the code. The reasoning behind it has not changed - SkyBlock answers
     * a loadout click by rewriting the menu, and closing into the middle of that leaves the client
     * and the server disagreeing about what is open - but the right number depends on the ping and
     * was never ours to pick for everyone.
     */
    private final NumberSetting closeDelay =
            new NumberSetting(this, "closeDelay", "Wait before closing (ms)", 100, 0, 2000, 25);
    /**
     * Vary each wait by a percentage instead of using it exactly.
     *
     * <p>What this is actually good for is not landing on the same moment every time: a fixed delay
     * that happens to fall just before the menu finishes populating fails <i>every</i> swap, while a
     * varying one fails some and succeeds others, which is both less bad and far easier to notice
     * and tune. It does not make the sequence look hand-driven - three menu actions at 150 ms are
     * machine-timed whether or not the number moves.
     */
    private final BooleanSetting randomDelay =
            new BooleanSetting(this, "randomDelay", "Vary the waits", false);
    private final NumberSetting randomPercent =
            new NumberSetting(this, "randomPercent", "Vary by (±%)", 15, 1, 50, 1);

    public LoadoutKeybindModule() {
        super("loadoutkeys", Category.MISC, "Loadout Keybinds",
                "Bind a key to a loadout and switch to it without opening the menu.");
        settings.add(command);
        settings.add(clickDelay);
        settings.add(closeDelay);
        settings.add(randomDelay);
        settings.add(randomPercent);
        INSTANCE = this;
    }

    /** The command that opens the Loadouts menu. */
    public String command() {
        return command.get();
    }

    public long clickDelayMs() {
        return (long) clickDelay.get();
    }

    public long closeDelayMs() {
        return (long) closeDelay.get();
    }

    public boolean randomDelay() {
        return randomDelay.get();
    }

    public double randomPercent() {
        return randomPercent.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        LoadoutKeys.tick(mc);
    }
}
