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
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.ModuleManager;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.Setting;
import dev.diego.diegoaddons.module.StringSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The DiegoAddons menu: a category rail on the left and a list of module cards on the right, whose
 * settings expand inline underneath the card you clicked.
 *
 * <p>Built on RenderLib's retained {@link GuiView}. Three of its behaviours shape the code:
 * <ul>
 *   <li>Components default to {@link GuiDisplay#RETAINED}, where the legacy component-local layout
 *       runs and {@code justifyContent}/{@code alignItems}/{@code flexGrow} do nothing. Containers
 *       are therefore always made through {@link #row}/{@link #column}, which opt into
 *       {@code display(FLEX)}.</li>
 *   <li>Text sizes itself from RenderLib's shaping; see {@link GuiText}. A label with no width
 *       wraps, and a text component with a forced height spills its glyphs, so a row that needs a
 *       fixed height wraps its text in a box.</li>
 *   <li>{@link ButtonComponent} arrives with a stock size, padding, gradient, shadow and hover
 *       colours. {@link #clickable} strips all of that so a button can be a themed box of our own.</li>
 * </ul>
 *
 * <p>The rail and the card list refresh independently, so typing in the search field never rebuilds
 * the field out from under the caret. Only a theme change rebuilds the whole tree.
 */
public class DiegoClickGuiView extends GuiView {
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 1080f;

    private static final float PANEL_W = 1160f;
    private static final float PANEL_H = 760f;
    private static final float HEADER_H = 66f;
    /** The panel's 1px border sits inside its height, so the body gets what is left of it. */
    private static final float BORDER = 1f;
    private static final float BODY_H = PANEL_H - HEADER_H - BORDER * 2f;

    private static final float RAIL_W = 250f;
    private static final float RAIL_PAD = 16f;
    private static final float RAIL_INNER = RAIL_W - RAIL_PAD * 2f;

    private static final float CONTENT_W = PANEL_W - RAIL_W - 1f;
    private static final float CONTENT_PAD = 20f;
    private static final float CONTENT_INNER = CONTENT_W - CONTENT_PAD * 2f;
    private static final float TITLE_H = 36f;
    private static final float LIST_H = BODY_H - CONTENT_PAD * 2f - TITLE_H - 12f;

    private static final float CARD_W = CONTENT_INNER - 12f;      // room for the scrollbar
    private static final float CARD_PAD_X = 16f;
    private static final float CARD_INNER = CARD_W - CARD_PAD_X * 2f;

    private static final float ROW_H = 32f;
    private static final float CARD_PAD_Y = 14f;
    private static final float CARD_GAP = 10f;
    private static final float SETTINGS_PAD_TOP = 6f;
    private static final float SETTINGS_GAP = 10f;
    private static final float NAME_H = 20f;
    private static final float NAME_GAP = 3f;
    private static final float DESC_LINE_H = 15f;
    private static final float SLIDER_LABEL_H = 20f;
    private static final float SLIDER_GAP = 6f;
    private static final float SLIDER_H = 16f;
    private static final float TOGGLE_W = 40f;
    private static final float TOGGLE_H = 22f;

    /** One glyph per {@link Category}, in enum order. */
    private static final String[] ICONS = {"◆", "▤", "⚔", "⛏", "✿", "≈", "☠", "⚙"};

    private Theme t = Themes.current();
    private Category category;
    private Module expanded;
    private String query = "";

    private ContainerComponent panel;
    private ContainerComponent railPane;
    private ContainerComponent contentPane;
    private TextInputComponent search;
    private TextComponent titleLabel;
    private TextComponent countLabel;
    private ScrollContainerComponent list;
    private final java.util.Map<String, Card> cards = new java.util.LinkedHashMap<>();

    /** A built card: the clickable shell, its head row and the box its settings live in. */
    private static final class Card {
        final Module module;
        final ButtonComponent shell;
        final ContainerComponent head;
        final ContainerComponent settings;
        /** Heights the card was built to; the card is sized from these, not from a layout pass. */
        float settingsHeight;
        float headHeight;

        Card(Module module, ButtonComponent shell, ContainerComponent head,
             ContainerComponent settings) {
            this.module = module;
            this.shell = shell;
            this.head = head;
            this.settings = settings;
        }
    }

    @Override
    protected void build() {
        panel = column(PANEL_W, 0f).height(PANEL_H)
                .position(GuiPositionType.ABSOLUTE)
                .x((DESIGN_W - PANEL_W) / 2f)
                .y((DESIGN_H - PANEL_H) / 2f);
        root().add(panel);
        rebuildAll();
    }

    /** Whole-tree rebuild. Only a theme change needs this; everything else refreshes a pane. */
    private void rebuildAll() {
        t = Themes.current();
        List<Category> categories = ModuleManager.categories();
        if (category == null && !categories.isEmpty()) {
            category = categories.get(0);
        }

        panel.clearChildren();
        panel.backgroundColor(GuiColors.of(t.surface())).cornerRadius(16f)
                .borderWidth(BORDER).borderColor(GuiColors.of(t.border()))
                // Without this the rail's square corners paint over the panel's rounded ones and
                // spill past its bottom edge.
                .clipChildren(true);
        panel.add(header());

        railPane = column(RAIL_W, 6f).height(BODY_H).padding(RAIL_PAD)
                .backgroundColor(GuiColors.of(t.surfaceAlt()));
        contentPane = column(CONTENT_W, 12f).height(BODY_H).padding(CONTENT_PAD);

        ContainerComponent body = row(PANEL_W, 0f).height(BODY_H).alignItems(GuiAlignment.STRETCH);
        body.add(railPane);
        body.add(new ContainerComponent().size(1f, BODY_H).backgroundColor(GuiColors.of(t.border())));
        body.add(contentPane);
        panel.add(body);

        buildContentChrome();
        refreshRail();
        refreshList();
    }

    /**
     * The parts of the content pane that outlive a list refresh. The search field in particular:
     * rebuilding it while you type destroys the focused component, which drops the caret after every
     * character.
     */
    private void buildContentChrome() {
        ContainerComponent title = row(CONTENT_INNER, 12f).height(TITLE_H);
        titleLabel = GuiText.label("", t.text(), 19f, GuiText.TITLE).flexGrow(1f);
        countLabel = GuiText.label("", t.textFaint(), 12f);
        title.add(textBox(titleLabel, 0f, TITLE_H).flexGrow(1f));
        title.add(countLabel);

        search = new TextInputComponent()
                .value(query)
                .placeholder("Search modules...")
                .font(GuiText.BODY)
                .textScalePixels(13f)
                .onChange(s -> {
                    query = s == null ? "" : s;
                    refreshList();
                });
        search.size(240f, 32f).padding(8f, 12f).cornerRadius(9f).flexShrink(0f)
                .backgroundColor(GuiColors.of(t.surfaceAlt()))
                .borderWidth(1f).borderColor(GuiColors.of(t.border()))
                .color(GuiColors.of(t.text()));
        title.add(search);
        contentPane.add(title);

        list = new ScrollContainerComponent();
        list.size(CONTENT_INNER, LIST_H);
        list.flowColumn();
        list.display(GuiDisplay.FLEX);
        list.flexDirection(GuiFlexDirection.COLUMN);
        list.alignItems(GuiAlignment.START);
        list.rowGap(GuiLength.pixels(10f));
        list.gap(10f);
        list.overflowY(GuiOverflowMode.AUTO);
        contentPane.add(list);
    }

    // --- header ------------------------------------------------------------------------------------

    private ContainerComponent header() {
        ContainerComponent bar = row(PANEL_W, 12f).height(HEADER_H).padding(0f, 22f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);

        ContainerComponent brand = row(0f, 10f).autoWidth().flexShrink(0f);
        brand.add(GuiText.glyph("⚡", t.accentTo(), 18f));
        brand.add(GuiText.label("DiegoAddons", t.text(), 24f, GuiText.TITLE));
        brand.add(GuiText.label("v2", t.textFaint(), 11f));
        bar.add(brand);

        // Grows into the space the brand leaves and packs to the right edge, so an over-wide button
        // overflows towards the middle instead of out past the panel.
        ContainerComponent right = row(0f, 10f).flexGrow(1f).flexShrink(1f)
                .justifyContent(GuiAlignment.END);
        right.add(pill("Theme · " + t.name(), () -> {
            Themes.select(nextTheme());
            rebuildAll();
        }));
        right.add(pill("HUD Editor", () -> {
            close();
            dev.diego.diegoaddons.hud.HudElements.openPlacementScreen();
        }));
        right.add(pill("Reset HUD", () -> {
            dev.diego.diegoaddons.hud.HudElements.resetPositions();
        }));
        right.add(glyphPill("✕", this::close));
        bar.add(right);
        return bar;
    }

    private Theme nextTheme() {
        List<Theme> all = Themes.ALL;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).name().equalsIgnoreCase(t.name())) {
                return all.get((i + 1) % all.size());
            }
        }
        return all.get(0);
    }

    // --- category rail -----------------------------------------------------------------------------

    private void refreshRail() {
        railPane.clearChildren();
        railPane.add(GuiText.label("CATEGORIES", t.textFaint(), 11f));

        for (Category c : ModuleManager.categories()) {
            railPane.add(categoryRow(c));
        }

        railPane.add(new ContainerComponent().width(RAIL_INNER).flexGrow(1f).flexShrink(1f));
        railPane.add(new ContainerComponent().size(RAIL_INNER, 1f).backgroundColor(GuiColors.of(t.border())));

        ContainerComponent footer = row(RAIL_INNER, 8f).height(26f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        footer.add(GuiText.label(enabledCount() + " enabled", t.textMuted(), 12f));
        footer.add(chip(openKey(), t.textMuted()));
        railPane.add(footer);
    }

    private ButtonComponent categoryRow(Category c) {
        boolean selected = c == category;
        int fg = selected ? t.accentText() : t.text();

        ButtonComponent b = clickable(selected ? t.accent() : 0x00000000, () -> {
            category = c;
            expanded = null;
            clearSearch();
            refreshRail();
            refreshList();
        });
        asRow(b, RAIL_INNER, 10f).height(38f).padding(0f, 13f).cornerRadius(10f).flexShrink(0f);
        b.add(GuiText.glyph(icon(c), fg, 12f).width(16f));
        b.add(GuiText.label(c.display, fg, 14f).flexGrow(1f));
        b.add(GuiText.label(String.valueOf(ModuleManager.modulesIn(c).size()),
                selected ? t.accentText() : t.textFaint(), 11f));
        return b;
    }

    private static String icon(Category c) {
        return c.ordinal() < ICONS.length ? ICONS[c.ordinal()] : "•";
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

    private static String openKey() {
        return DiegoAddonsV2Client.OPEN_MENU == null
                ? "\\"
                : DiegoAddonsV2Client.OPEN_MENU.getTranslatedKeyMessage().getString();
    }

    // --- module cards ------------------------------------------------------------------------------

    /** Rebuilds the card list only. The title row and the search field are left alone. */
    private void refreshList() {
        List<Module> modules = visibleModules();
        titleLabel.text(query.isEmpty() && category != null ? category.display : "Search");
        countLabel.text(modules.size() + (modules.size() == 1 ? " module" : " modules"))
                .width(GuiText.width("00 modules", 12f));

        list.clearChildren();
        cards.clear();
        for (Module m : modules) {
            list.add(card(m));
        }
    }

    private List<Module> visibleModules() {
        if (!query.isEmpty()) {
            String q = query.toLowerCase(Locale.ROOT);
            List<Module> out = new ArrayList<>();
            for (Module m : ModuleManager.all()) {
                if (matches(m, q)) {
                    out.add(m);
                }
            }
            return out;
        }
        return category == null ? List.of() : ModuleManager.modulesIn(category);
    }

    private static boolean matches(Module m, String q) {
        return m.name.toLowerCase(Locale.ROOT).contains(q)
                || m.category.display.toLowerCase(Locale.ROOT).contains(q)
                || (m.description != null && m.description.toLowerCase(Locale.ROOT).contains(q));
    }

    /**
     * One module card. The whole card is the clickable surface - a hit area the size of the text
     * block is the difference between a card that feels clickable and one that does not - and the
     * toggle inside it swallows its own clicks.
     */
    private ContainerComponent card(Module m) {
        ButtonComponent shell = clickable(t.surfaceAlt(), () -> expand(m));
        asColumn(shell, CARD_W, CARD_GAP).padding(CARD_PAD_Y, CARD_PAD_X).cornerRadius(12f)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()))
                .flexShrink(0f);

        float textW = CARD_INNER - TOGGLE_W - 14f;
        float headH = headHeight(m, textW);
        ContainerComponent head = row(CARD_INNER, 14f);
        head.height(headH);
        ContainerComponent titleBox = column(textW, NAME_GAP);
        titleBox.add(textBox(GuiText.label(m.name, t.text(), 15f, GuiText.MEDIUM), textW, NAME_H));
        if (m.description != null && !m.description.isEmpty()) {
            titleBox.add(GuiText.wrapped(m.description, t.textMuted(), 12f, textW));
        }
        head.add(titleBox);
        head.add(toggle(m.isEnabled(), v -> {
            ModuleManager.setEnabled(m, v);
            refreshRail();
        }));
        shell.add(head);

        // Added to the card only while open. Kept as a hidden child instead, the card keeps the
        // height it was measured at and the settings spill out of its box.
        ContainerComponent settings = column(CARD_INNER, SETTINGS_GAP);
        settings.padding(SETTINGS_PAD_TOP, 0f, 0f, 0f);

        Card card = new Card(m, shell, head, settings);
        card.headHeight = headH;
        cards.put(m.id, card);
        fill(card, m == expanded);
        return shell;
    }

    /** Opens or closes a card without rebuilding the list, so no other toggle re-animates. */
    private void expand(Module m) {
        Module previous = expanded;
        expanded = m == expanded ? null : m;
        if (previous != null) {
            fill(cards.get(previous.id), false);
        }
        if (expanded != null) {
            fill(cards.get(expanded.id), true);
        }
    }

    private void fill(Card card, boolean open) {
        if (card == null) {
            return;
        }
        Module m = card.module;
        int background = open ? t.elevated() : t.surfaceAlt();
        card.shell.backgroundColor(GuiColors.of(background)).gradient(flat(background))
                .borderColor(GuiColors.of(open ? t.accent() : t.border()));

        ContainerComponent box = card.settings;
        box.clearChildren();
        card.shell.remove(box);
        if (!open) {
            card.settingsHeight = 0f;
            card.shell.height(CARD_PAD_Y * 2f + card.headHeight);
            return;
        }

        float total = SETTINGS_PAD_TOP + 1f;   // own padding, then the divider
        box.add(new ContainerComponent().size(CARD_INNER, 1f).backgroundColor(GuiColors.of(t.border())));
        List<Setting> rows = card.module.settings();
        if (rows.isEmpty()) {
            ContainerComponent empty = row(CARD_INNER, 0f);
            empty.height(ROW_H);
            empty.add(GuiText.wrapped("No settings for this feature.", t.textFaint(), 13f, CARD_INNER));
            box.add(empty);
            total += SETTINGS_GAP + ROW_H;
        } else {
            for (Setting s : rows) {
                // Every row is built with a height of its own, so the box's height is just their
                // sum - no table of per-setting heights kept anywhere else to fall out of date.
                Row built = setting(s);
                box.add(built.box());
                total += SETTINGS_GAP + built.height();
            }
        }
        card.settingsHeight = total;
        box.height(total);
        card.shell.height(CARD_PAD_Y * 2f + card.headHeight + CARD_GAP + total);
        card.shell.add(box);
    }

    // --- settings ----------------------------------------------------------------------------------

    /**
     * The name row plus the lines its description wraps to. Everything here is a height this class
     * sets itself; the only thing measured is how many lines the description needs, and that uses
     * the width helper that calibrates itself against RenderLib's own shaping.
     */
    private static float headHeight(Module m, float textW) {
        float h = NAME_H;
        if (m.description != null && !m.description.isEmpty()) {
            int lines = Math.max(1, (int) Math.ceil(GuiText.width(m.description, 12f) / textW));
            h += NAME_GAP + lines * DESC_LINE_H;
        }
        return Math.max(h, TOGGLE_H);
    }

    /** A built settings row and the height it was built at. */
    private record Row(ContainerComponent box, float height) {
    }

    private Row setting(Setting s) {
        if (s instanceof BooleanSetting bs) {
            ContainerComponent r = row(CARD_INNER, 12f).height(ROW_H).flexShrink(0f)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            r.add(GuiText.label(bs.name, t.text(), 14f).flexGrow(1f));
            r.add(toggle(bs.get(), bs::set));
            return new Row(r, ROW_H);
        }

        if (s instanceof NumberSetting ns) {
            ContainerComponent col = column(CARD_INNER, SLIDER_GAP).flexShrink(0f);
            TextComponent value = GuiText.label(ns.display(), t.textMuted(), 12f);
            ContainerComponent top = row(CARD_INNER, 10f).height(SLIDER_LABEL_H)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            top.add(GuiText.label(ns.name, t.text(), 14f).flexGrow(1f));
            top.add(value);
            col.add(top);
            col.add(new SliderComponent()
                    .min(ns.min).max(ns.max).step(ns.step).value(ns.get())
                    .valueLabelPosition(SliderValueLabelPosition.OFF)
                    .onChange(v -> {
                        ns.set(v);
                        value.text(ns.display());   // in place: a rebuild would drop the drag
                    })
                    .size(CARD_INNER, SLIDER_H)
                    .trackColor(GuiColors.of(t.surface())).fillColor(GuiColors.of(t.accent())).thumbColor(GuiColors.of(t.accentText())));
            float height = SLIDER_LABEL_H + SLIDER_GAP + SLIDER_H;
            col.height(height);
            return new Row(col, height);
        }

        if (s instanceof CycleSetting cs) {
            return valueRow(cs.name, cs.label(), () -> {
                cs.cycle();
                fill(cards.get(cs.owner.id), true);
            });
        }
        if (s instanceof KeybindSetting ks) {
            return valueRow(ks.name, ks.display(), () -> {
                ks.clear();
                fill(cards.get(ks.owner.id), true);
            });
        }
        if (s instanceof StringSetting str) {
            // The value is usually long (a sound id is a whole namespace), so it gets the row to
            // itself and the chooser is what a click opens.
            String shown = str.get().isBlank() ? "not set" : str.get();
            return valueRow(str.name, shown, str::choose);
        }
        if (s instanceof ColorSetting col) {
            return colorRow(col);
        }
        if (s instanceof ActionSetting as) {
            return valueRow(as.name, as.action, as::run);
        }
        return new Row(row(CARD_INNER, 0f).height(ROW_H), ROW_H);
    }

    /**
     * A colour: the mode on top, then the channels for however many colours that mode uses.
     *
     * <p>Rainbow has nothing to pick - it is the whole wheel, moving - so it shows no channels at
     * all. Gradient shows two sets, since two ends is what a gradient is. The swatch beside the mode
     * is the live answer to "what will this actually draw", which for a gradient is both ends and
     * for a rainbow is wherever the wheel is at that moment.
     */
    private Row colorRow(ColorSetting col) {
        ContainerComponent wrap = column(CARD_INNER, SLIDER_GAP).flexShrink(0f);
        float height = 0f;

        ContainerComponent top = row(CARD_INNER, 10f).height(ROW_H)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        top.add(GuiText.label(col.name, t.text(), 14f).flexGrow(1f));
        ContainerComponent swatchA = swatch(col.argbAt(0f));
        top.add(swatchA);
        if (col.mode() == ColorSetting.GRADIENT) {
            top.add(swatch(col.argbAt(1f)));
        }
        ButtonComponent mode = clickable(t.surface(), () -> {
            col.cycleMode();
            fill(cards.get(col.owner.id), true);   // the channel rows below depend on the mode
        });
        asRow(mode, 96f, 0f).height(ROW_H).cornerRadius(8f).padding(0f, 12f)
                .justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        mode.add(GuiText.label(ColorSetting.MODES[col.mode()], t.textMuted(), 13f));
        top.add(mode);
        wrap.add(top);
        height += ROW_H;

        if (col.mode() != ColorSetting.RAINBOW) {
            height += channels(wrap, col, true, swatchA);
        }
        if (col.mode() == ColorSetting.GRADIENT) {
            height += channels(wrap, col, false, null);
        }
        wrap.height(height);
        return new Row(wrap, height);
    }

    /** The three channel sliders of one colour; returns the height they took. */
    private float channels(ContainerComponent into, ColorSetting col, boolean first,
                           ContainerComponent live) {
        float height = 0f;
        String[] names = {"Red", "Green", "Blue"};
        int[] shifts = {16, 8, 0};
        for (int i = 0; i < names.length; i++) {
            int shift = shifts[i];
            int argb = first ? col.colorA() : col.colorB();
            ContainerComponent labelRow = row(CARD_INNER, 10f).height(SLIDER_LABEL_H)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            labelRow.add(GuiText.label((first ? "" : "To ") + names[i], t.textMuted(), 12f).flexGrow(1f));
            into.add(labelRow);
            into.add(new SliderComponent()
                    .min(0).max(255).step(1).value((argb >> shift) & 0xFF)
                    .valueLabelPosition(SliderValueLabelPosition.OFF)
                    .onChange(v -> {
                        int current = first ? col.colorA() : col.colorB();
                        int level = (int) Math.round(v.doubleValue());
                        int next = (current & ~(0xFF << shift)) | (level << shift) | 0xFF000000;
                        if (first) {
                            col.setColorA(next);
                            if (live != null) {
                                live.backgroundColor(GuiColors.of(next));   // no rebuild mid-drag
                            }
                        } else {
                            col.setColorB(next);
                        }
                    })
                    .size(CARD_INNER, SLIDER_H)
                    .trackColor(GuiColors.of(t.surface()))
                    .fillColor(GuiColors.of(t.accent()))
                    .thumbColor(GuiColors.of(t.accentText())));
            height += SLIDER_LABEL_H + SLIDER_GAP + SLIDER_H;
        }
        return height;
    }

    /** A small block of colour, so a number is also a thing you can look at. */
    private ContainerComponent swatch(int argb) {
        ContainerComponent box = new ContainerComponent();
        box.size(22f, 14f).cornerRadius(4f)
                .backgroundColor(GuiColors.of(argb))
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        return box;
    }

    /** A full-width "name … value" row that runs an action when clicked. */
    private Row valueRow(String name, String value, Runnable action) {
        ContainerComponent wrap = row(CARD_INNER, 0f).height(ROW_H).flexShrink(0f);
        ButtonComponent b = clickable(t.surface(), action);
        asRow(b, CARD_INNER, 10f).height(ROW_H).padding(0f, 12f).cornerRadius(8f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        b.add(GuiText.label(name, t.text(), 14f).flexGrow(1f));
        b.add(GuiText.label(value, t.textMuted(), 13f).textAlignment(GuiTextAlignment.RIGHT));
        wrap.add(b);
        return new Row(wrap, ROW_H);
    }

    // --- building blocks ---------------------------------------------------------------------------

    private static ContainerComponent row(float width, float gap) {
        return asRow(new ContainerComponent(), width, gap);
    }

    private static ContainerComponent column(float width, float gap) {
        return asColumn(new ContainerComponent(), width, gap);
    }

    private static <T extends ContainerComponent> T asRow(T c, float width, float gap) {
        if (width > 0f) {
            c.width(width);
        }
        c.flowRow()      // legacy direction, in case a container falls back to RETAINED
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

    /** Puts text in a box of a known height, which text alone cannot have without spilling. */
    private static ContainerComponent textBox(TextComponent text, float width, float height) {
        ContainerComponent b = row(width, 0f);
        b.height(height);
        b.add(text);
        return b;
    }

    private ToggleSwitchComponent toggle(boolean value, java.util.function.Consumer<Boolean> onChange) {
        ToggleSwitchComponent sw = new ToggleSwitchComponent().value(value)
                .trackOnColor(GuiColors.of(t.accent())).trackOffColor(GuiColors.of(t.border()))
                .knobRadius(7f)
                .onChange(onChange);
        sw.size(TOGGLE_W, TOGGLE_H).flexShrink(0f);
        return sw;
    }

    /**
     * A {@link ButtonComponent} stripped of its stock label, size, padding, gradient, shadow and
     * hover colours, leaving a themed clickable box that holds its own children.
     */
    private ButtonComponent clickable(int background, Runnable action) {
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

    /** A flat single-colour gradient, so the stock button gradient never shows through. */
    private static GuiGradient flat(int argb) {
        return new GuiGradient().startColor(GuiColors.of(argb)).endColor(GuiColors.of(argb)).angleDegrees(180f);
    }

    private ButtonComponent pill(String label, Runnable action) {
        ButtonComponent b = clickable(t.elevated(), action);
        asRow(b, 0f, 0f).autoSize().justifyContent(GuiAlignment.CENTER)
                .padding(9f, 14f).cornerRadius(10f).flexShrink(0f);
        b.add(GuiText.label(label, t.text(), 13f));
        return b;
    }

    /** A pill whose label is a symbol, so it uses Minecraft's font rather than Poppins. */
    private ButtonComponent glyphPill(String label, Runnable action) {
        ButtonComponent b = clickable(t.elevated(), action);
        asRow(b, 0f, 0f).autoSize().justifyContent(GuiAlignment.CENTER)
                .padding(9f, 14f).cornerRadius(10f).flexShrink(0f);
        b.add(GuiText.glyph(label, t.text(), 12f));
        return b;
    }

    private ContainerComponent chip(String label, int color) {
        ContainerComponent c = row(0f, 0f).autoSize().justifyContent(GuiAlignment.CENTER)
                .padding(3f, 8f).cornerRadius(5f).flexShrink(0f)
                .backgroundColor(GuiColors.of(t.elevated())).borderWidth(1f).borderColor(GuiColors.of(t.border()));
        c.add(GuiText.label(label, color, 11f));
        return c;
    }

    private void clearSearch() {
        query = "";
        if (search != null) {
            search.value("");
        }
    }
}
