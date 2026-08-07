package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.gui.Themes;
import net.minecraft.client.Minecraft;

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

    /** Whether the caption is drawn before the value. */
    public boolean showLabel() {
        return showLabel.get();
    }

    /**
     * The caption, for whatever is drawing this element.
     *
     * <p>{@link #label()} and {@link #value} are protected because they are a module's own business
     * to define; these two are how the HUD layer asks for them.
     */
    public String hudLabel() {
        return label();
    }

    /** The live value, or null to show nothing this frame. */
    public String hudValue(Minecraft mc) {
        return value(mc);
    }

    protected HudModule(String id, String name, String description) {
        this(id, name, description, true);
    }

    /**
     * A HUD element filed under a different group. The element still draws on the HUD and is placed
     * in the HUD editor; only where it appears in the menu changes.
     */
    protected HudModule(String id, Category category, String name, String description) {
        this(id, category, name, description, true);
    }

    /** As above, but {@code textSettings=false} omits the text toggles for custom-drawn elements. */
    protected HudModule(String id, Category category, String name, String description, boolean textSettings) {
        super(id, category, name, description);
        if (textSettings) {
            settings.add(accentColour);
            settings.add(showLabel);
            settings.add(centered);
        }
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

    // TODO: rebuild the HUD on configlib's HudWidget.
    //
    // Every element used to be a RenderLib component tree built by createElement(ContainerComponent),
    // and RenderLib is gone. configlib's HudWidget is an immediate-mode interface - width(), height()
    // and render(GuiGraphicsExtractor) in local space - so each element is a rewrite of its drawing
    // rather than a change of imports, which is why none of them made it into this build.
    //
    // hudLine(...) below still produces what each element wants to show, so the data half of every
    // element survives intact; only the drawing has to be written again.

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

}
