package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.SkinChanger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Replaces player skins with your own PNG images. Drop skin files into the folder printed in chat
 * when the module is enabled ({@code <config>/diegoaddons/skins/}):
 *
 * <ul>
 *   <li>{@code _all.png} - one skin for every other player (toggle "Replace all others"),</li>
 *   <li>{@code <PlayerName>.png} - replace a single player; always applied when the module is on,</li>
 *   <li>{@code _self.png} - your own skin (toggle "Replace own skin").</li>
 * </ul>
 *
 * <p>Purely visual and client-side. A per-player file always takes priority over the global one.
 * The swap happens in {@code AvatarRendererMixin}, which reads {@link #INSTANCE} each frame. Files
 * are cached, so add or edit a PNG and re-toggle the module to reload them.
 */
public class SkinChangerModule extends Module {
    /** Set on construction so the renderer mixin can read the live toggle state statically. */
    public static SkinChangerModule INSTANCE;

    private final BooleanSetting self = new BooleanSetting(this, "self", "Replace own skin", false);
    private final BooleanSetting others = new BooleanSetting(this, "others", "Replace all others", true);

    public SkinChangerModule() {
        super("skinchanger", Category.RENDER, "Skin Changer",
                "Replace player skins with your own PNG files.");
        settings.add(self);
        settings.add(others);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        SkinChanger.reload();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(
                    "§b[DiegoAddons] §fSkins folder: §e" + SkinChanger.folder() + "\n"
                            + "§fDrop §e<PlayerName>.png§f, §e_all.png§f (all others) or §e_self.png§f (you), "
                            + "then re-toggle this module to reload."));
        }
    }

    /**
     * The replacement skin texture for a player being rendered, or {@code null} to keep vanilla.
     *
     * @param isSelf whether the player is the local player
     * @param name   the player's name, or {@code null} if it could not be resolved
     */
    public Identifier skinFor(boolean isSelf, String name) {
        if (isSelf) {
            return self.get() ? SkinChanger.get("_self") : null;
        }
        // A per-player file always wins, so individual overrides work even with the global toggle off.
        if (name != null) {
            Identifier specific = SkinChanger.get(name);
            if (specific != null) {
                return specific;
            }
        }
        return others.get() ? SkinChanger.get("_all") : null;
    }
}
