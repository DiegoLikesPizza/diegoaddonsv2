package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the Critter Safari is: thirty-seven critters, four biomes, and one rare variant of every one
 * of them.
 *
 * <p>The whole island is read from <b>name plates</b>. That is not a fallback - it is the only thing
 * the thirty-seven have in common. About a third of them are not vanilla animals at all (Duplico
 * disguises itself as a block, Gazer only appears while you sleep, Hideyho is a skinned player
 * entity), so matching by entity type would cover two thirds of the list and quietly miss the rest,
 * including most of the rare ones. Every critter carries its name above it, and the sparkling
 * variant carries {@code SPARKLING} in front of that, so one plate read answers both questions.
 *
 * <p><b>The plate format is a guess</b> and the matching is deliberately loose because of it: the
 * name is looked for anywhere in the line, on word boundaries, case-insensitively. A plate reading
 * {@code [Lv5] Foxtrot 100/100} and one reading {@code Foxtrot} both work; what does not work is
 * Hypixel spelling a critter differently from the wiki, which is the one failure this cannot absorb.
 */
public final class Safari {
    /** The tab-list island name. SkyHanni's island id for the same place is {@code safari}. */
    public static final String ISLAND = "Critter Safari";

    /** The four biomes, in the order the wiki lists them. */
    public enum Biome {
        CAVERN("Cavern"),
        FOREST("Forest"),
        HAUNTED("Haunted"),
        ICY("Icy");

        public final String display;

        Biome(String display) {
            this.display = display;
        }
    }

    /**
     * SkyBlock's rarity ladder, as far up it as the critters go, with the colours the game uses.
     *
     * <p>Kept as an ordinal ladder rather than a set because the useful filter is "nothing below
     * this", not "these exact ones" - past the first hour on the island the commons are noise.
     */
    public enum Rarity {
        COMMON("Common", 0xFFFFFFFF),
        UNCOMMON("Uncommon", 0xFF55FF55),
        RARE("Rare", 0xFF5555FF),
        EPIC("Epic", 0xFFAA00AA),
        LEGENDARY("Legendary", 0xFFFFAA00);

        public final String display;
        /** The rarity's own colour, for "Colour by rarity". */
        public final int color;

        Rarity(String display, int color) {
            this.display = display;
            this.color = color;
        }
    }

    /** One critter: what it is called, where it lives, and how rare it is. */
    public record Critter(String name, Biome biome, Rarity rarity) {
    }

