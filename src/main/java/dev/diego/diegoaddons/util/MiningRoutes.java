package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.AddonConfig;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.MiningRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The store behind the Mining Routes module: named lists of points the player recorded, plus which
 * one is currently being drawn. Everything is persisted in {@link AddonConfig}, so routes survive
 * restarts - the whole point of recording one. Names are matched case-insensitively.
 */
public final class MiningRoutes {
    private MiningRoutes() {
    }

    private static List<MiningRoute> routes() {
        return ConfigManager.get().miningRoutes;
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (MiningRoute r : routes()) {
            out.add(r.name);
        }
        return out;
    }

    public static MiningRoute get(String name) {
        for (MiningRoute r : routes()) {
            if (r.name.equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }

    /** Creates an empty route and makes it the active one. False if the name is already taken. */
    public static boolean create(String name) {
        if (name.isBlank() || get(name) != null) {
            return false;
        }
        routes().add(new MiningRoute(name.trim()));
        setActive(name.trim());
        return true;
    }

    public static boolean delete(String name) {
        MiningRoute r = get(name);
        if (r == null) {
            return false;
        }
        routes().remove(r);
        if (active().equalsIgnoreCase(name)) {
            clearActive();
        }
        ConfigManager.save();
        return true;
    }

    /** Appends a point to a route. */
    public static boolean addPoint(String name, double x, double y, double z) {
        MiningRoute r = get(name);
        if (r == null) {
            return false;
        }
        r.points.add(new double[]{x, y, z});
        ConfigManager.save();
        return true;
    }

    /** Drops the last point of a route, e.g. after a misplaced one. */
    public static boolean undo(String name) {
        MiningRoute r = get(name);
        if (r == null || r.points.isEmpty()) {
            return false;
        }
        r.points.remove(r.points.size() - 1);
        ConfigManager.save();
        return true;
    }

    public static String active() {
        String a = ConfigManager.get().activeMiningRoute;
        return a == null ? "" : a;
    }

    public static MiningRoute activeRoute() {
        String a = active();
        return a.isEmpty() ? null : get(a);
    }

    public static void setActive(String name) {
        ConfigManager.get().activeMiningRoute = name;
        ConfigManager.save();
    }

    public static void clearActive() {
        ConfigManager.get().activeMiningRoute = "";
        ConfigManager.save();
    }

    public static String describe(MiningRoute r) {
        return r.name + " §7(" + r.points.size() + " pts)";
    }
}
