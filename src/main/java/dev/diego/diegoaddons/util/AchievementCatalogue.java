package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.Achievement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The achievements the mod ships with - the milestones worth having, and a fair number that are
 * only worth having as a joke.
 *
 * <p>Built here in code rather than stored in your config, and rebuilt every launch. That is what
 * lets the list grow and its patterns be corrected without touching anybody's saved data: the only
 * things written down about a built-in are the parts you changed - a corrected pattern, whether you
 * switched it off - and the unlock itself.
 *
 * <p><b>The patterns are the soft part.</b> Every trigger below is a guess at how Hypixel words a
 * chat line, and Hypixel rewords things. A pattern that never fires is a wrong guess rather than a
 * broken feature, which is why every one of them is editable in the GUI. If you find one that is
 * wrong, fixing it there fixes it permanently.
 *
 * <p>Tiers share a counter on purpose: "10 runs" and "20,000 runs" are one tally read at two
 * heights, so clearing a floor advances every tier of it at once.
 */
public final class AchievementCatalogue {
    private static final List<Achievement> ALL = new ArrayList<>();

    private AchievementCatalogue() {
    }

    public static List<Achievement> all() {
        if (ALL.isEmpty()) {
            build();
        }
        return ALL;
    }

    // --- building blocks ----------------------------------------------------------------------------

    private static Achievement add(String category, String id, String name, String description,
                                   String chat, String excludes) {
        Achievement a = new Achievement("b." + id, name);
        a.description = description;
        a.chat = chat;
        a.excludes = excludes == null ? "" : excludes;
        a.category = category;
        a.builtin = true;
        ALL.add(a);
        return a;
    }

    private static Achievement add(String category, String id, String name, String description,
                                   String chat) {
        return add(category, id, name, description, chat, "");
    }

    /** One tally, read at several heights. */
    private static void tiers(String category, String idBase, String label, String chat,
                              String excludes, String noun, int... thresholds) {
        for (int t : thresholds) {
            Achievement a = add(category, idBase + "." + t,
                    t == 1 ? "First " + label : label + " × " + count(t),
                    t == 1 ? "Do it once." : count(t) + " " + noun + ".",
                    chat, excludes);
            a.counter = "c." + idBase;
            a.threshold = t;
        }
    }

    /** An achievement judged from the profile record rather than from chat. */
    private static void stat(String category, String id, String name, String description,
                             String statName, String comparator, double value, int profiles,
                             String gamemode) {
        Achievement a = add(category, id, name, description, "");
        Achievement.Condition c = new Achievement.Condition(statName, comparator, value);
        c.profiles = profiles;
        c.gamemode = gamemode;
        a.conditions.add(c);
    }

    private static String count(int n) {
        return n >= 1000 ? String.format(Locale.ROOT, "%,d", n) : String.valueOf(n);
    }

    // --- the list -----------------------------------------------------------------------------------

    private static void build() {
        dungeonFloors();
        dungeonProgress();
        dungeonDrops();
        skills();
        slayers();
        mining();
        fishing();
        farming();
        combat();
        milestones();
        misfortune();
        jokes();
    }

    private static final String DUNGEONS = "Dungeons";
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII"};

    /** Every floor, normal and master, counted from the completion line. */
    private static void dungeonFloors() {
        tiers(DUNGEONS, "floor.entrance", "Entrance clear",
                "*Catacombs - Entrance*", "", "entrance runs", 1, 10, 100, 1000);
        for (int i = 0; i < ROMAN.length; i++) {
            String r = ROMAN[i];
            int floor = i + 1;
            // The master line contains the normal one word for word, so normal must exclude it.
            tiers(DUNGEONS, "floor.f" + floor, "F" + floor + " clear",
                    "*Catacombs - Floor " + r + "*", "Master Mode", "Floor " + r + " runs",
                    1, 10, 100, 1000, 5000, 20000);
            tiers(DUNGEONS, "floor.m" + floor, "M" + floor + " clear",
                    "*Master Mode Catacombs - Floor " + r + "*", "", "Master Mode " + r + " runs",
                    1, 10, 100, 1000, 5000, 20000);
        }
    }

