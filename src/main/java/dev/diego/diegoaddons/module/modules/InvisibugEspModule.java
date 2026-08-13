package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.HuntingEspModule;
import dev.diego.diegoaddons.module.NumberSetting;

/**
 * Boxes Invisibugs, and optionally draws a line to each one.
 *
 * <p>The one critter that cannot be seen at all: an Invisibug is a handful of crit particles
 * drifting inside a one-block radius, with no model and no name plate, and finding it by eye is the
 * whole of the difficulty. {@link dev.diego.diegoaddons.util.Invisibug} explains how it is found;
 * everything on this card is about drawing it once it has been.
 *
 * <p><b>The box geometry is a guess.</b> What is remembered is a marker armour stand, and a marker
 * has no size of its own - so the box is built around its position at whatever size and height these
 * two sliders say, rather than from a bounding box that does not exist. If the box sits beside the
 * particles rather than on them in game, these are the two rows to move.
 */
public class InvisibugEspModule extends HuntingEspModule {
    public static InvisibugEspModule INSTANCE;

    private final NumberSetting size =
            new NumberSetting(this, "size", "Box size", 0.8, 0.2, 3.0, 0.1);
    private final NumberSetting lift =
            new NumberSetting(this, "lift", "Box height offset", 0.4, -2.0, 2.0, 0.1);
    /**
     * Off by default. A tracer is the fastest way to walk to one, and it is also a line across the
     * middle of the screen for as long as one is in range - which is most of the marsh.
     */
    private final BooleanSetting tracer =
            new BooleanSetting(this, "tracer", "Tracer line", false);
    /** Its own colour, because a tracer at the box's colour is hard to follow across a bright marsh. */
    private final ColorSetting tracerColor =
            new ColorSetting(this, "tracerColor", "Tracer color", 0xFF00E5FF);

    public InvisibugEspModule() {
        super("invisibugesp", "Invisibug ESP",
                "Find Invisibugs by their crit particles and box them.",
                0xFF00E5FF, "Galatea", GALATEA);
        settings.add(size);
        settings.add(lift);
        settings.add(tracer);
        settings.add(tracerColor);
        INSTANCE = this;
    }

    /** The width of the box drawn around a bug, in blocks. */
    public double size() {
        return size.get();
    }

    /** How far above the marker's own position the box is centred. */
    public double lift() {
        return lift.get();
    }

    public boolean tracer() {
        return tracer.get();
    }

    public int tracerColor() {
        return tracerColor.argb();
    }
}
