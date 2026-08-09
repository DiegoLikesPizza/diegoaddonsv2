package dev.diego.diegoaddons.module;

import dev.diego.configlib.hud.HudStyle;
import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.hud.HudElements;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * A module that draws a single line of text as a themed HUD chip. Subclasses supply a label and a
 * live value; {@link ModuleManager} handles the drawing and positioning. Colour and label are
 * driven live by two {@link BooleanSetting}s exposed in the ClickGUI.
 */
public abstract class HudModule extends Module {
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
            settings.add(showLabel);
            settings.add(centered);
        }
        addStyleSettings();
    }

    /**
     * @param textSettings when {@code false}, the {@code Accent colour} / {@code Show label} toggles
     *                     are not added - for custom-drawn HUD elements (e.g. the inventory grid)
     *                     where they don't apply.
     */
    protected HudModule(String id, String name, String description, boolean textSettings) {
        super(id, Category.HUD, name, description);
        if (textSettings) {
            settings.add(showLabel);
            settings.add(centered);
        }
        addStyleSettings();
    }

    // --- per-element appearance -------------------------------------------------------------------

    /**
     * Whether this element opts out of the shared HUD look.
     *
     * <p>Off by default, and that matters: the point of the shared style is that ten elements look
     * like one HUD. An override is for the one element you want to stand out - a timer you need to
     * catch out of the corner of your eye - not the way each element is expected to be dressed.
     */
    protected final BooleanSetting customStyle =
            new BooleanSetting(this, "customStyle", "Custom appearance", false);
    protected final ColorSetting styleColor =
            new ColorSetting(this, "styleColor", "Text colour", 0xFFFFFFFF);
    protected final BooleanSetting stylePlate =
            new BooleanSetting(this, "stylePlate", "Background plate", true);
    protected final NumberSetting stylePlateOpacity =
            new NumberSetting(this, "stylePlateOpacity", "Plate opacity", 80, 0, 100, 5);

    /**
     * Adds the appearance rows. Every HUD element gets these, including the custom-drawn ones -
     * unlike the text toggles, which only mean something for an element that is a line of text.
     */
    private void addStyleSettings() {
        settings.add(customStyle);
        settings.add(styleColor);
        settings.add(stylePlate);
        settings.add(stylePlateOpacity);
    }

    /**
     * Whether this element draws text that a colour would apply to.
     *
     * <p>False for the ones that are pictures - the inventory grid is item models, the player HUD is
     * a model between two columns of them. Offering "Text colour" on those is offering a control
     * that cannot do anything, so the config layer leaves the row out.
     */
    public boolean hasStyledText() {
        return true;
    }

    /** Whether the override is on, so the config layer can hide the rows that depend on it. */
    public boolean customStyleOn() {
        return customStyle.get();
    }

    /**
     * Whether {@code s} is one of the rows that only mean anything while the override is on.
     *
     * <p>Asked by the config layer so those rows are hidden rather than sitting there doing
     * nothing - a control that is present and inert is worse than one that is absent.
     */
    public boolean isStyleDetail(Setting s) {
        return s == styleColor || s == stylePlate || s == stylePlateOpacity;
    }

    /** Whether {@code s} is a row this element has no use for at all, rather than one it hides. */
    public boolean isUselessSetting(Setting s) {
        return s == styleColor && !hasStyledText();
    }

    /**
     * The look this element draws with: its own when it has opted out, otherwise the shared one.
     *
     * <p>Read every frame rather than cached, because both halves can change while the game is
     * running - the theme from the appearance page, the override from this module's own card.
     */
    public HudStyle style() {
        HudStyle shared = HudElements.sharedStyle();
        if (!customStyle.get()) {
            return shared;
        }
        // plate(...) last, and that is not style: plateOpacity turns the plate back on whenever the
        // alpha it is given is above zero, so setting the toggle before it meant "no background"
        // was silently undone by the opacity slider sitting at 80%.
        return shared.derive()
                .textColor(styleColor.argb())
                .plateOpacity((float) (stylePlateOpacity.get() / 100.0))
                .plate(stylePlate.get())
                .build();
    }

    /**
     * Whether this element is placed by the user in the HUD editor.
     *
     * <p>Almost every element is. The exception is one that has a place of its own for a reason - a
     * "look at this now" message belongs across the middle of the screen and nowhere else, and
     * offering a position for it would be offering a setting that should not be honoured. Such a
     * module draws from a plain HUD callback instead and declares no position at all, rather than
     * declaring one and quietly ignoring it.
     */
    public boolean placeable() {
        return true;
    }

    /**
     * Custom drawing for this element, or {@code null} to be drawn as a plain text chip.
     *
     * <p>Most HUD elements are a caption and a value, and {@link HudElements} draws those with
     * configlib's {@code labelValue} prefab off {@link #hudLabel} and {@link #hudValue} - no module
     * needs to say anything. An element that is not text at all (a map, a slot grid, a column of
     * armour) overrides this and returns its own {@link HudWidget}, which draws in local space from
     * the element's own top-left with configlib owning placement, scale and the editor.
     *
     * <p>Returning {@code null} is the default precisely so the two halves can be converted apart:
     * an element whose drawing has not been written yet keeps the text chip rather than vanishing.
     * That is the whole reason this is a hook and not an abstract method.
     */
    public HudWidget hudWidget() {
        return null;
    }

    /**
     * The colour this element's text draws in - its own when it has an override, else the theme's.
     *
     * <p>Was a boolean "Accent colour" toggle picking between two theme colours. That was a second
     * colour control beside Custom appearance's, so it is gone; the accent it defaulted to is now
     * the shared style's text colour, which means the default look is unchanged and choosing
     * anything else happens in one place.
     */
    public int color() {
        return style().textColor();
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
