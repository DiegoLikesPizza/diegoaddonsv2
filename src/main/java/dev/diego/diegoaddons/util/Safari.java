package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.warden.Warden;

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

    /**
     * One critter: what it is called, where it lives, how rare it is, and what it is made of.
     *
     * <p>{@code type} is the vanilla entity class it wears, or null for the ones that are not a
     * vanilla mob at all - Duplico disguises itself as a block, Gazer only appears while you sleep,
     * Hideyho is a skinned player entity. Those can only ever be found by their plate.
     */
    public record Critter(String name, Biome biome, Rarity rarity, Class<?> type) {
        Critter(String name, Biome biome, Rarity rarity) {
            this(name, biome, rarity, null);
        }
    }

    /**
     * All thirty-seven, off the wiki's Critter Safari table.
     *
     * <p>Nine in the Cavern, nine in the Forest, ten in the Haunted biome and nine in the Icy one.
     * If that total ever stops being thirty-seven, this list has drifted from the game.
     */
    private static final Critter[] CRITTERS = {
            // Cavern
            new Critter("Cavernfish", Biome.CAVERN, Rarity.COMMON, AbstractFish.class),
            new Critter("Flitter", Biome.CAVERN, Rarity.COMMON, Bat.class),
            new Critter("Shyworm", Biome.CAVERN, Rarity.COMMON),
            new Critter("Driftling", Biome.CAVERN, Rarity.UNCOMMON),
            new Critter("Chuckwalla", Biome.CAVERN, Rarity.RARE),
            new Critter("Rockmite", Biome.CAVERN, Rarity.RARE, Silverfish.class),
            new Critter("Scrappy", Biome.CAVERN, Rarity.RARE, Armadillo.class),
            new Critter("Snoozle", Biome.CAVERN, Rarity.RARE, Sniffer.class),
            new Critter("Gemzie", Biome.CAVERN, Rarity.EPIC, Vex.class),
            // Forest
            new Critter("Foxtrot", Biome.FOREST, Rarity.COMMON, Fox.class),
            new Critter("Bluebird", Biome.FOREST, Rarity.UNCOMMON, Parrot.class),
            new Critter("Honeybug", Biome.FOREST, Rarity.UNCOMMON, Bee.class),
            new Critter("Treefrog", Biome.FOREST, Rarity.UNCOMMON, Frog.class),
            new Critter("Woodchucker", Biome.FOREST, Rarity.UNCOMMON, Creaking.class),
            new Critter("Fluffling", Biome.FOREST, Rarity.RARE, Panda.class),
            new Critter("Hideonfloor", Biome.FOREST, Rarity.RARE, Shulker.class),
            new Critter("Parakeet", Biome.FOREST, Rarity.RARE, Parrot.class),
            new Critter("Macaw", Biome.FOREST, Rarity.LEGENDARY, Parrot.class),
            // Haunted
            new Critter("Areita", Biome.HAUNTED, Rarity.UNCOMMON, CaveSpider.class),
            new Critter("Bloodbat", Biome.HAUNTED, Rarity.UNCOMMON, Bat.class),
            new Critter("Duplico", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Gazer", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Litterbug", Biome.HAUNTED, Rarity.UNCOMMON, Endermite.class),
            new Critter("Solsnatcher", Biome.HAUNTED, Rarity.UNCOMMON, Phantom.class),
            new Critter("Gimmiegold", Biome.HAUNTED, Rarity.RARE),
            new Critter("Hideonwall", Biome.HAUNTED, Rarity.RARE, Shulker.class),
            // Deliberately no type: Hideyho is a skinned player entity, and matching that would box
            // every player in the lobby. The Hideyho Finder is how it gets found.
            new Critter("Hideyho", Biome.HAUNTED, Rarity.RARE),
            new Critter("Doomspiral", Biome.HAUNTED, Rarity.LEGENDARY, Warden.class),
            // Icy
            new Critter("Strongarm", Biome.ICY, Rarity.COMMON, SnowGolem.class),
            new Critter("Tepid", Biome.ICY, Rarity.COMMON),
            new Critter("Polaris", Biome.ICY, Rarity.UNCOMMON, PolarBear.class),
            new Critter("Shuddersquid", Biome.ICY, Rarity.UNCOMMON, GlowSquid.class),
            new Critter("Billygoat", Biome.ICY, Rarity.RARE, Goat.class),
            new Critter("Mantis Shrimp", Biome.ICY, Rarity.RARE),
            new Critter("Nozzlenose", Biome.ICY, Rarity.RARE, Dolphin.class),
            new Critter("Troodon", Biome.ICY, Rarity.RARE),
            new Critter("Wumpa", Biome.ICY, Rarity.LEGENDARY, Ravager.class),
    };

    /**
     * Every critter that shares an entity type, by that type.
     *
     * <p>A list rather than a single critter because several types are shared - a Bat is a Flitter in
     * the Cavern and a Bloodbat in the Haunted biome, and all three parrots are parrots. Without a
     * plate there is no way to tell those apart, so the answer to "what is this" is honestly a set,
     * and the filter is applied to the set rather than to a guess at one of them.
     */
    private static final Map<Class<?>, List<Critter>> BY_TYPE = new HashMap<>();

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
            if (c.type() != null) {
                BY_TYPE.computeIfAbsent(c.type(), k -> new ArrayList<>()).add(c);
            }
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

    /**
     * Whether the player is on the Critter Safari at all. Nothing here means anything elsewhere.
     *
     * <p>Two readings, and the second is not belt-and-braces: the island name comes from the tab
     * list, which the Safari may well not write the way the wiki does, and the whole category is
     * dead if that one string is wrong. The scoreboard's area line naming one of the four biomes is
     * an independent way to know the same thing, and either arriving is enough.
     */
    public static boolean onSafari(Minecraft mc) {
        String island = SkyblockLocation.island(mc).toLowerCase(Locale.ROOT);
        if (island.contains(ISLAND.toLowerCase(Locale.ROOT)) || island.contains("safari")) {
            return true;
        }
        String area = SkyblockLocation.area(mc).toLowerCase(Locale.ROOT);
        for (Biome b : Biome.values()) {
            // "Icy Biome", "Cavern Biome" - the form SkyHanni matches the Icy one by.
            if (area.contains(b.display.toLowerCase(Locale.ROOT) + " biome")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The critters this entity could be, by its type - empty if its type is nothing's.
     *
     * <p>This exists because <b>the plate may not.</b> Every critter here was matched by name plate
     * on the assumption that they carry one, and the Galatea critters are proof that assumption is
     * not safe: a Cinderbat has no nametag at all. Matching the type as well means the ESP still
     * works on an island where they are silent, and it costs nothing where they are not, because the
     * plate is preferred whenever there is one.
     */
    public static List<Critter> byEntity(Entity e) {
        for (Map.Entry<Class<?>, List<Critter>> entry : BY_TYPE.entrySet()) {
            if (entry.getKey().isInstance(e)) {
                return entry.getValue();
            }
        }
        return List.of();
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
