package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HuntingEspModule;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.StringSetting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;

import java.util.Locale;

/**
 * The Torrhus Canyon hunting mobs, nine of them, each with its own switch.
 *
 * <p>One card rather than nine, at Diego's ask, and it suits them: unlike the Galatea critters -
 * which are ordinary animals you might want boxed one at a time in different colours - these are all
 * the same job on the same island, and what you actually change between runs is <i>which of them you
 * are still after</i>. That is a row of toggles, not nine cards.
 *
 * <p><b>Two ways in, because neither covers all nine.</b> Seven are vanilla animals wearing a
 * SkyBlock name, so they are matched by entity type - the only thing that works when a mob carries
 * no plate. The other two are not vanilla mobs at all: a Grizzly Bear is a custom level-101 mob and
 * a Tiki is a totem of three rotating heads, so those are matched by their name plate. Anything
 * matched either way is boxed once; see {@link dev.diego.diegoaddons.util.Hunting}.
 */
public class TorrhusEspModule extends HuntingEspModule {
    /** One mob: its plate name, the vanilla class it wears (or null), and its switch. */
    private record Mob(String name, Class<?> type, BooleanSetting on) {
    }

    private final BooleanSetting firefox =
            new BooleanSetting(this, "firefox", "Firefox", true);
    private final BooleanSetting mountainGoat =
            new BooleanSetting(this, "mountainGoat", "Mountain Goat", true);
    private final BooleanSetting drybark =
            new BooleanSetting(this, "drybark", "Drybark", true);
    /**
     * The Grizzly Bear wears a player model, so it is found as a fake player rather than by type.
     *
     * <p>With no skin id set this boxes every player-model mob on the island, NPCs included - the
     * row says so, because over-boxing you can see beats a switch that silently does nothing.
     */
    private final BooleanSetting grizzlyBear =
            new BooleanSetting(this, "grizzlyBear", "Grizzly Bear (player model)", true);
    /** Empty means "any player-model mob". Paste the id out of the debug log to make it exact. */
    private final StringSetting grizzlySkin =
            new StringSetting(this, "grizzlySkin", "Grizzly skin id (optional)", "", null);
    private final BooleanSetting groundhog =
            new BooleanSetting(this, "groundhog", "Groundhog", true);
    /**
     * Honeybuzz is a bee, and so are Beeheemoth and Pollendart.
     *
     * <p>Nothing on the entity separates them, so with this on every Torrhus bee is boxed unless it
     * happens to carry a plate that names it. Said plainly on the row rather than left as a
     * surprise: the alternative is silently missing the Honeybuzz you turned it on for.
     */
    private final BooleanSetting honeybuzz =
            new BooleanSetting(this, "honeybuzz", "Honeybuzz (boxes every bee)", true);
    private final BooleanSetting pangolin =
            new BooleanSetting(this, "pangolin", "Pangolin", true);
    private final BooleanSetting blueJay =
            new BooleanSetting(this, "blueJay", "Blue Jay", true);
    /**
     * Every Tiki - Sneaky, Cheeky and Shrieky share a bestiary, a puzzle and all 24 spawn points.
     *
     * <p><b>A sleeping Tiki has no name plate</b>, which is why the plate match alone found nothing:
     * it is a totem of three heads until you turn them to face the same way, and only the mob that
     * wakes up out of it carries a name. The plate is therefore the wrong half of the problem - by
     * the time it exists the thing is already awake and attacking you. So this marks the 24
     * documented totem spots instead, and keeps the plate match for the awakened mob.
     */
    private final BooleanSetting tiki =
            new BooleanSetting(this, "tiki", "Tiki (marks the 24 totem spots)", true);
    private final NumberSetting tikiRange =
            new NumberSetting(this, "tikiRange", "Tiki spots within (blocks)", 96, 20, 300, 10);
    private final BooleanSetting tikiLabels =
            new BooleanSetting(this, "tikiLabels", "Name and distance on Tiki spots", true);
    /**
     * Logs what is actually standing around you, every five seconds: entity type, count, and any
     * name plate.
     *
     * <p>Here because this module has now been wrong twice for the same reason - the wiki says what
     * a mob <i>is</i> and not what the client is handed, and a mob with no plate is invisible to a
     * plate match while a mob of an unexpected type is invisible to a type match. One walk around
     * Torrhus with this on maps all nine definitively, which is worth more than a fourth guess.
     */
    private final BooleanSetting debug =
            new BooleanSetting(this, "debug", "Debug entities (log)", false);

