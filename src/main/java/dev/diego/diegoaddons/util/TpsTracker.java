package dev.diego.diegoaddons.util;

/**
 * Estimates the server's ticks-per-second from the {@code ClientboundSetTime} packets the server
 * sends periodically. Each packet carries the server's absolute game-tick counter; dividing the
 * change in that counter by the real elapsed time between two packets yields the tick rate,
 * independent of how often the server chooses to send the packet. Values are clamped to 20 and
 * exponentially smoothed to avoid jitter.
 *
 * <p>Fed from a Mixin on {@code ClientPacketListener#handleSetTime} and reset on disconnect.
 */
public final class TpsTracker {
    private static long lastNanos = 0L;
    private static long lastGameTime = 0L;
    private static volatile double tps = 20.0;
    private static volatile boolean hasData = false;

    private TpsTracker() {
    }

    /** Called for every server time packet, with the packet's absolute game-tick counter. */
    public static void onTimePacket(long gameTime) {
        long now = System.nanoTime();
        if (lastNanos != 0L) {
            double dtSeconds = (now - lastNanos) / 1_000_000_000.0;
            long dTicks = gameTime - lastGameTime;
            if (dtSeconds >= 5.0) {
                // Long gap (pause / lag spike / world switch) - drop the sample and re-baseline.
                hasData = false;
            } else if (dtSeconds > 0.05 && dTicks > 0) {
                double measured = Math.max(0.0, Math.min(20.0, dTicks / dtSeconds));
                tps = hasData ? tps * 0.75 + measured * 0.25 : measured;
                hasData = true;
            }
        }
        lastNanos = now;
        lastGameTime = gameTime;
    }

    /** Whether a live estimate is available (false right after joining, before two packets arrive). */
    public static boolean hasData() {
        return hasData;
    }

    public static double tps() {
        return tps;
    }

    /** Clear state when leaving a world so a stale value isn't carried into the next server. */
    public static void reset() {
        lastNanos = 0L;
        lastGameTime = 0L;
        hasData = false;
        tps = 20.0;
    }
}
