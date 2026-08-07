package dev.diego.diegoaddons.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain data object persisted to {@code config/diegoaddonsv2.json} (per Prism instance, since each
 * instance has its own config directory). Gson serializes/deserializes these fields directly.
 */
public class AddonConfig {
    /** Name of the active theme (see {@link dev.diego.diegoaddons.gui.Themes}). */
    public String theme = "Galaxy";

    /** Set once the first-run introduction screen has been shown in this instance. */
    public boolean introShown = false;

    /** Set once the "open all your SkyBlock menu pages" hint has been shown. */
    public boolean sbHintShown = false;

    /** Anti-alias the rounded corners with a soft edge pixel. */
    public boolean smoothCorners = true;

    /** Per-module settings, keyed by the module id. */
    public Map<String, ModuleConfig> modules = new LinkedHashMap<>();

    /**
     * Last seen SkyBlock equipment and pet, serialised as JSON item stacks. Those live only inside
     * server-side menus, so without this they would be blank after every restart until the menus
     * were opened again.
     */
    public String[] savedEquipment = new String[4];
    public String savedPet;

    /** Players you have blocked, with the reason (see the Better Ignore List module). */
    public List<BlockedPlayer> blockedPlayers = new ArrayList<>();

    /** Find/replace pairs applied to chat and item text (see the Replace Words module). */
    public List<WordReplacement> wordReplacements = new ArrayList<>();

    /** Name-plate terms highlighted by the Custom ESP module, managed with /da esp. */
    public List<String> espTerms = new ArrayList<>();

    /** Player-inventory slot indices (0-40) locked against moving/dropping (Slot Lock module). */
    public java.util.Set<Integer> lockedSlots = new java.util.HashSet<>();

    /** Commands bound to keys (see the Command Hotkeys module). */
    public List<CommandHotkey> commandHotkeys = new ArrayList<>();

    /** User-recorded mining routes, and the one currently being drawn (see the Mining Routes module). */
    public List<MiningRoute> miningRoutes = new ArrayList<>();

    /** The items Auto GFS keeps topped up, in the order they are checked. */
    public List<GfsItem> gfsItems = new ArrayList<>();

    /** Whether the three it used to have built in have been offered once. Emptying the list sticks. */
    public boolean gfsSeeded = false;
    public String activeMiningRoute = "";
}
