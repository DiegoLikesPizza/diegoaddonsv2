package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What is in your sacks, read whenever you open one and kept per profile.
 *
 * <p>Sacks are the right place to ask "do I already have this": they are where farming output
 * actually lands, they hold far more than a backpack, and they are the one store you never manually
 * file. A visitor wanting 24 Enchanted Carrot is a different question when 18 of them are already
 * in the Agronomy Sack.
 *
 * <p>Read from the menu, like everything else here - a sack's own item lore carries
 * {@code Stored: 28,183/60.5k}, and that line is the whole feature. There is no packet that says
 * what is in a sack, so the cost is the same as Ted's: it is a reading with a date on it, and it
 * only improves when you open the sack again. The counts are therefore a <b>floor</b>: you may have
 * more than this by now, never less, unless you have spent some.
 */
public final class Sacks {
    private Sacks() {
    }

    /** Item name to how many are stored, for the profile the reading came from. */
    private static final Map<String, Integer> COUNTS = new HashMap<>();
    private static String loadedProfile = "";

    /** How many of this item the sacks held when last read, or 0 when unknown. */
    public static int count(String itemName) {
        return COUNTS.getOrDefault(key(itemName), 0);
    }

    /** Whether any sack has ever been read for this profile. */
    public static boolean known() {
        return !COUNTS.isEmpty();
    }

    public static long readAt() {
        return ConfigManager.get().sacksReadAt;
    }

    private static String key(String itemName) {
        return LegacyText.strip(itemName).trim().toLowerCase(Locale.ROOT);
    }

    // --- reading it --------------------------------------------------------------------------------

    /** The container already read, so a sack left open is not re-read every tick. */
    private static int readContainer = -1;

    public static void tick(Minecraft mc) {
        String profile = SkyblockLocation.profile(mc);
        if (!profile.isEmpty() && !profile.equals(loadedProfile)) {
            // Swapping profile swaps the sacks with it, and showing one profile's counts on another
            // is the same lie the storage cache is keyed against.
            load(profile);
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            readContainer = -1;
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (menu.containerId == readContainer) {
            return;
        }
        String title = LegacyText.strip(screen.getTitle().getString()).trim();
        if (!SACK_TITLE.matcher(title).matches()) {
            return;
        }
        readContainer = menu.containerId;
        read(menu, profile);
    }

    /** "Agronomy Sack", "Enchanted Agronomy Sack", "Fishing Sack". */
    private static final Pattern SACK_TITLE =
            Pattern.compile("^(?:.*\\bSack|Enchanted .*\\bSack)$");

    /** "Stored: 28,183/60.5k" - the one line that makes this feature possible. */
    private static final Pattern STORED =
            Pattern.compile("Stored:\\s*(?<amount>[\\d.,]+[kKmMbB]?)\\s*/", Pattern.CASE_INSENSITIVE);

    private static void read(AbstractContainerMenu menu, String profile) {
        int own = Math.max(0, menu.slots.size() - 36);
        int found = 0;
        for (int i = 0; i < own; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            List<String> lore = Visitors.lore(stack);
            for (String line : lore) {
                Matcher m = STORED.matcher(line);
                if (!m.find()) {
                    continue;
                }
                long amount = amount(m.group("amount"));
                if (amount >= 0) {
                    COUNTS.put(key(stack.getHoverName().getString()), (int) Math.min(amount, Integer.MAX_VALUE));
                    found++;
                }
                break;
            }
        }
        if (found == 0) {
            return;
        }
        loadedProfile = profile;
        save(profile);
        DiegoAddonsV2Client.LOGGER.info("[sacks] read {} items from a sack menu", found);
    }

    /**
     * "28,183", "60.5k", "1.2M" - Hypixel shortens the big ones.
     *
     * <p>Shortened means rounded, so a "60.5k" reading is not exact. That is fine for the question
     * being asked - whether you have roughly enough - and it is why nothing here pretends to be a
     * precise inventory.
     */
    private static long amount(String text) {
        String s = text.replace(",", "").trim().toLowerCase(Locale.ROOT);
        double multiplier = 1;
        if (s.endsWith("k")) {
            multiplier = 1_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            multiplier = 1_000_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("b")) {
            multiplier = 1_000_000_000L;
            s = s.substring(0, s.length() - 1);
        }
        try {
            return (long) (Double.parseDouble(s) * multiplier);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // --- keeping it --------------------------------------------------------------------------------

    private static void load(String profile) {
        COUNTS.clear();
        loadedProfile = profile;
        Map<String, Integer> saved = ConfigManager.get().sackCounts;
        if (saved != null && profile.equals(ConfigManager.get().sackProfile)) {
            COUNTS.putAll(saved);
        }
    }

    private static void save(String profile) {
        ConfigManager.get().sackCounts = new HashMap<>(COUNTS);
        ConfigManager.get().sackProfile = profile;
        ConfigManager.get().sacksReadAt = System.currentTimeMillis();
        ConfigManager.save();
    }
}
