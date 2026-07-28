package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.SliderComponent;
import com.render.api.gui.SliderValueLabelPosition;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.GfsItem;

import java.util.List;

/**
 * The Auto GFS list: which SkyBlock items to keep topped up, and how low each may get first.
 *
 * <p>Items are named the way you read them on the item - "Ender Pearl", "Superboom TNT" - and the
 * id {@code /gfs} wants is derived from that. Typing the name you can see beats looking up an id you
 * cannot, and the few items where SkyBlock disagrees with its own naming take an override.
 *
 * <p>Each item carries its own threshold, because "top up below four" is right for pearls and wrong
 * for almost everything else.
 */
public class AutoGfsView extends DiegoView {
    private static final float PANEL_W = 980f;
    private static final float PANEL_H = 720f;
    private static final float ROW_H = 40f;

    private ScrollContainerComponent list;
    private String newName = "";

    public AutoGfsView() {
        super("Auto GFS", PANEL_W, PANEL_H);
    }

    private static List<GfsItem> items() {
        return ConfigManager.get().gfsItems;
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 12f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        body.add(textBox(GuiText.label(
                "Named as they appear in game. The sack id is worked out from the name.",
                t.textFaint(), 13f), inner, 22f));

        ContainerComponent add = row(inner, 12f).height(36f);
        add.add(field("", "Ender Pearl", 320f, s -> newName = s));
        ButtonComponent addBtn = clickable(t.accent(), () -> {
            if (!newName.isBlank()) {
                items().add(new GfsItem(newName.trim(), "", 4));
                ConfigManager.save();
                newName = "";
                rebuildView();
            }
        });
        asRow(addBtn, 110f, 0f).height(36f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER);
        addBtn.add(GuiText.label("Add", t.accentText(), 14f));
        add.add(addBtn);
        body.add(add);

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 22f - 36f - 24f);
        asColumn(list, inner, 8f);
        body.add(list);
        panel.add(body);
        fill(inner);
    }

    /** Rebuilds the whole view - the add field has to come back empty, so a pane refresh will not do. */
    private void rebuildView() {
        panel.clearChildren();
        build();
    }

    private void fill(float inner) {
        list.clearChildren();
        if (items().isEmpty()) {
            list.add(textBox(GuiText.label("Nothing yet. Add an item above.", t.textFaint(), 13f),
                    inner, 24f));
            return;
        }
        for (GfsItem item : List.copyOf(items())) {
            list.add(itemRow(item, inner - 24f));
        }
    }

    private ContainerComponent itemRow(GfsItem item, float inner) {
        ContainerComponent card = column(inner, 6f).padding(10f, 12f).cornerRadius(10f)
                .backgroundColor(GuiColors.of(t.surfaceAlt()))
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));

        ContainerComponent head = row(inner - 24f, 10f).height(ROW_H)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        head.add(textBox(GuiText.label(item.name, t.text(), 15f), 0f, ROW_H).flexGrow(1f));
        head.add(textBox(GuiText.label(item.sackId(), t.textFaint(), 12f), 0f, ROW_H));

        ButtonComponent onOff = clickable(item.enabled ? t.accent() : t.surface(), () -> {
            item.enabled = !item.enabled;
            ConfigManager.save();
            rebuildView();
        });
        asRow(onOff, 70f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        onOff.add(GuiText.label(item.enabled ? "On" : "Off",
                item.enabled ? t.accentText() : t.textMuted(), 13f));
        head.add(onOff);

        ButtonComponent remove = clickable(t.surface(), () -> {
            items().remove(item);
            ConfigManager.save();
            rebuildView();
        });
        asRow(remove, 90f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        remove.add(GuiText.label("Remove", t.textMuted(), 13f));
        head.add(remove);
        card.add(head);

        com.render.api.gui.TextComponent value =
                GuiText.label("Refill below " + item.threshold, t.textMuted(), 12f);
        card.add(textBox(value, inner - 24f, 18f));
        card.add(new SliderComponent()
                .min(1).max(64).step(1).value(item.threshold)
                .valueLabelPosition(SliderValueLabelPosition.OFF)
                .onChange(v -> {
                    item.threshold = (int) Math.round(v.doubleValue());
                    value.text("Refill below " + item.threshold);
                    ConfigManager.save();
                })
                .size(inner - 24f, 14f)
                .trackColor(GuiColors.of(t.surface()))
                .fillColor(GuiColors.of(t.accent()))
                .thumbColor(GuiColors.of(t.accentText())));
        return card;
    }
}
