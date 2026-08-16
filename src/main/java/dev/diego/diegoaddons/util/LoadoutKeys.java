package dev.diego.diegoaddons.util;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.LoadoutKey;
import dev.diego.diegoaddons.module.modules.LoadoutKeybindModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Switches SkyBlock loadout with one key.
 *
 * <p>Two steps, and the second is the one worth being careful about: the key sends the command that
 * opens the Loadouts menu, and then - <b>once the menu is actually there</b> - clicks the preset
 * whose name matches. Not "send the command, wait half a second, click slot 14": a click is a slot
 * number sent to whatever window is open, so a click that arrives early lands in whatever menu you
 * happened to be in. The Storage Overlay learned this the same way; the rule there and here is to
 * wait for the window to say it is the right one.
 *
 * <p>The preset is found by <b>name</b>, never by position. The Loadouts grid reorders as you add
 * and remove presets, so a saved slot number is a promise the menu does not keep.
 */
public final class LoadoutKeys {
    /** How long to wait for the menu before giving up on a press. */
    private static final long TIMEOUT_MS = 5000;

    /** Keys held last tick, so one press fires once. */
    private static final Set<Integer> DOWN = new HashSet<>();

    private LoadoutKeys() {
    }

    public static List<LoadoutKey> all() {
        if (ConfigManager.get().loadoutKeys == null) {
            ConfigManager.get().loadoutKeys = new ArrayList<>();
        }
        return ConfigManager.get().loadoutKeys;
    }

    private static long lastComplaint;