    /** Catacombs and class levels, off the level-up line. */
    private static void dungeonProgress() {
        for (int lvl : new int[]{5, 10, 15, 20, 25, 30, 35, 40, 45, 50}) {
            add(DUNGEONS, "cata." + lvl, "Catacombs " + lvl,
                    "Reach Catacombs level " + lvl + ".", "*Catacombs*➜*" + lvl + "*");
        }
        for (String cls : new String[]{"Healer", "Mage", "Berserk", "Archer", "Tank"}) {
            for (int lvl : new int[]{10, 25, 50}) {
                add(DUNGEONS, "class." + cls.toLowerCase(Locale.ROOT) + "." + lvl,
                        cls + " " + lvl, "Reach " + cls + " level " + lvl + ".",
                        "*" + cls + "*➜*" + lvl + "*");
            }
        }
        add(DUNGEONS, "score.s", "S rank", "Finish a run with an S.", "*Team Score*S*");
        add(DUNGEONS, "score.splus", "S+ rank", "Finish a run with an S+.", "*Team Score*S+*");
        tiers(DUNGEONS, "score.splus.many", "S+ run", "*Team Score*S+*", "", "S+ runs",
                1, 10, 100, 1000);
        add(DUNGEONS, "blood.open", "Blood door", "Open a blood door.", "*The BLOOD DOOR has been opened*");
        tiers(DUNGEONS, "secrets", "Secret", "*You found a secret*", "", "secrets found",
                1, 100, 1000, 10000, 100000);
        add(DUNGEONS, "solo.f7", "Solo Floor VII", "Clear F7 alone.", "*Catacombs - Floor VII*", "Master Mode");
        add(DUNGEONS, "necron.dead", "Necron down", "Kill Necron.", "*You have completed*Floor VII*");
        add(DUNGEONS, "sadan.dead", "Sadan down", "Kill Sadan.", "*Catacombs - Floor VI*", "Master Mode");
        add(DUNGEONS, "livid.dead", "Livid down", "Kill Livid.", "*Catacombs - Floor V*", "Master Mode");
        add(DUNGEONS, "thorn.dead", "Thorn down", "Kill Thorn.", "*Catacombs - Floor IV*", "Master Mode");
        add(DUNGEONS, "professor.dead", "Professor down", "Kill the Professor.",
                "*Catacombs - Floor III*", "Master Mode");
        add(DUNGEONS, "scarf.dead", "Scarf down", "Kill Scarf.", "*Catacombs - Floor II*", "Master Mode");
        add(DUNGEONS, "bonzo.dead", "Bonzo down", "Kill Bonzo.", "*Catacombs - Floor I*", "Master Mode");
    }

    /** The drops people actually talk about. */
    private static void dungeonDrops() {
        String[] loot = {
                "Necron's Handle", "Dark Claymore", "Shadow Warp", "Wither Shield", "Implosion",
                "Judgement Core", "Warden Heart", "Precursor Eye", "Giant's Sword", "Shadow Fury",
                "Livid Dagger", "Necromancer Lord Chestplate", "Soulweaver Gloves", "Bigfoot's Lasso",
                "Machine Gun Bow", "Sadan's Brooch", "Fel Skull", "Wither Blood", "Wither Catalyst",
                "Master Skull - Tier 5", "Master Skull - Tier 6", "Master Skull - Tier 7",
                "First Master Star", "Second Master Star", "Third Master Star", "Fourth Master Star",
                "Fifth Master Star", "Necron's Ladder", "Auto Recombobulator", "Spirit Mask",
                "Spirit Bow", "Spirit Pet", "Bonzo's Staff", "Scarf's Studies", "Adaptive Blade",
        };
        for (String item : loot) {
            add("Dungeon loot", "drop." + slug(item), item, "Get " + item + " to drop.",
                    "*DROP*" + item + "*");
        }
        tiers("Dungeon loot", "drop.handle.many", "Handle", "*DROP*Necron's Handle*", "",
                "handles dropped", 1, 5, 25, 100);
        tiers("Dungeon loot", "drop.claymore.many", "Claymore", "*DROP*Dark Claymore*", "",
                "claymores dropped", 1, 5, 25);
    }

