package dev.diego.diegoaddons.config;

import dev.diego.diegoaddons.DiegoAddonsV2Client;

/**
 * The mod's settings state, and the one place that asks for it to be written.
 *
 * <p>This used to own {@code config/diegoaddonsv2.json} outright - its own Gson, its own schema, a
 * map of every module's options. configlib owns storage now: every module setting is declared to it
 * as an option bound to the setting's own getter and setter, and everything here that is state
 * rather than a setting is declared as a hidden option (see {@link ModuleSpec}). So there is one
 * file, one writer, and no second copy of any value.
 *
 * <p>What survives is the shape callers already use. {@link #get()} still hands back the state
 * object and {@link #save()} still means "write it", which is why neither the modules nor the
 * commands had to change: only where the bytes end up did.
 */
public final class ConfigManager {

    private static final AddonConfig CONFIG = new AddonConfig();

    /**
     * Set while configlib is pushing a loaded file into the settings.
     *
     * <p>Loading calls every setter, and a setter's job is to ask for a save - so without this the
     * first load would write the file back sixty times while still reading it, and any setting not
     * yet reached would be saved at its default over the value about to be loaded.
     */
    private static boolean loading;

    private ConfigManager() {
    }

    public static AddonConfig get() {
        return CONFIG;
    }

    /**
     * Writes the config out.
     *
     * <p>A no-op before the handle exists: the settings are constructed while the module list is
     * being built, which is necessarily before configlib has been handed the spec describing them.
     * Nothing is lost by not writing then - the values being set are the defaults.
     */
    public static void save() {
        if (loading) {
            return;
        }
        var handle = DiegoAddonsV2Client.CONFIG;
        if (handle != null) {
            handle.save();
        }
    }

    /** Whether a load is in progress, so a setter can tell a restore from a user's choice. */
    public static boolean isLoading() {
        return loading;
    }

    /** Runs {@code body} with {@link #save()} disabled - see {@link #loading}. */
    public static void whileLoading(Runnable body) {
        loading = true;
        try {
            body.run();
        } finally {
            loading = false;
        }
    }
}
