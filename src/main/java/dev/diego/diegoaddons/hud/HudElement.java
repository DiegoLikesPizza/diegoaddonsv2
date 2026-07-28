package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiFont;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.GuiColors;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Base for everything the HUD draws through RenderLib's managed layout.
 *
 * <p>Each element owns the {@link ContainerComponent} RenderLib hands it and rebuilds its contents
 * only when what it shows actually changes; RenderLib owns where it sits, how big it is and how
 * transparent it is. {@link #update} runs once per client tick and answers whether there is anything
 * to show, which is what decides the element's visibility.
 *
 * <p>The layout rules that bite in RenderLib, collected here so no element has to rediscover them:
 * <ul>
 *   <li>A container only honours {@code alignItems}/{@code justifyContent}/{@code flexGrow} once it
 *       opts into {@code display(FLEX)} - hence {@link #row} and {@link #column}.</li>
 *   <li>A text component with no width wraps at whatever the layout hands it, and one with a forced
 *       height spills its glyphs, so text is measured ({@link #text}) and a row that needs a height
 *       gets a box around it ({@link #textRow}).</li>
 *   <li>The bundled Poppins faces carry no symbols, so glyphs are drawn in Minecraft's own font.</li>
 * </ul>
 */
public abstract class HudElement {
    /** The mod's faces, as RenderLib fonts. */
    public static final GuiFont MEDIUM = face("ui_medium");
    public static final GuiFont SMALL = face("ui_small");

    /** Natural pixel size of those faces, and how much wider RenderLib shapes them. */
    private static final float NATURAL_PX = 10f;
    private static final float SLACK = 1.25f;

    public static final float TEXT_PX = 10f;
    /** One row's height - the stride the mod's HUD has always used for 10px text. */
    public static final float ROW_H = 12f;
    /**
     * What to pass to {@code TextComponent.lineHeight(...)} for a {@link #ROW_H} row.
     *
     * <p>That setter takes a <b>multiple of the font size</b>, not a pixel height - RenderLib stores
     * it as a line-height multiplier. Handing it {@code ROW_H} asked for lines twelve times the size
     * of 10px text, so every chip's laid-out box was ten-odd rows tall even though it drew one line.
     * Nothing looked wrong on the HUD itself, but the placement screen outlines the union of what an
     * element lays out - which is why the chips sat in tall empty rectangles there.
     */
    public static final float LINE_HEIGHT = ROW_H / TEXT_PX;
    public static final float PAD_X = 8f;
    public static final float PAD_Y = 5f;

    protected final HudModule module;
    protected final ContainerComponent root;

    private String appliedTheme = "";

    protected HudElement(HudModule module, ContainerComponent root) {
        this.module = module;
        this.root = root;
    }

    /**
     * Refresh this element. Returns {@code false} when there is nothing to show right now - the
     * element is then hidden rather than drawn empty.
     */
    public abstract boolean update(Minecraft mc);

    // --- shared chrome ------------------------------------------------------------------------------

    /**
     * State the root's own box, width and height together.
     *
     * <p>RenderLib's element root takes the space it is offered rather than shrinking to fit its
     * children, so an element that sets only a width keeps a full-height root. Everything drew in the
     * right place - the content is laid out from the top-left either way - but the element's box is
     * what the placement screen outlines and what it hit-tests, which is why the chips sat in tall,
     * mostly empty rectangles there. An element that sizes a child explicitly (the maps, the
     * inventory) never showed it; the ones that relied on intrinsic height did.
     *
     * <p>Both figures include the root's padding, matching how the chip widths are already computed.
     */
    protected void sizeRoot(float width, float height) {
        root.width(width).height(height);
    }

    /** The themed chip background every element sits on. Re-applied when the theme changes. */
    protected void applyBackground(ContainerComponent box, float radius) {
        Theme t = Themes.current();
        box.backgroundColor(GuiColors.of((0xCC << 24) | (t.surface() & 0x00FFFFFF)))
                .cornerRadius(radius)
                .borderWidth(1f)
                .borderColor(GuiColors.of(Theme.withAlpha(t.border(), 0.9f)));
    }

    /** True once per theme change, so an element knows to recolour itself. */
    protected boolean themeChanged() {
        String name = Themes.current().name();
        if (name.equals(appliedTheme)) {
            return false;
        }
        appliedTheme = name;
        return true;
    }

    // --- layout helpers -----------------------------------------------------------------------------

    protected static ContainerComponent row(float width, float gap) {
        return asRow(new ContainerComponent(), width, gap);
    }

    protected static ContainerComponent column(float width, float gap) {
        return asColumn(new ContainerComponent(), width, gap);
    }

    protected static <T extends ContainerComponent> T asRow(T c, float width, float gap) {
        if (width > 0f) {
            c.width(width);
        }
        c.flowRow()
                .display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.ROW)
                .alignItems(GuiAlignment.CENTER)
                .columnGap(GuiLength.pixels(gap))
                .gap(gap);
        return c;
    }

    protected static <T extends ContainerComponent> T asColumn(T c, float width, float gap) {
        if (width > 0f) {
            c.width(width);
        }
        c.flowColumn()
                .display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.COLUMN)
                .alignItems(GuiAlignment.START)
                .rowGap(GuiLength.pixels(gap))
                .gap(gap);
        return c;
    }

    /** An absolutely-placed coloured box, for the map elements. */
    protected static ContainerComponent block(float x, float y, float w, float h, int color) {
        ContainerComponent box = new ContainerComponent();
        box.size(w, h).backgroundColor(GuiColors.of(color))
                .position(com.render.api.gui.layout.GuiPositionType.ABSOLUTE).x(x).y(y);
        return box;
    }

    // --- text ---------------------------------------------------------------------------------------

    protected static TextComponent text(String s, int color, float scale) {
        return new TextComponent().text(s).color(GuiColors.of(color)).font(MEDIUM).textScalePixels(scale)
                .width(width(s, scale));
    }

    protected static TextComponent small(String s, int color, float scale) {
        return new TextComponent().text(s).color(GuiColors.of(color)).font(SMALL).textScalePixels(scale)
                .width(width(s, scale));
    }

    /** A symbol in Minecraft's font; the bundled faces have no glyph for these. */
    protected static TextComponent glyph(String s, int color, float scale) {
        Minecraft mc = Minecraft.getInstance();
        float w = mc != null && mc.font != null ? mc.font.width(s) * (scale / 8f) + 4f : scale;
        return new TextComponent().text(s).color(GuiColors.of(color)).font(GuiFont.minecraftDefault())
                .textScalePixels(scale).width(w);
    }

    /** A text row of a known height: text alone cannot have one without spilling its glyphs. */
    protected static ContainerComponent textRow(TextComponent text, float width, float height) {
        ContainerComponent box = row(width, 0f);
        box.height(height);
        box.add(text);
        return box;
    }

    /** How wide {@code s} renders at {@code scale}. */
    public static float width(String s, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return s.length() * scale * 0.6f;
        }
        return mc.font.width(Fonts.t(s, Fonts.MEDIUM)) * (scale / NATURAL_PX) * SLACK + 4f;
    }

    /**
     * As {@link #width}, with every digit counted as the widest one, so a value that ticks over does
     * not make its chip twitch.
     */
    public static float steadyWidth(String s, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return width(s, scale);
        }
        char widest = '0';
        int max = -1;
        for (char c = '0'; c <= '9'; c++) {
            int cw = mc.font.width(Fonts.t(String.valueOf(c), Fonts.MEDIUM));
            if (cw > max) {
                max = cw;
                widest = c;
            }
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= '0' && c <= '9' ? widest : c);
        }
        return width(sb.toString(), scale);
    }

    private static GuiFont face(String path) {
        return GuiFont.of(Identifier.fromNamespaceAndPath("diegoaddonsv2", path));
    }
}