    /** Skill level-ups, from the SKILL LEVEL UP line. */
    private static void skills() {
        String[] all = {"Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting",
                "Alchemy", "Taming", "Carpentry", "Runecrafting", "Social"};
        for (String skill : all) {
            for (int lvl : new int[]{25, 50, 60}) {
                add("Skills", "skill." + skill.toLowerCase(Locale.ROOT) + "." + lvl,
                        skill + " " + lvl, "Reach " + skill + " level " + lvl + ".",
                        "*SKILL LEVEL UP*" + skill + "*" + lvl + "*");
            }
        }
    }

    private static void slayers() {
        String[] bosses = {"Revenant Horror", "Tarantula Broodfather", "Sven Packmaster",
                "Voidgloom Seraph", "Inferno Demonlord", "Riftstalker Bloodfiend"};
        String[] shorthand = {"Revenant", "Tarantula", "Sven", "Voidgloom", "Inferno", "Bloodfiend"};
        for (int i = 0; i < bosses.length; i++) {
            for (String r : new String[]{"I", "V", "IX"}) {
                add("Slayer", "slayer." + slug(shorthand[i]) + "." + r,
                        shorthand[i] + " " + r, "Reach " + bosses[i] + " " + r + ".",
                        "*LEVEL UP*" + bosses[i] + "*" + r + "*");
            }
            tiers("Slayer", "slayer.kill." + slug(shorthand[i]), shorthand[i] + " kill",
                    "*" + bosses[i] + "*SLAIN*", "", shorthand[i] + " bosses killed",
                    1, 100, 1000, 5000);
        }
        tiers("Slayer", "slayer.quests", "Slayer quest", "*SLAYER QUEST COMPLETE*", "",
                "slayer quests finished", 1, 100, 1000, 10000);
        String[] drops = {"Overflux Capacitor", "Warden Heart", "Judgement Core", "Enchanted Book",
                "Handy Blood Chalice", "Beheaded Horror", "Scythe Blade", "Shard of the Shredded",
                "Digested Mushrooms", "Grizzly Bait", "Red Claw Egg", "Tarantula Web",
                "Null Atom", "Pocket Espresso Machine", "Sinful Dice", "Mana Steal I",
                "Transmission Tuner", "Hazmat Enderman", "Blood Chalice", "Twilight Arrow Poison"};
        for (String d : drops) {
            add("Slayer", "slayerdrop." + slug(d), d, "Get " + d + " to drop.", "*DROP*" + d + "*");
        }
    }

    private static void mining() {
        String[] gems = {"Ruby", "Amethyst", "Jade", "Amber", "Sapphire", "Topaz", "Jasper", "Opal",
                "Aquamarine", "Citrine", "Onyx", "Peridot"};
        for (String g : gems) {
            add("Mining", "gem." + slug(g), "Perfect " + g, "Make a Perfect " + g + ".",
                    "*Perfect " + g + "*");
        }
        String[] loot = {"Divan's Alloy", "Divan Fragment", "Divan's Powder Coating",
                "Gemstone Chamber", "Pickonimbus 2000", "Jungle Amulet", "Amber Material",
                "Sapphire Material", "Jasper Material", "Ruby Material", "Amethyst Material",
                "Topaz Material", "Jade Material", "Control Switch", "Electron Transmitter",
                "FTX 3070", "Robotron Reflector", "Superlite Motor", "Synthetic Heart",
                "Titanium Ore", "Refined Titanium", "Starfall", "Goblin Egg", "Golden Goblin Egg",
                "Prehistoric Egg", "Fossil", "Glacite Jewel", "Frozen Corpse", "Ice Cold I"};
        for (String item : loot) {
            add("Mining", "mine." + slug(item), item, "Get " + item + ".", "*" + item + "*");
        }
        tiers("Mining", "mine.titanium", "Titanium", "*Titanium*", "", "titanium found",
                1, 100, 1000, 10000);
        tiers("Mining", "mine.powder", "Powder run", "*You received*Powder*", "",
                "powder pickups", 1, 1000, 10000, 100000);
        add("Mining", "mine.crystal.all", "Crystal collector", "Place all five crystals.",
                "*You have placed*crystal*");
        add("Mining", "mine.wormhole", "Corleone", "Kill Grand Master Corleone.", "*Corleone*");
        add("Mining", "mine.bal", "Bal", "Meet Bal.", "*Bal*is awake*");
    }

