package dev.diego.diegoaddons.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds, fetches and installs a newer build of the mod from the project's GitHub releases.
 *
 * <p>The work is in three steps that can each be stopped at, because "update my mod for me" is a
 * different amount of trust for different people - see the Auto Update module's mode setting:
 *
 * <ol>
 *   <li><b>Check.</b> Read the latest release off the GitHub API and compare its tag with the
 *       running version. Costs one request and changes nothing on disk.</li>
 *   <li><b>Download.</b> Fetch the release's {@code .jar} into {@code <game>/diegoaddons-updates/},
 *       deliberately <i>outside</i> the mods folder - two jars with the same mod id in there is a
 *       crash on the next launch, not a choice between versions. The file is verified before it is
 *       kept: it has to be a readable zip whose {@code fabric.mod.json} names this mod.</li>
 *   <li><b>Install.</b> At JVM shutdown, put the old jar aside and move the new one in.</li>
 * </ol>
 *
 * <p><b>Why the install waits for shutdown.</b> The running jar is open - on Windows it is locked
 * outright - so it cannot be replaced from inside the game that is using it. Even at shutdown the
 * lock usually outlives the hook, so a failed rename falls back to a small batch file that waits for
 * the process to go away and finishes the swap after it (Windows only; on Linux and macOS the rename
 * succeeds in the hook and the helper is never needed).
 *
 * <p><b>The old jar is never deleted.</b> It is renamed to {@code diegoaddonsv2-previous.jar.bak}
 * beside the new one - Fabric only scans {@code .jar}, so it is inert, and it is the way back if a
 * new build turns out to be broken mid-session.
 */
public final class Updater {

    /** Where releases are published. The only place a jar is ever fetched from. */
    private static final String REPO = "DiegoLikesPizza/diegoaddonsv2";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases";

    /**
     * Hosts a download may come from. The asset URL is read out of a response, so it is treated as
     * input rather than as something we wrote: anything not served by GitHub is refused instead of
     * fetched.
     */
    private static final List<String> ALLOWED_HOSTS =
            List.of("github.com", "api.github.com", "objects.githubusercontent.com",
                    "release-assets.githubusercontent.com");

    private static final int TIMEOUT_MS = 10_000;
    /** A generous ceiling on a mod jar, so a wrong URL cannot fill the disk. */
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final String STAGING_DIR = "diegoaddons-updates";
    /**
     * The backup name older versions left behind. Nothing writes one any more - it exists only so
     * {@link #removeStaleBackup} can find and delete the ones already sitting in mods folders.
     */
    private static final String BACKUP_NAME = DiegoAddonsV2Client.MOD_ID + "-previous.jar.bak";

    /** Where the check has got to. Read by the module for its status row; written by the worker. */
    public enum State {
        IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, STAGED, FAILED
    }

    /** One published release, reduced to the parts that matter here. */
    public record Release(String version, String assetName, String url, String pageUrl) {
    }

    private static volatile State state = State.IDLE;
    private static volatile String detail = "";
    private static volatile Release latest;
    /** What the shutdown hook should swap, read when it runs rather than when it was registered. */
    private static volatile Path pendingOld;
    private static volatile Path pendingNew;
    private static volatile boolean hookRegistered;
    private static volatile boolean busy;

    private Updater() {
    }

    public static State state() {
        return state;
    }

    /** A short line describing the current state, for a status row or a chat reply. */
    public static String detail() {
        return detail;
    }

    /** The newest release seen by the last successful check, or null if there has not been one. */
    public static Release latest() {
        return latest;
    }

    /** The running version, as Fabric knows it - {@code 2.5.0}, not {@code v2.5.0}. */
    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(DiegoAddonsV2Client.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0");
    }

    /**
     * Starts a check on a daemon thread, unless one is already running.
     *
     * @param prereleases whether a release marked pre-release counts
     * @param download    fetch the jar if there is a newer one
     * @param install     swap it in at shutdown once fetched (ignored without {@code download})
     * @param announce    say so in chat as well as in a toast
     * @param verbose     report the outcome in chat even when there is nothing to report - what a
     *                    check someone asked for by hand owes them, and what a timed one does not
     */
    public static void check(boolean prereleases, boolean download, boolean install,
                             boolean announce, boolean verbose) {
        synchronized (Updater.class) {
            if (busy) {
                return;
            }
            busy = true;
        }
        state = State.CHECKING;
        detail = "Checking for updates…";
        Thread t = new Thread(() -> {
            try {
                // A check asked for by hand is chatty by definition; the setting decides only what
                // the timed one is allowed to say.
                run(prereleases, download, install, announce || verbose);
                if (verbose && state != State.AVAILABLE && state != State.STAGED) {
                    chat("§b[DiegoAddons] §f" + detail);
                }
            } catch (Exception e) {
                fail(e.toString());
                if (verbose) {
                    chat("§b[DiegoAddons] §c" + detail);
                }
            } finally {
                busy = false;
            }
        }, "DiegoAddons Update");
        t.setDaemon(true);
        t.start();
    }

