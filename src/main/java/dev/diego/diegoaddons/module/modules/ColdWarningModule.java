package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.Cold;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Shouts before the Icy biome kills you.
 *
 * <p>Cold is one point every five seconds and instant death at a hundred, so the danger is not
 * sudden - it is eight slow minutes that are easy to spend not looking at the sidebar. Two warnings
 * rather than one, because the two are different messages: the first says "find a campfire soon",
 * the second says "now".
 *
 * <p><b>Not gated to the Safari</b>, despite living on its card. The Icy biome borrowed the Glacite
 * Tunnels' mechanic and Hypixel writes the same sidebar line in both, so reading it wherever it
 * appears covers the Dwarven Mines too - a warning that only works on one of the two islands with
 * the same instant death on it would be an odd thing to ship deliberately.
 */
public class ColdWarningModule extends HudModule {
    public static ColdWarningModule INSTANCE;

    private final NumberSetting warnAt =
            new NumberSetting(this, "warnAt", "Warn at", 60, 10, 99, 5);
    private final NumberSetting urgentAt =
            new NumberSetting(this, "urgentAt", "Urgent at", 85, 20, 99, 5);
    private final BooleanSetting warnTitle =
            new BooleanSetting(this, "warnTitle", "Title on screen", true);
    private final BooleanSetting warnChat =
            new BooleanSetting(this, "warnChat", "Message in chat", false);
    private final BooleanSetting warnSound =
            new BooleanSetting(this, "warnSound", "Play a sound", true);
    /** How long before the same tier says it again while you keep getting colder. */
    private final NumberSetting repeat =
            new NumberSetting(this, "repeat", "Repeat every (s)", 20, 5, 120, 5);
    /** Off by default: the sidebar already shows the number, and this is a warner, not a readout. */
    private final BooleanSetting showHud =
            new BooleanSetting(this, "showHud", "Show on the HUD", false);

    /** Which tier has been said: 0 none, 1 the first warning, 2 the urgent one. */
    private int stage;
    private long lastWarned;

    public ColdWarningModule() {
        super("coldwarning", Category.SAFARI, "Cold Warning",
                "Warn before Cold kills you, in the Icy biome and the Glacite Tunnels.");
        settings.add(warnAt);
        settings.add(urgentAt);
        settings.add(warnTitle);
        settings.add(warnChat);
        settings.add(warnSound);
        settings.add(repeat);
        settings.add(showHud);
        INSTANCE = this;
    }

    @Override
    protected String label() {
        return "Cold";
    }

    @Override
    protected String value(Minecraft mc) {
        if (!showHud.get() || !Cold.active()) {
            return null;
        }
        int seconds = Cold.secondsLeft();
        return Cold.cold() + "/" + Cold.LETHAL + " §7(~" + seconds / 60 + "m" + seconds % 60 + "s)";
    }

    @Override
    protected String sampleValue() {
        return "42/100 (~4m50s)";
    }

    /** Cleared on the chat line that says a campfire (or dying) took the cold away. */
    public static void onMessage(String plain) {
        ColdWarningModule m = INSTANCE;
        if (m != null && Cold.isReset(plain)) {
            m.stage = 0;
        }
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!Cold.active()) {
            stage = 0;
            return;
        }
        int cold = Cold.cold();
        int urgent = (int) urgentAt.get();
        int warn = (int) warnAt.get();
        // The urgent threshold wins even if it has been set below the first one - two settings that
        // can be dragged past each other should not produce a module that says nothing.
        int want = cold >= Math.max(warn, urgent) ? 2 : cold >= Math.min(warn, urgent) ? 1 : 0;
        if (want == 0) {
            // Dropped back below the line: a campfire did its job, so the warning re-arms.
            stage = 0;
            return;
        }
        long now = System.currentTimeMillis();
        boolean escalated = want > stage;
        if (!escalated && now - lastWarned < repeat.get() * 1000) {
            return;
        }
        stage = want;
        lastWarned = now;
        warn(mc, cold, want == 2);
    }

    private void warn(Minecraft mc, int cold, boolean urgent) {
        int seconds = Cold.secondsLeft();
        String detail = cold + "/" + Cold.LETHAL + " - roughly " + seconds + "s to a campfire";
        if (warnTitle.get() && mc.gui != null) {
            mc.gui.setTitle(Component.literal(urgent ? "§c§lFREEZING" : "§b§lCOLD"));
            mc.gui.setSubtitle(Component.literal((urgent ? "§c" : "§e") + detail));
        }
        if (warnChat.get() && mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(
                    "§b[DiegoAddons] §f" + (urgent ? "§cFreezing§f - " : "Cold - ") + detail));
        }
        if (warnSound.get() && mc.player != null) {
            // Lower and slower for the first, high and insistent for the second: the two warnings
            // have to be tellable apart without reading the screen, which is where you will be.
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, urgent ? 1.8f : 0.8f);
            if (urgent) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
            }
        }
    }

    @Override
    public List<String> hudLines(Minecraft mc) {
        return showHud.get() ? super.hudLines(mc) : List.of();
    }
}