    private static void fishing() {
        String[] creatures = {"Squid", "Sea Walker", "Night Squid", "Sea Guardian", "Sea Witch",
                "Sea Archer", "Monster of the Deep", "Catfish", "Carrot King", "Sea Leech",
                "Guardian Defender", "Deep Sea Protector", "Water Hydra", "Sea Emperor",
                "Frozen Steve", "Frosty the Snowman", "Grinch", "Nutcracker", "Yeti",
                "Reindrake", "Nightmare", "Werewolf", "Phantom Fisher", "Grim Reaper",
                "Lord Jawbus", "Thunder", "Blue Ringed Octopus", "Flaming Worm", "Fiery Scuttler",
                "Great White Shark", "Zombie Miner", "Blazing Fireball"};
        for (String c : creatures) {
            add("Fishing", "sea." + slug(c), c, "Fish up a " + c + ".", "*" + c + "*");
        }
        String[] drops = {"Yeti Pet", "Flying Fish", "Lucky Clover Core", "Titanoboa Shed",
                "Radioactive Vial", "Nutcracker Pet", "Reaper Mask", "Shredder", "Magma Rod",
                "Thunder Shard", "Jawbus Follower", "Baby Yeti"};
        for (String d : drops) {
            add("Fishing", "fishdrop." + slug(d), d, "Get " + d + " to drop.", "*DROP*" + d + "*");
        }
        tiers("Fishing", "fish.catches", "Sea creature", "*You caught a*", "", "sea creatures caught",
                1, 100, 1000, 10000, 100000);
    }

    private static void farming() {
        String[] pests = {"Mite", "Cricket", "Moth", "Earthworm", "Slug", "Beetle", "Locust",
                "Rat", "Mosquito", "Fly"};
        for (String p : pests) {
            add("Farming", "pest." + slug(p), p, "Kill a " + p + ".", "*" + p + "*");
        }
        String[] drops = {"Elephant Pet", "Mooshroom Cow", "Rabbit Pet", "Bee Pet", "Slug Pet",
                "Burrowing Spores", "Squeaky Mousemat", "Fine Flour", "Pesthunter Badge"};
        for (String d : drops) {
            add("Farming", "farmdrop." + slug(d), d, "Get " + d + ".", "*" + d + "*");
        }
        tiers("Farming", "farm.contests", "Farming contest", "*Farming Contest*", "",
                "contests entered", 1, 10, 100, 1000);
        add("Farming", "farm.gold", "Gold medal", "Take a gold in a farming contest.", "*GOLD medal*");
        add("Farming", "farm.diamond", "Diamond medal", "Take a diamond in a farming contest.",
                "*DIAMOND medal*");
        String[] forage = {"Sweep", "Fig Log", "Mangrove Log", "Tree Capitator", "Jungle Key",
                "Termite", "Lush Axe", "Whisper", "Ent Pet"};
        for (String f : forage) {
            add("Foraging", "forage." + slug(f), f, "Get " + f + ".", "*" + f + "*");
        }
    }

