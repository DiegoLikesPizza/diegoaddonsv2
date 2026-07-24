package dev.diego.diegoaddons.util;

/**
 * A server-tick clock, driven by Hypixel's per-tick ping packet rather than the client's own frame
 * loop.
 *
 * <p>Anything timing-critical against the server - the water board countdown - has to count the
 * server's ticks, not the client's: the two only agree when the connection is smooth, and the whole
 * point of a countdown is that it stays right when it is not. Hypixel sends a {@code ping} packet
 * with a non-zero id once per server tick, so {@code ClientboundPingPacketMixin} bumps this on each
 * one and everything else reads it here.
 */
public final class ServerTicks {
    private static long ticks;

    private ServerTicks() {
    }

    /** Called from the ping-packet mixin, once per server tick. */
    public static void increment() {
        ticks++;
    }

    /** The running server-tick count. Monotonic within a session. */
    public static long get() {
        return ticks;
    }
}
