package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.ColorSetting;
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

    // --- the particle route, which is the detector -------------------------------------------------

    /**
     * A second detector, on by default, and no longer the main one.
     *
     * <p>It was promoted to primary after the screenshot and demoted again after Diego said what the
     * pale bits on the top face actually are: <b>strings stuck in the block</b>. So the block really
     * is the marker and the tripwire guess was right the first time - a Floor Drop is not "some
     * block the island is covered in", it is a block with string in it, which nothing else is.
     *
     * <p>Kept on anyway because the two fail in different ways: the scan only sees what is in range
     * and lags a couple of seconds behind a drop being taken, while the particles arrive instantly
     * and from anywhere. Both feed one list, so a drop found twice is still drawn once.
     */
    private final BooleanSetting particles =
            new BooleanSetting(this, "particles", "Also find by particles", true);
    /** Green four-pointed sparkles are the villager-happy particle; a text box in case they are not. */
    private final StringSetting particleIds = new StringSetting(this, "particleIds",
            "Particle ids", "minecraft:happy_villager", null);
    private final NumberSetting particleMin =
            new NumberSetting(this, "particleMin", "Particles before marking", 4, 1, 40, 1);
    /**
     * How far below the particle cluster the block is.
     *
     * <p>They come off the top face and drift up, so the cluster settles above the block rather than
     * in it. How far above depends on how high they rise before the cluster stops following them,
     * which is a thing to look at in game rather than to reason about - hence a slider.
     */
    private final NumberSetting particleDrop =
            new NumberSetting(this, "particleDrop", "Block is below by", 1.0, 0.0, 3.0, 0.5);

    // --- the block route, kept as the fallback -----------------------------------------------------

    /**
     * The main detector, back on by default.
     *
     * <p>Diego: "floor drops erkennt man daran, dass da strings in dem block stecken". String placed
     * in the world is {@code minecraft:tripwire}, so the wiki's "String on the ground" and the pale
     * squiggles on the screenshot's top face are the same thing seen twice, and this was right
     * before I talked myself out of it.
     */
    private final BooleanSetting scan =
            new BooleanSetting(this, "scan", "Scan for the block", true);
    private final StringSetting block =
            new StringSetting(this, "block", "Block id", "minecraft:tripwire", null);
    /**
     * Whether the string sits in the air space above the block it looks stuck in.
     *
     * <p>That is how a tripwire works - it occupies its own block and renders its strings just off
     * the top face of the solid block below, which is why they look embedded in it. So the block
     * worth boxing is one down from the one the scan finds. Off if it turns out Hypixel does it some
     * other way and the boxes come out a block low.
     */
    private final BooleanSetting stringAbove =
            new BooleanSetting(this, "stringAbove", "String sits above the block", true);
    /**
     * Logs the block under your crosshair, and says it in chat.
     *
     * <p>Here so the row above stops being a guess: stand in front of a drop, look at it, press the
     * button. Also logs which particle types are arriving, every five seconds, for the same reason.
     */
    private final ActionSetting identify = new ActionSetting(this, "identify",
            "Block I'm looking at", "Log", FloorDrops::logLookingAt);
    private final BooleanSetting debug =
            new BooleanSetting(this, "debug", "Debug particles (log)", false);
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
     * The top face marked in a second colour, which is the look Diego asked for.
     *
     * <p>It earns its place rather than just being a copy: the string is <i>on the top face</i>, so
     * that face is the thing you are looking for and the one you have to click. A box says "here is
     * a block"; a filled top says "here is the surface with the drop on it", which is a different
     * and more useful sentence when you are running past at head height.
     */
    private final BooleanSetting topFace =
            new BooleanSetting(this, "topFace", "Mark the top face", true);
    private final ColorSetting topColor =
            new ColorSetting(this, "topColor", "Top face colour", 0xFFFF55FF);
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
                0xFFFFD500);
        settings.add(particles);
        settings.add(particleIds);
        settings.add(particleMin);
        settings.add(particleDrop);
        settings.add(scan);
        settings.add(block);
        settings.add(stringAbove);
        settings.add(identify);
        settings.add(debug);
        settings.add(radius);
        settings.add(height);
        settings.add(beam);
        settings.add(topFace);
        settings.add(topColor);
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

    public boolean particles() {
        return particles.get();
    }

    public String particleIds() {
        return particleIds.get();
    }

    public int particleMin() {
        return (int) particleMin.get();
    }

    public double particleDrop() {
        return particleDrop.get();
    }

    public boolean scan() {
        return scan.get();
    }

    public boolean stringAbove() {
        return stringAbove.get();
    }

    public boolean debug() {
        return debug.get();
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

    public boolean topFace() {
        return topFace.get();
    }

    public int topColor() {
        return topColor.argb();
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
