package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.EspModule;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.Safari;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The one critter you must not walk past: a sparkling variant, and a shout when one appears.
 *
 * <p>A sparkling critter is a 1-in-8,192 spawn worth ten times the loot and a Rainbow Feather - a
 * level on any pet - so the cost of missing one is not "catch it next time", it is several hours.
 * That is the whole argument for this being its own module rather than a colour on the Critter ESP
 * card: it wants its own box, its own colour, and a notification, and it wants them whatever the
 * critter filters are set to.
 *
 * <p><b>It deliberately ignores the biome and rarity filters</b> next door. A sparkling Cavernfish
 * is still a Rainbow Feather even if you have hidden every common in the game.
 */
public class SparklingCritterModule extends EspModule {
    public static SparklingCritterModule INSTANCE;

    private final BooleanSetting notifyTitle =
            new BooleanSetting(this, "notifyTitle", "Title on screen", true);
    private final BooleanSetting notifyChat =
            new BooleanSetting(this, "notifyChat", "Message in chat", true);
    private final BooleanSetting notifySound =
            new BooleanSetting(this, "notifySound", "Play a sound", true);
    /**
     * How long before the same critter is announced again.
     *
     * <p>Not "announce once ever": a critter can leave view and come back, and the entity it is
     * carried on may be replaced under the same name, so once-ever risks the second sighting being
     * the silent one. A cooldown is the version that fails towards telling you twice.
     */
    private final NumberSetting repeat =
            new NumberSetting(this, "repeat", "Re-announce after (s)", 60, 5, 600, 5);
    /** Off by default; the box is usually enough, and a beam through a cave roof is not subtle. */
    private final BooleanSetting beam =
            new BooleanSetting(this, "beam", "Beam of light", false);

    // --- the particle route ------------------------------------------------------------------------

    /**
     * On by default, because it is the half of this module that finds things you cannot see.
     *
     * <p>A name plate does not exist until the mob is rendered; a particle arrives as a packet from
     * much further out and through terrain. For a 1-in-8,192 spawn the failure being designed
     * against is walking past one, and this is what stops that.
     */
    private final BooleanSetting particleEsp =
            new BooleanSetting(this, "particleEsp", "Find by particles", true);
    /**
     * Which particles count, comma-separated.
     *
     * <p>A text box rather than a constant because <b>nobody knows</b>: the wiki says "golden
     * particles" and names no id. Three likely ones are watched at once so a wrong guess is not a
     * dead feature, and {@link #particleDebug} is how the right one gets found.
     */
    private final StringSetting particleIds = new StringSetting(this, "particleIds",
            "Particle ids", "minecraft:end_rod,minecraft:firework,minecraft:crit", null);
    /**
     * How many particles in one spot before it is marked.
     *
     * <p>The whole defence against a wrong id: a sparkling critter trails particles continuously, so
     * a real source passes this in well under a second, while a stray crit from hitting a mob never
     * does. Lower it if trails are being missed, raise it if anything is being marked spuriously.
     */
    private final NumberSetting particleMin =
            new NumberSetting(this, "particleMin", "Particles before marking", 8, 1, 60, 1);
    private final NumberSetting particleSize =
            new NumberSetting(this, "particleSize", "Marker size", 1.5, 0.5, 5.0, 0.5);
    /**
     * Logs every particle type arriving on the island, with counts, every five seconds.
     *
     * <p>Here so the id above stops being a guess. Stand next to a sparkling critter with this on
     * and the answer is whichever type suddenly appears in the list.
     */
    private final BooleanSetting particleDebug =
            new BooleanSetting(this, "particleDebug", "Debug particles (log)", false);

    public SparklingCritterModule() {
        super("sparklingcritter", Category.SAFARI, "Sparkling Critters",
                "Box SPARKLING critters and shout when one turns up.",
                0xFFFFD54F);
        settings.add(notifyTitle);
        settings.add(notifyChat);
        settings.add(notifySound);
        settings.add(repeat);
        settings.add(beam);
        settings.add(particleEsp);
        settings.add(particleIds);
        settings.add(particleMin);
        settings.add(particleSize);
        settings.add(particleDebug);
        INSTANCE = this;
    }

    public boolean beam() {
        return beam.get();
    }

    public boolean particleEsp() {
        return particleEsp.get();
    }

    public String particleIds() {
        return particleIds.get();
    }

    public int particleMin() {
        return (int) particleMin.get();
    }

    public double particleSize() {
        return particleSize.get();
    }

    public boolean particleDebug() {
        return particleDebug.get();
    }

    /** The cooldown, in milliseconds. */
    public long repeatMs() {
        return (long) (repeat.get() * 1000);
    }

    /** Shouts about a sparkling critter on whichever channels are switched on. */
    public void notifyFound(Minecraft mc, String critter) {
        String name = critter == null ? "Critter" : critter;
        if (notifyTitle.get() && mc.gui != null) {
            mc.gui.setTitle(Component.literal("§6§lSPARKLING!"));
            mc.gui.setSubtitle(Component.literal("§e" + name + " §7nearby"));
        }
        if (notifyChat.get() && mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(
                    "§b[DiegoAddons] §6SPARKLING " + name + " §fnearby - 10x loot and a Rainbow Feather."));
        }
        if (notifySound.get() && mc.player != null) {
            // Two notes rather than one: a single ping is what half the mod already sounds like, and
            // this is the one alert on the island worth being able to tell apart with your back to
            // the screen.
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.2f);
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 2.0f);
        }
    }

    /** Whether this plate is a sparkling critter. Convenience, so the pass reads as one thought. */
    public static boolean sparkling(String plate) {
        return Safari.isSparkling(plate);
    }
}
