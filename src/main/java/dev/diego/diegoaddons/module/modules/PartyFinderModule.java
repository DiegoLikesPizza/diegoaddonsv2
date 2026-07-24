package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.PartyFinder;

/**
 * Highlights party-finder listings that are still missing a class you want to play, so the party you
 * can actually join stands out instead of having to read every listing.
 *
 * <p>Any number of classes can be selected: a listing lights up when it is missing <b>any</b> of
 * them, which is what makes "I'll play healer or mage" a single pass down the menu. The same toggles
 * are drawn beside the party finder itself, so the choice can be changed without leaving the menu.
 *
 * <p>See {@link PartyFinder} for how a listing's classes are read.
 */
public class PartyFinderModule extends Module {
    public static PartyFinderModule INSTANCE;

    private final BooleanSetting[] classes = new BooleanSetting[PartyFinder.CLASSES.length];

    public PartyFinderModule() {
        super("partyfinder", Category.RENDER, "Party Finder",
                "Highlight parties still missing a class you picked.");
        for (int i = 0; i < classes.length; i++) {
            classes[i] = new BooleanSetting(this, "class_" + PartyFinder.CLASSES[i],
                    PartyFinder.CLASS_NAMES[i], false);
            settings.add(classes[i]);
        }
        INSTANCE = this;
    }

    /** Flips a class on or off - used by the toggle strip inside the party finder menu. */
    public void toggle(int index) {
        if (index >= 0 && index < classes.length) {
            classes[index].toggle();
        }
    }

    /** Whether the class at {@code index} is one you are looking to play. */
    public boolean wants(int index) {
        return index >= 0 && index < classes.length && classes[index].get();
    }

    /** False when nothing is selected, in which case there is nothing to highlight. */
    public boolean anySelected() {
        for (BooleanSetting s : classes) {
            if (s.get()) {
                return true;
            }
        }
        return false;
    }
}
