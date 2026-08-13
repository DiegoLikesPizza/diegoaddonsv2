package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.SparklingCritterModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds sparkling critters by the trail they leave rather than by the plate they wear.
 *
 * <p>The plate is the better identification and the worse detector: it names the critter, but a name
 * plate is a rendered entity, so it does not exist until the mob is loaded and roughly in front of
 * you. Particles arrive as packets from much further out and pass through terrain on the way. For a
 * 1-in-8,192 spawn that difference is the whole feature - the failure mode being designed against is
 * walking past one.
 *
 * <p><b>Which particle it is, is not known.</b> The wiki says "golden particles and ambient sparkling
 * sounds" and stops there, so the ids are a comma-separated <i>setting</i> with three likely ones in
 * it rather than a constant. Two things make a wrong guess survivable: several ids can be watched at
 * once, and a marker needs a <i>cluster</i> - {@link SparklingCritterModule#particleMin} particles in
 * one place - so a stray crit from hitting something never becomes a waypoint. The debug option logs
 * every particle type actually seen on the island with its count, which settles the question in one
 * visit rather than by another guess.
 */
public final class SparkleParticles {
    /** Particles within this of a source belong to it. Roughly the reach of a critter's trail. */
    private static final double CLUSTER = 3.0;
    /** A source with nothing new for this long is gone: the critter moved, or was caught. */
    private static final long EXPIRY_MS = 4000;

    /** One place particles keep coming from. */
    private static final class Source {
        double x;
        double y;
        double z;
        int count;
        long lastSeen;
        boolean announced;
    }

    private static final List<Source> sources = new ArrayList<>();

    /** Debug: how many of each particle type has been seen since the last report. */
    private static final Map<String, Integer> seen = new HashMap<>();
    private static long lastReport;

    /** The parsed form of the id setting, rebuilt only when the text changes. */
    private static String cachedIds;
    private static Set<ParticleType<?>> watched = Set.of();

    private SparkleParticles() {
    }

    /**
     * A particle packet. Called on the client thread from the packet handler.
     *
     * <p>Ordered so the common case costs almost nothing: this runs for every particle the server
     * sends anywhere, which on a busy island is thousands a second.
     */
    public static void onParticle(ParticleOptions options, double x, double y, double z) {
        SparklingCritterModule m = SparklingCritterModule.INSTANCE;
        if (m == null || !m.isEnabled() || !m.particleEsp()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !Safari.onSafari(mc)) {
            return;
        }
        if (m.particleDebug()) {
            record(options);
        }
        if (!watch(m.particleIds()).contains(options.getType())) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Source s : sources) {
            if (sqr(s.x - x) + sqr(s.y - y) + sqr(s.z - z) < CLUSTER * CLUSTER) {
                // Drift the source towards the new particle rather than replacing it: a trail is
                // spread over a block or two, and a source that jumped to the newest particle would
                // twitch about instead of sitting on the critter.
                s.x += (x - s.x) * 0.2;
                s.y += (y - s.y) * 0.2;
                s.z += (z - s.z) * 0.2;
                s.count++;
                s.lastSeen = now;
                return;
            }
        }
        Source s = new Source();
        s.x = x;
        s.y = y;
        s.z = z;
        s.count = 1;
        s.lastSeen = now;
        sources.add(s);
    }

    /** Draws the confirmed sources and drops the stale ones. Called once per client tick. */
    public static void tick(Minecraft mc) {
        SparklingCritterModule m = SparklingCritterModule.INSTANCE;
        if (m == null || !m.isEnabled() || !m.particleEsp() || mc.level == null || mc.player == null
                || !Safari.onSafari(mc)) {
            sources.clear();
            return;
        }
        report(m);

        long now = System.currentTimeMillis();
        int min = m.particleMin();
        Iterator<Source> it = sources.iterator();
        while (it.hasNext()) {
            Source s = it.next();
            if (now - s.lastSeen > EXPIRY_MS) {
                it.remove();
                continue;
            }
            // Below the threshold it is not a trail yet, so it is tracked but not drawn. A marker
            // that appears and vanishes on two stray particles is worse than no marker.
            if (s.count < min) {
                continue;
            }
            double half = m.particleSize() / 2.0;
            AABB box = new AABB(s.x - half, s.y - half, s.z - half,
                    s.x + half, s.y + half, s.z + half);
            EspRender.draw(null, box, m);
            if (m.beam()) {
                WorldRender.path(List.of(
                        new Vec3(s.x, s.y - half, s.z), new Vec3(s.x, s.y - half + 20, s.z)),
                        m.color(), 0.2);
            }
            if (!s.announced) {
                s.announced = true;
                // No name: this route finds a trail, not a plate. The plate pass names it if and
                // when the mob comes into view - see SafariEsp.
                m.notifyFound(mc, null);
            }
        }
    }

    /** Whether anything is currently being marked, so the plate pass can avoid announcing twice. */
    public static boolean marking() {
        SparklingCritterModule m = SparklingCritterModule.INSTANCE;
        if (m == null) {
            return false;
        }
        for (Source s : sources) {
            if (s.count >= m.particleMin()) {
                return true;
            }
        }
        return false;
    }

    // --- the id setting ----------------------------------------------------------------------------

    /** Parses the comma-separated id list, once per change rather than once per particle. */
    private static Set<ParticleType<?>> watch(String ids) {
        if (ids.equals(cachedIds)) {
            return watched;
        }
        cachedIds = ids;
        Set<ParticleType<?>> out = new HashSet<>();
        for (String part : ids.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            Identifier key = Identifier.tryParse(trimmed);
            ParticleType<?> type = key == null ? null : BuiltInRegistries.PARTICLE_TYPE.getValue(key);
            if (type != null) {
                out.add(type);
            } else {
                DiegoAddonsV2Client.LOGGER.warn(
                        "[sparkling] '{}' is not a particle id and will be ignored", trimmed);
            }
        }
        watched = out;
        return out;
    }

    // --- the debug measurement ---------------------------------------------------------------------

    private static void record(ParticleOptions options) {
        Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        seen.merge(id == null ? "?" : id.toString(), 1, Integer::sum);
    }

    /**
     * Prints what has actually been arriving, every five seconds.
     *
     * <p>This exists because the alternative is guessing at the particle id a second time. Stand
     * next to a sparkling critter with this on and the answer is whichever type suddenly appears in
     * the list.
     */
    private static void report(SparklingCritterModule m) {
        if (!m.particleDebug()) {
            seen.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastReport < 5000) {
            return;
        }
        lastReport = now;
        if (seen.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        seen.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(12)
                .forEach(e -> sb.append(e.getKey()).append(" x").append(e.getValue()).append("  "));
        DiegoAddonsV2Client.LOGGER.info("[sparkling] particles in the last 5s: {}", sb.toString().trim());
        seen.clear();
    }

    private static double sqr(double v) {
        return v * v;
    }

    /** Drops everything, e.g. on leaving a world. */
    public static void clear() {
        sources.clear();
        seen.clear();
    }
}