    private static void combat() {
        String[] dragons = {"Superior", "Strong", "Unstable", "Young", "Old", "Wise", "Protector"};
        for (String d : dragons) {
            add("Combat", "dragon." + slug(d), d + " Dragon", "Kill the " + d + " Dragon.",
                    "*" + d + " Dragon*");
        }
        tiers("Combat", "dragon.kills", "Dragon kill", "*Dragon has been slain*", "",
                "dragons killed", 1, 100, 1000, 10000);
        String[] drops = {"Ender Dragon Pet", "Aspect of the Dragons", "Dragon Horn", "Dragon Claw",
                "Dragon Scale", "Superior Dragon Fragment", "Ender Helmet", "Golden Dragon",
                "Griffin Feather", "Kat Flower", "Enderman Pet", "Golden Ghoul Pet",
                "Zealot Pet", "Summoning Eye", "Judgement Core", "Void Conqueror Enderman"};
        for (String d : drops) {
            add("Combat", "combatdrop." + slug(d), d, "Get " + d + ".", "*" + d + "*");
        }
        tiers("Combat", "zealot.eyes", "Summoning Eye", "*Summoning Eye*", "", "eyes found",
                1, 8, 100, 1000);
        tiers("Combat", "kills.total", "Kill", "*You killed*", "", "kills recorded",
                1, 1000, 10000, 100000);
        add("Combat", "kuudra.basic", "Kuudra: Basic", "Clear Basic Kuudra.", "*Kuudra*Basic*");
        add("Combat", "kuudra.hot", "Kuudra: Hot", "Clear Hot Kuudra.", "*Kuudra*Hot*");
        add("Combat", "kuudra.burning", "Kuudra: Burning", "Clear Burning Kuudra.", "*Kuudra*Burning*");
        add("Combat", "kuudra.fiery", "Kuudra: Fiery", "Clear Fiery Kuudra.", "*Kuudra*Fiery*");
        add("Combat", "kuudra.infernal", "Kuudra: Infernal", "Clear Infernal Kuudra.", "*Kuudra*Infernal*");
        tiers("Combat", "kuudra.runs", "Kuudra run", "*KUUDRA DOWN*", "", "Kuudra runs", 1, 100, 1000, 5000);
    }

    /** The ones judged from the profile record rather than from anything Hypixel says. */
    private static void milestones() {
        for (int lvl = 20; lvl <= 400; lvl += 20) {
            stat("Milestones", "sblevel." + lvl, "SkyBlock level " + lvl,
                    "Get a profile to SkyBlock level " + lvl + ".", "level", ">=", lvl, 1, "any");
        }
        stat("Milestones", "profiles.two200", "Twice over",
                "Have two profiles past level 200.", "level", ">=", 200, 2, "any");
        stat("Milestones", "profiles.three100", "Serial starter",
                "Have three profiles past level 100.", "level", ">=", 100, 3, "any");
        stat("Milestones", "profiles.five", "Profile hoarder",
                "Have five profiles past level 50.", "level", ">=", 50, 5, "any");
        stat("Milestones", "iron.200", "Iron will",
                "Get an ironman profile past level 200.", "level", ">=", 200, 1, "ironman");
        stat("Milestones", "played.100h", "Hundred hours",
                "Spend 100 hours on one profile, with the mod watching.",
                "playtime", ">=", 100, 1, "any");
        stat("Milestones", "played.1000h", "No notes",
                "Spend 1,000 hours on one profile.", "playtime", ">=", 1000, 1, "any");
    }

