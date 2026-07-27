package dev.diego.diegoaddons.hud;

import com.render.api.HudLayoutElement;
import com.render.api.HudPlacement;
import com.render.api.ManagedHudLayout;
import com.render.api.RenderLibHud;
import com.render.api.RenderRegistration;
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
 * Owns the mod's {@link ManagedHudLayout}. RenderLib holds each element's position, scale and
 * opacity, persists them, and supplies the placement screen the mod's HUD editor used to.
 *
 * <p>RenderLib freezes an element list at registration, so the layout is rebuilt and re-registered
 * whenever the set of <em>enabled</em> HUD modules changes. That is deliberate: only elements you
 * actually have switched on appear in the placement screen, instead of a row of empty ghosts for
 * everything the mod could draw.
 */
public final class HudElements {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("DiegoAddonsV2");
    private static final String MOD_ID = "diegoaddonsv2";

    private static final float DEFAULT_X = 8f;
    private static final float DEFAULT_TOP = 8f;
    private static final float DEFAULT_GAP = 4f;

    private record Entry(HudModule module, HudLayoutElement element, HudElement content) {
    }

    private static ManagedHudLayout layout;
    private static RenderRegistration registration;
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    /**
     * Element trees, kept across re-registrations. Toggling one HUD module rebuilds the layout - the
     * list RenderLib holds is frozen at registration - but there is no reason for that to throw away
     * the component trees of every other element, so they are reused.
     */
    private static final Map<String, HudElement> CONTENT = new LinkedHashMap<>();
    private static final Map<String, HudLayoutElement> SHELLS = new LinkedHashMap<>();
    private static String registeredFor = "";

    private HudElements() {
    }

    /** Refresh every element. Call once per client tick. */
    public static void tick(Minecraft mc) {
        String enabled = enabledSignature();
        if (!enabled.equals(registeredFor)) {
            register(enabled);
        }
        boolean hidden = mc.options != null && mc.options.hideGui;
        for (Entry e : ENTRIES.values()) {
            e.element().visible(!hidden && e.content().update(mc));
        }
    }

    /** Rebuilds the layout so it holds exactly the enabled HUD modules, and registers it. */
    private static void register(String enabled) {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
        ENTRIES.clear();
        layout = new ManagedHudLayout(MOD_ID);

        float cursor = DEFAULT_TOP;
        for (Module m : ModuleManager.all()) {
            if (!(m instanceof HudModule hud) || !hud.isEnabled() || !hud.managedHud()) {
                continue;
            }
            HudLayoutElement element = SHELLS.get(hud.id);
            HudElement content = CONTENT.get(hud.id);
            if (element == null || content == null) {
                element = new HudLayoutElement(hud.id, placement(hud, cursor));
                content = hud.createElement(element.root());
                SHELLS.put(hud.id, element);
                CONTENT.put(hud.id, content);
            }
            cursor += estimatedHeight(hud) + DEFAULT_GAP;
            element.visible(false);
            layout.addElement(element);
            ENTRIES.put(hud.id, new Entry(hud, element, content));
        }

        registration = RenderLibHud.register(layout);
        registeredFor = enabled;
    }

    /**
     * Where an element sits until RenderLib has a stored placement of its own. A position chosen in
     * the mod's old HUD editor is carried across; anything never placed stacks down the left edge,
     * stepped by its own height so a tall readout does not bury the next one.
     */
    private static HudPlacement placement(HudModule hud, float stackTop) {
        var cfg = ConfigManager.moduleConfig(hud.id);
        float scale = HudPlacement.clampScale(cfg.hudScale > 0f ? cfg.hudScale : 1f);
        if (cfg.hudX >= 0 && cfg.hudY >= 0) {
            return new HudPlacement(cfg.hudX, cfg.hudY, scale);
        }
        return new HudPlacement(DEFAULT_X, stackTop, scale);
    }

    private static float estimatedHeight(HudModule hud) {
        int rows = 1;
        try {
            rows = Math.max(1, hud.editorLines(Minecraft.getInstance()).size());
        } catch (RuntimeException ignored) {
            // No live data this early; one row is a safe guess.
        }
        return rows * HudElement.ROW_H + HudElement.PAD_Y * 2f;
    }

    /** Which HUD modules are on, as a string - the layout is re-registered when this changes. */
    private static String enabledSignature() {
        StringBuilder sb = new StringBuilder();
        for (Module m : ModuleManager.all()) {
            if (m instanceof HudModule hud && hud.managedHud() && hud.isEnabled()) {
                sb.append(hud.id).append(',');
            }
        }
        return sb.toString();
    }

    /**
     * Puts every element back where it started: RenderLib's stored placements are deleted and the
     * layout is registered again, so each element falls back to its default position and scale.
     */
    public static void resetPositions() {
        for (Module m : ModuleManager.all()) {
            if (m instanceof HudModule hud && hud.managedHud()) {
                var cfg = ConfigManager.moduleConfig(hud.id);
                cfg.hudX = -1;
                cfg.hudY = -1;
            }
        }
        ConfigManager.save();
        try {
            java.nio.file.Path file = net.minecraft.client.Minecraft.getInstance().gameDirectory
                    .toPath().resolve("config").resolve("render-lib").resolve("hud-layouts")
                    .resolve(MOD_ID + ".json");
            java.nio.file.Files.deleteIfExists(file);
        } catch (java.io.IOException | RuntimeException e) {
            // RenderLib owns that file and offers no API to clear it, so this is the only way in.
            // If it ever moves or gets cached in memory, reset would silently stop working - so say
            // so rather than leave you wondering why your elements came back.
            LOGGER.warn("[DiegoAddons V2] Could not clear the stored HUD placements", e);
        }
        SHELLS.clear();       // defaults are recomputed, so the shells have to be rebuilt
        CONTENT.clear();
        registeredFor = "";   // forces a fresh registration on the next tick
    }

    /** Opens RenderLib's placement screen - the replacement for the mod's own HUD editor. */
    public static void openPlacementScreen() {
        if (layout != null) {
            layout.openPlacementScreen();
        }
    }

    /** The modules currently drawn by the managed layout. */
    public static List<HudModule> managedModules() {
        List<HudModule> out = new ArrayList<>();
        for (Entry e : ENTRIES.values()) {
            out.add(e.module());
        }
        return out;
    }
}
