package dev.diego.diegoaddons.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's state that is not a module setting.
 *
 * <p>Module settings are the {@link dev.diego.diegoaddons.module.Setting} objects themselves; what
 * is left here is the rest - which theme is on, whether a one-time notice has been shown, and the
 * lists the list-editor modules own. Each field is declared to configlib as a hidden option (see
 * {@link ModuleSpec}), so it is persisted without ever being drawn as a row.
 *
 * <p>The {@code modules} map that used to sit here is gone: it was a second copy of every setting,
 * keyed by name, and keeping it in step with the settings was exactly the bug that arrangement
 * invites.
 */
public class AddonConfig {
    /** Name of the active theme (see {@link dev.diego.diegoaddons.gui.Themes}). */
    public String theme = "Galaxy";

    /**
     * Whether {@link #accentColor} replaces the active theme's own accent.
     *
     * <p>A flag rather than "a colour of 0 means unset": every opaque ARGB colour has its top bit
     * set, so a zero sentinel is indistinguishable from a deliberate transparent black, and the
     * user losing their chosen colour by turning the option off and on again is worse than a field.
     */
    public boolean customAccent = false;

    /** The accent used when {@link #customAccent} is on. Defaults to Galaxy's own. */
    public int accentColor = 0xFF8B5CF6;

    /** Set once the first-run introduction screen has been shown in this instance. */
    public boolean introShown = false;

    /**
     * Set once the pre-configlib config file has been read into these settings.
     *
     * <p>Lives here rather than being inferred from the old file's absence, so an import is never
     * run twice - the old file is kept after being read, and re-importing it would undo whatever
     * the user changed since. See {@link LegacyImport}.
     */
    public boolean legacyImported = false;

    /** Set once the "open all your SkyBlock menu pages" hint has been shown. */
    public boolean sbHintShown = false;

    /** Anti-alias the rounded corners with a soft edge pixel. */
    public boolean smoothCorners = true;

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
