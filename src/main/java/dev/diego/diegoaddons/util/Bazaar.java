package dev.diego.diegoaddons.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bazaar prices, fetched from Hypixel's public endpoint and held in memory.
 *
 * <p>{@code api.hypixel.net/v2/skyblock/bazaar} needs no API key and is the same list for everyone,
 * so nothing about the player leaves the machine - the request carries no name, no profile and no
 * key. That is the whole reason this feature could be built at all without asking Diego for a key.
 *
 * <p><b>Prices are a snapshot with an age, and the age is exposed on purpose.</b> Anything that acts
 * on a price rather than merely showing one has to be able to ask whether the number is real yet:
 * the visitor helper refuses to decline an offer it cannot price, and {@link #fresh()} is how it
 * knows. A stale price is worse than none when the consequence is throwing away a visitor.
 *
 * <p>Fetched at most every {@link #REFRESH_MS}, on a daemon thread, and never on the client thread.
 * A failed fetch keeps the previous snapshot and logs once - the alternative, clearing the cache on
 * a blip, would turn one lost request into a run of refused decisions.
 */
public final class Bazaar {
    private static final String URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    /** How often the snapshot is replaced. The bazaar moves, but not in seconds. */
    private static final long REFRESH_MS = 5 * 60 * 1000L;
    /** How old a snapshot may be and still be acted on. */
    private static final long STALE_MS = 20 * 60 * 1000L;

    private Bazaar() {
    }

    private static final Map<String, Double> BUY = new HashMap<>();
    private static final Map<String, Double> SELL = new HashMap<>();
    private static volatile long fetchedAt;
    private static volatile long lastAttempt;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    /** Whether there is a snapshot recent enough to make a decision on. */
    public static boolean fresh() {
        return fetchedAt != 0 && System.currentTimeMillis() - fetchedAt < STALE_MS;
    }

    /** How old the snapshot is in milliseconds, or -1 when there has never been one. */
    public static long age() {
        return fetchedAt == 0 ? -1 : System.currentTimeMillis() - fetchedAt;
    }

    /**
     * What it costs to buy one of these right now - the instant-buy price.
     *
     * <p>Instant buy rather than the sell offer, because this answers "what am I handing over": a
     * visitor's items are things you either buy or could have sold, and the honest cost of giving
     * one away is what it would take to replace it. Returns 0 for anything the bazaar does not
     * trade, which is the caller's signal that it cannot price this offer.
     */
    public static double buyPrice(String productId) {
        Double v = BUY.get(productId);
        return v == null ? 0 : v;
    }

    /** What one of these fetches on an instant sell. */
    public static double sellPrice(String productId) {
        Double v = SELL.get(productId);
        return v == null ? 0 : v;
    }

    /**
     * The bazaar id for an item's display name, e.g. "Enchanted Carrot" to {@code ENCHANTED_CARROT}.
     *
     * <p>SkyBlock's own ids are the display name in capitals with the spaces replaced, and for the
     * farming items a visitor asks for that mapping is exact. It is not exact in general - some
     * items carry ids that no longer resemble their names - so a miss here means "not priced"
     * rather than a wrong price, which is the failure this feature can live with.
     */
    public static String idFor(String displayName) {
        String plain = displayName.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return ALIASES.getOrDefault(plain, plain);
    }

    /**
     * The ids that are <b>not</b> the display name upcased - all of them Minecraft's own old item
     * names, which SkyBlock kept.
     *
     * <p>This is not a nicety. Every one of these is a crop somebody farms in the Garden, so without
     * the table the farming session prices a Nether Wart run at nothing and the visitor helper
     * cannot price half of what a visitor asks for - which reads as "the tracker is broken", because
     * from the outside it is. {@code NETHER_WART} and {@code CARROT} are ids the bazaar simply does
     * not have, so the miss was silent.
     *
     * <p>Cocoa beans are the odd one: their id still carries the old dye metadata, {@code INK_SACK:3}.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("NETHER_WART", "NETHER_STALK"),
            Map.entry("ENCHANTED_NETHER_WART", "ENCHANTED_NETHER_STALK"),
            Map.entry("MUTANT_NETHER_WART", "MUTANT_NETHER_STALK"),
            Map.entry("CARROT", "CARROT_ITEM"),
            Map.entry("POTATO", "POTATO_ITEM"),
            Map.entry("COCOA_BEANS", "INK_SACK:3"),
            Map.entry("ENCHANTED_COCOA_BEANS", "ENCHANTED_COCOA"),
            Map.entry("MUSHROOM", "RED_MUSHROOM"),
            Map.entry("ENCHANTED_MUSHROOM", "ENCHANTED_RED_MUSHROOM"));

    /** Price by display name, or 0 when the bazaar does not know it. */
    public static double priceOf(String displayName) {
        return buyPrice(idFor(displayName));
    }

    /**
     * Starts a refresh if one is due. Called from the client tick; returns at once.
     *
     * <p>Only ever called by a feature that wants a price - there is no point holding a live copy of
     * the bazaar for a player who has the visitor helper switched off.
     */
    public static void refreshIfDue() {
        long now = System.currentTimeMillis();
        if (now - fetchedAt < REFRESH_MS || now - lastAttempt < 30_000) {
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        lastAttempt = now;
        Thread t = new Thread(Bazaar::fetch, "diegoaddons-bazaar");
        t.setDaemon(true);
        t.start();
    }

    private static void fetch() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "DiegoAddonsV2")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                DiegoAddonsV2Client.LOGGER.warn("Bazaar returned {}", response.statusCode());
                return;
            }
            parse(JsonParser.parseString(response.body()).getAsJsonObject());
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("Could not fetch bazaar prices: {}", e.toString());
        } finally {
            RUNNING.set(false);
        }
    }

    /**
     * Reads the snapshot into two maps.
     *
     * <p>Built aside and swapped in rather than cleared and refilled: the client thread reads these
     * while this one writes, and a map that is momentarily empty is a price of zero, which reads as
     * "cannot price this" exactly when the answer is available.
     */
    private static void parse(JsonObject root) {
        if (!root.has("products")) {
            return;
        }
        Map<String, Double> buy = new HashMap<>();
        Map<String, Double> sell = new HashMap<>();
        JsonObject products = root.getAsJsonObject("products");
        for (String id : products.keySet()) {
            JsonObject product = products.getAsJsonObject(id);
            if (!product.has("quick_status")) {
                continue;
            }
            JsonObject status = product.getAsJsonObject("quick_status");
            if (status.has("buyPrice")) {
                buy.put(id, status.get("buyPrice").getAsDouble());
            }
            if (status.has("sellPrice")) {
                sell.put(id, status.get("sellPrice").getAsDouble());
            }
        }
        if (buy.isEmpty()) {
            return;
        }
        synchronized (BUY) {
            BUY.clear();
            BUY.putAll(buy);
        }
        synchronized (SELL) {
            SELL.clear();
            SELL.putAll(sell);
        }
        fetchedAt = System.currentTimeMillis();
        DiegoAddonsV2Client.LOGGER.info("Bazaar prices loaded ({} products)", buy.size());
    }
}
