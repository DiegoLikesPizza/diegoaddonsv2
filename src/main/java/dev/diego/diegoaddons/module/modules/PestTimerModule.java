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
     * Auto swap: equip the pest loadout once the cooldown is inside {@link #swapBefore}, and the
     * farming one back the moment pests actually spawn - which is when the pest gear has done its
     * job and every second longer in it is farming fortune you are not getting.
     *
     * <p>Off by default, and it should be: this is the only thing in the Garden features that
     * reaches into the game on your behalf. See {@link #canSwap} for the rules that keep it from
     * doing so at a bad moment.
     */
    private final BooleanSetting autoSwap =
            new BooleanSetting(this, "autoSwap", "Auto swap loadout", false);
    /**
     * Its own clock rather than the warning's. Warning off used to mean swap off, and the two had to
     * fire at the same instant - when the useful arrangement is often a warning first and the swap a
     * little later, or a swap with no warning at all.
     */
    private final NumberSetting swapBefore =
            new NumberSetting(this, "swapBefore", "Swap at (seconds before)", 30, 0, 240, 5);
    private final StringSetting pestLoadout =
            new StringSetting(this, "pestLoadout", "Pest loadout", "", null);
    private final StringSetting farmLoadout =
            new StringSetting(this, "farmLoadout", "Farming loadout", "", null);

    /**
     * Whether the warning and the swap are allowed to fire, re-armed once the cooldown climbs back
     * above their thresholds.
     *
     * <p><b>Arming rather than "have I fired for this cooldown".</b> The first version identified a
     * cooldown by the moment it ends, which fails on the very thing this feature does: pest gear
     * shortens the cooldown, so swapping changes the end time, and the shortened cooldown reads as a
     * brand new one that is already inside the window. It swapped to pest gear, back to farming, and
     * straight into pest gear again - Diego watched it happen. Firing on the <i>crossing</i> of the
     * threshold cannot loop, because after a swap the timer is below the line and stays disarmed
     * until a spawn resets it to a full cooldown.
     */
    private boolean warnArmed = true;
    private boolean swapArmed = true;
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
        settings.add(swapBefore);
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
        long swapLead = (long) (swapBefore.get() * 1000);

        // Re-arm whenever the cooldown is comfortably above its threshold again. Both of these fire
        // on the *crossing*, not on the value - see the note on arming below.
        if (left > lead) {
            warnArmed = true;
        }
        if (left > swapLead) {
            swapArmed = true;
        }

        // Not while pests are already out. The warning exists to get you into pest gear before the
        // cooldown ends; with pests on the ground the job is to vacuum them, and a title across the
        // screen telling you to swap is telling you to do the wrong thing. Left armed, so it still
        // fires if you clear them while the cooldown is inside the window.
        if (lead > 0 && warnArmed && left <= lead && Pests.alive() == 0) {
            warnArmed = false;
            warn(mc, "Swap to pest gear", "Pest cooldown in " + time(left));
        }
        // The swap has its own clock. Tying it to the warning meant turning warnings off turned the
        // swap off with them, and it forced both to happen at the same moment - when the point is
        // often to be told first and swapped a little later, or swapped without being told at all.
        if (swapLead > 0 && swapArmed && left <= swapLead) {
            swapArmed = false;
            swapPending = true;
        }
        trySwap(mc);
    }

    // --- auto swap --------------------------------------------------------------------------------

    /** A swap into pest gear that has been decided but not yet carried out. */
    private boolean swapPending;
    /** Whether the pest loadout is the one currently on, as far as this module put it there. */
    private boolean inPestGear;
    /** The spawn that was last seen, so the swap back happens once per spawn rather than per tick. */
    private long swappedBackAt;
    /** How many pests were alive when the pest gear went on, for the count-based half of {@link #spawned}. */
    private int aliveWhenSwapped;

    /**
     * Whether pests have spawned since the pest gear went on - the signal to swap back.
     *
     * <p><b>Two signals, because either alone is a way to get stuck in pest gear.</b> The chat
     * announcement is immediate but is matched by a pattern that is still a guess at Hypixel's
     * wording, and chat can be swallowed by a filter. The pest count from the tab widget is the
     * number Hypixel itself maintains, but it only refreshes about once a second. Taking whichever
     * arrives first means a wrong guess about a chat line costs a second, not the whole feature.
     */
    private boolean spawned() {
        return Pests.lastSpawn() > swappedBackAt || Pests.alive() > aliveWhenSwapped;
    }

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
        if (inPestGear && spawned() && !farmLoadout.get().isBlank()) {
            if (!canSwap(mc)) {
                return;
            }
            if (LoadoutKeys.equip(mc, farmLoadout.get())) {
                swappedBackAt = Pests.lastSpawn();
                aliveWhenSwapped = Pests.alive();
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
            // Both baselines are taken here, at the moment the gear goes on: a spawn only counts as
            // "since the swap" if it is later than this, and pests already alive must not read as new.
            swappedBackAt = Pests.lastSpawn();
            aliveWhenSwapped = Pests.alive();
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
