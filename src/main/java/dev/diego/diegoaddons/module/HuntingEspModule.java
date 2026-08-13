package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.util.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An ESP for one of the Hunting skill's mobs.
 *
 * <p>Every one of these is the same shape - box a kind of mob, on the island where hunting it means
 * anything - so the only thing a subclass has to say is <i>which</i> mob and <i>where</i>. What is
 * shared lives here: the island gate, and the registry that
 * {@link dev.diego.diegoaddons.util.Hunting} walks so the whole set costs one pass over the entities
 * rather than one pass each.
 *
 * <p><b>Why the gate is a setting rather than a constant.</b> Most of these critters are ordinary
 * vanilla animals - a dolphin is a dolphin - so boxing them everywhere would light up the whole of
 * Backwater Bayou while you are fishing. It defaults to on for that reason, and it is a switch
 * rather than a rule because the location strings below are read off Hypixel's own tab list and are
 * the part of this that can go stale.
 */
public abstract class HuntingEspModule extends EspModule {
    /**
     * Every hunting ESP, in construction order.
     *
     * <p>The alternative is a static {@code INSTANCE} per module and a hand-written list of the ten
     * of them somewhere else, which is exactly the kind of list that gets one entry short and stays
     * that way. Registering here means adding a module is one file.
     */
    private static final List<HuntingEspModule> ALL = new ArrayList<>();

    /**
     * Galatea, where every critter lives. Both names because the tab list gives the island and the
     * scoreboard gives the sub-area, and the marsh is the only part of Galatea critters are in.
     */
    public static final String[] GALATEA = {"Galatea", "Moonglade Marsh"};
    /** The Crimson Isle, for the two hunting mobs that are not critters at all. */
    public static final String[] CRIMSON = {"Crimson Isle", "Blazing Volcano"};
    /**
     * Torrhus Canyon, the second Foraging island.
     *
     * <p>Just "Torrhus", because the Heights are part of it and at least one mob (Sneaky Tiki) is
     * documented in both - one name that covers the whole place beats two that have to be kept in
     * step with wherever Hypixel draws the line between them.
     */
    public static final String[] TORRHUS = {"Torrhus"};

    /** The location names that count as "here", matched case-insensitively by {@code contains}. */
    private final String[] places;
    private final BooleanSetting onlyHere;

    /**
     * @param places   the tab-list island and/or scoreboard area names this mob is hunted in
     * @param placeName how those places are named on the setting's row, e.g. "Galatea"
     */
    protected HuntingEspModule(String id, String name, String description, int defaultColor,
                               String placeName, String... places) {
        super(id, Category.HUNTING, name, description, defaultColor);
        this.places = places;
        this.onlyHere = new BooleanSetting(this, "onlyHere", "Only on " + placeName, true);
        settings.add(onlyHere);
        ALL.add(this);
    }

    /** Every registered hunting ESP. Do not modify the result. */
    public static List<HuntingEspModule> all() {
        return ALL;
    }

    /** Whether this module should be drawing at all right now: enabled, and on the right island. */
    public boolean shouldDraw(Minecraft mc) {
        if (!isEnabled()) {
            return false;
        }
        if (!onlyHere.get()) {
            return true;
        }
        // Both readings, because a sub-area is not the island: the tab list says "Galatea" while the
        // scoreboard says "Moonglade Marsh", and either one arriving first is enough to start drawing.
        String island = SkyblockLocation.island(mc).toLowerCase(Locale.ROOT);
        String area = SkyblockLocation.area(mc).toLowerCase(Locale.ROOT);
        for (String p : places) {
            String needle = p.toLowerCase(Locale.ROOT);
            if (island.contains(needle) || area.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this entity is the mob this module boxes.
     *
     * <p>False by default, for the two kinds of hunting ESP that are not a plain entity match: one
     * that reads a name plate ({@link #matchesPlate}) and one that is found by its particles at all
     * (see {@link dev.diego.diegoaddons.util.Invisibug}).
     */
    public boolean matches(Entity e) {
        return false;
    }

    /**
     * Whether this name plate belongs to the mob this module boxes, colour codes already stripped.
     *
     * <p>The critters are real vanilla animals and are matched by type; a SkyBlock <i>mob</i> is a
     * zombie in a costume wearing a plate, and only its plate says what it is.
     */
    public boolean matchesPlate(String plate) {
        return false;
    }

    /** How far to grow the mob's own bounding box before drawing, so the box is not flush with it. */
    public double inflate() {
        return 0.1;
    }
}