    /** Things going wrong, which is most of them. */
    private static void misfortune() {
        String cat = "Misfortune";
        tiers(cat, "death", "Death", "*You died*", "", "deaths", 1, 100, 1000, 10000);
        add(cat, "death.void", "Gravity", "Fall into the void.", "*fell into the void*");
        add(cat, "death.lava", "Warm", "Die to lava.", "*swam in lava*");
        add(cat, "kicked", "Shown the door", "Get kicked from the server.", "*You were kicked*");
        add(cat, "banned.temp", "Cooling off", "Earn a temporary ban.", "*temporarily banned*");
        add(cat, "puzzle.fail", "Puzzle fail", "Fail a dungeon puzzle.", "*PUZZLE FAIL*");
        tiers(cat, "puzzle.fails", "Puzzle fail", "*PUZZLE FAIL*", "", "puzzles failed", 1, 10, 100);
        add(cat, "death.first30", "Off the deep end", "Die in the first 30 seconds of a run.",
                "*You died*");
        add(cat, "party.kicked", "Uninvited", "Get kicked from a party.",
                "*has removed you from the party*");
        add(cat, "party.disband", "Everyone left", "Watch a party disband.", "*has disbanded the party*");
        add(cat, "lost.item", "Butterfingers", "Lose an item to the void.", "*You dropped*");
        add(cat, "mayor.disappointment", "Democracy", "See a mayor you did not vote for win.",
                "*is the new Mayor*");
    }

