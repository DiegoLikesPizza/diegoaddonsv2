package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiGradient;
import com.render.api.gui.GuiOverflowMode;
import com.render.api.gui.GuiView;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.SliderComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.TextInputComponent;
import com.render.api.gui.ToggleSwitchComponent;
import com.render.api.gui.layout.GuiAlignment;
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
 * The DiegoAddons menu, built on RenderLib's retained {@link GuiView} as a three-pane master-detail
 * screen: a category rail, a module list, and a settings pane for the selected module.
 *
 * <p>Opening a module never reflows the list - only the right-hand pane swaps - so the list stays
 * where you left it and a module can carry as many settings as it likes; the pane scrolls on its own.
 * The header carries a search field that filters every module in every category at once.
 *
 * <p>Each pane refreshes independently ({@link #refreshRail()} / {@link #refreshList()} /
 * {@link #refreshDetail()}) rather than rebuilding the whole tree, which keeps the search field's
 * focus and caret alive while you type. Only a theme change rebuilds everything.
 */
public class DiegoClickGuiView extends GuiView {
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 1080f;

    private static final float PANEL_W = 1160f;
    private static final float PANEL_H = 760f;
    private static final float HEADER_H = 66f;
    private static final float BODY_H = PANEL_H - HEADER_H;   // 694

    private static final float RAIL_W = 250f;
    private static final float LIST_W = 360f;
    private static final float DIVIDER_W = 1f;
    private static final float DETAIL_W = PANEL_W - RAIL_W - LIST_W - DIVIDER_W * 2f;  // 548

    private static final float RAIL_INNER = RAIL_W - 32f;     // rail padding 16
    private static final float LIST_INNER = LIST_W - 28f;     // list padding 14
    private static final float DETAIL_INNER = DETAIL_W - 44f; // detail padding 22

    /** One glyph per {@link Category}, in enum order, for the rail. */
    private static final String[] CATEGORY_ICONS = {"◆", "▤", "⚔", "⛏", "✿", "≈", "☠", "⚙"};

    private Theme t = Themes.current();
    private Category selectedCategory;
    private Module selectedModule;
    private String query = "";

    private ContainerComponent panel;
    private ContainerComponent railPane;
    private ContainerComponent listPane;
    private ContainerComponent detailPane;
    private TextInputComponent searchField;

    @Override
    protected void build() {
        panel = new ContainerComponent().flowColumn().size(PANEL_W, PANEL_H)
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

        railPane = new ContainerComponent().flowColumn().gap(6f).padding(16f)
                .width(RAIL_W).height(BODY_H).backgroundColor(t.surfaceAlt());
        listPane = new ContainerComponent().flowColumn().gap(8f).padding(14f)
                .width(LIST_W).height(BODY_H);
        detailPane = new ContainerComponent().flowColumn().gap(14f).padding(22f)
                .width(DETAIL_W).height(BODY_H);

        panel.add(new ContainerComponent().flowRow().size(PANEL_W, BODY_H)
                .add(railPane)
                .add(verticalDivider())
                .add(listPane)
                .add(verticalDivider())
                .add(detailPane));

        refreshRail();
        refreshList();
        refreshDetail();
    }

    private ContainerComponent verticalDivider() {
        return new ContainerComponent().width(DIVIDER_W).height(BODY_H).backgroundColor(t.border());
    }

    // --- header -----------------------------------------------------------------------------------

    private ContainerComponent headerBar() {
        ContainerComponent bar = new ContainerComponent().flowRow()
                .width(PANEL_W).height(HEADER_H).padding(0f, 22f).gap(12f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN)
                .alignItems(GuiAlignment.CENTER);

        ContainerComponent brand = new ContainerComponent().flowRow().gap(10f)
                .alignItems(GuiAlignment.CENTER);
        brand.add(new TextComponent().text("⚡").color(t.accentTo()).textScalePixels(20f));
        brand.add(new TextComponent().text("DiegoAddons").color(t.text()).textScalePixels(24f));
        bar.add(brand);

        ContainerComponent right = new ContainerComponent().flowRow().gap(10f)
                .alignItems(GuiAlignment.CENTER);

        searchField = new TextInputComponent()
                .value(query)
                .placeholder("Search all " + ModuleManager.all().size() + " modules...")
                .textScalePixels(14f)
                .onChange(s -> {
                    query = s == null ? "" : s;
                    refreshList();
                });
        searchField.size(300f, 36f).padding(0f, 12f).cornerRadius(10f)
                .backgroundColor(t.surfaceAlt())
                .borderColor(t.border()).borderWidth(1f)
                .color(t.text());
        right.add(searchField);

        right.add(pill("Theme - " + t.name(), () -> {
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
        railPane.add(new TextComponent().text("CATEGORIES").color(t.textFaint()).textScalePixels(11f));

        for (Category c : ModuleManager.categories()) {
            railPane.add(categoryRow(c));
        }

        railPane.add(new ContainerComponent().width(RAIL_INNER).flexGrow(1f));
        railPane.add(new ContainerComponent().width(RAIL_INNER).height(1f).backgroundColor(t.border()));

        ContainerComponent footer = new ContainerComponent().flowRow().width(RAIL_INNER)
                .padding(4f, 2f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN).alignItems(GuiAlignment.CENTER);
        footer.add(new TextComponent().text(enabledCount() + " enabled")
                .color(t.textMuted()).textScalePixels(12f));
        footer.add(keyChip(openKeyName()));
        railPane.add(footer);
    }

    private ButtonComponent categoryRow(Category c) {
        boolean sel = query.isEmpty() && c == selectedCategory;
        int fg = sel ? t.accentText() : t.text();
        int countFg = sel ? t.accentText() : t.textFaint();

        ButtonComponent row = plainButton(sel ? t.accent() : 0x00000000, () -> {
            selectedCategory = c;
            selectedModule = null;
            clearSearch();
            refreshRail();
            refreshList();
            refreshDetail();
        });
        row.flowRow().gap(10f).alignItems(GuiAlignment.CENTER)
                .width(RAIL_INNER).height(38f).padding(0f, 13f).cornerRadius(10f);

        row.add(new TextComponent().text(icon(c)).color(fg).textScalePixels(13f).width(16f));
        row.add(new TextComponent().text(c.display).color(fg).textScalePixels(14f).flexGrow(1f));
        row.add(new TextComponent().text(String.valueOf(ModuleManager.modulesIn(c).size()))
                .color(countFg).textScalePixels(11f));
        return row;
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

    // --- module list ------------------------------------------------------------------------------

    private void refreshList() {
        listPane.clearChildren();

        List<Module> modules = visibleModules();
        String heading = query.isEmpty()
                ? (selectedCategory == null ? "" : selectedCategory.display + " - " + modules.size() + " modules")
                : modules.size() + (modules.size() == 1 ? " match" : " matches");
        listPane.add(new TextComponent().text(heading.toUpperCase(Locale.ROOT))
                .color(t.textFaint()).textScalePixels(11f).padding(0f, 4f));

        if (modules.isEmpty()) {
            listPane.add(new TextComponent().text("Nothing here. Try another search.")
                    .color(t.textFaint()).textScalePixels(13f).width(LIST_INNER));
            return;
        }

        ScrollContainerComponent list = new ScrollContainerComponent();
        list.flowColumn();
        list.gap(6f);
        list.width(LIST_INNER);
        list.height(BODY_H - 52f);
        list.overflowY(GuiOverflowMode.AUTO);
        for (Module m : modules) {
            list.add(moduleRow(m));
        }
        listPane.add(list);
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

    private ButtonComponent moduleRow(Module m) {
        boolean sel = m == selectedModule;
        float inner = LIST_INNER - 12f;   // room for the scrollbar gutter

        ButtonComponent row = plainButton(sel ? t.elevated() : t.surfaceAlt(), () -> {
            selectedModule = m;
            refreshList();
            refreshDetail();
        });
        row.flowRow().gap(10f).alignItems(GuiAlignment.CENTER)
                .width(inner).padding(11f, 13f).cornerRadius(10f)
                .borderWidth(1f).borderColor(sel ? t.accent() : t.border());

        ContainerComponent titleBox = new ContainerComponent().flowColumn().gap(2f).flexGrow(1f);
        titleBox.add(new TextComponent().text(m.name).color(t.text()).textScalePixels(14f));
        String sub = query.isEmpty() ? subtitle(m) : m.category.display + " - " + subtitle(m);
        if (!sub.isEmpty()) {
            titleBox.add(new TextComponent().text(sub)
                    .color(t.textMuted()).textScalePixels(11f).width(inner - 76f));
        }
        row.add(titleBox);
        row.add(new ToggleSwitchComponent().value(m.isEnabled())
                .trackOnColor(t.accent()).trackOffColor(t.border())
                .onChange(v -> {
                    ModuleManager.setEnabled(m, v);
                    refreshRail();
                    if (m == selectedModule) {
                        refreshDetail();
                    }
                }));
        return row;
    }

    /** First clause of the description, so a list row stays one line. */
    private static String subtitle(Module m) {
        if (m.description == null || m.description.isEmpty()) {
            return "";
        }
        String s = m.description;
        int cut = s.indexOf(". ");
        if (cut > 0) {
            s = s.substring(0, cut);
        }
        return s.length() > 46 ? s.substring(0, 45).trim() + "..." : s;
    }

    // --- settings pane ----------------------------------------------------------------------------

    private void refreshDetail() {
        detailPane.clearChildren();

        if (selectedModule == null) {
            ContainerComponent empty = new ContainerComponent().flowColumn().gap(8f)
                    .width(DETAIL_INNER).height(BODY_H - 44f)
                    .justifyContent(GuiAlignment.CENTER).alignItems(GuiAlignment.CENTER);
            empty.add(new TextComponent().text("⚙").color(t.textFaint()).textScalePixels(28f));
            empty.add(new TextComponent().text("Pick a module to see its settings.")
                    .color(t.textFaint()).textScalePixels(14f));
            detailPane.add(empty);
            return;
        }

        Module m = selectedModule;

        ContainerComponent head = new ContainerComponent().flowRow().gap(14f).width(DETAIL_INNER)
                .justifyContent(GuiAlignment.SPACE_BETWEEN).alignItems(GuiAlignment.CENTER);
        ContainerComponent titleBox = new ContainerComponent().flowColumn().gap(4f).flexGrow(1f);
        titleBox.add(new TextComponent().text(m.name).color(t.text()).textScalePixels(19f));
        titleBox.add(new TextComponent().text(m.category.display)
                .color(t.textFaint()).textScalePixels(11f));
        if (m.description != null && !m.description.isEmpty()) {
            titleBox.add(new TextComponent().text(m.description)
                    .color(t.textMuted()).textScalePixels(12f).width(DETAIL_INNER - 70f));
        }
        head.add(titleBox);
        head.add(new ToggleSwitchComponent().value(m.isEnabled())
                .trackOnColor(t.accent()).trackOffColor(t.border())
                .onChange(v -> {
                    ModuleManager.setEnabled(m, v);
                    refreshRail();
                    refreshList();
                }));
        detailPane.add(head);
        detailPane.add(new ContainerComponent().width(DETAIL_INNER).height(1f)
                .backgroundColor(t.border()));

        List<Setting> settings = m.settings();
        if (settings.isEmpty()) {
            detailPane.add(new TextComponent().text("No settings for this feature.")
                    .color(t.textFaint()).textScalePixels(13f));
            return;
        }

        ScrollContainerComponent body = new ScrollContainerComponent();
        body.flowColumn();
        body.gap(14f);
        body.width(DETAIL_INNER);
        body.height(BODY_H - 150f);
        body.overflowY(GuiOverflowMode.AUTO);
        for (Setting s : settings) {
            body.add(settingRow(s));
        }
        detailPane.add(body);
    }

    private ContainerComponent settingRow(Setting s) {
        float inner = DETAIL_INNER - 14f;   // scrollbar gutter

        if (s instanceof BooleanSetting bs) {
            ContainerComponent row = new ContainerComponent().flowRow().width(inner).gap(12f)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN).alignItems(GuiAlignment.CENTER);
            row.add(new TextComponent().text(bs.name).color(t.text()).textScalePixels(14f).flexGrow(1f));
            row.add(new ToggleSwitchComponent().value(bs.get())
                    .trackOnColor(t.accent()).trackOffColor(t.border())
                    .onChange(bs::set));
            return row;
        }

        if (s instanceof NumberSetting ns) {
            ContainerComponent box = new ContainerComponent().flowColumn().gap(7f).width(inner);
            TextComponent value = new TextComponent().text(ns.display())
                    .color(t.textMuted()).textScalePixels(12f);
            ContainerComponent top = new ContainerComponent().flowRow().width(inner)
                    .justifyContent(GuiAlignment.SPACE_BETWEEN).alignItems(GuiAlignment.CENTER);
            top.add(new TextComponent().text(ns.name).color(t.text()).textScalePixels(14f));
            top.add(value);
            box.add(top);
            box.add(new SliderComponent().min(ns.min).max(ns.max).step(ns.step).value(ns.get())
                    .valueFormatter(v -> String.format(Locale.ROOT, "%." + ns.decimals + "f", v))
                    .onChange(v -> {
                        ns.set(v);
                        // Update the read-out in place; rebuilding here would kill the drag.
                        value.text(ns.display());
                    })
                    .width(inner)
                    .trackColor(t.surface()).fillColor(t.accent()).thumbColor(t.accentText()));
            return box;
        }

        if (s instanceof CycleSetting cs) {
            return valueButton(inner, cs.name, cs.label(), () -> {
                cs.cycle();
                refreshDetail();
            });
        }

        if (s instanceof KeybindSetting ks) {
            return valueButton(inner, ks.name, ks.display(), () -> {
                ks.clear();
                refreshDetail();
            });
        }

        if (s instanceof ActionSetting as) {
            return valueButton(inner, as.name, as.action, as::run);
        }

        return new ContainerComponent().width(inner);
    }

    /** A full-width row that reads "name ... value" and runs an action when clicked. */
    private ContainerComponent valueButton(float inner, String name, String value, Runnable action) {
        ContainerComponent wrap = new ContainerComponent().flowRow().width(inner);
        ButtonComponent b = plainButton(t.surfaceAlt(), action);
        b.flowRow().gap(10f).alignItems(GuiAlignment.CENTER)
                .justifyContent(GuiAlignment.SPACE_BETWEEN)
                .width(inner).padding(9f, 12f).cornerRadius(8f)
                .borderWidth(1f).borderColor(t.border());
        b.add(new TextComponent().text(name).color(t.text()).textScalePixels(14f).flexGrow(1f));
        b.add(new TextComponent().text(value).color(t.textMuted()).textScalePixels(13f));
        wrap.add(b);
        return wrap;
    }

    // --- small shared pieces ----------------------------------------------------------------------

    /**
     * A {@link ButtonComponent} stripped of its stock label, gradient and hover colours so it can be
     * used as a themed, clickable row that holds its own children.
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

    private ButtonComponent pill(String text, Runnable action) {
        ButtonComponent b = plainButton(t.elevated(), action);
        b.flowRow().alignItems(GuiAlignment.CENTER)
                .height(36f).padding(0f, 14f).cornerRadius(10f);
        b.add(new TextComponent().text(text).color(t.text()).textScalePixels(13f));
        return b;
    }

    private ButtonComponent iconButton(String glyph, Runnable action) {
        ButtonComponent b = plainButton(t.elevated(), action);
        b.flowRow().alignItems(GuiAlignment.CENTER).justifyContent(GuiAlignment.CENTER)
                .size(36f, 36f).cornerRadius(10f);
        b.add(new TextComponent().text(glyph).color(t.text()).textScalePixels(13f));
        return b;
    }

    private ContainerComponent keyChip(String key) {
        ContainerComponent chip = new ContainerComponent().flowRow()
                .alignItems(GuiAlignment.CENTER).justifyContent(GuiAlignment.CENTER)
                .height(20f).padding(0f, 7f).cornerRadius(5f)
                .backgroundColor(t.elevated()).borderColor(t.border()).borderWidth(1f);
        chip.add(new TextComponent().text(key).color(t.textMuted()).textScalePixels(11f));
        return chip;
    }

    private void clearSearch() {
        query = "";
        if (searchField != null) {
            searchField.value("");
        }
    }
}
