package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiGradient;
import com.render.api.gui.GuiView;
import com.render.api.gui.TextInputComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import com.render.api.gui.layout.GuiPositionType;

/**
 * The shared bones of the mod's RenderLib screens: the design space, a centred panel with a header,
 * and the handful of helpers every one of them needs.
 *
 * <p>These were all sitting inside the ClickGUI, which was fine while it was the only such screen.
 * It is not any more, and the alternative to putting them here is each new screen either copying
 * them or quietly going back to the hand-drawn stack the rest of the mod is moving off.
 *
 * <p>The layout rules worth knowing, learned the hard way:
 * <ul>
 *   <li>Containers default to {@code RETAINED} layout, where {@code justifyContent} and friends do
 *       nothing - hence {@link #row} and {@link #column}, which opt into {@code display(FLEX)}.</li>
 *   <li>A {@link ButtonComponent} arrives with its own size, padding, gradient and shadow;
 *       {@link #clickable} strips all of it so a button can be a themed box of ours.</li>
 * </ul>
 */
public abstract class DiegoView extends GuiView {
    protected static final float DESIGN_W = 1920f;
    protected static final float DESIGN_H = 1080f;
    protected static final float HEADER_H = 72f;
    protected static final float PAD = 24f;

    protected Theme t = Themes.current();
    protected ContainerComponent panel;

    private final float panelW;
    private final float panelH;
    private final String title;

    protected DiegoView(String title, float panelW, float panelH) {
        this.title = title;
        this.panelW = panelW;
        this.panelH = panelH;
    }

    @Override
    protected void build() {
        t = Themes.current();
        panel = column(panelW, 0f).height(panelH)
                .position(GuiPositionType.ABSOLUTE)
                .x((DESIGN_W - panelW) / 2f)
                .y((DESIGN_H - panelH) / 2f)
                .backgroundColor(GuiColors.of(t.surface()))
                .cornerRadius(16f)
                .borderWidth(2f).borderColor(GuiColors.of(t.border()))
                .clipChildren(true);
        root().add(panel);
        if (showHeader()) {
            panel.add(header());
        }
        content(panelW, showHeader() ? panelH - HEADER_H : panelH);
    }

    /** Fills the panel below the header. Called once per build. */
    protected abstract void content(float width, float height);

    /**
     * Whether the standard title-and-close bar is drawn. A screen that is its own front page - the
     * welcome - says what it is by its own content, and a Close button on it would be a third way
     * out beside the two it already offers.
     */
    protected boolean showHeader() {
        return true;
    }

    private ContainerComponent header() {
        // The header carries the panel's own radius: clipping the children was not enough to stop
        // its square corners showing through the rounded ones above them.
        ContainerComponent bar = row(panelW, 12f).height(HEADER_H).padding(0f, PAD)
                .backgroundColor(GuiColors.of(t.surfaceAlt()))
                .cornerRadius(16f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        bar.add(textBox(GuiText.label(title, t.text(), 20f), 0f, HEADER_H).flexGrow(1f));
        ButtonComponent close = clickable(t.surface(), this::close);
        asRow(close, 96f, 0f).height(36f).cornerRadius(9f)
                .justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        close.add(GuiText.label("Close", t.textMuted(), 14f));
        bar.add(close);
        return bar;
    }

    // --- building blocks ---------------------------------------------------------------------------

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
        c.flowRow().display(GuiDisplay.FLEX).flexDirection(GuiFlexDirection.ROW)
                .alignItems(GuiAlignment.CENTER).columnGap(GuiLength.pixels(gap)).gap(gap);
        return c;
    }

    protected static <T extends ContainerComponent> T asColumn(T c, float width, float gap) {
        if (width > 0f) {
            c.width(width);
        }
        c.flowColumn().display(GuiDisplay.FLEX).flexDirection(GuiFlexDirection.COLUMN)
                .alignItems(GuiAlignment.START).rowGap(GuiLength.pixels(gap)).gap(gap);
        return c;
    }

    /** Text in a box of a known height, which text alone cannot have without spilling its glyphs. */
    protected static ContainerComponent textBox(com.render.api.gui.TextComponent text,
                                                float width, float height) {
        ContainerComponent box = row(width, 0f);
        box.height(height);
        box.add(text);
        return box;
    }

    /** A button stripped of RenderLib's stock look, so it can be a themed box of ours. */
    protected ButtonComponent clickable(int background, Runnable action) {
        ButtonComponent b = new ButtonComponent();
        b.clearChildren();
        b.onPress(action);
        b.autoSize().padding(0f)
                .backgroundColor(GuiColors.of(background))
                .gradient(flat(background))
                .borderWidth(0f)
                .shadow(null)
                .glow(null);
        b.hovered(c -> c.backgroundColor(GuiColors.of(t.elevated())).gradient(flat(t.elevated())));
        b.pressed(c -> c.backgroundColor(GuiColors.of(t.elevated())).gradient(flat(t.elevated())));
        return b;
    }

    /** A themed text field. */
    protected TextInputComponent field(String value, String placeholder, float width,
                                       java.util.function.Consumer<String> onChange) {
        TextInputComponent in = new TextInputComponent()
                .value(value)
                .placeholder(placeholder)
                .font(GuiText.BODY)
                .textScalePixels(13f)
                .onChange(s -> onChange.accept(s == null ? "" : s));
        in.size(width, 32f).padding(8f, 12f).cornerRadius(9f).flexShrink(0f)
                .backgroundColor(GuiColors.of(t.surfaceAlt()))
                .borderWidth(1f).borderColor(GuiColors.of(t.border()))
                .color(GuiColors.of(t.text()));
        return in;
    }

    /** A gradient of one colour - RenderLib wants one where a flat fill would do. */
    protected static GuiGradient flat(int argb) {
        return new GuiGradient().startColor(GuiColors.of(argb)).endColor(GuiColors.of(argb));
    }
}
