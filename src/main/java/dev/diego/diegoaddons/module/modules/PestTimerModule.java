package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.LoadoutKeys;
import dev.diego.diegoaddons.util.Pests;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The pest spawn cooldown, on the HUD, with a warning early enough to swap into your pest gear
 * before it runs out.
 *
 * <p><b>Why the warning fires so early.</b> Pest gear does not only kill pests faster - every piece
 * of it shortens the spawn cooldown, from five minutes down to as little as 1:10. So the play is to
 * farm in farming gear, swap into pest gear shortly before the cooldown ends, let the pests spawn
 * against the shorter cooldown, clear them, and swap back. That swap has to happen *before* the
 * cooldown expires to be worth anything, and how long before depends on your own two setups - which
 * is why the lead time is a slider going up to four minutes rather than a number picked here.
 *
 * <p>The cooldown itself comes from the tab list's Pests widget; see {@link Pests} for why it is read
 * rather than calculated, and for the one thing that has to be switched on in {@code /widget}.
 */
public class PestTimerModule extends HudModule {
    public static PestTimerModule INSTANCE;

    private final BooleanSetting showAlive =
            new BooleanSetting(this, "showAlive", "Show pests alive", true);
    private final BooleanSetting showPlots =
            new BooleanSetting(this, "showPlots", "Show infested plots", true);
    private final BooleanSetting showLastSpawn =
            new BooleanSetting(this, "showLastSpawn", "Show time since last spawn", false);

    /**
     * Seconds before the cooldown ends at which the swap warning fires. Zero turns the early warning
     * off and leaves only the one at expiry, which is the "just tell me when it's up" setting.
     */
    private final NumberSetting warnBefore =
            new NumberSetting(this, "warnBefore", "Swap warning (seconds before)", 60, 0, 240, 5);
    private final BooleanSetting warnTitle =
            new BooleanSetting(this, "warnTitle", "Warning as a title", true);
    private final BooleanSetting warnSound =
            new BooleanSetting(this, "warnSound", "Play a sound", true);
    private final BooleanSetting warnChat =
            new BooleanSetting(this, "warnChat", "Warning in chat", false);
    private final BooleanSetting warnExpired =
            new BooleanSetting(this, "warnExpired", "Warn again when it expires", true);

    /**
     * Auto swap: equip the pest loadout when the warning fires, and the farming one back once a pest
     * has actually spawned - which is the moment the pest gear has done its job.
     *
     * <p>Off by default, and it should be: this is the only thing in the Garden features that
     * reaches into the game on your behalf. See {@link #canSwap} for the rules that keep it from
     * doing so at a bad moment.
     */
    private final BooleanSetting autoSwap =
            new BooleanSetting(this, "autoSwap", "Auto swap loadout", false);
    private final StringSetting pestLoadout =
            new StringSetting(this, "pestLoadout", "Pest loadout", "", null);
    private final StringSetting farmLoadout =
            new StringSetting(this, "farmLoadout", "Farming loadout", "", null);

    /**
     * The cooldown each warning has already fired for, identified by the moment it ends.
     *
     * <p>A flag would not do: the widget re-reads the same cooldown once a second, so "have I warned"
     * has to mean "have I warned about *this* cooldown". A new cooldown has a new end time, which is
     * what makes both warnings fire again without anything having to notice a pest spawning.
     */
    private long warnedFor;
    private long expiredFor;
    /** The cooldown currently being counted down, kept after it ends so the expiry can be named. */
    private long tracking;

    public PestTimerModule() {
        super("pesttimer", Category.GARDEN, "Pest Timer",
                "The pest spawn cooldown, with a warning in time to swap into your pest gear.");
        settings.add(showAlive);
        settings.add(showPlots);
        settings.add(showLastSpawn);
        settings.add(warnBefore);
        settings.add(warnTitle);
        settings.add(warnSound);
        settings.add(warnChat);
        settings.add(warnExpired);
        settings.add(autoSwap);
        settings.add(pestLoadout);
        settings.add(farmLoadout);
        INSTANCE = this;
    }

    @Override
    protected String label() {
        return "Pest";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // several lines rather than one; see hudLines
    }

    @Override
    protected String sampleValue() {
        return "1m 58s";
    }

    /** The cooldown as text: the number when there is one, and the reason when there is not. */
    private String cooldownText() {
        if (!Pests.widgetSeen()) {
            return "Enable the Pests widget";
        }
        if (Pests.maxPests()) {
            return "Max pests";
        }
        if (Pests.ready()) {
            return "Ready";
        }
        long ms = Pests.msLeft();
        return ms < 0 ? "Unknown" : time(ms);
    }

