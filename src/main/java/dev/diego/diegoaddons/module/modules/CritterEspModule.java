package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.EspModule;
import dev.diego.diegoaddons.util.Safari;

/**
 * Boxes the Critter Safari's critters - all thirty-seven of them, on one card.
 *
 * <p>One module rather than thirty-seven, and that is not laziness. A critter is a thing you catch
 * once: past the first pass over the island what you want is not "show me Foxtrots" but "show me
 * whatever is left worth catching", which is a filter, not a switch per species. So the card carries
 * the two filters that actually get used - which biome, and how rare - and thirty-seven cards you
 * would have had to turn on one at a time are a list of names in {@link Safari} instead.
 *
 * <p>The biome toggles filter by <b>which critter it is</b>, not by where you are standing. A
 * critter belongs to its biome wherever it turns up, and the four biomes meet in the middle of the
 * island, so filtering by location would blink the boxes on and off as you walk across a border.
 */
public class CritterEspModule extends EspModule {
    public static CritterEspModule INSTANCE;

    private final BooleanSetting cavern =
            new BooleanSetting(this, "cavern", "Cavern critters", true);
    private final BooleanSetting forest =
            new BooleanSetting(this, "forest", "Forest critters", true);
    private final BooleanSetting haunted =
            new BooleanSetting(this, "haunted", "Haunted critters", true);
    private final BooleanSetting icy =
            new BooleanSetting(this, "icy", "Icy critters", true);
    /**
     * Nothing below this is drawn. Common by default, so the whole list shows until you decide
     * otherwise - a filter that starts by hiding things is a filter nobody knows is on.
     */
    private final CycleSetting minRarity = new CycleSetting(this, "minRarity", "At least",
            0, "Common", "Uncommon", "Rare", "Epic", "Legendary");
    /**
     * On by default: the rarity is the single most useful thing to know at a glance on this island,
     * and a box that is gold rather than white says "drop what you are doing" without a label.
     */
    private final BooleanSetting byRarity =
            new BooleanSetting(this, "byRarity", "Colour by rarity", true);
    /** Off by default. The name is on the plate already; this is for reading it through a hill. */
    private final BooleanSetting labels =
            new BooleanSetting(this, "labels", "Show names", false);
    /**
     * Logs every plate this boxes and every plate it nearly boxed.
     *
     * <p>Here because the plate format is the one assumption the whole card rests on, and if the
     * critters turn out to be named differently from the wiki there is no way to find that out by
     * looking at an island with no boxes on it.
     */
    private final BooleanSetting debug =
            new BooleanSetting(this, "debug", "Debug plates (log)", false);

    public CritterEspModule() {
        super("critteresp", Category.SAFARI, "Critter ESP",
                "Box the Critter Safari's critters, filtered by biome and rarity.",
                0xFF00E676);
        settings.add(cavern);
        settings.add(forest);
        settings.add(haunted);
        settings.add(icy);
        settings.add(minRarity);
        settings.add(byRarity);
        settings.add(labels);
        settings.add(debug);
        INSTANCE = this;
    }

    /** Whether this critter passes both filters. */
    public boolean wants(Safari.Critter c) {
        if (c.rarity().ordinal() < minRarity.get()) {
            return false;
        }
        return switch (c.biome()) {
            case CAVERN -> cavern.get();
            case FOREST -> forest.get();
            case HAUNTED -> haunted.get();
            case ICY -> icy.get();
        };
    }

    /** The colour to box this critter in: its rarity's, or the card's own. */
    public int colorFor(Safari.Critter c) {
        return byRarity.get() ? c.rarity().color : color();
    }

    public boolean labels() {
        return labels.get();
    }

    public boolean debugPlates() {
        return debug.get();
    }
}
