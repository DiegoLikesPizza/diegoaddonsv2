package dev.diego.diegoaddons.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The curated icons offered in the inventory-button editor: one entry per SkyBlock warp and per
 * common action, each already paired with the command it usually goes with, so placing a button is
 * a single click rather than picking an icon and typing a command.
 *
 * <p>Icons are ordinary items chosen to read at 16x16. Custom player heads are supported too - see
 * {@link InventoryButtons#icon} for the {@code skull:<texture>} form - but none are hard-coded here:
 * a head only renders correctly with a genuine texture hash, and a wrong one shows as a blank head,
 * so entering them is left to the editor's skull field rather than guessed at.
 */
public final class IconCatalogue {
    /**
     * One offered icon.
     *
     * @param name    label shown in the picker
     * @param icon    item id, or {@code skull:<texture>}
     * @param command the command this icon suggests, or empty for a plain icon
     */
    public record Entry(String name, String icon, String command) {
    }

    private static final List<Entry> WARPS = new ArrayList<>();
    private static final List<Entry> ACTIONS = new ArrayList<>();

    private IconCatalogue() {
    }

    private static void warp(String name, String icon, String warp) {
        WARPS.add(new Entry(name, icon, "warp " + warp));
    }

    private static void action(String name, String icon, String command) {
        ACTIONS.add(new Entry(name, icon, command));
    }

    static {
        // --- Warps, in roughly the order the game lists them ---
        warp("Hub", "minecraft:beacon", "hub");
        warp("Island", "minecraft:grass_block", "island");
        warp("Dungeon Hub", "minecraft:wither_skeleton_skull", "dungeon_hub");
        warp("The Barn", "minecraft:wheat", "barn");
        warp("Mushroom Desert", "minecraft:red_mushroom", "desert");
        warp("Gold Mine", "minecraft:gold_ore", "gold");
        warp("Deep Caverns", "minecraft:deepslate", "deep");
        warp("Dwarven Mines", "minecraft:iron_ore", "mines");
        warp("Crystal Hollows", "minecraft:amethyst_cluster", "crystals");
        warp("Spider's Den", "minecraft:cobweb", "spider");
        warp("The End", "minecraft:end_stone", "end");
        warp("Crimson Isle", "minecraft:netherrack", "nether");
        warp("The Park", "minecraft:oak_sapling", "park");
        warp("Jerry's Workshop", "minecraft:snow_block", "jerry");
        warp("The Rift", "minecraft:crying_obsidian", "rift");
        warp("Garden", "minecraft:farmland", "garden");
        warp("Backwater Bayou", "minecraft:lily_pad", "bayou");
        warp("Hunting Lodge", "minecraft:bow", "lodge");
        warp("Museum", "minecraft:item_frame", "museum");
        warp("Home", "minecraft:red_bed", "home");

        // --- Common actions ---
        action("SkyBlock Menu", "minecraft:nether_star", "sbmenu");
        action("Wardrobe", "minecraft:leather_chestplate", "wardrobe");
        action("Loadout", "minecraft:diamond_chestplate", "equipment");
        action("Pets", "minecraft:bone", "pets");
        action("Storage", "minecraft:chest", "storage");
        action("Ender Chest", "minecraft:ender_chest", "enderchest");
        action("Backpacks", "minecraft:bundle", "backpack");
        action("Sacks", "minecraft:brown_shulker_box", "sacks");
        action("Accessory Bag", "minecraft:gold_ingot", "accessorybag");
        action("Auction House", "minecraft:gold_block", "ah");
        action("Bazaar", "minecraft:emerald", "bz");
        action("Bank", "minecraft:gold_nugget", "bank");
        action("Trades", "minecraft:emerald_block", "trades");
        action("Crafting", "minecraft:crafting_table", "craft");
        action("Anvil", "minecraft:anvil", "anvil");
        action("Enchanting", "minecraft:enchanting_table", "enchant");
        action("Recipe Book", "minecraft:knowledge_book", "recipe");
        action("Skills", "minecraft:experience_bottle", "skills");
        action("Collections", "minecraft:book", "collection");
        action("Pets Menu", "minecraft:egg", "pets");
        action("Fast Travel", "minecraft:ender_pearl", "travel");
        action("Warp Menu", "minecraft:compass", "warp");
        action("Calendar", "minecraft:clock", "calendar");
        action("Quests", "minecraft:writable_book", "quests");
        action("Party", "minecraft:player_head", "party list");
        action("Guild", "minecraft:shield", "guild");
        action("Visit Player", "minecraft:oak_door", "visit");
        action("Hub Lobby", "minecraft:end_portal_frame", "l");
        action("Play Again", "minecraft:diamond_sword", "rejoin");
        action("Pickup Stash", "minecraft:hopper", "pickupstash");
        action("Trash", "minecraft:lava_bucket", "trash");
    }

    public static List<Entry> warps() {
        return WARPS;
    }

    public static List<Entry> actions() {
        return ACTIONS;
    }

    /** Everything, warps first. */
    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>(WARPS.size() + ACTIONS.size());
        out.addAll(WARPS);
        out.addAll(ACTIONS);
        return out;
    }
}
