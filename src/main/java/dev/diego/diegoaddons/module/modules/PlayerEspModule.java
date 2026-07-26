package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Boxes real players, filtering out the many SkyBlock NPCs that wear player models. The filter lives
 * in {@link dev.diego.diegoaddons.util.EntityEsp}: a real player has a version-4 account UUID and a
 * tab-list entry, which the version-2, unlisted NPC "players" do not.
 */
public class PlayerEspModule extends Module {
    public static PlayerEspModule INSTANCE;

    public PlayerEspModule() {
        super("playeresp", Category.RENDER, "Player ESP", "Box real players, hiding NPCs.");
        INSTANCE = this;
    }

    public int color() {
        return 0xFF44AAFF;   // blue
    }
}
