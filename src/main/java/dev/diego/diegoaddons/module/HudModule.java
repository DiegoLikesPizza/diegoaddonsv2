package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * A module that draws a single line of text as a themed HUD chip. Subclasses supply a label and a
 * live value; {@link ModuleManager} handles the drawing and positioning. Colour and label are
 * driven live by two {@link BooleanSetting}s exposed in the ClickGUI.
 */
public abstract class HudModule extends Module {
    protected final BooleanSetting accentColour = new BooleanSetting(this, "accentColour", "Accent colour", true);
    protected final BooleanSetting showLabel = new BooleanSetting(this, "showLabel", "Show label", true);
    protected final BooleanSetting centered = new BooleanSetting(this, "centered", "Centered", defaultCentered());

    /**
     * Whether this element's text starts out centred in its chip rather than left-aligned. Worth
     * turning on for purely numeric elements: their chip is sized to the widest digit glyph so it
     * cannot jitter as the value changes, which leaves slack that would otherwise all sit on the
     * right. Overridden per module; the user can still flip it in the ClickGUI.
     */
    protected boolean defaultCentered() {
        return false;
    }

    /** Whether to centre the chip's text this frame. */
    public boolean isCentered() {
        return centered.get();
    }

    protected HudModule(String id, String name, String description) {
        this(id, name, description, true);
    }

    /**
     * @param textSettings when {@code false}, the {@code Accent colour} / {@code Show label} toggles
     *                     are not added - for custom-drawn HUD elements (e.g. the inventory grid)
     *                     where they don't apply.
     */
    protected HudModule(String id, String name, String description, boolean textSettings) {
        super(id, Category.HUD, name, description);
        if (textSettings) {
            settings.add(accentColour);
            settings.add(showLabel);
            settings.add(centered);
        }
    }

    public int color() {
        return accentColour.get() ? Themes.current().accent() : Themes.current().text();
    }

    /** The label shown before the value when enabled (e.g. "FPS"). */
    protected abstract String label();

    /** The live value (e.g. "60"), or {@code null} to hide the chip this frame. */
    protected abstract String value(Minecraft mc);

    /** The full HUD line, or {@code null} to skip. */
    public String hudLine(Minecraft mc) {
        String v = value(mc);
        if (v == null) {
            return null;
        }
        return showLabel.get() ? label() + ": " + v : v;
    }

    /** A representative value used by the HUD editor when live data is unavailable (e.g. no world). */
    protected String sampleValue() {
        return "—";
    }

    /** Like {@link #hudLine}, but never null - falls back to {@link #sampleValue()} for the editor. */
    public String editorLine(Minecraft mc) {
        String v = value(mc);
        if (v == null) {
            v = sampleValue();
        }
        return showLabel.get() ? label() + ": " + v : v;
    }

    // --- Multi-line support (a chip may show several stacked rows; single-line by default) ---

    /** The live lines to draw as a chip, or an empty list to hide the chip this frame. */
    public List<String> hudLines(Minecraft mc) {
        String l = hudLine(mc);
        return l == null ? List.of() : List.of(l);
    }

    /** Like {@link #hudLines}, but never empty - used by the HUD editor so the chip is always draggable. */
    public List<String> editorLines(Minecraft mc) {
        List<String> live = hudLines(mc);
        return live.isEmpty() ? List.of(editorLine(mc)) : live;
    }

    // --- Generalised element footprint + drawing (default: the text chip; overridable for grids) ---

    /** Unscaled element width in GUI pixels. */
    public int hudWidth(Font font, Minecraft mc, boolean editor) {
        return ModuleManager.chipWidth(font, editor ? editorLines(mc) : hudLines(mc));
    }

    /** Unscaled element height in GUI pixels. */
    public int hudHeight(Minecraft mc, boolean editor) {
        return ModuleManager.chipHeight(editor ? editorLines(mc) : hudLines(mc));
    }

    /**
     * Draw this element at the local origin {@code (0, 0)}; the caller has already translated to the
     * element position and applied the scale. Return {@code false} if there is nothing to draw this
     * frame (so the live HUD skips it). The default renders the text chip.
     */
    public boolean drawLocal(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth, Minecraft mc, boolean editor) {
        List<String> lines = editor ? editorLines(mc) : hudLines(mc);
        if (lines.isEmpty()) {
            return false;
        }
        ModuleManager.drawTextChipLocal(g, font, t, smooth, this, lines);
        return true;
    }
}
