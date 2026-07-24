package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;

/**
 * Renders players without their worn armour. Purely client-side and cosmetic: the actual armour
 * layer is skipped during rendering, so equipped protection is unchanged - only the visual is
 * hidden. Two toggles pick whose armour to hide (your own, other players', or both).
 *
 * <p>The work happens in {@code HumanoidArmorLayerMixin}, which reads {@link #INSTANCE} each frame
 * to decide whether to cancel the armour layer for the player being drawn.
 */
public class ArmorHiderModule extends Module {
    /** Set on construction so the armour-layer mixin can read the live toggle state statically. */
    public static ArmorHiderModule INSTANCE;

    private final BooleanSetting self = new BooleanSetting(this, "self", "Hide own armor", false);
    private final BooleanSetting others = new BooleanSetting(this, "others", "Hide others' armor", true);

    public ArmorHiderModule() {
        super("armorhider", Category.RENDER, "Armor Hider", "Render players without their armor.");
        settings.add(self);
        settings.add(others);
        INSTANCE = this;
    }

    /**
     * @param isSelf whether the player being rendered is the local player
     * @return whether that player's armour should be hidden this frame
     */
    public boolean hides(boolean isSelf) {
        return isSelf ? self.get() : others.get();
    }
}
