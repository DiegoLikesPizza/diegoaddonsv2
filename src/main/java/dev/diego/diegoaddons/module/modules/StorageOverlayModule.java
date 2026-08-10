package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.StorageData;
import dev.diego.diegoaddons.util.StorageScanner;
import dev.diego.diegoaddons.util.Toasts;
import net.minecraft.client.Minecraft;

/**
 * Every storage page on one sheet, drawn over SkyBlock's storage menu.
 *
 * <p>Open {@code /storage} and the overlay takes the menu's place: your ender chest pages and your
 * backpacks side by side, your own inventory underneath, and a search across all of them. The page
 * you are actually in is live, so its slots are the menu's own and items move normally; clicking a
 * slot on any other page walks you there first (see {@link dev.diego.diegoaddons.gui.StorageOverlay}).
 *
 * <p>Pages you are not in are drawn from the cache in {@link StorageData}, which is filled by the
 * menus themselves - the client is never told what is in a page it has not been shown.
 *
 * <p>The three commands are settings rather than constants: they are how the overlay moves between
 * pages, and if Hypixel ever renames one, that is a text box rather than a new version of the mod.
 */
public class StorageOverlayModule extends Module {
    public static StorageOverlayModule INSTANCE;

    private final NumberSetting slotSize =
            new NumberSetting(this, "slotSize", "Slot size", 18, 14, 28, 1);
    private final NumberSetting pagesAcross =
            new NumberSetting(this, "pagesAcross", "Pages across", 3, 2, 5, 1);
    private final NumberSetting navRows =
            new NumberSetting(this, "navRows", "Navigation rows to hide", 1, 0, 2, 1);
    private final BooleanSetting rarity =
            new BooleanSetting(this, "rarity", "Rarity colours", true);
    private final StringSetting enderChestCommand =
            new StringSetting(this, "ecCommand", "Ender chest command", "/ec %d", null);
    private final StringSetting backpackCommand =
            new StringSetting(this, "bpCommand", "Backpack command", "/backpack %d", null);
    private final StringSetting storageCommand =
            new StringSetting(this, "storageCommand", "Storage menu command", "/storage", null);
    private final ActionSetting openStorage =
            new ActionSetting(this, "open", "Storage menu", "Open", StorageOverlayModule::openStorage);
    private final ActionSetting clear =
            new ActionSetting(this, "clear", "Stored pages", "Clear", StorageOverlayModule::clearCache);
    private final BooleanSetting debug =
            new BooleanSetting(this, "debug", "Debug scan (log)", false);

    public StorageOverlayModule() {
        super("storage", Category.MISC, "Storage Overlay",
                "Every ender chest page and backpack on one sheet, over the storage menu.");
        settings.add(slotSize);
        settings.add(pagesAcross);
        settings.add(navRows);
        settings.add(rarity);
        settings.add(enderChestCommand);
        settings.add(backpackCommand);
        settings.add(storageCommand);
        settings.add(openStorage);
        settings.add(clear);
        settings.add(debug);
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        StorageScanner.tick(mc);
    }

    /** The button on the card: runs the storage command, which is what raises the sheet. */
    private static void openStorage() {
        Minecraft mc = Minecraft.getInstance();
        StorageOverlayModule module = INSTANCE;
        if (mc.player == null || module == null) {
            return;
        }
        String command = module.storageCommand();
        mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    private static void clearCache() {
        StorageData.clear();
        Toasts.show("Storage cleared", "Open your storage again to fill it back in");
    }

    public double slotSize() {
        return slotSize.get();
    }

    public int pagesAcross() {
        return (int) Math.round(pagesAcross.get());
    }

    /** Rows of navigation at the top of a page - close, back, the page arrows - not drawn. */
    public int navRows() {
        return (int) Math.round(navRows.get());
    }

    public boolean rarity() {
        return rarity.get();
    }

    public boolean debug() {
        return debug.get();
    }

    /**
     * The command that opens one page, with its number filled in.
     *
     * <p>Formatted rather than concatenated so the number can sit anywhere in the command, and
     * guarded because the format string is typed by hand: a stray {@code %s} would otherwise throw
     * on a click rather than politely doing nothing.
     */
    public String commandFor(StorageData.Page page) {
        String raw = page.kind == StorageData.Kind.ENDER_CHEST
                ? enderChestCommand.get()
                : backpackCommand.get();
        try {
            return String.format(java.util.Locale.ROOT, raw, page.index);
        } catch (java.util.IllegalFormatException e) {
            return raw;
        }
    }

    public String storageCommand() {
        return storageCommand.get();
    }
}
