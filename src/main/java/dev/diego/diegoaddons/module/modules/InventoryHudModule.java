package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.InventoryLayoutScreen;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A HUD element that mirrors the player's inventory on screen as a grid of item slots, so you can
 * see your inventory without opening it.
 *
 * <p>The element is built from optional <b>sections</b> - armour, equipment and the storage grid -
 * laid out left to right in whatever order you arrange them in
 * ({@link InventoryLayoutScreen}, from this feature's settings). Whichever section is tallest sets
 * the height and the storage grid grows its slots to match, so the element has no half-empty column.
 *
 * <p>The drawing lives in {@link dev.diego.diegoaddons.hud.InventoryElement}, which builds it as a
 * RenderLib component tree; this class is only the settings and what they mean.
 */
public class InventoryHudModule extends HudModule {
    private final BooleanSetting background = new BooleanSetting(this, "background", "Background", true);
    private final BooleanSetting slotBoxes = new BooleanSetting(this, "slots", "Slot boxes", true);
    private final BooleanSetting hotbar = new BooleanSetting(this, "hotbar", "Show hotbar", false);
    private final BooleanSetting armor = new BooleanSetting(this, "armor", "Armor", false);
    private final BooleanSetting equipment = new BooleanSetting(this, "equipment", "Equipment (SkyBlock)", false);
    private final BooleanSetting debugScan = new BooleanSetting(this, "debug", "Debug scan (log)", false);
    private final ActionSetting layout =
            new ActionSetting(this, "layout", "Section order", "Arrange", this::openLayoutEditor);

    public InventoryHudModule() {
        super("inventory", "Inventory HUD", "Shows your inventory as a grid of item slots.", false);
        settings.add(background);
        settings.add(slotBoxes);
        settings.add(hotbar);
        settings.add(armor);
        settings.add(equipment);
        settings.add(layout);
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
        if (!equipment.get() && !petWanted()) {
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

    public boolean showArmor() {
        return armor.get();
    }

    public boolean showEquipment() {
        return equipment.get();
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
        return "" + background.get() + slotBoxes.get() + hotbar.get() + armor.get()
                + equipment.get() + String.join(",", sectionOrder());
    }

    // --- section order ----------------------------------------------------------------------------

    /** The sections, in the order they are laid out in unless you rearrange them. */
    public static final List<String> SECTIONS = List.of("armor", "equipment", "storage");

    /** What a section is called in the arranging screen. */
    public static String sectionName(String section) {
        return switch (section) {
            case "armor" -> "Armor";
            case "equipment" -> "Equipment";
            case "storage" -> "Inventory";
            default -> section;
        };
    }

    /** Whether a section is currently drawn; the storage grid is the one that is always there. */
    public boolean sectionShown(String section) {
        return switch (section) {
            case "armor" -> armor.get();
            case "equipment" -> equipment.get();
            default -> true;
        };
    }

    /**
     * The left-to-right order of the sections. Read back defensively: anything unknown is dropped and
     * anything missing is appended, so a hand-edited or older config still gives a full layout.
     */
    public List<String> sectionOrder() {
        List<String> out = new ArrayList<>();
        String saved = ConfigManager.moduleConfig(id).texts.get(ORDER_KEY);
        if (saved != null) {
            for (String part : saved.split(",")) {
                String s = part.trim();
                if (SECTIONS.contains(s) && !out.contains(s)) {
                    out.add(s);
                }
            }
        }
        for (String s : SECTIONS) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    public void setSectionOrder(List<String> order) {
        ConfigManager.moduleConfig(id).texts.put(ORDER_KEY, String.join(",", order));
        ConfigManager.save();
    }

    private static final String ORDER_KEY = "sectionOrder";

    private void openLayoutEditor() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new InventoryLayoutScreen(mc.screen, this));
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.InventoryElement(this, root);
    }
}
