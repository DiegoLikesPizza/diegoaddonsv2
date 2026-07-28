package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.WordReplacement;

import java.util.List;

/**
 * The find/replace list applied to chat and item text.
 *
 * <p>A pair can be switched off without being deleted, which is what you want for the ones you only
 * run sometimes.
 */
public class ReplaceWordsView extends DiegoView {
    private static final float PANEL_W = 900f;
    private static final float PANEL_H = 640f;
    private static final float ROW_H = 40f;

    private ScrollContainerComponent list;
    private String from = "";
    private String to = "";

    public ReplaceWordsView() {
        super("Replace words", PANEL_W, PANEL_H);
    }

    private static List<WordReplacement> items() {
        return ConfigManager.get().wordReplacements;
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 12f).height(height).padding(PAD);
        float inner = width - PAD * 2f;
        float boxW = (inner - 118f - 24f) / 2f;

        ContainerComponent add = row(inner, 12f).height(36f);
        add.add(field(from, "Find", boxW, s -> from = s));
        add.add(field(to, "Replace with", boxW, s -> to = s));
        ButtonComponent addBtn = clickable(t.accent(), () -> {
            if (!from.isBlank()) {
                items().add(new WordReplacement(from.trim(), to));
                ConfigManager.save();
                from = "";
                to = "";
                rebuildView();
            }
        });
        asRow(addBtn, 118f, 0f).height(36f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER);
        addBtn.add(GuiText.label("Add", t.accentText(), 14f));
        add.add(addBtn);
        body.add(add);

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 36f - 12f);
        asColumn(list, inner, 8f);
        body.add(list);
        panel.add(body);
        fill(inner);
    }

    private void rebuildView() {
        panel.clearChildren();
        build();
    }

    private void fill(float inner) {
        list.clearChildren();
        if (items().isEmpty()) {
            list.add(textBox(GuiText.label("No replacements yet.", t.textFaint(), 13f), inner, 24f));
            return;
        }
        for (WordReplacement w : List.copyOf(items())) {
            ContainerComponent r = row(inner - 24f, 10f).height(ROW_H).padding(0f, 12f)
                    .cornerRadius(10f).backgroundColor(GuiColors.of(t.surfaceAlt()))
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()))
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            r.add(textBox(GuiText.label(w.from + "  -> " + w.to, t.text(), 14f), 0f, ROW_H)
                    .flexGrow(1f));

            ButtonComponent onOff = clickable(w.enabled ? t.accent() : t.surface(), () -> {
                w.enabled = !w.enabled;
                ConfigManager.save();
                rebuildView();
            });
            asRow(onOff, 70f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));
            onOff.add(GuiText.label(w.enabled ? "On" : "Off",
                    w.enabled ? t.accentText() : t.textMuted(), 13f));
            r.add(onOff);

            ButtonComponent remove = clickable(t.surface(), () -> {
                items().remove(w);
                ConfigManager.save();
                rebuildView();
            });
            asRow(remove, 90f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));
            remove.add(GuiText.label("Remove", t.textMuted(), 13f));
            r.add(remove);
            list.add(r);
        }
    }
}