    private final Mob[] mobs;

    public TorrhusEspModule() {
        super("torrhusesp", "Torrhus ESP",
                "Box the Torrhus Canyon hunting mobs, each one switchable.",
                0xFFFF9800, "Torrhus Canyon", TORRHUS);
        settings.add(firefox);
        settings.add(mountainGoat);
        settings.add(drybark);
        settings.add(grizzlyBear);
        settings.add(grizzlySkin);
        settings.add(groundhog);
        settings.add(honeybuzz);
        settings.add(pangolin);
        settings.add(blueJay);
        settings.add(tiki);
        settings.add(tikiRange);
        settings.add(tikiLabels);
        settings.add(debug);
        // Built after the settings so each row is the one the entry points at. Null type means the
        // mob is not a vanilla entity and can only be found by its plate.
        mobs = new Mob[] {
                new Mob("Firefox", Fox.class, firefox),
                new Mob("Mountain Goat", Goat.class, mountainGoat),
                new Mob("Drybark", Creaking.class, drybark),
                // No class: a Grizzly Bear wears a player model, which no Class check can express
                // safely - matching AbstractClientPlayer would box every player in the lobby. It is
                // handled by isGrizzly() instead.
                new Mob("Grizzly Bear", null, grizzlyBear),
                new Mob("Groundhog", Hoglin.class, groundhog),
                new Mob("Honeybuzz", Bee.class, honeybuzz),
                new Mob("Pangolin", Armadillo.class, pangolin),
                new Mob("Blue Jay", Parrot.class, blueJay),
                new Mob("Tiki", null, tiki),
        };
    }

    /**
     * The 24 Tiki totem spots, x/y/z flattened.
     *
     * <p>Off the wiki, and shared by all three Tikis - the Cheeky page lists the same coordinates as
     * the Sneaky one, so this is one set of totem sites and which Tiki is standing on it varies.
     * They cover both Torrhus Canyon and Torrhus Heights, which is why the island gate is just
     * "Torrhus".
     */
    private static final int[] TIKI_SPOTS = {
            596, 128, 284, 735, 128, 268, 729, 143, 179, 731, 134, 153,
            600, 137, 153, 614, 124, 281, 667, 133, 263, 674, 143, 146,
            591, 139, 175, 661, 125, 245, 687, 131, 219, 573, 136, 184,
            587, 152, 239, 625, 133, 283, 700, 132, 288, 714, 135, 290,
            742, 136, 231, 637, 142, 161, 731, 130, 172, 759, 125, 245,
            560, 144, 191, 544, 139, 208, 545, 131, 213, 603, 172, 231,
    };

