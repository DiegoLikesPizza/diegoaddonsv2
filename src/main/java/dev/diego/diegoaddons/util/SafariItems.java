package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.SafariItemsModule;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * What a Safari item is actually for, under its own lore.
 *
 * <p>Several of the thirty-seven critters are not caught by throwing a capsule at them - they are
 * caught by using the right object in the right place, and the object's own description does not say
 * which. A Shining Coin says it is a shiny coin; it does not say that throwing it in water is the
 * only way a Gimmiegold ever appears. That gap is the whole feature: the information exists, it is
 * on the wiki, and it is not on the item you are holding while standing next to the water.
 *
 * <p>Matched on the <b>display name</b>, lowercased and by {@code contains}, which is the loosest
 * match that still cannot collide - these names are distinctive enough that no vanilla or SkyBlock
 * item shares them. A name that stops matching costs one missing line, not a wrong one.
 */
public final class SafariItems {
    /** One item, and the sentence worth adding to it. */
    private record Note(String match, String text) {
    }

    /**
     * The mapping, off the wiki's own capture-method column.
     *
     * <p>Ordered longest-match-first is not needed here because no name is a prefix of another, but
     * the two capsules are deliberately adjacent so the difference between them stays visible to
     * whoever edits this next.
     */
    private static final Note[] NOTES = {
            new Note("masterful critter capsule",
                    "The better capsule - higher catch rate on the rare and legendary critters."),
            new Note("critter capsule",
                    "Thrown at a critter to catch it. The only tool that works here; "
                            + "hunting tools are sent to your Stash."),
            new Note("shining coin",
                    "Throw it in water to make a §eGimmiegold §7appear. (Haunted, rare)"),
            new Note("soothing incense",
                    "Place §f4 §7of them to summon §6Doomspiral§7. (Haunted, legendary)"),
            new Note("bag of seeds",
                    "Load the Birdfeeder with it for a §6Macaw§7. (Forest, legendary)"),
            new Note("yogi berry",
                    "Load the Birdfeeder with it for a §aBluebird§7. (Forest, uncommon)"),
            new Note("wriggleworm",
                    "Load the Birdfeeder with it for a §9Parakeet§7. (Forest, rare)"),
            new Note("sparkling amulet",
                    "Doubles your chance of a §6SPARKLING §7critter. Does not stack."),
            new Note("rainbow feather",
                    "Raises any pet by one level. Dropped only by §6SPARKLING §7critters."),
            new Note("safari essence",
                    "Spent with §eArchie §7at the entrance - perks, upgrades and cosmetics."),
            new Note("safari ticket",
                    "One run of the Critter Safari. From §eMiria's §7contests."),
    };

    /**
     * Critters caught by feeding or using something ordinary, keyed the other way round: the note
     * goes on the food rather than on the critter, because the food is what you are holding when the
     * question comes up.
     */
    private static final Note[] FOOD = {
            new Note("bamboo", "Feed §f40-44 §7to a panda critter to catch it."),
            new Note("lily pad", "One of the three foods a §9Scrappy §7(armadillo) will take."),
    };

    private SafariItems() {
    }

    /** Appends the note for this item, if there is one. Registered as a tooltip callback. */
    public static void appendTooltip(ItemStack stack, List<Component> lines) {
        SafariItemsModule m = SafariItemsModule.INSTANCE;
        if (m == null || !m.isEnabled() || stack.isEmpty() || lines.isEmpty()) {
            return;
        }
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String note = lookup(name, m.includeFood());
        if (note == null) {
            return;
        }
        lines.add(Component.literal(""));
        lines.add(Component.literal("§b§lSafari"));
        // Wrapped by hand rather than left as one long line: a tooltip does not wrap itself, and a
        // sentence running off the edge of the screen is worse than no sentence.
        for (String part : wrap(note, 48)) {
            lines.add(Component.literal("§7" + part));
        }
    }

    private static String lookup(String name, boolean includeFood) {
        for (Note n : NOTES) {
            if (name.contains(n.match())) {
                return n.text();
            }
        }
        if (includeFood) {
            for (Note n : FOOD) {
                if (name.contains(n.match())) {
                    return n.text();
                }
            }
        }
        return null;
    }

    /** Breaks on spaces at roughly {@code width} visible characters, ignoring colour codes. */
    private static List<String> wrap(String text, int width) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        int visible = 0;
        for (String word : text.split(" ")) {
            int len = LegacyText.strip(word).length();
            if (visible > 0 && visible + 1 + len > width) {
                out.add(line.toString());
                line.setLength(0);
                visible = 0;
            }
            if (visible > 0) {
                line.append(' ');
                visible++;
            }
            line.append(word);
            visible += len;
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }
}
