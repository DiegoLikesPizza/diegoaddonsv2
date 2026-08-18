package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.ActionSetting;
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
    private final BooleanSetting showMissing =
            new BooleanSetting(this, "showMissing", "Show missing classes on hover", true);
    private final BooleanSetting panel =
            new BooleanSetting(this, "panel", "Show the class panel in the menu", true);

    public PartyFinderModule() {
        super("partyfinder", Category.RENDER, "Party Finder",
                "Highlight parties still missing a class you picked.");
        // These are settings first and panel rows second. The panel edits the same objects the
        // settings menu does, so the two cannot disagree - which is what the old strip, with its own
        // copy of the picks, could not promise.
        for (int i = 0; i < classes.length; i++) {
            classes[i] = new BooleanSetting(this, "class_" + PartyFinder.CLASSES[i],
                    PartyFinder.CLASS_NAMES[i], false);
            settings.add(classes[i]);
        }
        settings.add(showMissing);
        settings.add(panel);
        // The panel is dragged by its title bar and remembers where it was put, so the one thing it
        // cannot do for itself is come back from somewhere you would rather it was not.
        settings.add(new ActionSetting(this, "resetPanel", "Panel position", "Reset",
                PartyFinder::resetPosition));
        INSTANCE = this;
    }

    /** Flips a class on or off - used by the panel inside the party finder menu. */
    public void toggle(int index) {
        if (index >= 0 && index < classes.length) {
            classes[index].toggle();
        }
    }

    /** Sets a class pick - the panel's toggles hand a value rather than a flip. */
    public void setWanted(int index, boolean value) {
        if (index >= 0 && index < classes.length) {
            classes[index].set(value);
        }
    }

    /** Whether the panel is drawn beside the party finder. */
    public boolean panel() {
        return panel.get();
    }

    /** The "missing classes" setting itself, so the panel can flip it. */
    public void setShowMissing(boolean value) {
        showMissing.set(value);
    }

    /** Whether the class at {@code index} is one you are looking to play. */
    public boolean wants(int index) {
        return index >= 0 && index < classes.length && classes[index].get();
    }

    /** Whether hovering a listing names the classes nobody in it is playing. */
    public boolean showMissing() {
        return showMissing.get();
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
