package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
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

    public StructureFinderModule() {
        super("structurefinder", Category.MINING, "Structure Finder",
                "Marks special Crystal Hollows structures once you find them.");
        INSTANCE = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (!CrystalHollows.inHollows()) {
            return;
        }
        CrystalHollows.detect(mc, false);
        for (CrystalHollows.Waypoint w : CrystalHollows.waypoints()) {
            if (w.type() == CrystalHollows.Type.STRUCTURE || w.type() == CrystalHollows.Type.NUCLEUS) {
                CrystalHollows.drawBeam(mc, w.pos(), w.type().color, w.name());
            }
        }
    }
}
