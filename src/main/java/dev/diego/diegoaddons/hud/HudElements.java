package dev.diego.diegoaddons.hud;

import com.render.api.HudLayoutElement;
import com.render.api.HudPlacement;
import com.render.api.ManagedHudLayout;
import com.render.api.RenderLibHud;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the mod's {@link ManagedHudLayout}: RenderLib holds the position, scale and opacity of every
 * HUD element, persists them itself, and supplies the placement screen that replaces the mod's own
 * HUD editor.
 *
 * <p>RenderLib freezes the top-level element list at registration, so every {@linkplain
 * HudModule#managedHud() managed} HUD module gets an element up front whether or not it is enabled;
 * being switched off just makes the element invisible. The retained tree inside each element stays
 * mutable, and {@link #tick} is what refreshes it - once per client tick, not per frame.
 *
 * <p>Elements a module has not been ported to yet ({@code managedHud() == false}) are left to the
 * old chip renderer and the old editor until they are.
 */
public final class HudElements {
    private static final String MOD_ID = "diegoaddonsv2";

    /** Where the first element sits, and how far apart the defaults stack, in design-space pixels. */
    private static final float DEFAULT_X = 8f;
    private static final float DEFAULT_TOP = 8f;
    private static final float DEFAULT_GAP = 4f;

    private record Entry(HudModule module, HudLayoutElement element, HudChip chip) {
    }

    private static ManagedHudLayout layout;
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private HudElements() {
    }

    /** Build and register the layout. Call once, after {@link ModuleManager#init()}. */
    public static void init() {
        if (layout != null) {
            return;
        }
        layout = new ManagedHudLayout(MOD_ID);

        for (Module m : ModuleManager.all()) {
            if (!(m instanceof HudModule hud) || !hud.managedHud()) {
                continue;
            }
            HudLayoutElement element = new HudLayoutElement(hud.id, defaultPlacement(hud));
            HudChip chip = hud.createChip(element.root());
            element.visible(false);
            layout.addElement(element);
            ENTRIES.put(hud.id, new Entry(hud, element, chip));
        }

        RenderLibHud.register(layout);
    }

    /** Running top edge for elements that have never been placed, so the defaults don't overlap. */
    private static float stackCursor = DEFAULT_TOP;

    /**
     * Where an element sits the first time it is seen. RenderLib only consults this while it has no
     * stored placement of its own, so a position the player already chose in the old HUD editor is
     * carried over here and owned by RenderLib from then on.
     *
     * <p>Unplaced elements stack down the left edge, each one stepped by its own height rather than
     * a fixed stride - a five-row readout is much taller than a clock, and a fixed stride buries one
     * under the other.
     */
    private static HudPlacement defaultPlacement(HudModule hud) {
        var cfg = ConfigManager.moduleConfig(hud.id);
        float scale = cfg.hudScale > 0f ? cfg.hudScale : 1f;
        if (cfg.hudX >= 0 && cfg.hudY >= 0) {
            return new HudPlacement(cfg.hudX, cfg.hudY, HudPlacement.clampScale(scale));
        }
        float y = stackCursor;
        stackCursor += estimatedHeight(hud) + DEFAULT_GAP;
        return new HudPlacement(DEFAULT_X, y, HudPlacement.clampScale(scale));
    }

    /** Roughly how tall a chip is, from the row count its sample content produces. */
    private static float estimatedHeight(HudModule hud) {
        int rows = 1;
        try {
            rows = Math.max(1, hud.editorLines(Minecraft.getInstance()).size());
        } catch (RuntimeException ignored) {
            // Live data is not reachable this early; one row is a safe guess.
        }
        return rows * (HudChip.TEXT_PX + HudChip.ROW_GAP) + HudChip.PAD_Y * 2f;
    }

    /** Refresh every managed element. Call once per client tick. */
    public static void tick(Minecraft mc) {
        if (layout == null) {
            return;
        }
        boolean hidden = mc.options != null && mc.options.hideGui;
        for (Entry e : ENTRIES.values()) {
            boolean show = !hidden && e.module().isEnabled() && e.chip().update(mc);
            e.element().visible(show);
        }
    }

    /** Whether this module's element is drawn by RenderLib rather than the old chip renderer. */
    public static boolean isManaged(HudModule hud) {
        return ENTRIES.containsKey(hud.id);
    }

    /** Opens RenderLib's placement screen - the replacement for the mod's own HUD editor. */
    public static void openPlacementScreen() {
        if (layout != null) {
            layout.openPlacementScreen();
        }
    }

    /** The managed modules, in registration order. */
    public static List<HudModule> managedModules() {
        List<HudModule> out = new ArrayList<>();
        for (Entry e : ENTRIES.values()) {
            out.add(e.module());
        }
        return out;
    }
}
