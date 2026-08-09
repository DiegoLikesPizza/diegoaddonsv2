package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.CrystalHollows;
import net.minecraft.client.Minecraft;

/**
 * Marks the Crystal Hollows' special structures - the Jungle Temple, Mines of Divan, Goblin Queen's
 * Den, Lost Precursor City, Khazad-dûm and the Crystal Nucleus.
 *
 * <p>Each is recorded the moment you step into it (the scoreboard's area line naming it is the
 * trigger), then drawn as a beam so you can find your way back, and shown on the Crystal Hollows map.
 * The Hollows regenerate every visit, so the marks are cleared on leaving.
 */
public class StructureFinderModule extends Module {
    public static StructureFinderModule INSTANCE;

    private final BooleanSetting beams =
            new BooleanSetting(this, "beams", "Beams", true);
    private final BooleanSetting labels =
            new BooleanSetting(this, "labels", "Names", true);
    private final BooleanSetting nucleus =
            new BooleanSetting(this, "nucleus", "Mark the Crystal Nucleus", true);
    private final BooleanSetting customColor =
            new BooleanSetting(this, "customColor", "One colour for all", false);
    private final ColorSetting color =
            new ColorSetting(this, "color", "Colour", 0xFF55FFFF);

    public StructureFinderModule() {
        super("structurefinder", Category.MINING, "Structure Finder",
                "Marks special Crystal Hollows structures once you find them.");
        settings.add(beams);
        settings.add(labels);
        settings.add(nucleus);
        settings.add(customColor);
        settings.add(color);
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!CrystalHollows.inHollows()) {
            return;
        }
        CrystalHollows.detect(mc, false);
        if (!beams.get()) {
            return;
        }
        for (CrystalHollows.Waypoint w : CrystalHollows.waypoints()) {
            boolean structure = w.type() == CrystalHollows.Type.STRUCTURE;
            boolean core = w.type() == CrystalHollows.Type.NUCLEUS;
            if (!structure && !core) {
                continue;
            }
            if (core && !nucleus.get()) {
                continue;
            }
            // Each structure has a colour of its own, which is how you tell them apart at distance;
            // the override is for people who would rather they all read as one kind of marker.
            CrystalHollows.drawBeam(mc, w.pos(),
                    customColor.get() ? color.argb() : w.type().color,
                    labels.get() ? w.name() : "");
        }
    }
}
