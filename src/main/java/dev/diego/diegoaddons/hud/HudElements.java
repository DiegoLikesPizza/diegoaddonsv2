package dev.diego.diegoaddons.hud;

import dev.diego.configlib.ConfigHandle;
import dev.diego.configlib.hud.HudPos;
import dev.diego.configlib.hud.HudStyle;
import dev.diego.configlib.hud.HudTemplates;
import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
     * <p>An element that draws something other than text supplies its own widget through
     * {@link HudModule#hudWidget()}; everything else falls back to {@code labelValue}. The fallback
     * is what lets the custom elements be written one at a time without the rest of the HUD going
     * dark in between - a module with no widget yet is still placed, still draggable and still shows
     * its value, just as plain text.
     *
     * <p>Text elements draw through {@link TextChip} rather than configlib's {@code labelValue}
     * prefab: the prefab is one caption and one value, read once, which cannot show a module that
     * builds several rows and cannot notice a setting being changed. See that class.
     */
    public static void attach(ConfigHandle<?> handle) {
        for (Module module : ModuleManager.all()) {
            // Not placeable means no position was declared, and asking configlib to attach a widget
            // to a field that does not exist is an exception rather than a no-op.
            if (!(module instanceof HudModule m) || !m.placeable()) {
                continue;
            }
            // One switch, not two. configlib's placement row carries its own visibility toggle,
            // and a module already is a thing you turn on - so the row was a second switch beside
            // the module's, free to disagree with it and leave an element invisible with the module
            // still saying it was on. Both now drive the module.
            handle.spec().hudNode(m.id + ".hud").ifPresent(node ->
                    node.bindEnabled(m::isEnabled, v -> ModuleManager.setEnabled(m, v)));

            HudWidget custom = m.hudWidget();
            // Per element rather than leaving it on the handle's shared style: the module decides
            // whether it is following the theme or its own override, and answering that per frame
            // is what makes the switch take effect while the menu is open.
            handle.hud(m.id + ".hud", custom != null ? custom : new TextChip(m).style(m::style));
        }
    }

    /**
     * The look every prefab-drawn element follows - the mod's theme, expressed as a {@link HudStyle}.
     *
     * <p>Without this the elements split in two: the seven that draw themselves read the theme
     * directly, while the ones on configlib's {@code labelValue} prefab drew in the library's own
     * white-on-dark. Two elements side by side on the same HUD in different palettes is not a
     * theme, and this is the whole of the fix - configlib re-reads the style every frame, so it
     * follows a theme change live.
     */
    public static HudStyle sharedStyle() {
        Theme t = Themes.current();
        // The value in the accent and the caption muted, which is what the old per-element "Accent
        // colour" toggle produced with its default on. That toggle is gone - one colour control per
        // element, under Custom appearance - so the default it used to give is the default here.
        // mutedColor after textColor: the builder derives a muted shade from the text colour, so
        // setting them the other way round throws this one away.
        return HudStyle.builder()
                .textColor(t.accent())
                .mutedColor(t.textMuted())
                .accentColor(t.accent())
                .plateColor((0xCC << 24) | (t.surface() & 0x00FFFFFF))
                .plateRadius(6)
                .build();
    }

    /**
     * The panel a custom-drawn element sits on, in that element's own style.
     *
     * <p>The seven elements that draw themselves cannot go through {@code HudTemplate}, which is
     * what draws the plate for the prefabs - so this is their equivalent. Going through it rather
     * than each calling {@code fillRounded} with its own colour is what keeps "background off" and
     * a custom plate opacity meaning the same thing on every element.
     */
    public static void panel(GuiGraphicsExtractor g, HudModule m, int w, int h, int radius,
                             boolean smooth) {
        HudStyle s = m.style();
        if (!s.plate()) {
            return;
        }
        UiRender.fillRounded(g, 0, 0, w, h, radius, s.plateColor(), smooth);
    }

    /** Whether a module currently has anything to show, for the element's own visibility. */
    public static boolean hasContent(HudModule m) {
        return m.isEnabled() && m.hudLine(Minecraft.getInstance()) != null;
    }
}
