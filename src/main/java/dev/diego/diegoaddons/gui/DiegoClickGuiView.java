package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiGradient;
import com.render.api.gui.GuiOverflowMode;
import com.render.api.gui.GuiTextAlignment;
import com.render.api.gui.GuiView;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.SliderComponent;
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

/**
 * The DiegoAddons menu, built on RenderLib's retained {@link GuiView}: a header, a category rail and
 * a list of module cards whose settings expand inline underneath the card you clicked.
 *
 * <p>Every container lays out through the modern flex model ({@link #row}/{@link #column}).
 * {@code flowRow()}/{@code flowColumn()} only set RenderLib's legacy child-layout flag, which leaves
 * {@code justifyContent}, {@code alignItems} and {@code flexGrow} inert - the reason to go through
 * the two helpers here rather than reaching for those methods directly.
 *
 * <p>The rail and the card list refresh independently ({@link #refreshRail()} / {@link
 * #refreshContent()}) instead of rebuilding the tree, which keeps the search field's focus and caret
 * alive while you type. Only a theme change rebuilds everything.
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
    private static final float RAIL_INNER = RAIL_W - RAIL_PAD * 2f;   // 218
    private static final float RAIL_LABEL_H = 16f;
    private static final float RAIL_FOOTER_H = 28f;
    private static final float RAIL_GAP = 8f;
    /** Height left for the category buttons once the label, divider and footer have their share. */
    private static final float RAIL_LIST_H =
            BODY_H - RAIL_PAD * 2f - RAIL_LABEL_H - 1f - RAIL_FOOTER_H - RAIL_GAP * 3f;

    private static final float DIVIDER_W = 1f;
    private static final float CONTENT_W = PANEL_W - RAIL_W - DIVIDER_W;   // 909
    private static final float CONTENT_PAD = 18f;
    private static final float CONTENT_INNER = CONTENT_W - CONTENT_PAD * 2f;   // 873
    private static final float TITLE_H = 26f;
    private static final float LIST_H = BODY_H - CONTENT_PAD * 2f - TITLE_H - 10f;
    private static final float SCROLLBAR_GUTTER = 14f;
    private static final float CARD_W = CONTENT_INNER - SCROLLBAR_GUTTER;    // 859
    private static final float CARD_PAD_X = 18f;
    private static final float CARD_INNER = CARD_W - CARD_PAD_X * 2f;        // 823

    private static final float ROW_H = 34f;
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
        panel = column(PANEL_W, PANEL_H, 0f)
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

        railPane = column(RAIL_W, BODY_H, RAIL_GAP).padding(RAIL_PAD)
                .backgroundColor(t.surfaceAlt());
        contentPane = column(CONTENT_W, BODY_H, 10f).padding(CONTENT_PAD);

        ContainerComponent body = row(PANEL_W, BODY_H, 0f).alignItems(GuiAlignment.STRETCH);
        body.add(railPane);
        body.add(new ContainerComponent().size(DIVIDER_W, BODY_H).backgroundColor(t.border()));
        body.add(contentPane);
        panel.add(body);

        refreshRail();
        refreshContent();
    }

    // --- header -----------------------------------------------------------------------------------

    private ContainerComponent headerBar() {
        ContainerComponent bar = row(PANEL_W, HEADER_H, 12f)
                .padding(0f, 22f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);

        ContainerComponent brand = row(0f, HEADER_H, 10f).autoWidth().flexShrink(0f);
        brand.add(text("⚡", t.accentTo(), 20f).centerTextVertically().height(HEADER_H));
        brand.add(text("DiegoAddons", t.text(), 24f).centerTextVertically().height(HEADER_H));
        bar.add(brand);

        ContainerComponent right = row(0f, HEADER_H, 10f).autoWidth().flexShrink(0f)
                .justifyContent(GuiAlignment.END);

        searchField = new TextInputComponent()
                .value(query)
                .placeholder("Search all " + ModuleManager.all().size() + " modules...")
                .textScalePixels(14f)
                .onChange(s -> {
                    query = s == null ? "" : s;
                    refreshContent();
                });
        searchField.size(280f, 34f).padding(0f, 12f).cornerRadius(10f)
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
            Minecraft.getInstance().setScreen(new HudEditorScreen());
        }));
        right.add(iconButton("✕", this::close));
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
        railPane.add(text("CATEGORIES", t.textFaint(), 11f)
                .centerTextVertically().size(RAIL_INNER, RAIL_LABEL_H));

        ContainerComponent list = column(RAIL_INNER, RAIL_LIST_H, 6f);
        for (Category c : ModuleManager.categories()) {
            list.add(categoryRow(c));
        }
        railPane.add(list);

        railPane.add(new ContainerComponent().size(RAIL_INNER, 1f).backgroundColor(t.border()));

        ContainerComponent footer = row(RAIL_INNER, RAIL_FOOTER_H, 8f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        footer.add(text(enabledCount() + " enabled", t.textMuted(), 12f)
                .centerTextVertically().height(RAIL_FOOTER_H).flexGrow(1f));
        footer.add(keyChip(openKeyName()));
        railPane.add(footer);
    }

    private ButtonComponent categoryRow(Category c) {
        boolean sel = c == selectedCategory;
        int fg = sel ? t.accentText() : t.text();
        int countFg = sel ? t.accentText() : t.textFaint();
        float h = 38f;

        ButtonComponent b = plainButton(sel ? t.accent() : 0x00000000, () -> {
            selectedCategory = c;
            expandedModule = null;
            refreshRail();
            refreshContent();
        });
        asRow(b, RAIL_INNER, h, 10f).padding(0f, 13f).cornerRadius(10f);

        b.add(text(icon(c), fg, 13f).centerTextVertically().size(16f, h).flexShrink(0f));
        b.add(text(c.display, fg, 14f).centerTextVertically().height(h).flexGrow(1f));
        b.add(text(String.valueOf(ModuleManager.modulesIn(c).size()), countFg, 11f)
                .centerTextVertically().height(h).flexShrink(0f));
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
        ContainerComponent title = row(CONTENT_INNER, TITLE_H, 10f);
        title.add(text(query.isEmpty() && selectedCategory != null ? selectedCategory.display : "Search",
                t.text(), 19f).centerTextVertically().height(TITLE_H).flexGrow(1f));
        title.add(text(modules.size() + (modules.size() == 1 ? " module" : " modules"),
                t.textFaint(), 12f).centerTextVertically().height(TITLE_H)
                .textAlignment(GuiTextAlignment.RIGHT).flexShrink(0f));
        contentPane.add(title);

        if (modules.isEmpty()) {
            contentPane.add(text("Nothing here. Try another search.", t.textFaint(), 14f)
                    .width(CONTENT_INNER));
            return;
        }

        ScrollContainerComponent list = new ScrollContainerComponent();
        list.size(CONTENT_INNER, LIST_H);
        list.flowColumn();
        list.display(GuiDisplay.FLEX);
        list.flexDirection(GuiFlexDirection.COLUMN);
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

    private ContainerComponent moduleCard(Module m) {
        boolean expanded = m == expandedModule;
        ContainerComponent card = column(CARD_W, 0f, 12f).autoHeight()
                .padding(14f, CARD_PAD_X).cornerRadius(12f)
                .backgroundColor(expanded ? t.elevated() : t.surfaceAlt())
                .borderWidth(1f).borderColor(expanded ? t.accent() : t.border());

        // The toggle sits outside the pressable area, so flipping a module never expands its card.
        ContainerComponent head = row(CARD_INNER, 0f, 14f).autoHeight();
        float textW = CARD_INNER - TOGGLE_W - 14f;

        ButtonComponent open = plainButton(0x00000000, () -> {
            expandedModule = expanded ? null : m;
            refreshContent();
        });
        asColumn(open, textW, 0f, 3f).autoHeight().padding(2f, 0f).cornerRadius(8f)
                .alignItems(GuiAlignment.STRETCH);
        open.add(text(m.name, t.text(), 15f).width(textW));
        if (m.description != null && !m.description.isEmpty()) {
            open.add(text(m.description, t.textMuted(), 12f).width(textW));
        }
        head.add(open);
        head.add(toggle(m.isEnabled(), v -> {
            ModuleManager.setEnabled(m, v);
            refreshRail();
        }));
        card.add(head);

        if (expanded) {
            card.add(new ContainerComponent().size(CARD_INNER, 1f).backgroundColor(t.border()));
            List<Setting> settings = m.settings();
            if (settings.isEmpty()) {
                card.add(text("No settings for this feature.", t.textFaint(), 13f).width(CARD_INNER));
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
            ContainerComponent r = row(CARD_INNER, ROW_H, 12f)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            r.add(text(bs.name, t.text(), 14f).centerTextVertically().height(ROW_H).flexGrow(1f));
            r.add(toggle(bs.get(), bs::set));
            return r;
        }

        if (s instanceof NumberSetting ns) {
            ContainerComponent box = column(CARD_INNER, 0f, 6f).autoHeight();
            TextComponent value = text(ns.display(), t.textMuted(), 12f)
                    .centerTextVertically().height(20f).textAlignment(GuiTextAlignment.RIGHT);
            ContainerComponent top = row(CARD_INNER, 20f, 10f)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            top.add(text(ns.name, t.text(), 14f).centerTextVertically().height(20f).flexGrow(1f));
            top.add(value.flexShrink(0f));
            box.add(top);
            box.add(new SliderComponent().min(ns.min).max(ns.max).step(ns.step).value(ns.get())
                    .valueFormatter(v -> String.format(Locale.ROOT, "%." + ns.decimals + "f", v))
                    .onChange(v -> {
                        ns.set(v);
                        // Update the read-out in place; rebuilding here would kill the drag.
                        value.text(ns.display());
                    })
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

        return new ContainerComponent().size(CARD_INNER, 0f);
    }

    /** A full-width row that reads "name ... value" and runs an action when clicked. */
    private ContainerComponent valueButton(String name, String value, Runnable action) {
        ContainerComponent wrap = row(CARD_INNER, ROW_H, 0f);
        ButtonComponent b = plainButton(t.surface(), action);
        asRow(b, CARD_INNER, ROW_H, 10f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN)
                .padding(0f, 12f).cornerRadius(8f)
                .borderWidth(1f).borderColor(t.border());
        b.add(text(name, t.text(), 14f).centerTextVertically().height(ROW_H).flexGrow(1f));
        b.add(text(value, t.textMuted(), 13f).centerTextVertically().height(ROW_H)
                .textAlignment(GuiTextAlignment.RIGHT).flexShrink(0f));
        wrap.add(b);
        return wrap;
    }

    // --- small shared pieces ----------------------------------------------------------------------

    /** A flex row. Width or height of {@code 0} means "leave it to the layout". */
    private static ContainerComponent row(float width, float height, float gap) {
        return sized(new ContainerComponent(), width, height)
                .flowRow()      // legacy direction, in case a container ever falls back to RETAINED
                .display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.ROW)
                .alignItems(GuiAlignment.CENTER)
                .columnGap(GuiLength.pixels(gap))
                .gap(gap);
    }

    private static ContainerComponent column(float width, float height, float gap) {
        return sized(new ContainerComponent(), width, height)
                .flowColumn()
                .display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.COLUMN)
                .alignItems(GuiAlignment.START)
                .rowGap(GuiLength.pixels(gap))
                .gap(gap);
    }

    /** Turns an already-created component (a button, say) into a flex row. */
    private static <T extends ContainerComponent> T asRow(T c, float width, float height, float gap) {
        sized(c, width, height)
                .flowRow()
                .display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.ROW)
                .alignItems(GuiAlignment.CENTER)
                .columnGap(GuiLength.pixels(gap))
                .gap(gap);
        return c;
    }

    private static <T extends ContainerComponent> T asColumn(T c, float width, float height, float gap) {
        sized(c, width, height)
                .flowColumn()
                .display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.COLUMN)
                .alignItems(GuiAlignment.START)
                .rowGap(GuiLength.pixels(gap))
                .gap(gap);
        return c;
    }

    private static ContainerComponent sized(ContainerComponent c, float width, float height) {
        if (width > 0f) {
            c.width(width);
        }
        if (height > 0f) {
            c.height(height);
        }
        return c;
    }

    private static TextComponent text(String s, int color, float scale) {
        return new TextComponent().text(s).color(color).textScalePixels(scale);
    }

    private ToggleSwitchComponent toggle(boolean value, java.util.function.Consumer<Boolean> onChange) {
        ToggleSwitchComponent sw = new ToggleSwitchComponent().value(value)
                .trackOnColor(t.accent()).trackOffColor(t.border())
                .knobRadius(TOGGLE_KNOB_R)
                .onChange(onChange);
        sw.size(TOGGLE_W, TOGGLE_H).flexShrink(0f);
        return sw;
    }

    /**
     * A {@link ButtonComponent} stripped of its stock label, gradient, shadow and hover colours so it
     * can be used as a themed, clickable row that holds its own children.
     */
    private ButtonComponent plainButton(int background, Runnable action) {
        ButtonComponent b = new ButtonComponent();
        b.clearChildren();          // drop the built-in centred label; we supply the content
        b.onPress(action);
        b.backgroundColor(background)
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

    /** Roughly how wide a run of text renders at {@code scale}, for auto-sized pills. */
    private static float textWidth(String s, float scale) {
        return Minecraft.getInstance().font.width(s) * (scale / 9f);
    }

    private ButtonComponent pill(String label, Runnable action) {
        float h = 34f;
        float w = textWidth(label, 13f) + 28f;
        ButtonComponent b = plainButton(t.elevated(), action);
        asRow(b, w, h, 0f).justifyContent(GuiAlignment.CENTER).cornerRadius(10f).flexShrink(0f);
        b.add(text(label, t.text(), 13f).centerTextVertically()
                .size(w - 28f, h).textAlignment(GuiTextAlignment.CENTER));
        return b;
    }

    private ButtonComponent iconButton(String glyph, Runnable action) {
        float s = 34f;
        ButtonComponent b = plainButton(t.elevated(), action);
        asRow(b, s, s, 0f).justifyContent(GuiAlignment.CENTER).cornerRadius(10f).flexShrink(0f);
        b.add(text(glyph, t.text(), 13f).centerTextVertically().size(s, s)
                .textAlignment(GuiTextAlignment.CENTER));
        return b;
    }

    private ContainerComponent keyChip(String key) {
        float w = textWidth(key, 11f) + 16f;
        ContainerComponent chip = row(w, 20f, 0f).justifyContent(GuiAlignment.CENTER)
                .cornerRadius(5f).flexShrink(0f)
                .backgroundColor(t.elevated()).borderColor(t.border()).borderWidth(1f);
        chip.add(text(key, t.textMuted(), 11f).centerTextVertically().size(w, 20f)
                .textAlignment(GuiTextAlignment.CENTER));
        return chip;
    }
}
