package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;


/**
 * A HUD element that mirrors the player's inventory on screen as a grid of item slots, so you can
 * see your inventory without opening it.
 *
 * <p>Just the storage grid, with the hotbar as an option. Armour, your character and your equipment
 * moved to the Player HUD, where they belong - they are all "what am I wearing", which a grid of
 * everything you are carrying is not.
 *
 * <p>The drawing lives in {@link dev.diego.diegoaddons.hud.InventoryElement}, which builds it as a
 * RenderLib component tree; this class is only the settings and what they mean.
 */
public class InventoryHudModule extends HudModule {
    private final BooleanSetting background = new BooleanSetting(this, "background", "Background", true);
    private final BooleanSetting slotBoxes = new BooleanSetting(this, "slots", "Slot boxes", true);
    private final BooleanSetting hotbar = new BooleanSetting(this, "hotbar", "Show hotbar", false);
    private final BooleanSetting debugScan = new BooleanSetting(this, "debug", "Debug scan (log)", false);

    public InventoryHudModule() {
        super("inventory", "Inventory HUD", "Shows your inventory as a grid of item slots.", false);
        settings.add(background);
        settings.add(slotBoxes);
        settings.add(hotbar);
        settings.add(debugScan);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        SkyblockHud.debug = debugScan.get();

        // One-time hint: the SkyBlock pet/equipment can only be read from their (paged) menus, so
        // ask the user to open every page once. Shown only when those toggles are actually in use.
        if (mc.player == null || ConfigManager.get().sbHintShown) {
            return;
        }
        if (!equipmentWanted() && !petWanted()) {
            return;
        }
        mc.gui.getChat().addClientSystemMessage(Component.literal(
                "§b[DiegoAddons] §fTo show your SkyBlock pet & equipment, open §eevery page§f of your "
                        + "§e(x/y) Pets §fand §e(x/y) Equipment Sets §fmenus once - the HUD reads them from there."));
        ConfigManager.get().sbHintShown = true;
        ConfigManager.save();
    }

    @Override
    protected String label() {
        return "Inventory";
    }

    @Override
    protected String value(Minecraft mc) {
        return null; // custom-drawn; see drawLocal
    }

    // --- read by the RenderLib element -----------------------------------------------------------

    public boolean showBackground() {
        return background.get();
    }

    public boolean showSlotBoxes() {
        return slotBoxes.get();
    }

    public boolean showHotbar() {
        return hotbar.get();
    }

    /** Whether anything is asking for the equipment cache - the player HUD owns it now. */
    private static boolean equipmentWanted() {
        PlayerHudModule player = PlayerHudModule.INSTANCE;
        return player != null && player.isEnabled() && player.sectionShown("equipment");
    }

    /** Whether anything is asking for the pet cache - the pet HUD is its own module now. */
    private static boolean petWanted() {
        PetHudModule pet = PetHudModule.INSTANCE;
        return pet != null && pet.isEnabled();
    }

    /**
     * Which sections are on and in what order, as a string - the element rebuilds its tree when this
     * changes and leaves it alone otherwise.
     */
    public String sectionSignature() {
        return "" + background.get() + slotBoxes.get() + hotbar.get();
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.InventoryElement(this, root);
    }
}
