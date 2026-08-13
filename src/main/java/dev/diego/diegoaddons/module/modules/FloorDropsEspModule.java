package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.EspModule;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.FloorDrops;
import net.minecraft.client.Minecraft;

/**
 * Boxes Floor Drops - the clickable pick-ups scattered over the Marsh, the Canyon and the Safari.
 *
 * <p>A Floor Drop is a piece of string lying on a grass block, which in Minecraft terms is a
 * tripwire: a few pixels of thread the same colour as everything else on the ground, at preset
 * spots you are expected to have learned. That is the entire difficulty of collecting them, and it
 * is a rendering problem rather than a knowledge one, which is what makes it worth a box.
 *
 * <p><b>The block is a guess, so it is a text box rather than a constant</b> - the same call the
 * Storage Overlay's commands got. "String on the ground" is what the wiki says and
 * {@code minecraft:tripwire} is what that is; if it turns out to be something else, this row is the
 * fix and no code has to change.
 */
public class FloorDropsEspModule extends EspModule {
    public static FloorDropsEspModule INSTANCE;

    private final StringSetting block =
            new StringSetting(this, "block", "Block id", "minecraft:tripwire", null);
    /**
     * How far out to look. Every block in the cube is asked what it is, so this is the one setting
     * here with a real cost - see {@link FloorDrops}, which is why the scan is not every tick.
     */
    private final NumberSetting radius =
            new NumberSetting(this, "radius", "Search radius", 24, 8, 64, 4);
    /** Ground clutter is at ground level; there is no reason to search a chunk's full height. */
    private final NumberSetting height =
            new NumberSetting(this, "height", "Search height (±)", 8, 2, 32, 2);
    /** Off by default: on the Safari these are dense, and a beam each turns the island into a fence. */
    private final BooleanSetting beam =
            new BooleanSetting(this, "beam", "Beam of light", false);
    /**
     * Which islands to search on.
     *
     * <p>On by default because the scan is the expensive part of this module and there is nothing to
     * find anywhere else - the three Foraging islands are the only places Floor Drops exist.
     */
    private final BooleanSetting onlyForaging =
            new BooleanSetting(this, "onlyForaging", "Only on the Foraging islands", true);
    /**
     * The 113 known Safari spots, drawn as a quieter second layer.
     *
     * <p>These are <b>where a drop can be</b>, not where one is - the wiki says drops spawn "in
     * preset locations" with a chance, so this is a route to walk rather than a list of things to
     * collect. It is worth having anyway: the block scan can only see what is already in range, and
     * this is what tells you where to go when nothing is.
     */
    private final BooleanSetting presets =
            new BooleanSetting(this, "presets", "Mark known spots (Safari)", true);
    private final NumberSetting presetRange =
            new NumberSetting(this, "presetRange", "Known spots within (blocks)", 80, 20, 300, 10);

    public FloorDropsEspModule() {
        super("floordropsesp", Category.SAFARI, "Floor Drops ESP",
                "Box the string-marked Floor Drops on the ground.",
                0xFF80DEEA);
        settings.add(block);
        settings.add(radius);
        settings.add(height);
        settings.add(beam);
        settings.add(onlyForaging);
        settings.add(presets);
        settings.add(presetRange);
        INSTANCE = this;
    }

    public boolean presets() {
        return presets.get();
    }

    public double presetRange() {
        return presetRange.get();
    }

    public String blockId() {
        return block.get();
    }

    public int radius() {
        return (int) radius.get();
    }

    public int height() {
        return (int) height.get();
    }

    public boolean beam() {
        return beam.get();
    }

    /** Whether the scan should run where the player currently is. */
    public boolean here(Minecraft mc) {
        return !onlyForaging.get() || FloorDrops.onAFloorDropIsland(mc);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        FloorDrops.tick(mc, this);
    }
}