    private static void run(boolean prereleases, boolean download, boolean install, boolean announce)
            throws Exception {
        Release release = fetchLatest(prereleases);
        if (release == null) {
            state = State.UP_TO_DATE;
            detail = "No release published yet";
            DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] Update check: no usable release found");
            return;
        }
        latest = release;
        String current = currentVersion();
        if (compare(release.version(), current) <= 0) {
            state = State.UP_TO_DATE;
            detail = "Up to date (" + current + ")";
            DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] Update check: {} is the latest", current);
            return;
        }

        state = State.AVAILABLE;
        detail = release.version() + " is available (you have " + current + ")";
        DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] Update available: {} -> {}",
                current, release.version());
        toast("Update available", "v" + release.version());
        if (announce) {
            chat("§b[DiegoAddons] §fVersion §e" + release.version()
                    + " §fis out - you are on §7" + current + "§f.");
        }
        if (!download || release.url() == null) {
            return;
        }

        state = State.DOWNLOADING;
        detail = "Downloading " + release.version() + "…";
        Path jar = downloadAndVerify(release);
        state = State.STAGED;

        Path currentJar = ownJar();
        if (install && currentJar != null) {
            armInstall(currentJar, jar);
            detail = "v" + release.version() + " installs on restart";
            toast("Update ready", "Restart to apply v" + release.version());
            if (announce) {
                chat("§b[DiegoAddons] §fVersion §e" + release.version()
                        + " §fhas been downloaded and installs when you restart.");
            }
        } else {
            detail = "Downloaded to " + STAGING_DIR + "/" + jar.getFileName();
            toast("Update downloaded", jar.getFileName().toString());
            if (announce) {
                chat("§b[DiegoAddons] §fVersion §e" + release.version() + " §fwas downloaded to §7"
                        + STAGING_DIR + "§f. Move it into your mods folder to use it.");
            }
            if (install) {
                // Asked to install, but there is nothing to replace - a dev run, or the mod loaded
                // from somewhere that is not a file. Say so rather than silently doing half the job.
                DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Cannot install automatically: the "
                        + "running mod is not a jar on disk. The download is in {}/", STAGING_DIR);
            }
        }
    }

    // --- The check ------------------------------------------------------------------------------

    /**
     * The newest release worth offering, or null if there is none.
     *
     * <p>The list endpoint is used rather than {@code /releases/latest} in both cases: latest ignores
     * pre-releases entirely, so honouring the setting means looking at the list anyway, and one code
     * path is easier to trust than two. Releases come back newest first.
     */
    private static Release fetchLatest(boolean prereleases) throws Exception {
        String json = get(API + "?per_page=30");
        JsonArray releases = JsonParser.parseString(json).getAsJsonArray();
        Release best = null;
        for (JsonElement el : releases) {
            JsonObject r = el.getAsJsonObject();
            if (bool(r, "draft") || (bool(r, "prerelease") && !prereleases)) {
                continue;
            }
            String tag = string(r, "tag_name");
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            JsonArray assets = r.getAsJsonArray("assets");
            String name = null;
            String url = null;
            if (assets != null) {
                for (JsonElement a : assets) {
                    JsonObject asset = a.getAsJsonObject();
                    String assetName = string(asset, "name");
                    if (assetName == null || !assetName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        continue;
                    }
                    // The build also produces -sources and -dev jars; neither is the mod.
                    String lower = assetName.toLowerCase(Locale.ROOT);
                    if (lower.contains("-sources") || lower.contains("-dev")) {
                        continue;
                    }
                    name = assetName;
                    url = string(asset, "browser_download_url");
                    break;
                }
            }
            // The highest version wins, not the first one listed.
            //
            // This used to return here, on the assumption that GitHub hands the releases back
            // newest-first. It does not, and the way it fails is silent: with b-9 and b-10 both
            // published, the live feed came back b-9, b-10, b-8, b-6, b-2 - neither by date nor
            // alphabetically - so every client would have been offered b-9 forever and the newer
            // build would simply never have existed to them. Comparing is correct whatever order
            // the feed is in, which is the point: the order was never ours to rely on.
            Release candidate = new Release(version, name, url, string(r, "html_url"));
            if (best == null || compare(candidate.version(), best.version()) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Compares two versions: the dotted numbers first, longest wins on a tie ({@code 2.5.1} beats
     * {@code 2.5}), and <b>a pre-release ranks below the release it leads to</b> - {@code 2.5.5}
     * beats {@code 2.5.5-beta.1}, which is the semver rule and the one thing this has to get right
     * for betas to work at all.
     *
     * <p>It used to split on every non-digit, which made {@code 2.5.5-beta.1} parse as
     * {@code 2.5.5.1} and therefore <i>newer</i> than the finished {@code 2.5.5}. Two failures came
     * out of that, and both are the kind you only notice weeks later: a tester on the beta would
     * never be offered the release, and anyone on the release with pre-releases switched on would be
     * offered the beta as an upgrade, forever.
     *
     * <p>Anything after the first hyphen is the pre-release part, compared by its own numbers so
     * {@code beta.2} beats {@code beta.1}, then by text so {@code rc} beats {@code beta}. It never
     * throws on a tag someone typed by hand, which is still the point.
     */
    static int compare(String a, String b) {
        String coreA = core(a);
        String coreB = core(b);
        int byCore = compareNumbers(coreA, coreB);
        if (byCore != 0) {
            return byCore;
        }
        String preA = pre(a);
        String preB = pre(b);
        if (preA.isEmpty() && preB.isEmpty()) {
            return 0;
        }
        // A release outranks any pre-release of the same numbers.
        if (preA.isEmpty()) {
            return 1;
        }
        if (preB.isEmpty()) {
            return -1;
        }
        // The word before the number decides first: "rc.1" is later than "beta.9", and comparing the
        // numbers first would call that backwards. Same word, then the number: beta.2 after beta.1.
        String labelA = preA.replaceAll("[^A-Za-z]", "");
        String labelB = preB.replaceAll("[^A-Za-z]", "");
        if (!labelA.equalsIgnoreCase(labelB)) {
            return labelA.compareToIgnoreCase(labelB);
        }
        return compareNumbers(preA, preB);
    }

    /** The numbers before any pre-release suffix: "2.5.5-beta.1" is "2.5.5". */
    private static String core(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    /** Everything after the first hyphen, or "" for a plain release. */
    private static String pre(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? "" : version.substring(dash + 1);
    }

    private static int compareNumbers(String a, String b) {
        String[] left = a.split("[^0-9]+");
        String[] right = b.split("[^0-9]+");
        int n = Math.max(left.length, right.length);
        for (int i = 0; i < n; i++) {
            int l = number(left, i);
            int r = number(right, i);
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int number(String[] parts, int i) {
        if (i >= parts.length || parts[i].isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[i]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- The download ---------------------------------------------------------------------------

    /**
     * Fetches the release jar and hands back the staged file, having checked it is one.
     *
     * <p>Written to {@code .part} first and renamed once it is whole, so a download cut off halfway
     * never looks like a finished update waiting to be installed.
     */
    private static Path downloadAndVerify(Release release) throws IOException {
        URI uri = URI.create(release.url());
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host)) {
            throw new IOException("refusing a download from " + host);
        }

        Path dir = FabricLoader.getInstance().getGameDir().resolve(STAGING_DIR);
        Files.createDirectories(dir);
        String name = safeName(release.assetName() != null
                ? release.assetName()
                : DiegoAddonsV2Client.MOD_ID + "-" + release.version() + ".jar");
        Path part = dir.resolve(name + ".part");
        Path out = dir.resolve(name);
        Files.deleteIfExists(part);

        HttpURLConnection c = open(release.url());
        try (InputStream in = c.getInputStream(); OutputStream os = Files.newOutputStream(part)) {
            byte[] buf = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buf)) > 0) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IOException("the download is larger than " + (MAX_BYTES >> 20) + " MB");
                }
                os.write(buf, 0, read);
            }
        } finally {
            c.disconnect();
        }

        verify(part, release.version());
        Files.move(part, out, StandardCopyOption.REPLACE_EXISTING);
        DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] Downloaded {} to {}", name, dir);
        return out;
    }

    /**
     * Refuses anything that is not this mod.
     *
     * <p>The jar is about to be put in the mods folder and run, so "the server said it was a jar" is
     * not enough: it has to open as a zip and carry a {@code fabric.mod.json} whose id is ours. A
     * release with the wrong file attached is then a failed check rather than a game that will not
     * start.
     */
    private static void verify(Path jar, String expectedVersion) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                throw new IOException("no fabric.mod.json - not a Fabric mod");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject meta = JsonParser.parseString(text).getAsJsonObject();
                String id = string(meta, "id");
                if (!DiegoAddonsV2Client.MOD_ID.equals(id)) {
                    throw new IOException("the jar is '" + id + "', not " + DiegoAddonsV2Client.MOD_ID);
                }
                String version = string(meta, "version");
                if (version != null && compare(version, currentVersion()) <= 0) {
                    throw new IOException("the jar is version " + version + ", which is not newer");
                }
                if (version != null && !version.equals(expectedVersion)) {
                    // Not fatal: the tag and the built version can drift by a "v" or a suffix. Worth
                    // a line in the log, because it is the first thing to look at if the wrong
                    // build ever lands.
                    DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Release is tagged {} but the jar "
                            + "says {}; installing what the jar says", expectedVersion, version);
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(jar);
            throw e;
        }
    }

    /** Strips a filename down to what is safe to write, since it arrives from a response. */
    private static String safeName(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!cleaned.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            cleaned = cleaned + ".jar";
        }
        return cleaned;
    }

    // --- The install ----------------------------------------------------------------------------

    /** The file this mod is running from, or null if it is not a jar on disk (a dev run). */
    private static Path ownJar() {
        ModContainer container = FabricLoader.getInstance()
                .getModContainer(DiegoAddonsV2Client.MOD_ID).orElse(null);
        if (container == null || container.getOrigin().getKind() != ModOrigin.Kind.PATH) {
            return null;
        }
        for (Path p : container.getOrigin().getPaths()) {
            if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) {
                return p;
            }
        }
        return null;
    }

    /**
     * Arranges for the swap to happen once the game has stopped using the jar.
     *
     * <p>A shutdown hook rather than a client-stopping event, so quitting through the window's close
     * button lands here too. The hook is registered once but reads the pending paths when it runs,
     * so a second check later in the same session installs what it found rather than what the first
     * one did.
     */
    private static synchronized void armInstall(Path currentJar, Path newJar) {
        pendingOld = currentJar;
        pendingNew = newJar;
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Path old = pendingOld;
            Path fresh = pendingNew;
            if (old != null && fresh != null) {
                install(old, fresh);
            }
        }, "DiegoAddons Update Install"));
    }

    /**
     * Deletes the old jar and puts the new one in its place.
     *
     * <p><b>The old jar is deleted, not kept.</b> It used to be renamed to a {@code .jar.bak} beside
     * it, on the reasoning that a backup is the way back from a bad update. That reasoning was
     * wrong twice over: the way back is the GitHub release it came from, which is still there and
     * still downloadable, and a spare jar left in the <i>mods folder</i> is a loaded gun. Diego's
     * instance was broken badly enough by exactly that to need a reboot. A folder the loader scans
     * is no place to store anything that is not meant to be loaded.
     *
     * <p>Ordered delete-then-move rather than the other way round, and the reason is which failure
     * you would rather have. Moving the new one in first and failing to remove the old leaves
     * <b>two jars with the same mod id</b>, which does not start at all - the very thing this is
     * being changed to avoid. Deleting first and failing to move leaves <b>no</b> mod, which starts
     * fine without it and is fixed by dragging one file in from the staging folder. Missing beats
     * broken.
     */
    private static void install(Path currentJar, Path newJar) {
        if (!Files.exists(newJar)) {
            return;
        }
        Path mods = currentJar.getParent();
        Path target = mods.resolve(newJar.getFileName().toString());
        try {
            Files.delete(currentJar);
        } catch (IOException e) {
            // The usual case on Windows: the jar is still mapped by the JVM that is exiting. Hand
            // the job to something that outlives it.
            handOff(currentJar, newJar, target);
            return;
        }
        try {
            Files.move(newJar, target, StandardCopyOption.REPLACE_EXISTING);
            DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] Updated to {}", target.getFileName());
        } catch (IOException e) {
            // Nothing to put back - the old one is gone on purpose. Say plainly where the new jar
            // is, because the game will start without any version of this mod at all.
            DiegoAddonsV2Client.LOGGER.error("[DiegoAddons] The old jar was removed but the new one "
                    + "could not be put in place. Move {} into {} by hand", newJar, mods, e);
        }
    }

    /**
     * Removes a {@code .jar.bak} left in the mods folder by a version that still made them.
     *
     * <p>Deleting a file at startup is not something to do lightly, so this is deliberately narrow:
     * one exact name, one that only this mod ever writes, in the folder this mod is running from.
     * It is here because the file is actively harmful where it sits and everybody who updated on an
     * older version already has one - leaving them to find out the way Diego did is not a fix.
     */
    public static void removeStaleBackup() {
        try {
            Path own = ownJar();
            if (own == null) {
                return;
            }
            Path backup = own.getParent().resolve(BACKUP_NAME);
            if (Files.deleteIfExists(backup)) {
                DiegoAddonsV2Client.LOGGER.info(
                        "[DiegoAddons] Removed a leftover {} from the mods folder", BACKUP_NAME);
            }
        } catch (IOException | RuntimeException e) {
            // Not worth failing startup over: the file is inert until something tries to load it,
            // and saying so is all that can usefully be done.
            DiegoAddonsV2Client.LOGGER.warn(
                    "[DiegoAddons] Could not remove a leftover {}; delete it by hand", BACKUP_NAME, e);
        }
    }

    /**
     * Writes a batch file that waits for this process to release the jar, then finishes the swap.
     *
     * <p>Windows only, and only because Windows is the one place the rename above reliably fails.
     * It gives up after a minute rather than looping forever, and deletes itself either way - a
     * failed swap leaves the old version running, which is the outcome to prefer.
     */
    private static void handOff(Path oldJar, Path newJar, Path target) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Could not replace {} - the new jar is at "
                    + "{} and can be moved in by hand", oldJar.getFileName(), newJar);
            return;
        }
        try {
            Path script = newJar.getParent().resolve("apply-update.bat");
            String body = """
                    @echo off
                    setlocal
                    set "OLD=%s"
                    set "NEW=%s"
                    set "DST=%s"
                    set /a tries=0
                    :retry
                    del /f /q "%%OLD%%" >nul 2>&1
                    if not exist "%%OLD%%" goto place
                    set /a tries+=1
                    if %%tries%% GEQ 60 goto done
                    timeout /t 1 /nobreak >nul
                    goto retry
                    :place
                    move /y "%%NEW%%" "%%DST%%" >nul 2>&1
                    :done
                    del "%%~f0"
                    """.formatted(oldJar, newJar, target);
            Files.writeString(script, body);
            new ProcessBuilder("cmd", "/c", "start", "", "/min", script.toString())
                    .directory(script.getParent().toFile())
                    .start();
            DiegoAddonsV2Client.LOGGER.info("[DiegoAddons] The update finishes after the game closes");
        } catch (IOException e) {
            DiegoAddonsV2Client.LOGGER.error("[DiegoAddons] Could not hand the update off; the new "
                    + "jar is at {}", newJar, e);
        }
    }

    // --- Plumbing -------------------------------------------------------------------------------

    private static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            return new String(in.readNBytes((int) MAX_BYTES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // GitHub answers a private or missing repository with a 404, which is worth saying in
            // plain words: it is the difference between "no update" and "nobody can see this repo".
            if (c.getResponseCode() == 404) {
                throw new IOException("the release list is not public (HTTP 404)");
            }
            throw e;
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "DiegoAddonsV2/" + currentVersion());
        c.setRequestProperty("Accept", "application/vnd.github+json");
        return c;
    }

    private static void fail(String message) {
        state = State.FAILED;
        detail = "Update check failed: " + message;
        DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Update check failed: {}", message);
    }

    /**
     * Queues a toast from the worker thread.
     *
     * <p>The toast list is a plain list walked by the render thread every frame, so adding to it
     * from here directly is a concurrent modification waiting for the wrong frame.
     */
    private static void toast(String title, String body) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> Toasts.show(title, body));
    }

    /** Chat runs on the client thread, and this is all said from the worker. */
    private static void chat(String message) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.gui != null) {
                mc.gui.getChat().addClientSystemMessage(
                        net.minecraft.network.chat.Component.literal(message));
            }
        });
    }

    private static String string(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && !el.isJsonNull() && el.getAsBoolean();
    }
}
