package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiOverflowMode;
import com.render.api.gui.GuiView;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.SliderComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.ToggleSwitchComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiPositionType;
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

import java.util.List;
import java.util.Locale;

/**
 * The DiegoAddons menu, built fresh on RenderLib's retained {@link GuiView}. A centred panel with a
 * header (brand, theme, close), a left category rail, and a content area of module cards. Each card
 * shows the module's name, description and an enable toggle; clicking a card expands its settings
 * inline underneath. Painted with the addon's {@link Theme} colours through RenderLib's {@code int}
 * ARGB overloads; selection/theme changes rebuild the tree (not per frame).
 */
public class DiegoClickGuiView extends GuiView {
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 1080f;

    private static final float PANEL_W = 1220f;
    private static final float PANEL_H = 820f;
    private static final float HEADER_H = 72f;
    private static final float BODY_H = PANEL_H - HEADER_H;

    private static final float RAIL_W = 260f;
    private static final float CONTENT_W = PANEL_W - RAIL_W;   // 960
    private static final float LIST_W = CONTENT_W - 36f;       // inside content padding
    private static final float CARD_W = LIST_W - 16f;          // room for the scrollbar
    private static final float CARD_INNER = CARD_W - 36f;      // inside card padding

    private Theme t = Themes.current();
    private Category selectedCategory;
    private Module expandedModule;
    private ContainerComponent panel;

    @Override
    protected void build() {
        List<Category> cats = ModuleManager.categories();
        if (selectedCategory == null && !cats.isEmpty()) {
            selectedCategory = cats.get(0);
        }
        panel = new ContainerComponent().flowColumn().size(PANEL_W, PANEL_H)
                .position(GuiPositionType.ABSOLUTE)
                .x((DESIGN_W - PANEL_W) / 2f)
                .y((DESIGN_H - PANEL_H) / 2f);
        root().add(panel);
        rebuild();
    }

    private void rebuild() {
        t = Themes.current();
        panel.clearChildren();
        panel.backgroundColor(t.surface()).cornerRadius(16f).borderColor(t.border()).borderWidth(1f);
        panel.add(headerBar());
        panel.add(new ContainerComponent().flowRow().size(PANEL_W, BODY_H)
                .add(categoryRail())
                .add(contentArea()));
    }

    // --- header ---------------------------------------------------------------------------------

    private ContainerComponent headerBar() {
        ContainerComponent bar = new ContainerComponent().flowRow()
                .width(PANEL_W).height(HEADER_H).padding(0f, 22f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN)
                .alignItems(GuiAlignment.CENTER);

        ContainerComponent brand = new ContainerComponent().flowRow().gap(10f)
                .alignItems(GuiAlignment.CENTER);
        brand.add(new TextComponent().text("⚡").color(t.accent()).textScalePixels(24f));
        brand.add(new TextComponent().text("DiegoAddons").color(t.text()).textScalePixels(26f));
        bar.add(brand);

        ContainerComponent right = new ContainerComponent().flowRow().gap(10f)
                .alignItems(GuiAlignment.CENTER);
        right.add(pill("Theme: " + t.name(), () -> {
            Themes.select(nextTheme());
            rebuild();
        }));
        right.add(pill("HUD Editor", () -> {
            close();
            Minecraft.getInstance().setScreen(new HudEditorScreen());
        }));
        right.add(new ButtonComponent().text("✕")
                .onPress(this::close)
                .size(40f, 40f).cornerRadius(10f).backgroundColor(t.elevated()).color(t.text()));
        bar.add(right);
        return bar;
    }

