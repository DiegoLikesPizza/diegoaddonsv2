package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.HypixelSkulls;
import dev.diego.diegoaddons.util.InvButtons;
import dev.diego.diegoaddons.util.Toasts;
import net.minecraft.client.Minecraft;

/**
 * Buttons on your inventory that run a command.
 *
 * <p>A port of the Inventory Buttons mod, which is itself NotEnoughUpdates' feature carried forward:
 * buttons sit around the edge of any menu, wear an item, a SkyBlock head or one of sixteen bundled
 * icons, and send their command when clicked. The buttons and the editor are drawn exactly as the
 * mod they came from draws them - see {@link dev.diego.diegoaddons.gui.InvButtonEditor}.
 *
 * <p>The layout lives in the mod's config; a <b>profile</b> is a copy of it saved to a file, so a
 * mining set and a dungeon set can be swapped between from this card. Saving one is done in the
 * editor, where the layout you would want to save is in front of you.
 */
public class InventoryButtonsModule extends Module {
    public static InventoryButtonsModule INSTANCE;

    private final BooleanSetting tooltips =
            new BooleanSetting(this, "tooltips", "Show the command on hover", true);
    private final BooleanSetting hideInCreative =
            new BooleanSetting(this, "hideInCreative", "Hide in creative", false);
    private final BooleanSetting gridSnap =
            new BooleanSetting(this, "gridSnap", "Grid snap by default", false);
    private final ActionSetting edit =
            new ActionSetting(this, "edit", "Buttons", "Edit", InventoryButtonsModule::openEditor);
    private final ActionSetting deleteProfile =
            new ActionSetting(this, "deleteProfile", "Selected profile", "Delete",
                    InventoryButtonsModule::deleteProfile);

    /**
     * The profile last loaded from the card.
     *
     * <p>Not in {@code settings}: it is drawn as a picker over the profiles folder (see
     * {@link dev.diego.diegoaddons.config.ListSpecs}), and adding it here as well would put the
     * same value on the card twice.
     */
    private final StringSetting profile =
            new StringSetting(this, "profile", "Profile", "", null);

    public InventoryButtonsModule() {
        super("inventorybuttons", Category.MISC, "Inventory Buttons",
                "Buttons on your inventory that run a command.");
        settings.add(tooltips);
        settings.add(hideInCreative);
        settings.add(gridSnap);
        settings.add(edit);
        settings.add(deleteProfile);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        // The icon picker wants Hypixel's item list; asked for here so it has landed by the time
        // anyone opens the editor. It is fetched once per session however often this is called.
        HypixelSkulls.load();
    }

    public boolean showTooltips() {
        return tooltips.get();
    }

    public boolean hideInCreative() {
        return hideInCreative.get();
    }

    public boolean gridSnap() {
        return gridSnap.get();
    }

    public String profile() {
        return profile.get();
    }

    /** Picking a profile in the menu is what loads it - there is no second "apply" to forget. */
    public void setProfile(String name) {
        profile.set(name == null ? "" : name);
        if (name != null && !name.isEmpty()) {
            InvButtons.loadProfile(name);
        }
    }

    /** The button on the card: the editor, over whatever screen asked for it. */
    private static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new dev.diego.diegoaddons.gui.InvButtonEditor(mc.screen)));
    }

    private static void deleteProfile() {
        InventoryButtonsModule module = INSTANCE;
        if (module == null) {
            return;
        }
        String name = module.profile();
        if (name.isEmpty()) {
            Toasts.show("No profile selected", "Pick one above first");
            return;
        }
        InvButtons.deleteProfile(name);
        module.profile.set("");
        Toasts.show("Profile deleted", name);
    }
}
