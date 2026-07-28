package dev.diego.diegoaddons.config;

/**
 * One thing Auto GFS keeps topped up: a SkyBlock item, and how low it may get first.
 *
 * <p>The name is what you read on the item in game - "Ender Pearl", "Superboom TNT". The sack id
 * that {@code /gfs} wants is derived from it (upper case, spaces to underscores), which is how
 * SkyBlock names its own ids, and can be overridden for the handful where it is not.
 */
public class GfsItem {
    public String name = "";
    /** Left blank to derive from the name; set only when SkyBlock disagrees with itself. */
    public String id = "";
    public int threshold = 4;
    public boolean enabled = true;

    public GfsItem() {
    }

    public GfsItem(String name, String id, int threshold) {
        this.name = name;
        this.id = id;
        this.threshold = threshold;
    }

    /** The id to hand {@code /gfs}: the override if there is one, else the name in SkyBlock's style. */
    public String sackId() {
        if (id != null && !id.isBlank()) {
            return id.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        }
        return name == null ? "" : name.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    /** What to look for in an item's display name, lower case. */
    public String match() {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
