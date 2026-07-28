package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.layout.GuiAlignment;

import java.util.function.Consumer;

/**
 * Types a value for a text setting.
 *
 * <p>The settings card shows the value and opens this; it does not let you type into the card
 * itself. A card is a list of rows that rebuild whenever anything changes, and a caret does not
 * survive being rebuilt out from under it - which is exactly what the colour and cycle rows do to
 * their neighbours.
 */
public class TextEntryView extends DiegoView {
    private static final float PANEL_W = 640f;
    private static final float PANEL_H = 260f;

    private final String hint;
    private final Consumer<String> onSave;
    private String value;

    public TextEntryView(String title, String value, String hint, Consumer<String> onSave) {
        super(title, PANEL_W, PANEL_H);
        this.value = value == null ? "" : value;
        this.hint = hint;
        this.onSave = onSave;
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 16f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        body.add(textBox(GuiText.label(hint, t.textFaint(), 13f), inner, 22f));
        body.add(field(value, hint, inner, s -> value = s));

        ContainerComponent buttons = row(inner, 12f).height(44f)
                .justifyContent(GuiAlignment.CENTER);
        ButtonComponent clear = clickable(t.surfaceAlt(), () -> {
            onSave.accept("");
            close();
        });
        asRow(clear, 140f, 0f).height(44f).cornerRadius(10f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        clear.add(GuiText.label("Clear", t.textMuted(), 14f));
        buttons.add(clear);

        ButtonComponent save = clickable(t.accent(), () -> {
            onSave.accept(value);
            close();
        });
        asRow(save, 140f, 0f).height(44f).cornerRadius(10f).justifyContent(GuiAlignment.CENTER);
        save.add(GuiText.label("Save", t.accentText(), 14f));
        buttons.add(save);
        body.add(buttons);

        panel.add(body);
    }
}