    /** The reason a list like this is worth having at all. */
    private static void jokes() {
        String cat = "Nonsense";
        Achievement quit = add(cat, "ironman.quit", "Commitment issues",
                "Make an ironman and walk away inside a day.", "");
        Achievement.Condition young = new Achievement.Condition("playtime", "<=", 24);
        young.gamemode = "ironman";
        Achievement.Condition cold = new Achievement.Condition("idle", ">=", 30);
        cold.gamemode = "ironman";
        quit.conditions.add(young);
        quit.conditions.add(cold);

        Achievement tourist = add(cat, "profile.tourist", "Tourist",
                "Start four profiles and finish none of them.", "");
        Achievement.Condition shallow = new Achievement.Condition("level", "<=", 30);
        shallow.profiles = 4;
        tourist.conditions.add(shallow);

        stat(cat, "ghost.town", "Ghost town",
                "Leave a profile untouched for a year.", "idle", ">=", 365, 1, "any");
        stat(cat, "one.true", "Monogamous",
                "Play one profile for 500 hours.", "playtime", ">=", 500, 1, "any");

        add(cat, "handle.metered", "Metered a handle",
                "Watch a Handle drop and know, immediately, that it was not for you.",
                "*DROP*Necron's Handle*");
        add(cat, "handle.again", "Again?!", "A second Handle. Still not yours.",
                "*DROP*Necron's Handle*");
        add(cat, "carry.paid", "Paid to be carried", "Say the quiet part in party chat.",
                "*carry*");
        add(cat, "carry.sold", "Carry seller", "Advertise a carry.", "*carries*");
        add(cat, "afk.pool", "Professional swimmer", "Get told off for AFKing.", "*AFK*");
        add(cat, "sell.mistake", "Off by one zero", "Undersell something on the auction house.",
                "*You collected*coins*");
        add(cat, "bazaar.flip", "Bazaar economist", "Flip something on the bazaar.",
                "*Bazaar*sold*");
        add(cat, "coins.broke", "Broke", "Spend down to nothing.", "*You don't have enough coins*");
        add(cat, "coop.betrayed", "Trust exercise", "Have a co-op member clear the vault.",
                "*removed*from the co-op*");
        add(cat, "coop.joined", "Two heads", "Join a co-op.", "*joined the co-op*");
        add(cat, "melody.done", "Melody's harp", "Finish Melody's Harp, never speak of it again.",
                "*Melody*");
        add(cat, "wardrobe.naked", "Fashion statement", "Enter a dungeon with an empty armour slot.",
                "*Catacombs*");
        add(cat, "pet.wrong", "Wrong pet, whole run", "Notice at the boss.", "*You summoned your*");
        add(cat, "no.pickaxe", "Left the pickaxe at home", "Reach the mines without it.",
                "*Crystal Hollows*");
        add(cat, "leap.wrong", "Leaped to the wrong person", "It happens to everyone.",
                "*You have teleported to*");
        add(cat, "terminal.slow", "Last terminal", "Be the one still doing terminals.",
                "*has completed a terminal*");
        add(cat, "terminal.none", "Moral support", "Complete a Floor VII with zero terminals.",
                "*Catacombs - Floor VII*", "Master Mode");
        add(cat, "died.simon", "Beat by Simon says", "Die on the device.", "*You died*");
        add(cat, "spirit.leap", "Spirit leap enthusiast", "Leap the moment the boss starts.",
                "*Spirit Leap*");
        add(cat, "chat.spam", "Enthusiastic", "Get rate limited.", "*You are sending commands too fast*");
        add(cat, "sba.crash", "Client death", "Survive a crash and come straight back.",
                "*joined the lobby*");
        add(cat, "hub.lost", "Hub wanderer", "Warp to the hub for no reason.", "*Sending to server hub*");
        add(cat, "island.visit", "Neighbourly", "Visit somebody's island.", "*Warping to*island*");
        add(cat, "fairy.soul", "Fairy soul", "Find a fairy soul.", "*You found a Fairy Soul*");
        tiers(cat, "fairy.souls", "Fairy soul", "*You found a Fairy Soul*", "", "fairy souls found",
                1, 50, 100, 227);
        add(cat, "jerry.box", "Jerry box", "Open a Jerry box.", "*Jerry Box*");
        add(cat, "jerry.mayor", "Perkpocalypse", "Live through a Jerry mayor.", "*Jerry*Mayor*");
        add(cat, "sleep.never", "Six in the morning", "Still going.", "*Good morning*");
        add(cat, "dinner.cold", "Dinner's cold", "One more run.", "*Catacombs*");
        add(cat, "rng.mercy", "Statistically improbable", "Two rare drops in one run.", "*DROP*");
        add(cat, "rng.none", "Dry", "A hundred runs, nothing to show.", "*Catacombs*");
        add(cat, "friend.carried", "Carried a friend", "Do all the work quietly.", "*Team Score*");
        add(cat, "gfs.abuse", "Grab from sack", "Let the mod do the shopping.", "*You received*");
        add(cat, "etherwarp.fail", "Etherwarp misjudged", "Warp somewhere unhelpful.", "*You died*");
        add(cat, "mining.streak", "Powder brain", "An entire evening of powder.", "*Powder*");
        add(cat, "auction.sniped", "Sniped", "Lose an auction by a second.", "*outbid*");
        add(cat, "auction.won", "Won an auction", "Pay too much, happily.", "*You won the auction*");
        add(cat, "museum.donate", "Museum piece", "Donate something you will want back.", "*Museum*");
        add(cat, "reforge.bad", "Reforge roulette", "Reforge into something worse.", "*reforged*");
        add(cat, "enchant.fail", "Enchanting table regret", "Waste the levels.", "*enchanted*");
        add(cat, "pet.level100", "Pet parent", "Take a pet to 100.", "*is now level 100*");
        add(cat, "dragon.eye", "Eye placer", "Place the last summoning eye.", "*placed a Summoning Eye*");
        add(cat, "dragon.stole", "Kill stolen", "Watch somebody else take the loot.", "*Dragon*");
        add(cat, "wrong.floor", "Queued the wrong floor", "Find out at the entrance.", "*Catacombs*");
        add(cat, "healer.abandoned", "Left the healer", "Sprint ahead regardless.", "*Healer*");
        add(cat, "tank.forgotten", "Nobody plays tank", "Be the one who does.", "*Tank*");
        add(cat, "berserk.dead", "Berserk, briefly", "Die first, as intended.", "*You died*");
        add(cat, "mage.beam", "Beamed it", "One well-timed beam.", "*Mage*");
        add(cat, "archer.afk", "Archer, allegedly", "Contribute from the back.", "*Archer*");
    }

    /** A stable id fragment from a display name. */
    private static String slug(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
