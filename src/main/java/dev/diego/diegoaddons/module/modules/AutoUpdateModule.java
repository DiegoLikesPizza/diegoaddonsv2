package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.Toasts;
import dev.diego.diegoaddons.util.Updater;
import net.minecraft.client.Minecraft;

/**
 * Keeps the mod current: looks for a newer release, fetches it, and swaps it in at the next restart.
 *
 * <p>How far it goes is the {@link #mode} setting, because downloading and running code without
 * being asked is not something to decide on someone's behalf:
 *
 * <ul>
 *   <li><b>Notify only</b> - say a new version exists and stop there.</li>
 *   <li><b>Download</b> - fetch it into {@code <game>/diegoaddons-updates/} and leave it for you to
 *       move in.</li>
 *   <li><b>Download &amp; install</b> - fetch it and put it in the mods folder when the game closes,
 *       keeping the old jar beside it as {@code diegoaddonsv2-previous.jar.bak}.</li>
 * </ul>
 *
 * <p>The module ships off, so nothing goes out to the network until it is switched on. The work
 * itself is in {@link Updater}; this is the card that drives it.
 */
public class AutoUpdateModule extends Module {
    public static AutoUpdateModule INSTANCE;

    public static final int NOTIFY = 0;
    public static final int DOWNLOAD = 1;
    public static final int INSTALL = 2;

    private final CycleSetting mode = new CycleSetting(this, "mode", "What to do", INSTALL,
            "Notify only", "Download", "Download & install");
    private final BooleanSetting onStart =
            new BooleanSetting(this, "onStart", "Check when the game starts", true);
    private final NumberSetting every =
            new NumberSetting(this, "every", "Check again every (hours, 0 = never)", 6, 0, 24, 1);
    private final BooleanSetting prereleases =
            new BooleanSetting(this, "prereleases", "Include pre-releases", false);
    private final BooleanSetting announce =
            new BooleanSetting(this, "announce", "Say so in chat as well", true);
    private final ActionSetting checkNow =
            new ActionSetting(this, "checkNow", "Check now", "Check", this::checkNow);

    /** Client ticks since this module last started a check; -1 until the first one has run. */
    private int ticks;
    private boolean checkedThisSession;

    public AutoUpdateModule() {
        super("autoupdate", Category.MISC, "Auto Update",
                "Download new versions of DiegoAddons as they are released.");
        settings.add(mode);
        settings.add(onStart);
        settings.add(every);
        settings.add(prereleases);
        settings.add(announce);
        settings.add(checkNow);
        INSTANCE = this;
    }

    /**
     * Waits a few seconds after being switched on before the first check.
     *
     * <p>Enabling happens during startup for a module that was already on, and a toast landing while
     * the client is still building itself has nowhere to draw. Ten seconds also keeps the check off
     * the critical path of getting to the title screen.
     */
    @Override
    protected void onEnable() {
        ticks = 0;
        checkedThisSession = false;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        ticks++;
        if (!checkedThisSession) {
            if (onStart.get() && ticks >= 200) {
                checkedThisSession = true;
                ticks = 0;
                start();
            }
            return;
        }
        int hours = (int) every.get();
        if (hours <= 0) {
            return;
        }
        if (ticks >= hours * 72_000) {   // 20 ticks a second, 3600 seconds an hour
            ticks = 0;
            start();
        }
    }

    private void start() {
        check(false);
    }

    /**
     * The "Check now" button, and {@code /da update}. Unlike the timed check this always reports
     * back, so pressing it on an up-to-date install says so rather than looking like it did nothing.
     */
    public void checkNow() {
        checkedThisSession = true;
        ticks = 0;
        Toasts.show("DiegoAddons", "Checking for updates…");
        check(true);
    }

    private void check(boolean verbose) {
        Updater.check(prereleases.get(), mode.get() >= DOWNLOAD, mode.get() >= INSTALL,
                announce.get(), verbose);
    }
}
