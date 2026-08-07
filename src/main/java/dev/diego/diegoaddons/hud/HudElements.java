package dev.diego.diegoaddons.hud;

import dev.diego.configlib.ConfigHandle;
import dev.diego.configlib.hud.HudPos;
import dev.diego.configlib.hud.HudTemplates;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The HUD, drawn by configlib.
 *
 * <p>Every {@link HudModule} already answers "what should this show right now" through
 * {@link HudModule#hudLine}, and always did - that half survived the move off RenderLib untouched.
 * What changed is who draws it: instead of each element building a component tree, a module is
 * handed to one of configlib's prefabs and the library owns placement, scale, the editor and the
 * saving of where things sit.
 *
 * <p>Positions live here rather than in {@code ModuleConfig}. The old {@code hudX}/{@code hudY} were
 * whole GUI pixels from a fixed corner; a {@link HudPos} is a fraction of the screen plus an anchor,
 * which is what keeps an element in the same visual place when the window is resized. Translating
 * between the two would preserve nothing worth preserving, so the new placements are simply new -
 * configlib persists them in its own file.
 */
public final class HudElements {

    /** One live placement per module id. The editor mutates these in place. */
    private static final Map<String, HudPos> POSITIONS = new LinkedHashMap<>();

    private HudElements() {
    }

    /**
     * The placement for a module, created on first ask.
     *
     * <p>Elements start stacked down the top-left rather than on top of each other, so switching
     * several on and opening the editor gives a column to drag apart rather than one chip with the
     * rest hidden underneath it.
     */
    public static HudPos position(String moduleId) {
        return POSITIONS.computeIfAbsent(moduleId,
                id -> HudPos.of(0.01, 0.01 + POSITIONS.size() * 0.03));
    }

    /** Declares every HUD module to the spec. Called while the spec is being built. */
    public static void declare(dev.diego.configlib.core.SpecBuilder b, HudModule m) {
        b.hud(m.id + ".hud", m.name, "Where this sits on screen", () -> position(m.id), true);
    }

    /**
     * Attaches the drawing half, once the handle exists.
     *
     * <p>{@code labelValue} rather than a plain line because the module already keeps the caption
     * and the value apart, and the prefab aligns them the way every other element on the HUD is
     * aligned - which a single pre-joined string cannot do.
     */
    public static void attach(ConfigHandle<?> handle) {
        for (Module module : ModuleManager.all()) {
            if (!(module instanceof HudModule m)) {
                continue;
            }
            handle.hud(m.id + ".hud", HudTemplates.labelValue()
                    .label(m.showLabel() ? m.hudLabel() : "")
                    .value(() -> {
                        Minecraft mc = Minecraft.getInstance();
                        String v = m.hudValue(mc);
                        return v == null ? "" : v;
                    }));
        }
    }

    /** Whether a module currently has anything to show, for the element's own visibility. */
    public static boolean hasContent(HudModule m) {
        return m.isEnabled() && m.hudLine(Minecraft.getInstance()) != null;
    }
}
