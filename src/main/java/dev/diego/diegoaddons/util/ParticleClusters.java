package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Where are particles coming from", which turns out to be how two different things are found.
 *
 * <p>A single particle says nothing - the server sends thousands a second from everything anyone is
 * doing. What identifies a thing is a <b>stream</b> of them from one place, so this collects them by
 * position, counts them, and only calls a spot real once enough have arrived. That threshold is the
 * whole defence against a wrong particle id: a stray sparkle never reaches it, and a real source
 * passes it in well under a second.
 *
 * <p>Shared rather than written twice because the sparkling critters and the floor drops want
 * exactly the same thing with different ids - and a clustering rule that drifted apart between two
 * copies would be two features failing in different ways for the same reason.
 */
public final class ParticleClusters {
    /** One place particles keep coming from. */
    public static final class Source {
        public double x;
        public double y;
        public double z;
        public int count;
        public long lastSeen;
        /** Free for the owner to use - both users need "have I already shouted about this". */
        public boolean announced;
    }

    private final double radius;
    private final long expiryMs;
    private final List<Source> sources = new ArrayList<>();

    /**
     * @param radius   how far from a source a particle still belongs to it
     * @param expiryMs how long a source survives with nothing new arriving
     */
    public ParticleClusters(double radius, long expiryMs) {
        this.radius = radius;
        this.expiryMs = expiryMs;
    }

    /** Files one particle, either into the source it belongs to or into a new one. */
    public void add(double x, double y, double z) {
        long now = System.currentTimeMillis();
        for (Source s : sources) {
            if (sqr(s.x - x) + sqr(s.y - y) + sqr(s.z - z) < radius * radius) {
                // Drift towards the new particle rather than jumping to it: a trail is spread over a
                // block or two, and a source that took the newest position would twitch about
                // instead of settling on the thing emitting them.
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

    /**
     * Drops what has gone quiet and hands back what has passed {@code min}.
     *
     * <p>Expiry happens here rather than on a timer of its own, so a caller that stops asking stops
     * accumulating - there is no background list growing behind a disabled feature.
     */
    public List<Source> ready(int min) {
        long now = System.currentTimeMillis();
        List<Source> out = new ArrayList<>();
        Iterator<Source> it = sources.iterator();
        while (it.hasNext()) {
            Source s = it.next();
            if (now - s.lastSeen > expiryMs) {
                it.remove();
                continue;
            }
            if (s.count >= min) {
                out.add(s);
            }
        }
        return out;
    }

    /** Whether anything has passed the threshold right now. */
    public boolean any(int min) {
        for (Source s : sources) {
            if (s.count >= min) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        sources.clear();
    }

    private static double sqr(double v) {
        return v * v;
    }

    // --- the id setting, which both users spell the same way ---------------------------------------

    /**
     * Parses a comma-separated particle id list.
     *
     * <p>Unknown ids are warned about once by the caller's cache and then ignored, so a typo in a
     * text box costs that one id rather than the whole setting.
     */
    public static Set<ParticleType<?>> parseIds(String ids) {
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
                        "[DiegoAddons] '{}' is not a particle id and will be ignored", trimmed);
            }
        }
        return out;
    }
}
