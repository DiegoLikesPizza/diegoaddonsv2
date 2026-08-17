package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.LevelColor;

/**
 * Picks which of your unlocked SkyBlock level colours the badge in front of your name is drawn in.
 *
 * <p>Hypixel hands out a new colour every 40 levels to 480 and you wear whichever you passed last.
 * The picker lists all thirteen, and a colour above your level is refused at draw time rather than
 * hidden here: the menu is built once at startup and your level is not known then, while the badge
 * being drawn always carries it. So the list never lies about what exists, and the badge never shows
 * a colour you have not earned.
 *
 * <p>Local only - the badge is recoloured as it is drawn, so nobody else sees your pick.
 */
public class LevelColorModule extends Module {
    public static LevelColorModule INSTANCE;

    /** Index 0 is "leave it alone"; every option after it is a tier, in unlock order. */
    private static final int OFF = 0;

    private final CycleSetting colour;

    public LevelColorModule() {
        super("levelcolor", Category.MISC, "Level Colour",
                "Draw your SkyBlock level badge in a colour you have already unlocked.");
        String[] options = new String[LevelColor.tiers() + 1];
        options[OFF] = "Hypixel's own";
        System.arraycopy(LevelColor.NAMES, 0, options, 1, LevelColor.tiers());
        colour = new CycleSetting(this, "colour", "Colour", OFF, options);
        settings.add(colour);
        INSTANCE = this;
    }

    /** The picked tier, or -1 while the badge is to be left as Hypixel drew it. */
    public int picked() {
        return colour.get() - 1;
    }
}
