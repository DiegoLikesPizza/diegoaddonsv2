package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiGradient;
import com.render.api.gui.GuiOverflowMode;
import com.render.api.gui.GuiTextAlignment;
import com.render.api.gui.GuiView;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.SliderComponent;
import com.render.api.gui.SliderValueLabelPosition;
import com.render.api.gui.TextComponent;
import com.render.api.gui.TextInputComponent;
import com.render.api.gui.ToggleSwitchComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import com.render.api.gui.layout.GuiPositionType;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.ModuleManager;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The DiegoAddons menu, built on RenderLib's retained {@link GuiView}: a header, a category rail and
 * a list of module cards whose settings expand inline underneath the card you clicked.
 *
 * <p>Two RenderLib facts shape everything here:
 * <ul>
 *   <li>Components default to {@link GuiDisplay#RETAINED}, where the legacy component-local layout
 *       runs and {@code justifyContent}/{@code alignItems}/{@code flexGrow} are inert. Every
 *       container therefore goes through {@link #row}/{@link #column}, which opt into
 *       {@code display(FLEX)}.</li>
 *   <li>Text shapes through RenderLib, not Minecraft's font metrics, and a text component with no
 *       width wraps at whatever the layout hands it. Every label therefore gets a measured width
 *       ({@link #text}), while heights are always left to the content - forcing a height makes the
 *       glyphs spill out below their box.</li>
 * </ul>
 *
 * <p>The rail and the card list refresh independently ({@link #refreshRail()} /
 * {@link #refreshContent()}) instead of rebuilding the tree, which keeps the search field's focus
 * and caret alive while you type. Only a theme change rebuilds everything.
 */
public class DiegoClickGuiView extends GuiView {
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 1080f;

    private static final float PANEL_W = 1160f;
    private static final float PANEL_H = 760f;
    private static final float HEADER_H = 66f;
    private static final float BODY_H = PANEL_H - HEADER_H;      // 694

    private static final float RAIL_W = 250f;
    private static final float RAIL_PAD = 16f;
    private static final float RAIL_INNER = RAIL_W - RAIL_PAD * 2f;        // 218

    private static final float DIVIDER_W = 1f;
    private static final float CONTENT_W = PANEL_W - RAIL_W - DIVIDER_W;   // 909
    private static final float CONTENT_PAD = 18f;
    private static final float CONTENT_INNER = CONTENT_W - CONTENT_PAD * 2f;   // 873
    private static final float LIST_H = BODY_H - CONTENT_PAD * 2f - 44f;

    private static final float SCROLLBAR_GUTTER = 14f;
    private static final float CARD_W = CONTENT_INNER - SCROLLBAR_GUTTER;      // 859
    private static final float CARD_PAD_X = 18f;
    private static final float CARD_INNER = CARD_W - CARD_PAD_X * 2f;          // 823

    private static final float TOGGLE_W = 46f;
    private static final float TOGGLE_H = 24f;
    private static final float TOGGLE_KNOB_R = 8f;

    /** One glyph per {@link Category}, in enum order, for the rail. */
    private static final String[] CATEGORY_ICONS = {"◆", "▤", "⚔", "⛏", "✿", "≈", "☠", "⚙"};

    private Theme t = Themes.current();
    private Category selectedCategory;
    private Module expandedModule;
    private String query = "";

    private ContainerComponent panel;
    private ContainerComponent railPane;
    private ContainerComponent contentPane;
    private TextInputComponent searchField;

    @Override
    protected void build() {
        panel = column(PANEL_W, 0f).height(PANEL_H)
                .position(GuiPositionType.ABSOLUTE)
                .x((DESIGN_W - PANEL_W) / 2f)
                .y((DESIGN_H - PANEL_H) / 2f);
        root().add(panel);
        rebuildAll();
    }

    /** Full rebuild - only needed when the theme changes, since every colour is baked in. */
    private void rebuildAll() {
        t = Themes.current();
        List<Category> cats = ModuleManager.categories();
        if (selectedCategory == null && !cats.isEmpty()) {
            selectedCategory = cats.get(0);
        }

        panel.clearChildren();
        panel.backgroundColor(t.surface()).cornerRadius(16f).borderColor(t.border()).borderWidth(1f);
        panel.add(headerBar());

        railPane = column(RAIL_W, 8f).height(BODY_H).padding(RAIL_PAD)
                .backgroundColor(t.surfaceAlt());
        contentPane = column(CONTENT_W, 12f).height(BODY_H).padding(CONTENT_PAD);

        ContainerComponent body = row(PANEL_W, 0f).height(BODY_H)
                .alignItems(GuiAlignment.STRETCH);
        body.add(railPane);
        body.add(new ContainerComponent().size(DIVIDER_W, BODY_H).backgroundColor(t.border()));
        body.add(contentPane);
        panel.add(body);

        refreshRail();
        refreshContent();
    }

    // --- header -----------------------------------------------------------------------------------

    private ContainerComponent headerBar() {
        ContainerComponent bar = row(PANEL_W, 12f).height(HEADER_H)
                .padding(0f, 22f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);

        ContainerComponent brand = row(0f, 10f).autoWidth().flexShrink(0f);
        brand.add(text("⚡", t.accentTo(), 20f));
        brand.add(text("DiegoAddons", t.text(), 24f));
        bar.add(brand);

        // Grows into whatever the brand leaves, and packs its children against the right edge, so an
        // over-wide button overflows towards the middle instead of out past the panel.
        ContainerComponent right = row(0f, 10f).flexGrow(1f).flexShrink(1f)
                .justifyContent(GuiAlignment.END);

        searchField = new TextInputComponent()
                .value(query)
                .placeholder("Search all " + ModuleManager.all().size() + " modules...")
                .textScalePixels(14f)
                .onChange(s -> {
                    query = s == null ? "" : s;
                    refreshContent();
                });
        // A fixed height with symmetric padding centres the text: left to autoHeight the box
        // collapses to a hairline and the placeholder draws underneath it.
        searchField.size(280f, 36f).padding(9f, 12f).cornerRadius(10f)
                .flexShrink(0f)
                .backgroundColor(t.surfaceAlt())
                .borderColor(t.border()).borderWidth(1f)
                .color(t.text());
        right.add(searchField);

        right.add(pill("Theme: " + t.name(), () -> {
            Themes.select(nextTheme());
            rebuildAll();
        }));
        right.add(pill("HUD Editor", () -> {
            close();
            dev.diego.diegoaddons.hud.HudElements.openPlacementScreen();
        }));
        right.add(pill("✕", this::close));
        bar.add(right);
        return bar;
    }

    private Theme nextTheme() {
        List<Theme> all = Themes.ALL;
        int i = 0;
        for (int k = 0; k < all.size(); k++) {
            if (all.get(k).name().equalsIgnoreCase(t.name())) {
                i = k;
                break;
            }
        }
        return all.get((i + 1) % all.size());
    }

    // --- category rail ----------------------------------------------------------------------------

    private void refreshRail() {
        railPane.clearChildren();
        railPane.add(text("CATEGORIES", t.textFaint(), 11f));

        for (Category c : ModuleManager.categories()) {
            railPane.add(categoryRow(c));
        }

        // Pushes the footer to the bottom of the rail.
        railPane.add(new ContainerComponent().width(RAIL_INNER).flexGrow(1f).flexShrink(1f));
        railPane.add(new ContainerComponent().size(RAIL_INNER, 1f).backgroundColor(t.border()));

        ContainerComponent footer = row(RAIL_INNER, 8f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        footer.add(text(enabledCount() + " enabled", t.textMuted(), 12f));
        footer.add(keyChip(openKeyName()));
        railPane.add(footer);
    }

    private ButtonComponent categoryRow(Category c) {
        boolean sel = c == selectedCategory;
        int fg = sel ? t.accentText() : t.text();
        int countFg = sel ? t.accentText() : t.textFaint();

        ButtonComponent b = plainButton(sel ? t.accent() : 0x00000000, () -> {
            selectedCategory = c;
            expandedModule = null;
            refreshRail();
            refreshContent();
        });
        asRow(b, RAIL_INNER, 10f).autoHeight().padding(10f, 13f).cornerRadius(10f).flexShrink(0f);

        b.add(text(icon(c), fg, 13f).width(16f));
        b.add(text(c.display, fg, 14f).flexGrow(1f));
        b.add(text(String.valueOf(ModuleManager.modulesIn(c).size()), countFg, 11f));
        return b;
    }

    private static String icon(Category c) {
        int i = c.ordinal();
        return i >= 0 && i < CATEGORY_ICONS.length ? CATEGORY_ICONS[i] : "•";
    }

    private static int enabledCount() {
        int n = 0;
        for (Module m : ModuleManager.all()) {
            if (m.isEnabled()) {
                n++;
            }
        }
        return n;
    }

    private static String openKeyName() {
        return DiegoAddonsV2Client.OPEN_MENU == null
                ? "\\"
                : DiegoAddonsV2Client.OPEN_MENU.getTranslatedKeyMessage().getString();
    }

    // --- module cards -----------------------------------------------------------------------------

    private void refreshContent() {
        contentPane.clearChildren();

        List<Module> modules = visibleModules();
        ContainerComponent title = row(CONTENT_INNER, 10f);
        title.add(text(query.isEmpty() && selectedCategory != null ? selectedCategory.display : "Search",
                t.text(), 19f).flexGrow(1f));
        title.add(text(modules.size() + (modules.size() == 1 ? " module" : " modules"),
                t.textFaint(), 12f));
        contentPane.add(title);

        if (modules.isEmpty()) {
            contentPane.add(wrappingText("Nothing here. Try another search.", t.textFaint(), 14f, CONTENT_INNER));
            return;
        }

        ScrollContainerComponent list = new ScrollContainerComponent();
        list.width(CONTENT_INNER);
        list.height(LIST_H);
        list.flowColumn();
        list.display(GuiDisplay.FLEX);
        list.flexDirection(GuiFlexDirection.COLUMN);
        list.alignItems(GuiAlignment.START);
        list.rowGap(GuiLength.pixels(10f));
        list.gap(10f);
        list.overflowY(GuiOverflowMode.AUTO);
        for (Module m : modules) {
            list.add(moduleCard(m));
        }
        contentPane.add(list);
    }

    /** The modules the list shows: the selected category, or every match while searching. */
    private List<Module> visibleModules() {
        if (!query.isEmpty()) {
            List<Module> out = new ArrayList<>();
            String q = query.toLowerCase(Locale.ROOT);
            for (Module m : ModuleManager.all()) {
                if (matches(m, q)) {
                    out.add(m);
                }
            }
            return out;
        }
        return selectedCategory == null ? List.of() : ModuleManager.modulesIn(selectedCategory);
    }

    private static boolean matches(Module m, String lowerQuery) {
        if (m.name.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (m.category.display.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        return m.description != null && m.description.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    /**
     * The whole card is the clickable surface, so anywhere on it expands or collapses the settings.
     * The toggle is a child of it and swallows its own clicks, which keeps flipping a module from
     * expanding it as a side effect.
     */
    private ButtonComponent moduleCard(Module m) {
        boolean expanded = m == expandedModule;

        ButtonComponent card = plainButton(expanded ? t.elevated() : t.surfaceAlt(), () -> {
            expandedModule = expanded ? null : m;
            refreshContent();
        });
        asColumn(card, CARD_W, 12f).autoHeight()
                .padding(14f, CARD_PAD_X).cornerRadius(12f)
                .borderWidth(1f).borderColor(expanded ? t.accent() : t.border())
                .flexShrink(0f);

        ContainerComponent head = row(CARD_INNER, 14f);
        ContainerComponent titleBox = column(0f, 3f).flexGrow(1f).flexShrink(1f);
        float textW = CARD_INNER - TOGGLE_W - 14f;
        titleBox.add(wrappingText(m.name, t.text(), 15f, textW));
        if (m.description != null && !m.description.isEmpty()) {
            titleBox.add(wrappingText(m.description, t.textMuted(), 12f, textW));
        }
        head.add(titleBox);
        head.add(toggle(m.isEnabled(), v -> {
            ModuleManager.setEnabled(m, v);
            refreshRail();
        }));
        card.add(head);

        if (expanded) {
            card.add(new ContainerComponent().size(CARD_INNER, 1f).backgroundColor(t.border())
                    .flexShrink(0f));
            List<Setting> settings = m.settings();
            if (settings.isEmpty()) {
                card.add(wrappingText("No settings for this feature.", t.textFaint(), 13f, CARD_INNER));
            } else {
                for (Setting s : settings) {
                    card.add(settingRow(s));
                }
            }
        }
        return card;
    }

    // --- setting controls -------------------------------------------------------------------------

    private ContainerComponent settingRow(Setting s) {
        if (s instanceof BooleanSetting bs) {
            ContainerComponent r = row(CARD_INNER, 12f)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN).flexShrink(0f);
            r.add(text(bs.name, t.text(), 14f).flexGrow(1f));
            r.add(toggle(bs.get(), bs::set));
            return r;
        }

        if (s instanceof NumberSetting ns) {
            ContainerComponent box = column(CARD_INNER, 6f).flexShrink(0f);
            TextComponent value = text(ns.display(), t.textMuted(), 12f);
            ContainerComponent top = row(CARD_INNER, 10f)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            top.add(text(ns.name, t.text(), 14f).flexGrow(1f));
            top.add(value);
            box.add(top);
            box.add(new SliderComponent().min(ns.min).max(ns.max).step(ns.step).value(ns.get())
                    .valueFormatter(v -> String.format(Locale.ROOT, "%." + ns.decimals + "f", v))
                    .onChange(v -> {
                        ns.set(v);
                        // Update the read-out in place; rebuilding here would kill the drag.
                        value.text(ns.display());
                    })
                    .valueLabelPosition(SliderValueLabelPosition.OFF)
                    .size(CARD_INNER, 18f)
                    .trackColor(t.surface()).fillColor(t.accent()).thumbColor(t.accentText()));
            return box;
        }

        if (s instanceof CycleSetting cs) {
            return valueButton(cs.name, cs.label(), () -> {
                cs.cycle();
                refreshContent();
            });
        }

        if (s instanceof KeybindSetting ks) {
            return valueButton(ks.name, ks.display(), () -> {
                ks.clear();
                refreshContent();
            });
        }

        if (s instanceof ActionSetting as) {
            return valueButton(as.name, as.action, as::run);
        }

        return new ContainerComponent().width(CARD_INNER);
    }

    /** A full-width row that reads "name ... value" and runs an action when clicked. */
    private ContainerComponent valueButton(String name, String value, Runnable action) {
        ContainerComponent wrap = row(CARD_INNER, 0f).flexShrink(0f);
        ButtonComponent b = plainButton(t.surface(), action);
        asRow(b, CARD_INNER, 10f).autoHeight()
                .justifyContent(GuiAlignment.SPACE_BETWEEN)
                .padding(9f, 12f).cornerRadius(8f)
                .borderWidth(1f).borderColor(t.border());
        b.add(text(name, t.text(), 14f).flexGrow(1f));
        b.add(text(value, t.textMuted(), 13f).textAlignment(GuiTextAlignment.RIGHT));
        wrap.add(b);
        return wrap;
    }

    // --- small shared pieces ----------------------------------------------------------------------

    /**
     * A flex row that centres its children vertically. A width of {@code 0} leaves the width to the
     * layout; heights are always left to the content unless a caller sets one.
     */
    private static ContainerComponent row(float width, float gap) {
        ContainerComponent c = new ContainerComponent();
        if (width > 0f) {
            c.width(width);
        }
        return asRow(c, 0f, gap);
    }

    private static ContainerComponent column(float width, float gap) {
        ContainerComponent c = new ContainerComponent();
        if (width > 0f) {
            c.width(width);
        }
        return asColumn(c, 0f, gap);
    }

    /** Turns an already-created component (a button, say) into a flex row. */
    private static <T extends ContainerComponent> T asRow(T c, float width, float gap) {
        if (width > 0f) {
            c.width(width);
        }
        c.flowRow()      // legacy direction, in case a container ever falls back to RETAINED
                .display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.ROW)
                .alignItems(GuiAlignment.CENTER)
                .columnGap(GuiLength.pixels(gap))
                .gap(gap);
        return c;
    }

    private static <T extends ContainerComponent> T asColumn(T c, float width, float gap) {
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

    /**
     * Text with a width wide enough that it never wraps. A text component with no width wraps at
     * whatever the flex engine hands it, which is how "HUD Editor" ended up on two lines - so every
     * single-line label gets a measured width. Height is always left to the content: forcing one
     * makes the glyphs spill out below the box.
     */
    private static TextComponent text(String s, int color, float scale) {
        return new TextComponent().text(s).color(color).textScalePixels(scale)
                .width(textWidth(s, scale));
    }

    /** Text that may wrap, at the width given. */
    private static TextComponent wrappingText(String s, int color, float scale, float width) {
        return new TextComponent().text(s).color(color).textScalePixels(scale).width(width);
    }

    /**
     * How wide a run of text renders, from Minecraft's own metrics rescaled to RenderLib's. The
     * ratio was calibrated against a screenshot of the running client (RenderLib draws roughly
     * {@code scale / 9.6} times the vanilla width); the extra slack keeps a label off its own
     * wrapping point.
     */
    private static float textWidth(String s, float scale) {
        return Minecraft.getInstance().font.width(s) * (scale / 9.6f) * 1.12f + 6f;
    }

    private ToggleSwitchComponent toggle(boolean value, Consumer<Boolean> onChange) {
        ToggleSwitchComponent sw = new ToggleSwitchComponent().value(value)
                .trackOnColor(t.accent()).trackOffColor(t.border())
                .knobRadius(TOGGLE_KNOB_R)
                .onChange(onChange);
        sw.size(TOGGLE_W, TOGGLE_H).flexShrink(0f);
        return sw;
    }

    /**
     * A {@link ButtonComponent} stripped of its stock label, size, padding, gradient, shadow and
     * hover colours, so it can be used as a themed clickable box that holds its own children.
     */
    private ButtonComponent plainButton(int background, Runnable action) {
        ButtonComponent b = new ButtonComponent();
        b.clearChildren();          // drop the built-in centred label; we supply the content
        b.onPress(action);
        b.autoSize()                // clear the stock 220x52
                .padding(0f)        // and the stock 14x18 padding
                .backgroundColor(background)
                .gradient(flat(background))
                .borderWidth(0f)
                .shadow(null)
                .glow(null);
        b.hovered(c -> c.backgroundColor(t.elevated()).gradient(flat(t.elevated())));
        b.pressed(c -> c.backgroundColor(t.elevated()).gradient(flat(t.elevated())));
        return b;
    }

    /** A single-colour "gradient", so the stock button gradient never shows through. */
    private static GuiGradient flat(int argb) {
        return new GuiGradient().startColor(argb).endColor(argb).angleDegrees(180f);
    }

    /** A header button that hugs its label - no fixed width, so nothing wraps or clips. */
    private ButtonComponent pill(String label, Runnable action) {
        ButtonComponent b = plainButton(t.elevated(), action);
        asRow(b, 0f, 0f).autoSize()
                .justifyContent(GuiAlignment.CENTER)
                .padding(9f, 14f).cornerRadius(10f).flexShrink(0f);
        b.add(text(label, t.text(), 13f));
        return b;
    }

    private ContainerComponent keyChip(String key) {
        ContainerComponent chip = row(0f, 0f).autoSize()
                .justifyContent(GuiAlignment.CENTER)
                .padding(3f, 8f).cornerRadius(5f).flexShrink(0f)
                .backgroundColor(t.elevated()).borderColor(t.border()).borderWidth(1f);
        chip.add(text(key, t.textMuted(), 11f));
        return chip;
    }
}
