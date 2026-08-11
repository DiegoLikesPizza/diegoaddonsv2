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
        if (module == null || !module.isEnabled() || name == null || name.isBlank()) {
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
            return;
        }
        pending = name;
        pendingSince = System.currentTimeMillis();
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
        if (System.currentTimeMillis() - pendingSince > TIMEOUT_MS) {
            pending = null;
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
        if (slot < 0) {
            // The menu is open but the name is not in it: say so once rather than leave a key that
            // silently does nothing. A typo in the name is by far the likeliest cause.
            if (mc.gui != null) {
                mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§b[DiegoAddons] §fNo loadout called §e" + pending + "§f in this menu."));
            }
            pending = null;
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (mc.gameMode != null) {
            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        }
        pending = null;
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
