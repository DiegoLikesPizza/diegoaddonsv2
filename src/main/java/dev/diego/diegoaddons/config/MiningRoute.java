package dev.diego.diegoaddons.config;

import java.util.ArrayList;
import java.util.List;

/**
 * A user-recorded mining route: a named ordered list of points to walk between. Persisted in
 * {@link AddonConfig}, so it is a plain Gson data object with a no-arg constructor and public fields.
 * Each point is a {@code [x, y, z]} triple.
 */
public class MiningRoute {
    public String name = "";
    public List<double[]> points = new ArrayList<>();

    public MiningRoute() {
    }

    public MiningRoute(String name) {
        this.name = name;
    }
}