    @Override
    public List<String> hudLines(Minecraft mc) {
        if (!Pests.inGarden()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(4);
        out.add(showLabel() ? "Pest cooldown: " + cooldownText() : cooldownText());
        if (showAlive.get() && Pests.widgetSeen()) {
            out.add("Alive: " + Pests.alive() + "/" + Pests.MAX_ALIVE);
        }
        if (showPlots.get() && !Pests.plots().isEmpty()) {
            out.add("Plots: " + Pests.plots());
        }
        if (showLastSpawn.get() && Pests.lastSpawn() != 0) {
            out.add("Last spawn: " + time(System.currentTimeMillis() - Pests.lastSpawn()) + " ago");
        }
        return out;
    }

    /** "1m 58s" / "58s", the same shape the widget itself uses. */
    private static String time(long ms) {
        long total = Math.max(0, ms) / 1000;
        long m = total / 60;
        long s = total % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || !Pests.inGarden() || !Pests.widgetSeen()) {
            return;
        }
        // Before every early return below: a swap can still be owed while the cooldown reads READY
        // or MAX PESTS, and those are exactly the moments this method used to stop at.
        trySwap(mc);

        long end = Pests.cooldownEnd();
        if (end != 0) {
            tracking = end;
        }
        // "Ready" is the expiry, and it is the *only* form of it we are sure to see: the widget
        // switches from a number straight to READY, which zeroes the end time. Warning off the
        // countdown reaching zero alone would miss it whenever that switch lands between two ticks.
        if (Pests.ready() || Pests.maxPests()) {
            if (Pests.ready() && warnExpired.get() && tracking != 0 && expiredFor != tracking) {
                expiredFor = tracking;
                warn(mc, "Pest cooldown is up", "Pests can spawn now");
            }
            return;
        }
        long left = Pests.msLeft();
        if (left < 0) {
            return;
        }

        long lead = (long) (warnBefore.get() * 1000);
        if (lead > 0 && warnedFor != end && left <= lead) {
            warnedFor = end;
            warn(mc, "Swap to pest gear", "Pest cooldown in " + time(left));
            swapPending = true;
            trySwap(mc);
        }
    }

    // --- auto swap --------------------------------------------------------------------------------

    /** A swap into pest gear that has been decided but not yet carried out. */
    private boolean swapPending;
    /** Whether the pest loadout is the one currently on, as far as this module put it there. */
    private boolean inPestGear;
    /** The spawn that was last seen, so the swap back happens once per spawn rather than per tick. */
    private long swappedBackAt;

    /**
     * Whether now is a safe moment to open a menu on the player's behalf.
     *
     * <p>The dangerous case is the ordinary one: <b>farming means holding the attack button down</b>,
     * and a menu that opens under a held mouse button takes that button as a click on whatever slot
     * the cursor happens to be over. In a loadout menu that is a real click on real gear. So the
     * swap waits for the button to come up - which, while farming a row, is a second or two away at
     * most, and the warning fires a minute early precisely so there is room for that.
     *
     * <p>The rest are the same kind of rule: not while another screen is open (the swap needs the
     * loadout menu and nothing else), not while a swap is already in flight, and not outside the
     * Garden, where a loadout menu opening unasked would be startling rather than helpful.
     */
    private boolean canSwap(Minecraft mc) {
        if (!autoSwap.get() || mc.player == null || !Pests.inGarden()) {
            return false;
        }
        if (mc.screen != null || LoadoutKeys.busy()) {
            return false;
        }
        return !mc.options.keyAttack.isDown() && !mc.options.keyUse.isDown();
    }

    /**
     * Carries out a swap that is due, in whichever direction.
     *
     * <p>Into pest gear when the warning has fired, and back into farming gear once a pest has
     * actually spawned - the pest gear's whole purpose is to make that happen, so the spawn is the
     * signal that it is no longer needed.
     */
    private void trySwap(Minecraft mc) {
        if (!autoSwap.get()) {
            swapPending = false;
            return;
        }
        if (inPestGear && Pests.lastSpawn() > swappedBackAt && !farmLoadout.get().isBlank()) {
            if (!canSwap(mc)) {
                return;
            }
            if (LoadoutKeys.equip(mc, farmLoadout.get())) {
                swappedBackAt = Pests.lastSpawn();
                inPestGear = false;
                announce(mc, "Back to " + farmLoadout.get());
            }
            return;
        }
        if (!swapPending || pestLoadout.get().isBlank()) {
            return;
        }
        if (!canSwap(mc)) {
            return;
        }
        if (LoadoutKeys.equip(mc, pestLoadout.get())) {
            swapPending = false;
            inPestGear = true;
            swappedBackAt = Pests.lastSpawn();
            announce(mc, "Swapped to " + pestLoadout.get());
        }
    }

    private void announce(Minecraft mc, String what) {
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    Component.literal("§b[DiegoAddons] §f" + what));
        }
    }

    /** The warning itself, on whichever of the three channels are switched on. */
    private void warn(Minecraft mc, String title, String detail) {
        if (warnTitle.get() && mc.gui != null) {
            mc.gui.setTitle(Component.literal("§a§l" + title.toUpperCase(java.util.Locale.ROOT)));
            mc.gui.setSubtitle(Component.literal("§e" + detail));
        }
        if (warnChat.get() && mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    Component.literal("§b[DiegoAddons] §f" + title + " §7- " + detail));
        }
        if (warnSound.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.6f);
        }
    }
}