    /**
     * Draws the totem spots. The rest of this module is handled by the shared entity pass.
     *
     * <p>Nothing is struck off as you visit it, unlike the Hideyho spots: a Tiki totem is a fixture
     * that respawns, not one hidden thing in one of eleven places, so "already checked" is not a
     * state that means anything here.
     */
    @Override
    public void onClientTick(net.minecraft.client.Minecraft mc) {
        if (mc.player == null || !shouldDraw(mc)) {
            return;
        }
        if (debug.get()) {
            logNearby(mc);
        }
        if (!tiki.get()) {
            return;
        }
        double range = tikiRange.get();
        net.minecraft.world.phys.Vec3 me = mc.player.position();
        for (int i = 0; i < TIKI_SPOTS.length; i += 3) {
            int x = TIKI_SPOTS[i];
            int y = TIKI_SPOTS[i + 1];
            int z = TIKI_SPOTS[i + 2];
            double dist = me.distanceTo(new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5));
            if (dist > range) {
                continue;
            }
            // Three heads tall, and drawn from one below the listed y: the coordinates are somebody's
            // F3 readout and it is not stated whether they took it at the base or at a head, so the
            // box is deliberately generous enough to contain the totem either way.
            dev.diego.diegoaddons.util.WorldRender.thickBox(
                    new net.minecraft.world.phys.AABB(x, y - 1, z, x + 1, y + 3, z + 1),
                    color(), 0.05, true);
            if (tikiLabels.get()) {
                dev.diego.diegoaddons.util.WorldRender.text("Tiki §7(" + (int) dist + "m)",
                        new net.minecraft.world.phys.Vec3(x + 0.5, y + 3.4, z + 0.5), 1.0f);
            }
        }
    }

    private long lastLog;

    /**
     * Prints every entity type within 32 blocks, with counts and any name plates.
     *
     * <p>Name plates are listed separately and in full, colour codes stripped, because the two
     * questions this answers are different: "what type is that mob" fixes a type match, and "what
     * does its plate say" fixes a plate one. Armour stands are reported only when they carry a name,
     * since the island is full of nameless ones holding up scenery.
     */
    private void logNearby(net.minecraft.client.Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastLog < 5000 || mc.level == null) {
            return;
        }
        lastLog = now;
        java.util.Map<String, Integer> types = new java.util.TreeMap<>();
        java.util.List<String> plates = new java.util.ArrayList<>();
        java.util.Set<String> skins = new java.util.LinkedHashSet<>();
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(32))) {
            String type = net.minecraft.world.entity.EntityType.getKey(e.getType()).toString();
            types.merge(type, 1, Integer::sum);
            if (e.hasCustomName() && e.getCustomName() != null) {
                String plate = dev.diego.diegoaddons.util.LegacyText.strip(
                        e.getCustomName().getString()).trim();
                if (!plate.isEmpty() && plates.size() < 20) {
                    plates.add(type + " -> \"" + plate + "\"");
                }
            }
            // The skins of the fake players, which is the one thing that can tell a Grizzly Bear
            // from the island's NPCs. Real players are left out - their skins are nobody's business
            // and they are never the mob being looked for.
            if (e instanceof AbstractClientPlayer p
                    && !dev.diego.diegoaddons.util.EntityEsp.isRealPlayer(mc, p)
                    && skins.size() < 12) {
                String skin = skinOf(p);
                skins.add(dev.diego.diegoaddons.util.LegacyText.strip(p.getName().getString())
                        + " -> " + (skin == null ? "?" : skin));
            }
        }
        dev.diego.diegoaddons.DiegoAddonsV2Client.LOGGER.info(
                "[torrhus] entities within 32: {}", types);
        if (!plates.isEmpty()) {
            dev.diego.diegoaddons.DiegoAddonsV2Client.LOGGER.info(
                    "[torrhus] name plates: {}", String.join(" | ", plates));
        }
        if (!skins.isEmpty()) {
            dev.diego.diegoaddons.DiegoAddonsV2Client.LOGGER.info(
                    "[torrhus] player-model mobs: {}", String.join(" | ", skins));
        }
    }

    @Override
    public boolean matches(Entity e) {
        for (Mob m : mobs) {
            if (m.type() != null && m.on().get() && m.type().isInstance(e)) {
                return true;
            }
        }
        return grizzlyBear.get() && isGrizzly(e);
    }

    /**
     * The Grizzly Bear, which Diego reports wears a <b>player model</b>.
     *
     * <p>That rules out a type match outright: the class is the same one every real player in the
     * lobby has, so matching it would box the lobby. What separates them is that a SkyBlock mob
     * wearing a player model is a <i>fake</i> player - spawned client-side with a version-2 UUID and
     * no tab-list entry - and {@code EntityEsp.isRealPlayer} already knows that difference, because
     * Player ESP has needed it since it was written.
     *
     * <p>That alone still catches the island's NPCs, since they are fake players too. So the skin is
     * the second filter, and it is a <b>text box</b> rather than a constant because a skin hash is
     * not something to guess: leave it empty and every player-model mob is boxed, which works and
     * over-boxes; paste the id out of "Debug entities (log)" and it boxes exactly the bear.
     */
    private boolean isGrizzly(Entity e) {
        if (!(e instanceof AbstractClientPlayer p)) {
            return false;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (dev.diego.diegoaddons.util.EntityEsp.isRealPlayer(mc, p)) {
            return false;
        }
        String want = grizzlySkin.get().trim();
        if (want.isEmpty()) {
            return true;
        }
        String skin = skinOf(p);
        return skin != null && skin.toLowerCase(Locale.ROOT).contains(want.toLowerCase(Locale.ROOT));
    }

    /** The texture a player entity is wearing, as a path, or null when it cannot be read. */
    private static String skinOf(AbstractClientPlayer p) {
        try {
            var body = p.getSkin().body();
            return body == null || body.texturePath() == null ? null : body.texturePath().toString();
        } catch (RuntimeException ex) {
            // A skin that has not resolved yet is not worth a crash in an ESP pass.
            return null;
        }
    }

    @Override
    public boolean matchesPlate(String plate) {
        String haystack = plate.toLowerCase(Locale.ROOT);
        for (Mob m : mobs) {
            // Every mob is checked by plate, not only the two without a type: a plate is the one
            // thing that says which bee this is, and it costs a string search either way.
            if (m.on().get() && haystack.contains(m.name().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
