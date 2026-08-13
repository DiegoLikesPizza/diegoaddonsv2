package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.util.Hideyho;
import net.minecraft.client.Minecraft;

/**
 * Marks every place Hideyho can be hiding, and strikes each one off as you check it.
 *
 * <p>Not an ESP, which is why it is a plain module: there is nothing to box. Hideyho teleports out
 * of view the moment it agrees to play, so what the feature draws is a list of <i>candidate</i>
 * locations, and the useful state is which of them you have already ruled out. See {@link Hideyho}.
 */
public class HideyhoFinderModule extends Module {
    public static HideyhoFinderModule INSTANCE;

    private final ColorSetting color =
            new ColorSetting(this, "color", "Colour", 0xFFAB47BC);
    private final BooleanSetting beams =
            new BooleanSetting(this, "beams", "Beams", true);
    private final BooleanSetting labels =
            new BooleanSetting(this, "labels", "Names and distance", true);
    private final BooleanSetting announce =
            new BooleanSetting(this, "announce", "Message in chat", true);
    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Sound when it hides", true);
    /**
     * Off by default. Once you know where it stands, six permanent markers in the Haunted biome are
     * clutter; they are here for the first visit, when the problem is finding it at all.
     */
    private final BooleanSetting showStarts =
            new BooleanSetting(this, "showStarts", "Mark its start positions", false);
    /**
     * How close counts as "checked".
     *
     * <p>Generous by default: you see a corner of a room from further than you can stand in it, and
     * a spot struck off too early is only a spot you walk back to, while one struck off too late is
     * a marker in your face for the whole round.
     */
    private final NumberSetting strikeRadius =
            new NumberSetting(this, "strikeRadius", "Checked within (blocks)", 8, 2, 20, 1);
    private final NumberSetting range =
            new NumberSetting(this, "range", "Draw within (blocks)", 120, 20, 300, 10);

    public HideyhoFinderModule() {
        super("hideyhofinder", Category.SAFARI, "Hideyho Finder",
                "Mark every hiding spot when Hideyho hides, and cross them off as you check them.");
        settings.add(color);
        settings.add(beams);
        settings.add(labels);
        settings.add(announce);
        settings.add(sound);
        settings.add(showStarts);
        settings.add(strikeRadius);
        settings.add(range);
        INSTANCE = this;
    }

    public int color() {
        return color.argb();
    }

    public boolean beams() {
        return beams.get();
    }

    public boolean labels() {
        return labels.get();
    }

    public boolean announce() {
        return announce.get();
    }

    public boolean sound() {
        return sound.get();
    }

    public boolean showStarts() {
        return showStarts.get();
    }

    public double strikeRadius() {
        return strikeRadius.get();
    }

    public double range() {
        return range.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        Hideyho.tick(mc, this);
    }

    @Override
    protected void onDisable() {
        Hideyho.reset();
    }
}