    /** One line in chat, prefixed like everything else the mod says. */
    private static void say(Minecraft mc, String message) {
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    net.minecraft.network.chat.Component.literal("§b[DiegoAddons] " + message));
        }
    }

    /** The loadout a press is waiting to click, or null when nothing is pending. */
    private static String pending;
    private static long pendingSince;

    public static void tick(Minecraft mc) {
        LoadoutKeybindModule module = LoadoutKeybindModule.INSTANCE;
        if (module == null || !module.isEnabled() || mc.player == null) {
            pending = null;
            return;
        }
        pollKeys(mc, module);
        applyPending(mc);
        closeIfDue(mc);
    }

    private static void pollKeys(Minecraft mc, LoadoutKeybindModule module) {
        if (mc.getWindow() == null) {
            return;
        }
        // Only from normal play, like the command hotkeys: a bound letter has to stay typeable.
        boolean allowed = mc.screen == null;
        for (LoadoutKey l : all()) {
            if (!l.enabled || l.key == InputConstants.UNKNOWN.getValue() || l.key < 0
                    || l.name.isBlank()) {
                continue;
            }
            boolean down = InputConstants.isKeyDown(mc.getWindow(), l.key);
            boolean was = DOWN.contains(l.key);
            if (down) {
                DOWN.add(l.key);
            } else {
                DOWN.remove(l.key);
            }
            if (down && !was && allowed) {
                press(mc, module, l.name);
            }
        }
    }

    /**
     * Equips a loadout by name from somewhere other than a key press - the pest timer's auto swap.
     *
     * @return whether the attempt started; false when the module is off or already busy
     */
    public static boolean equip(Minecraft mc, String name) {
        LoadoutKeybindModule module = LoadoutKeybindModule.INSTANCE;
        if (name == null || name.isBlank()) {
            return false;
        }
        if (module == null || !module.isEnabled()) {
            // The pest timer drives this, and its card does not say that a second module has to be
            // on. Rather than do nothing, name the missing piece - once, not once a tick.
            if (System.currentTimeMillis() - lastComplaint > 30_000) {
                lastComplaint = System.currentTimeMillis();
                say(mc, "§cAuto swap needs the §eLoadout Keybinds §cmodule switched on.");
            }
            return false;
        }
        if (pending != null) {
            return false;
        }
        press(mc, module, name);
        return true;
    }

    /** Whether a swap is in flight, so nothing else starts a second one on top of it. */
    public static boolean busy() {
        return pending != null;
    }

    /** Opens the menu and remembers what to click when it arrives. */
    private static void press(Minecraft mc, LoadoutKeybindModule module, String name) {
        String command = module.command();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isBlank() || mc.player == null) {
            say(mc, "§cNo menu command set on the Loadout Keybinds card.");
            return;
        }
        pending = name;
        pendingSince = System.currentTimeMillis();
        clickAt = 0;   // a fresh press schedules its own wait once its menu shows up
        mc.player.connection.sendCommand(command);
    }

    /**
     * Clicks the pending loadout once its menu is open.
     *
     * <p>Gives up quietly after {@link #TIMEOUT_MS}. A press that could not find its menu should
     * stop trying rather than click into whatever the player opened next.
     */
    private static void applyPending(Minecraft mc) {
        if (pending == null) {
            return;
        }
        // Not while a click is already scheduled: the timeout is for "the menu never opened", and
        // once it has opened and we are only waiting out the configured delay, giving up would
        // abandon a swap that is going fine. Without this, a long wait plus a slow menu times out.
        if (clickAt == 0 && System.currentTimeMillis() - pendingSince > TIMEOUT_MS) {
            // Silence here was the worst part of this feature: a swap that never happened looked
            // exactly like a swap that was never asked for. The command is a guess, so say which one.
            String command = LoadoutKeybindModule.INSTANCE == null
                    ? "?" : LoadoutKeybindModule.INSTANCE.command();
            say(mc, "§cThe loadout menu did not open. Is \"" + command + "\" the right command?");
            pending = null;
            clickAt = 0;
            return;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        String title = LegacyText.strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!title.contains("loadout")) {
            return;
        }
        int slot = findPreset(screen, pending);
        if (slot >= 0 && clickAt == 0) {
            // The menu is up and the preset is in it. Schedule the click rather than sending it on
            // this tick: SkyBlock may still be filling the menu in, and a click into a half-built
            // one lands on whatever is at that slot number - the exact failure the whole
            // wait-for-the-menu design exists to prevent.
            clickAt = System.currentTimeMillis() + roll(delay(true));
            return;
        }
        if (slot >= 0 && System.currentTimeMillis() < clickAt) {
            // Still waiting. The preset is re-found every tick on purpose: if the menu changes
            // under us before the click lands, the slot number goes with it.
            return;
        }
        if (slot < 0) {
            // The menu is open but the name is not in it: say so once rather than leave a key that
            // silently does nothing. A typo in the name is by far the likeliest cause.
            if (mc.gui != null) {
                mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§b[DiegoAddons] §fNo loadout called §e" + pending + "§f in this menu."));
            }
            pending = null;
            clickAt = 0;
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (mc.gameMode != null) {
            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        }
        pending = null;
        clickAt = 0;
        closeAt = System.currentTimeMillis() + roll(delay(false));
    }

    /** When the pending click is due, or 0 when one has not been scheduled yet. */
    private static long clickAt;

    /** The configured wait, or the old hard-coded default when the module is not there to ask. */
    private static long delay(boolean beforeClick) {
        LoadoutKeybindModule module = LoadoutKeybindModule.INSTANCE;
        if (module == null) {
            return beforeClick ? 0 : CLOSE_DELAY_MS;
        }
        return beforeClick ? module.clickDelayMs() : module.closeDelayMs();
    }

    /**
     * The wait, varied by the configured percentage when that is switched on.
     *
     * <p>The point of varying it is not disguise - three menu actions a tenth of a second apart are
     * machine-timed whatever the exact numbers are. It is that a fixed wait which happens to land
     * just short of the menu being ready fails <b>every</b> swap in exactly the same way, whereas a
     * varying one fails some and succeeds others: less bad while it is wrong, and far easier to
     * recognise as a timing problem rather than a broken feature.
     */
    private static long roll(long base) {
        LoadoutKeybindModule module = LoadoutKeybindModule.INSTANCE;
        if (module == null || !module.randomDelay() || base <= 0) {
            return base;
        }
        double spread = module.randomPercent() / 100.0;
        double factor = 1 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * spread;
        return Math.max(0, Math.round(base * factor));
    }

    /**
     * The fallback wait before shutting the menu, used only when the module is not available to ask.
     *
     * <p>The reasoning it was chosen for still holds and is why the setting defaults to it: the click
     * and the close are two packets on one connection, so the server does see them in order - but
     * SkyBlock answers a loadout click by rewriting the menu, and closing into the middle of that has
     * a way of leaving the client and the server disagreeing about what is open. A tenth of a second
     * is invisible to you and unambiguous to the server; a slower connection may want more, which is
     * what made it a setting.
     */
    private static final long CLOSE_DELAY_MS = 100;
    private static long closeAt;

    /**
     * Shuts the loadout menu once the swap has gone through.
     *
     * <p>Only if a loadout menu is still what is open: between the click and this, you may have hit
     * Escape yourself or the server may have put something else up, and closing that would be the
     * feature reaching further than it was asked to.
     */
    private static void closeIfDue(Minecraft mc) {
        if (closeAt == 0 || System.currentTimeMillis() < closeAt) {
            return;
        }
        closeAt = 0;
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        if (LegacyText.strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT).contains("loadout")) {
            mc.player.closeContainer();
        }
    }

    /**
     * The slot holding the named preset, or -1.
     *
     * <p>Matched on the item's name with the colour codes off, and only against the menu's own
     * slots - your inventory is in the same window and an item in it that happens to share the name
     * is not a loadout.
     */
    private static int findPreset(AbstractContainerScreen<?> screen, String name) {
        AbstractContainerMenu menu = screen.getMenu();
        int own = Math.max(0, menu.slots.size() - 36);
        String want = name.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < own; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String label = LegacyText.strip(stack.getHoverName().getString())
                    .trim().toLowerCase(Locale.ROOT);
            if (label.equals(want) || label.contains(want)) {
                return i;
            }
        }
        return -1;
    }
}
