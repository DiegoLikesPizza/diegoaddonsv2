package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.PortalImage;
import dev.diego.diegoaddons.module.modules.PortalImagesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the portals around you and hangs a picture on each one.
 *
 * <p>A portal is not one block, it is a rectangle of them, so the blocks are flood-filled into
 * <b>panes</b> first: a connected run of portal blocks lying in one plane, which is the thing that
 * has a picture-shaped surface. Drawing per block would tile the image once per square metre, which
 * is the one result nobody wants.
 *
 * <p>The scan is on a timer rather than per tick, for the reason the Floor Drops scan is: asking the
 * level what is at every position in a cube of this size is tens of thousands of reads, and a portal
 * does not move. What it found is redrawn every tick from the cached list, so a portal that has just
 * been built takes up to {@link #PERIOD_MS} to be papered over.
 *
 * <p>Which image goes on which portal is stored <b>by position</b> (see {@link PortalImage}), set
 * with {@code /da portal <file>} while standing in front of one. Position, not island name: the
 * island is read from the tab list and is blank for the first seconds after a warp, and a key that
 * is briefly blank is a key that briefly finds nothing - which would read as the picture falling off
 * every time you change lobby.
 */
public final class PortalImages {
    /** How often to re-scan while standing still. */
    private static final long PERIOD_MS = 1500;
    /** Re-scan at once once the player has moved this far from where the last scan was centred. */
    private static final double MOVED = 6.0;
    /** How far above and below to look. See the scan for why this is not the search radius. */
    private static final int HEIGHT = 8;

    /** One portal surface: the block range it covers, all of it in one plane. */
    public record Pane(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        /** The key an assignment is stored under - the corner, which no two panes share. */
        public String key() {
            return minX + "," + minY + "," + minZ;
        }

        public Vec3 centre() {
            return new Vec3((minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
        }
    }

    private static final List<Pane> PANES = new ArrayList<>();
    private static long lastScan;
    private static Vec3 lastCentre;
    /** The block id last resolved, and what it resolved to, so a text setting is not parsed per scan. */
    private static String cachedId;
    private static Block cachedBlock;
    private static boolean warned;

    private PortalImages() {
    }

    /** Everything found by the last scan, for the commands to point at. */
    public static List<Pane> panes() {
        return PANES;
    }

    /** Drops what was found, e.g. on leaving a world - the next tick scans again anyway. */
    public static void clear() {
        PANES.clear();
        lastCentre = null;
        lastScan = 0;
    }

    /** Re-scans when due, then submits a picture for every pane that has one. */
    public static void tick(PortalImagesModule module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 me = mc.player.position();
        long now = System.currentTimeMillis();
        if (now - lastScan > PERIOD_MS || lastCentre == null || lastCentre.distanceTo(me) > MOVED) {
            lastScan = now;
            lastCentre = me;
            scan(mc, module, me);
        }
        for (Pane p : PANES) {
            draw(module, p);
        }
    }

    /**
     * The pane you are looking at, or null.
     *
     * <p>Deliberately not a ray trace: a nether portal has no collision box, so the crosshair passes
     * straight through it and the game's own hit result never names one. What is asked instead is
     * which nearby pane is closest to the middle of the screen, which is the same question a player
     * means by "this one".
     */
    public static Pane looking() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || PANES.isEmpty()) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Pane best = null;
        double bestScore = -1;
        for (Pane p : PANES) {
            Vec3 to = p.centre().subtract(eye);
            double distance = to.length();
            if (distance > 24 || distance < 1.0e-4) {
                continue;
            }
            // The cosine of the angle between where you are pointing and where the pane is. Ties
            // between two portals side by side go to the nearer one, which is the one in front.
            double aim = to.scale(1 / distance).dot(look);
            if (aim < 0.5) {
                continue;
            }
            double score = aim - distance / 1000.0;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    /** The image assigned to a pane, or the module's default when it has none. */
    public static String imageFor(PortalImagesModule module, Pane pane) {
        String assigned = assignment(pane.key());
        return assigned != null ? assigned : module.defaultImage();
    }

    /** The file assigned to a position, or null. */
    public static String assignment(String key) {
        for (PortalImage p : ConfigManager.get().portalImages) {
            if (p.key.equals(key)) {
                return p.file;
            }
        }
        return null;
    }

    /** Assigns an image to a pane; a blank file removes the assignment. Persisted immediately. */
    public static void assign(Pane pane, String file) {
        List<PortalImage> list = ConfigManager.get().portalImages;
        list.removeIf(p -> p.key.equals(pane.key()));
        if (file != null && !file.isBlank()) {
            list.add(new PortalImage(pane.key(), file.trim()));
        }
        ConfigManager.save();
    }

    /** Forgets every assignment. */
    public static void clearAssignments() {
        ConfigManager.get().portalImages.clear();
        ConfigManager.save();
    }

    private static void draw(PortalImagesModule module, Pane p) {
        CustomImages.Image img = CustomImages.get(imageFor(module, p));
        if (img == null) {
            return;
        }
        double width;
        Vec3 origin;
        Vec3 right;
        Vec3 up = new Vec3(0, p.maxY() - p.minY() + 1, 0);
        if (p.minX() == p.maxX() && p.minZ() != p.maxZ()) {
            // A pane one block thick in X: it faces along X, and runs along Z.
            width = p.maxZ() - p.minZ() + 1;
            origin = new Vec3(p.minX() + 0.5, p.minY(), p.minZ());
            right = new Vec3(0, 0, width);
        } else if (p.minZ() == p.maxZ() && p.minX() != p.maxX()) {
            width = p.maxX() - p.minX() + 1;
            origin = new Vec3(p.minX(), p.minY(), p.minZ() + 0.5);
            right = new Vec3(width, 0, 0);
        } else if (p.minY() == p.maxY()) {
            // Flat on the ground - an end portal rather than a nether one. The picture lies on it,
            // read from the north, and "up" on the image runs north.
            origin = new Vec3(p.minX(), p.minY() + 0.9, p.maxZ() + 1);
            right = new Vec3(p.maxX() - p.minX() + 1, 0, 0);
            up = new Vec3(0, 0, -(p.maxZ() - p.minZ() + 1));
        } else {
            // A single block, or something that is not a plane at all. One block is still a portal
            // worth papering; anything else is skipped rather than guessed at.
            if (p.minX() != p.maxX() || p.minZ() != p.maxZ()) {
                return;
            }
            origin = new Vec3(p.minX(), p.minY(), p.minZ() + 0.5);
            right = new Vec3(1, 0, 0);
        }
        ImageQuad quad = ImageQuad.fit(origin, right, up, img.aspect(), module.fitMode());
        WorldRender.image(img.id(), quad, module.tint(), module.bothSides());
    }

    /**
     * Flood-fills every portal block in range into panes.
     *
     * <p>Neighbours are taken in all six directions rather than only within a plane: two portals
     * touching corner to corner are not a thing, and a pane that is a plane comes out of the block
     * range on its own - {@link #draw} reads which axis is flat instead of being told.
     */
    private static void scan(Minecraft mc, PortalImagesModule module, Vec3 me) {
        PANES.clear();
        Block target = resolve(module.blockId());
        if (target == null) {
            return;
        }
        int r = module.radius();
        int cx = (int) Math.floor(me.x);
        int cy = (int) Math.floor(me.y);
        int cz = (int) Math.floor(me.z);
        Set<BlockPos> portal = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                // Shorter than it is wide, deliberately: a portal is something you walk into, so it
                // is at your own height, and the third axis is the one that multiplies the cost.
                for (int y = cy - HEIGHT; y <= cy + HEIGHT; y++) {
                    pos.set(x, y, z);
                    // Unloaded chunks answer "air", so nothing has to be asked about loading here.
                    if (mc.level.getBlockState(pos).is(target)) {
                        portal.add(pos.immutable());
                    }
                }
            }
        }
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : portal) {
            if (!seen.add(start)) {
                continue;
            }
            int minX = start.getX();
            int minY = start.getY();
            int minZ = start.getZ();
            int maxX = minX;
            int maxY = minY;
            int maxZ = minZ;
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos at = queue.poll();
                minX = Math.min(minX, at.getX());
                minY = Math.min(minY, at.getY());
                minZ = Math.min(minZ, at.getZ());
                maxX = Math.max(maxX, at.getX());
                maxY = Math.max(maxY, at.getY());
                maxZ = Math.max(maxZ, at.getZ());
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    BlockPos next = at.relative(d);
                    if (portal.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            PANES.add(new Pane(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    /** The block the id names, cached, with one warning if it names nothing. */
    private static Block resolve(String id) {
        String want = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (want.equals(cachedId)) {
            return cachedBlock;
        }
        cachedId = want;
        cachedBlock = null;
        Identifier rl = Identifier.tryParse(want.isEmpty() ? "minecraft:nether_portal" : want);
        if (rl != null) {
            Block b = BuiltInRegistries.BLOCK.getValue(rl);
            // The registry answers with air for an id it does not know, which is not a portal and
            // would otherwise paper over the whole sky.
            if (b != Blocks.AIR) {
                cachedBlock = b;
            }
        }
        if (cachedBlock == null && !warned) {
            warned = true;
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Portal Images: '{}' is not a block id", id);
        }
        return cachedBlock;
    }
}