    private ButtonComponent pill(String text, Runnable action) {
        return (ButtonComponent) new ButtonComponent().text(text).onPress(action)
                .cornerRadius(10f).padding(10f, 16f).backgroundColor(t.elevated()).color(t.text());
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

    // --- category rail --------------------------------------------------------------------------

    private ContainerComponent categoryRail() {
        ContainerComponent rail = new ContainerComponent().flowColumn().gap(6f).padding(16f)
                .width(RAIL_W).height(BODY_H)
                .backgroundColor(t.surfaceAlt());
        rail.add(new TextComponent().text("CATEGORIES").color(t.textFaint()).textScalePixels(12f));
        for (Category c : ModuleManager.categories()) {
            boolean sel = c == selectedCategory;
            rail.add(new ButtonComponent().text(c.display)
                    .onPress(() -> {
                        selectedCategory = c;
                        expandedModule = null;
                        rebuild();
                    })
                    .width(RAIL_W - 32f).cornerRadius(10f).padding(11f, 14f)
                    .backgroundColor(sel ? t.accent() : 0x00000000)
                    .color(sel ? t.accentText() : t.text()));
        }
        return rail;
    }

    // --- content area ---------------------------------------------------------------------------

    private ContainerComponent contentArea() {
        ContainerComponent content = new ContainerComponent().flowColumn().gap(10f).padding(18f)
                .width(CONTENT_W).height(BODY_H);
        content.add(new TextComponent()
                .text(selectedCategory == null ? "" : selectedCategory.display)
                .color(t.text()).textScalePixels(20f));
        if (selectedCategory == null) {
            return content;
        }
        ScrollContainerComponent list = scrollList(LIST_W, BODY_H - 60f);
        for (Module m : ModuleManager.modulesIn(selectedCategory)) {
            list.add(moduleCard(m));
        }
        content.add(list);
        return content;
    }

    private ContainerComponent moduleCard(Module m) {
        boolean expanded = m == expandedModule;
        ContainerComponent card = new ContainerComponent().flowColumn().gap(10f)
                .width(CARD_W).padding(16f, 18f).cornerRadius(12f)
                .backgroundColor(expanded ? t.elevated() : t.surfaceAlt())
                .borderColor(expanded ? t.accent() : t.border()).borderWidth(1f);

        ContainerComponent head = new ContainerComponent().flowRow().width(CARD_INNER)
                .justifyContent(GuiAlignment.SPACE_BETWEEN).alignItems(GuiAlignment.CENTER);
        ContainerComponent titleBox = new ContainerComponent().flowColumn().gap(3f);
        titleBox.add(new ButtonComponent().text(m.name)
                .onPress(() -> {
                    expandedModule = expanded ? null : m;
                    rebuild();
                })
                .backgroundColor(0x00000000).color(t.text()));
        if (m.description != null && !m.description.isEmpty()) {
            titleBox.add(new TextComponent().text(m.description)
                    .color(t.textMuted()).textScalePixels(12f).width(CARD_INNER - 90f));
        }
        head.add(titleBox);
        head.add(new ToggleSwitchComponent().value(m.isEnabled())
                .trackOnColor(t.accent()).onChange(v -> ModuleManager.setEnabled(m, v)));
        card.add(head);

        if (expanded) {
            List<Setting> settings = m.settings();
            card.add(new ContainerComponent().width(CARD_INNER).height(1f)
                    .backgroundColor(t.border()));
            if (settings.isEmpty()) {
                card.add(new TextComponent().text("No settings for this feature.")
                        .color(t.textFaint()).textScalePixels(13f));
            } else {
                for (Setting s : settings) {
                    card.add(settingRow(s));
                }
            }
        }
        return card;
    }

    private ScrollContainerComponent scrollList(float width, float height) {
        ScrollContainerComponent list = new ScrollContainerComponent();
        list.flowColumn();
        list.gap(10f);
        list.width(width);
        list.height(height);
        list.overflowY(GuiOverflowMode.AUTO);
        return list;
    }

    // --- setting controls -----------------------------------------------------------------------

    private ContainerComponent settingRow(Setting s) {
        if (s instanceof BooleanSetting bs) {
            ContainerComponent row = rowBase();
            row.add(label(bs.name));
            row.add(new ToggleSwitchComponent().value(bs.get())
                    .trackOnColor(t.accent()).onChange(bs::set));
            return row;
        }
        if (s instanceof NumberSetting ns) {
            ContainerComponent box = new ContainerComponent().flowColumn().gap(6f).width(CARD_INNER);
            box.add(label(ns.name + ":  " + ns.display()));
            box.add(new SliderComponent().min(ns.min).max(ns.max).step(ns.step).value(ns.get())
                    .valueFormatter(v -> String.format(Locale.ROOT, "%." + ns.decimals + "f", v))
                    .onChange(v -> {
                        ns.set(v);
                        rebuild();
                    })
                    .width(CARD_INNER).trackColor(t.surface()).fillColor(t.accent()).thumbColor(t.accentText()));
            return box;
        }
        if (s instanceof CycleSetting cs) {
            return actionRow(cs.name + ":  " + cs.label(), () -> {
                cs.cycle();
                rebuild();
            });
        }
        if (s instanceof ActionSetting as) {
            return actionRow(as.action, as::run);
        }
        if (s instanceof KeybindSetting ks) {
            return actionRow(ks.name + ":  " + ks.display(), () -> {
                ks.clear();
                rebuild();
            });
        }
        return rowBase();
    }

    private ContainerComponent actionRow(String text, Runnable action) {
        ContainerComponent row = new ContainerComponent().flowRow().width(CARD_INNER);
        row.add(new ButtonComponent().text(text).onPress(action)
                .width(CARD_INNER).cornerRadius(8f).padding(9f, 12f)
                .backgroundColor(t.surface()).color(t.text()));
        return row;
    }

    private ContainerComponent rowBase() {
        return new ContainerComponent().flowRow().width(CARD_INNER)
                .justifyContent(GuiAlignment.SPACE_BETWEEN).alignItems(GuiAlignment.CENTER);
    }

    private TextComponent label(String s) {
        return new TextComponent().text(s).color(t.text()).textScalePixels(14f);
    }
}
