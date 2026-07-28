package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiTextAlignment;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;

/**
 * The one-time welcome, shown the first time the mod runs in an instance.
 *
 * <p>Dismissing it - by either button, or by closing it - records that it has been seen, so it never
 * comes back on its own. Whichever way you leave, you leave having agreed to it; a welcome screen
 * that reappears because you closed it "wrong" is a bad welcome.
 *
 * <p>The theme swatches are live: clicking one switches the theme and rebuilds, so the first thing
 * the screen does is show you it can be your colour.
 */
public class IntroView extends DiegoView {
    private static final float PANEL_W = 760f;
    private static final float PANEL_H = 640f;
    private static final float SWATCH = 56f;

    private boolean finished;

    public IntroView() {
        super("Welcome", PANEL_W, PANEL_H);
    }

    private void finish() {
        if (!finished) {
            finished = true;
            ConfigManager.get().introShown = true;
            ConfigManager.save();
        }
    }

    @Override
    public void onClose() {
        finish();
        super.onClose();
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 14f).height(height).padding(PAD)
                .alignItems(GuiAlignment.CENTER);
        float inner = width - PAD * 2f;

        body.add(centred("Thanks for installing!", t.accent(), 24f, inner, 34f));
        body.add(centred("A rounded menu with its own font, five themes, and modules grouped by "
                + "what they do - each with its own settings.", t.textMuted(), 15f, inner, 48f));

        String key = DiegoAddonsV2Client.OPEN_MENU.getTranslatedKeyMessage().getString();
        body.add(bullet("Press [" + key + "] in game to open the menu", inner));
        body.add(bullet("Five themes, switched whenever you like", inner));
        body.add(bullet("Drag your HUD where you want it from the menu", inner));

        body.add(centred("Pick a colour", t.textFaint(), 13f, inner, 24f));
        ContainerComponent swatches = row(inner, 14f).height(SWATCH + 8f)
                .justifyContent(GuiAlignment.CENTER);
        for (Theme th : Themes.ALL) {
            swatches.add(swatch(th));
        }
        body.add(swatches);

        ContainerComponent buttons = row(inner, 14f).height(48f)
                .justifyContent(GuiAlignment.CENTER);
        ButtonComponent open = clickable(t.surfaceAlt(), () -> {
            finish();
            close();
            new DiegoClickGuiView().open();
        });
        asRow(open, 200f, 0f).height(48f).cornerRadius(10f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        open.add(GuiText.label("Open the menu", t.text(), 15f));
        buttons.add(open);

        ButtonComponent go = clickable(t.accent(), () -> {
            finish();
            close();
        });
        asRow(go, 200f, 0f).height(48f).cornerRadius(10f).justifyContent(GuiAlignment.CENTER);
        go.add(GuiText.label("Get started", t.accentText(), 15f));
        buttons.add(go);
        body.add(buttons);

        panel.add(body);
    }

    private ContainerComponent centred(String text, int color, float size, float width, float height) {
        com.render.api.gui.TextComponent label = GuiText.label(text, color, size)
                .width(width).textAlignment(GuiTextAlignment.CENTER);
        return textBox(label, width, height);
    }

    private ContainerComponent bullet(String text, float width) {
        ContainerComponent r = row(width, 10f).height(28f).justifyContent(GuiAlignment.CENTER);
        ContainerComponent dot = new ContainerComponent();
        dot.size(8f, 8f).cornerRadius(4f).backgroundColor(GuiColors.of(t.accent()));
        r.add(dot);
        r.add(textBox(GuiText.label(text, t.textMuted(), 14f), 0f, 28f));
        return r;
    }

    /** One theme, as a button of its own colours; the current one wears a ring. */
    private ButtonComponent swatch(Theme th) {
        boolean current = th.name().equals(t.name());
        ButtonComponent b = clickable(th.accent(), () -> {
            Themes.select(th);
            panel.clearChildren();
            build();
        });
        asRow(b, SWATCH, 0f).height(SWATCH).cornerRadius(14f)
                .gradient(new com.render.api.gui.GuiGradient()
                        .startColor(GuiColors.of(th.accent()))
                        .endColor(GuiColors.of(th.accentTo())))
                .borderWidth(current ? 3f : 1f)
                .borderColor(GuiColors.of(current ? t.text() : t.border()));
        return b;
    }
}