    /**
     * All thirty-seven, off the wiki's Critter Safari table.
     *
     * <p>Nine in the Cavern, nine in the Forest, ten in the Haunted biome and nine in the Icy one.
     * If that total ever stops being thirty-seven, this list has drifted from the game.
     */
    private static final Critter[] CRITTERS = {
            // Cavern
            new Critter("Cavernfish", Biome.CAVERN, Rarity.COMMON),
            new Critter("Flitter", Biome.CAVERN, Rarity.COMMON),
            new Critter("Shyworm", Biome.CAVERN, Rarity.COMMON),
            new Critter("Driftling", Biome.CAVERN, Rarity.UNCOMMON),
            new Critter("Chuckwalla", Biome.CAVERN, Rarity.RARE),
            new Critter("Rockmite", Biome.CAVERN, Rarity.RARE),
            new Critter("Scrappy", Biome.CAVERN, Rarity.RARE),
            new Critter("Snoozle", Biome.CAVERN, Rarity.RARE),
            new Critter("Gemzie", Biome.CAVERN, Rarity.EPIC),
            // Forest
            new Critter("Foxtrot", Biome.FOREST, Rarity.COMMON),
            new Critter("Bluebird", Biome.FOREST, Rarity.UNCOMMON),
            new Critter("Honeybug", Biome.FOREST, Rarity.UNCOMMON),
            new Critter("Treefrog", Biome.FOREST, Rarity.UNCOMMON),
            new Critter("Woodchucker", Biome.FOREST, Rarity.UNCOMMON),
            new Critter("Fluffling", Biome.FOREST, Rarity.RARE),
            new Critter("Hideonfloor", Biome.FOREST, Rarity.RARE),
            new Critter("Parakeet", Biome.FOREST, Rarity.RARE),
            new Critter("Macaw", Biome.FOREST, Rarity.LEGENDARY),
            // Haunted
            new Critter("Areita", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Bloodbat", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Duplico", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Gazer", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Litterbug", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Solsnatcher", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Gimmiegold", Biome.HAUNTED, Rarity.RARE),
            new Critter("Hideonwall", Biome.HAUNTED, Rarity.RARE),
            new Critter("Hideyho", Biome.HAUNTED, Rarity.RARE),
            new Critter("Doomspiral", Biome.HAUNTED, Rarity.LEGENDARY),
            // Icy
            new Critter("Strongarm", Biome.ICY, Rarity.COMMON),
            new Critter("Tepid", Biome.ICY, Rarity.COMMON),
            new Critter("Polaris", Biome.ICY, Rarity.UNCOMMON),
            new Critter("Shuddersquid", Biome.ICY, Rarity.UNCOMMON),
            new Critter("Billygoat", Biome.ICY, Rarity.RARE),
            new Critter("Mantis Shrimp", Biome.ICY, Rarity.RARE),
            new Critter("Nozzlenose", Biome.ICY, Rarity.RARE),
            new Critter("Troodon", Biome.ICY, Rarity.RARE),
            new Critter("Wumpa", Biome.ICY, Rarity.LEGENDARY),
    };

    /** The prefix Hypixel puts on the 1-in-8,192 variant, which is the whole of its detection. */
    private static final String SPARKLING = "SPARKLING";

    private static final Map<String, Critter> BY_NAME = new HashMap<>();
    private static final Pattern ANY_CRITTER;

    static {
        // Longest first, so a name that is a prefix of another can never win the shorter match. None
        // of the thirty-seven is currently a prefix of another - this is here so that stays true
        // without anyone having to notice when the next one is added.
        List<String> names = new ArrayList<>();
        for (Critter c : CRITTERS) {
            BY_NAME.put(c.name().toLowerCase(Locale.ROOT), c);
            names.add(c.name());
        }
        names.sort((a, b) -> b.length() - a.length());
        StringBuilder sb = new StringBuilder("\\b(");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(Pattern.quote(names.get(i)));
        }
        sb.append(")\\b");
        ANY_CRITTER = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    private Safari() {
    }

    /** Every critter, in wiki order. Do not modify the result. */
    public static Critter[] critters() {
        return CRITTERS;
    }

    /** Whether the player is on the Critter Safari at all. Nothing here means anything elsewhere. */
    public static boolean onSafari(Minecraft mc) {
        return SkyblockLocation.island(mc).toLowerCase(Locale.ROOT)
                .contains(ISLAND.toLowerCase(Locale.ROOT));
    }

    /**
     * The critter this name plate belongs to, or null.
     *
     * <p>Pets are excluded first, and for the usual reason: a Macaw is both a legendary critter and
     * a pet somebody may well have out, and the same is true of a Fox or a Bee for anyone whose pet
     * happens to share a name. The {@code [Lvl n]} prefix separates the two - see
     * {@link Pests#isPetPlate}.
     */
    public static Critter onPlate(String plate) {
        if (Pests.isPetPlate(plate)) {
            return null;
        }
        Matcher m = ANY_CRITTER.matcher(plate);
        return m.find() ? BY_NAME.get(m.group(1).toLowerCase(Locale.ROOT)) : null;
    }

    /**
     * Whether this plate is the sparkling variant.
     *
     * <p>Checked on the plate rather than on the particles or the sound the wiki also describes:
     * those say a sparkling critter is <i>somewhere</i>, and the prefix says <i>which one</i>.
     */
    public static boolean isSparkling(String plate) {
        return plate.toUpperCase(Locale.ROOT).contains(SPARKLING);
    }
}
