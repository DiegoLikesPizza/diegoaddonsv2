package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.WardrobeKeybindsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Drives a keybound wardrobe swap: open the wardrobe, click the requested set, and optionally close
 * the menu again. Because the menu lives on the server, this cannot be done in one go - the request
 * is parked and a small state machine picks it up once the menu actually arrives.
 *
 * <p>Every step goes through the game's own input path ({@code slotClicked}), so a keypress does
 * exactly what clicking that slot by hand would do; nothing is faked at the packet level.
 *
 * <p>Sets are located by their column in the live menu, not by hard-coded slot numbers, so a layout
 * change does not silently click the wrong thing. Only the sets on the page the wardrobe opens on
 * can be reached - there is no page navigation.
 */
public final class WardrobeSwapper {
    /** How long to wait for the menu to open before giving up, in client ticks. */
    private static final int TIMEOUT = 100;
    /** Ticks to wait after clicking before closing, so the click is sent first. */
    private static final int CLOSE_DELAY = 3;
    /**
     * Menu titles the wardrobe uses, lower-case. SkyBlock renamed it from "Wardrobe" to "Armor
     * Sets" and the title carries a page prefix ("(1/3) Armor Sets"), so we match on a substring
     * and keep the old name for older versions. This is the tuning knob if it is renamed again.
     */
    private static final String[] TITLES = {"wardrobe", "armor sets", "armour sets"};
    /** Lore markers on the per-set button: which one is present tells us the set's current state. */
    private static final String EQUIP = "equip";
    private static final String UNEQUIP = "unequip";

    private static int pending = -1;      // 1-based set number the user asked for
    private static int waited;
    private static int closeIn = -1;

    private WardrobeSwapper() {
    }

    /** Ask for set {@code number} (1-based). Opens the wardrobe if it is not already open. */
    public static void request(Minecraft mc, int number) {
        if (mc.player == null) {
            return;
        }
        pending = number;
        waited = 0;
        closeIn = -1;
        if (!isWardrobe(mc)) {
            mc.player.connection.sendCommand("wardrobe");
        }
    }

    /** Called every client tick while the module is on; performs the parked swap when it can. */
    public static void tick(Minecraft mc) {
        if (closeIn > 0 && --closeIn == 0 && mc.player != null) {
            mc.player.closeContainer();
        }
        if (pending < 0) {
            return;
        }
        if (++waited > TIMEOUT) {
            pending = -1;
            say(mc, "§b[DiegoAddons] §fWardrobe did not open - swap cancelled.");
            return;
        }
        if (!isWardrobe(mc)) {
            return;   // still waiting for the server to send the menu
        }

        int number = pending;
        pending = -1;
        swap(mc, (AbstractContainerScreen<?>) mc.screen, number);
    }

    /** Clicks the set's button, honouring the "prevent unequipping" option. */
    private static void swap(Minecraft mc, AbstractContainerScreen<?> screen, int number) {
        WardrobeKeybindsModule mod = WardrobeKeybindsModule.INSTANCE;
        List<Slot> columns = buttons(screen);
        if (number < 1 || number > columns.size()) {
            say(mc, "§b[DiegoAddons] §fWardrobe slot §e" + number + "§f is not on this page.");
            return;
        }
        Slot target = columns.get(number - 1);

        if (mod != null && mod.preventUnequip() && isEquipped(target.getItem())) {
            say(mc, "§b[DiegoAddons] §fSet §e" + number + "§f is already worn - not unequipping.");
            if (mod.closeAfter()) {
                closeIn = CLOSE_DELAY;
            }
            return;
        }

        ((AbstractContainerScreenAccessor) screen)
                .diego$slotClicked(target, target.index, 0, ContainerInput.PICKUP);
        if (mod != null && mod.closeAfter()) {
            closeIn = CLOSE_DELAY;
        }
    }

    /**
     * One clickable slot per set, ordered left to right. SkyBlock puts an equip/unequip button under
     * each set's column; where that button exists we use it, otherwise we fall back to the column's
     * topmost armour piece, which equips the set too.
     */
    private static List<Slot> buttons(AbstractContainerScreen<?> screen) {
        List<Slot> slots = screen.getMenu().slots;
        int chestCount = Math.max(0, slots.size() - 36);   // last 36 are the player's own inventory
        TreeMap<Integer, Slot> armourTop = new TreeMap<>();
        TreeMap<Integer, Slot> buttons = new TreeMap<>();

        for (int i = 0; i < chestCount && i < slots.size(); i++) {
            Slot s = slots.get(i);
            ItemStack st = s.getItem();
            if (st.isEmpty()) {
                continue;
            }
            if (isSetButton(st)) {
                buttons.putIfAbsent(s.x, s);
            } else if (st.get(DataComponents.EQUIPPABLE) instanceof Equippable eq && eq.slot().isArmor()) {
                armourTop.putIfAbsent(s.x, s);   // slots come in reading order, so this is the top one
            }
        }
        // Prefer the button column; keep the armour columns as the fallback keyed by the same x.
        TreeMap<Integer, Slot> merged = new TreeMap<>(armourTop);
        merged.putAll(buttons);
        return List.copyOf(merged.values());
    }

    /** A per-set button is the item whose lore offers to equip or unequip that set. */
    private static boolean isSetButton(ItemStack stack) {
        for (String line : lore(stack)) {
            if (line.contains(EQUIP)) {
                return true;   // "click to equip" / "click to unequip"
            }
        }
        return false;
    }

    /** True when clicking this set would take the armour off, i.e. it is the one being worn. */
    private static boolean isEquipped(ItemStack stack) {
        for (String line : lore(stack)) {
            if (line.contains(UNEQUIP) || line.contains("equipped")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> lore(ItemStack stack) {
        ItemLore l = stack.get(DataComponents.LORE);
        if (l == null) {
            return List.of();
        }
        return l.lines().stream()
                .map(c -> c.getString().replaceAll("§.", "").toLowerCase(Locale.ROOT))
                .toList();
    }

    private static boolean isWardrobe(Minecraft mc) {
        return mc.screen instanceof AbstractContainerScreen<?> s && isWardrobeTitle(s.getTitle().getString());
    }

    /** Whether {@code title} is the wardrobe, ignoring page prefix, colour codes and case. */
    private static boolean isWardrobeTitle(String title) {
        String t = title.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        for (String name : TITLES) {
            if (t.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static void say(Minecraft mc, String msg) {
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
        }
    }
}
