package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.SectionOrderScreen;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.NumberSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Your character on the HUD, on its own rather than as a column of the inventory.
 *
 * <p>Split out because the two are looked at differently: an inventory is read, a model is glanced
 * at. Tying them together meant the model could only be where the inventory was, and only as tall as
 * the grid beside it.
 */
public class PlayerHudModule extends HudModule {
    public static PlayerHudModule INSTANCE;

    private final BooleanSetting armor =
            new BooleanSetting(this, "armor", "Armor", true);
    private final BooleanSetting model =
            new BooleanSetting(this, "model", "Player model", true);
    private final BooleanSetting equipment =
            new BooleanSetting(this, "equipment", "Equipment (SkyBlock)", true);
    private final NumberSetting height =
            new NumberSetting(this, "height", "Height", 70, 40, 160, 5);
    private final BooleanSetting slotBoxes =
            new BooleanSetting(this, "slots", "Slot boxes", true);
    private final BooleanSetting background =
            new BooleanSetting(this, "background", "Background", true);
    private final ActionSetting layout =
            new ActionSetting(this, "layout", "Section order", "Arrange", this::openLayoutEditor);

    public PlayerHudModule() {
        super("playerhud", "Player HUD", "Your armour, your character and your equipment.", false);
        settings.add(armor);
        settings.add(model);
        settings.add(equipment);
        settings.add(height);
        settings.add(slotBoxes);
        settings.add(background);
        settings.add(layout);
        INSTANCE = this;
    }

    public boolean showSlotBoxes() {
        return slotBoxes.get();
    }

    // --- sections ---------------------------------------------------------------------------------

    /** Left to right, unless you rearrange them. */
    public static final List<String> SECTIONS = List.of("armor", "player", "equipment");

    public static String sectionName(String section) {
        return switch (section) {
            case "armor" -> "Armor";
            case "player" -> "Player model";
            case "equipment" -> "Equipment";
            default -> section;
        };
    }

    public boolean sectionShown(String section) {
        return switch (section) {
            case "armor" -> armor.get();
            case "player" -> model.get();
            case "equipment" -> equipment.get();
            default -> false;
        };
    }

    /** Read back defensively: anything unknown is dropped, anything missing is appended. */
    public List<String> sectionOrder() {
        List<String> out = new ArrayList<>();
        String saved = ConfigManager.moduleConfig(id).texts.get("sectionOrder");
        if (saved != null) {
            for (String part : saved.split(",")) {
                String v = part.trim();
                if (SECTIONS.contains(v) && !out.contains(v)) {
                    out.add(v);
                }
            }
        }
        for (String v : SECTIONS) {
            if (!out.contains(v)) {
                out.add(v);
            }
        }
        return out;
    }

    public void setSectionOrder(List<String> order) {
        ConfigManager.moduleConfig(id).texts.put("sectionOrder", String.join(",", order));
        ConfigManager.save();
    }

    /** What the element rebuilds on. */
    public String sectionSignature() {
        return "" + armor.get() + model.get() + equipment.get() + height.get()
                + slotBoxes.get() + background.get() + String.join(",", sectionOrder());
    }

    private void openLayoutEditor() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new SectionOrderScreen(mc.screen, this)));
    }

    public float height() {
        return (float) height.get();
    }

    public boolean showBackground() {
        return background.get();
    }

    @Override
    protected String label() {
        return "Player";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // drawn by its own element
    }

    @Override
    public dev.diego.diegoaddons.hud.HudElement createElement(
            com.render.api.gui.ContainerComponent root) {
        return new dev.diego.diegoaddons.hud.PlayerElement(this, root);
    }
}
